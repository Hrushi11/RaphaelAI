"""One Session per WebSocket connection (spec §4.5).

A turn runs as one asyncio task: listen (audio -> STT) and then reply, where
the LLM and TTS stages run side by side joined by a sentence queue. Cancelling
the turn task cancels all of it.
"""

import asyncio
import logging
import time
from collections import deque
from contextlib import suppress
from typing import AsyncIterator
from uuid import uuid4

from fastapi import WebSocket
from pydantic import ValidationError

from . import protocol as p
from . import wakeword
from .chunker import SentenceChunker
from .providers import Providers
from .providers.base import ASRProvider, Final, Message, Partial, ProviderError, SpeechEnded
from .settings import Settings

log = logging.getLogger("raphael.session")

_END_REQUESTED = object()  # put on a turn's event queue when the phone sends turn.end
_ASR_CLOSED = object()


def now_ms() -> float:
    return time.perf_counter() * 1000


class Turn:
    def __init__(self, turn_id: str, trigger: str) -> None:
        self.id = turn_id
        self.trigger = trigger
        self.audio: asyncio.Queue[bytes] = asyncio.Queue()
        self.events: asyncio.Queue = asyncio.Queue()  # ASR events + _END_REQUESTED
        self.listening = True
        self.task: asyncio.Task | None = None


def build_system_prompt(cfg: p.AssistantConfig) -> str:
    rules = (
        "You are speaking into the user's earpiece, so everything you write is read aloud. "
        f"Answer in at most {cfg.max_sentences} short sentences unless the user asks for more. "
        "No lists, markdown, URLs or emoji. "
        "Write numbers the way they are spoken, for example 'four lakh' or 'eighteen percent'. "
        "Use metric units and Indian number words (lakh, crore) where natural. "
        "If you are unsure, say so briefly instead of guessing. "
        "The user's words come from speech recognition and may contain mishearings; "
        "read them charitably (for example 'forelock' is probably 'four lakh')."
    )
    if cfg.language == "hi-en":
        rules += " The user may mix Hindi and English; reply in the same natural mix."
    return f"{cfg.persona.strip()}\n\n{rules}"


class Session:
    def __init__(self, ws: WebSocket, providers: Providers, settings: Settings) -> None:
        self.ws = ws
        self.providers = providers
        self.settings = settings
        self.id = uuid4().hex[:12]
        self.config = p.RaphaelConfig()
        self.history: deque[tuple[str, str]] = deque(maxlen=settings.history_turns)
        self.turn: Turn | None = None
        self.tts = providers.new_tts()
        self._send_lock = asyncio.Lock()
        self._closed = False
        self._background: set[asyncio.Task] = set()

    # ------------------------------------------------------------ connection

    async def run(self) -> None:
        log.info("[%s] connected", self.id)
        try:
            while True:
                msg = await self.ws.receive()
                if msg["type"] == "websocket.disconnect":
                    return
                if msg.get("bytes") is not None:
                    self._on_audio(msg["bytes"])
                elif msg.get("text") is not None:
                    await self._on_text(msg["text"])
        finally:
            self._closed = True
            await self._cancel_turn()
            await self.tts.close()
            log.info("[%s] disconnected", self.id)

    async def send_json(self, obj: dict) -> None:
        await self._send(self.ws.send_json, obj)

    async def send_bytes(self, data: bytes) -> None:
        await self._send(self.ws.send_bytes, data)

    async def _send(self, fn, payload) -> None:
        if self._closed:
            return
        async with self._send_lock:
            try:
                await fn(payload)
            except Exception:  # noqa: BLE001 - socket gone; receive loop will notice
                self._closed = True

    def _spawn(self, coro) -> None:
        task = asyncio.create_task(coro)
        self._background.add(task)
        task.add_done_callback(self._background.discard)

    # ------------------------------------------------------------ inbound

    def _on_audio(self, data: bytes) -> None:
        turn = self.turn
        if turn and turn.listening:
            turn.audio.put_nowait(data)
        # Audio outside a listening turn is dropped, never stored.

    async def _on_text(self, text: str) -> None:
        try:
            msg = p.client_message.validate_json(text)
        except ValidationError as e:
            await self.send_json(p.error("bad_message", str(e)[:500]))
            return

        match msg:
            case p.SessionStart() | p.ConfigUpdate():
                self.config = msg.config
                if isinstance(msg, p.SessionStart):
                    log.info("[%s] session.start client=%s", self.id, msg.client_version)
                    await self.send_json(p.session_ready(self.id))
                self._spawn(self._warm_tts())
                self._spawn(self.providers.llm(self.config).warm())
            case p.TurnStart():
                await self._cancel_turn()
                turn = Turn(msg.turn_id, msg.trigger)
                self.turn = turn
                turn.task = asyncio.create_task(self._run_turn(turn))
                # Idle HTTPS connections get closed; reopen while the user is still talking.
                self._spawn(self.providers.llm(self.config).warm())
                log.info("[%s] turn %s started (%s)", self.id, turn.id, turn.trigger)
            case p.TurnEnd():
                if self.turn and self.turn.id == msg.turn_id:
                    self.turn.events.put_nowait(_END_REQUESTED)
            case p.TurnCancel():
                if self.turn and self.turn.id == msg.turn_id:
                    log.info("[%s] turn %s cancelled", self.id, msg.turn_id)
                    await self._cancel_turn()
            case p.Ping():
                await self.send_json(p.pong(msg.t))

    async def _warm_tts(self) -> None:
        """Open the TTS socket early so the first reply doesn't pay for it."""
        try:
            await self.tts.open(self.providers.voice_for(self.config), p.SAMPLE_RATE, self.config.assistant.language)
        except ProviderError as e:
            log.warning("[%s] TTS warm-up failed: %s", self.id, e)

    async def _cancel_turn(self) -> None:
        turn, self.turn = self.turn, None
        if turn and turn.task and not turn.task.done():
            turn.task.cancel()
            with suppress(asyncio.CancelledError, Exception):
                await turn.task

    # ------------------------------------------------------------ one turn

    async def _run_turn(self, turn: Turn) -> None:
        try:
            heard = await self._listen(turn)
            if heard is not None:
                await self._reply(turn, *heard)
        except ProviderError as e:
            log.warning("[%s] turn %s failed: %s", self.id, turn.id, e)
            await self.send_json(p.error(f"{e.stage}_failed", str(e), e.retryable, turn.id))
            if e.stage == "asr" and await self._speak_notice(turn, "Sorry, I didn't catch that."):
                return
            await self.send_json(p.turn_dropped(turn.id, f"{e.stage}_failed"))
        except Exception as e:  # noqa: BLE001
            log.exception("[%s] turn %s crashed", self.id, turn.id)
            await self.send_json(p.error("internal", repr(e), True, turn.id))
            await self.send_json(p.turn_dropped(turn.id, "internal"))
        finally:
            if self.turn is turn:
                self.turn = None

    async def _listen(self, turn: Turn) -> tuple[str, float, float] | None:
        """Stream the question to STT. Returns (text, t_end_ms, eos_ms) or None if dropped."""
        s = self.settings
        asr = self.providers.new_asr(self.config)
        await asr.open(p.SAMPLE_RATE, self.config.assistant.language, self.config.asr.endpoint_ms)

        async def pump() -> None:
            async for ev in asr.events():
                turn.events.put_nowait(ev)
            turn.events.put_nowait(_ASR_CLOSED)

        fed_bytes = 0

        async def feed() -> None:
            nonlocal fed_bytes
            while True:
                pcm = await turn.audio.get()
                await asr.send_audio(pcm)
                fed_bytes += len(pcm)

        tasks = [asyncio.create_task(pump()), asyncio.create_task(feed())]
        finals: list[str] = []
        interim = ""
        start = now_ms()
        last_text_at = start
        speech_end_s: float | None = None
        finalize_deadline: float | None = None
        # "vad" turns: the phone heard speech; keep the turn only if it starts with the wake phrase.
        gated = turn.trigger == "vad"
        phrases = self.config.wake.phrases
        woke = not gated
        question_from = start  # when we started waiting for the question

        def full_text() -> str:
            return " ".join([*finals, interim]).strip()

        def question() -> str:
            if not gated:
                return full_text()
            m = wakeword.match(full_text(), phrases)
            return m[1] if m else ""

        def committed_question() -> str:
            """The question from finalised segments only (no interim words)."""
            text = " ".join(finals).strip()
            if not gated:
                return text
            m = wakeword.match(text, phrases)
            return m[1] if m else ""

        async def finalize() -> float:
            await asr.finalize()
            return now_ms() + s.finalize_grace_s * 1000

        async def drop(reason: str) -> None:
            # No transcript in logs or messages: speech without the wake phrase is discarded,
            # unless DEBUG_WAKE=1 is set while debugging the wake word.
            log.info("[%s] turn %s dropped: %s", self.id, turn.id, reason)
            if s.debug_wake and gated:
                log.info("[%s]   DEBUG_WAKE heard %r (wake phrases %s, %.0f ms after turn start)",
                         self.id, full_text(), phrases, now_ms() - start)
            await self.send_json(p.turn_dropped(turn.id, reason))

        try:
            while True:
                deadline = start + s.max_turn_s * 1000
                if not woke:
                    deadline = min(deadline, start + s.wake_check_s * 1000)
                elif not question():
                    deadline = min(deadline, question_from + s.no_speech_timeout_s * 1000)
                if finalize_deadline:
                    deadline = min(deadline, finalize_deadline)
                try:
                    ev = await asyncio.wait_for(turn.events.get(), max(0.0, deadline - now_ms()) / 1000)
                except TimeoutError:
                    if not woke:
                        await drop("no_wake")
                        return None
                    if not question():
                        await drop("no_speech")
                        return None
                    if finalize_deadline:
                        break  # ASR never confirmed; go with what we have
                    finalize_deadline = await finalize()  # question too long
                    continue

                if ev is _ASR_CLOSED:
                    if tasks[1].done() and tasks[1].exception():
                        raise tasks[1].exception()
                    break
                if ev is _END_REQUESTED:
                    if finalize_deadline is None:
                        finalize_deadline = await finalize()
                    continue
                if isinstance(ev, Partial):
                    interim, last_text_at = ev.text, now_ms()
                    speech_end_s = ev.speech_end_s or speech_end_s
                elif isinstance(ev, Final):
                    interim, last_text_at = "", now_ms()
                    speech_end_s = ev.speech_end_s or speech_end_s
                    finals.append(ev.text)
                elif isinstance(ev, SpeechEnded):
                    if not woke:
                        await drop("no_wake")
                        return None
                    # End only once part of the question is final. A late "speech ended" for the
                    # wake phrase can arrive while the question has only just started (interim text).
                    if committed_question():
                        break
                    continue  # paused after "Hey Raphael": keep waiting for the question

                if not woke:
                    m = wakeword.match(full_text(), phrases)
                    if m is None:
                        if wakeword.ruled_out(full_text(), phrases):
                            await drop("no_wake")
                            return None
                        continue
                    woke, question_from = True, now_ms()
                    log.info("[%s] turn %s: wake phrase '%s'", self.id, turn.id, m[0])
                    if s.debug_wake:
                        log.info("[%s]   DEBUG_WAKE matched in %r after %.0f ms", self.id, full_text(), now_ms() - start)
                    await self.send_json(p.wake_detected(turn.id, m[0]))
                if q := question():
                    await self.send_json(p.transcript_partial(turn.id, q))
        finally:
            turn.listening = False
            for t in tasks:
                t.cancel()
            self._spawn(self._close_asr(asr))

        t_end = now_ms()
        text = question()
        if not text:
            await drop("empty")
            return None
        log.info("[%s] Q: %s", self.id, text)
        if speech_end_s is not None:
            # Audio sent so far minus where the last word ended = silence we waited through.
            eos_ms = max(0.0, fed_bytes / 32 - speech_end_s * 1000)  # 32 bytes per ms at 16 kHz PCM16
        else:
            eos_ms = t_end - last_text_at
        return text, t_end, eos_ms

    @staticmethod
    async def _close_asr(asr: ASRProvider) -> None:
        with suppress(Exception):
            await asr.close()

    async def _reply(self, turn: Turn, question: str, t_end: float, eos_ms: float) -> None:
        await self.send_json(p.transcript_final(turn.id, question))
        cfg = self.config.assistant
        messages: list[Message] = []
        for q, a in self.history:
            messages += [{"role": "user", "content": q}, {"role": "assistant", "content": a}]
        messages.append({"role": "user", "content": question})

        marks: dict[str, float] = {}
        reply: list[str] = []
        sentences: asyncio.Queue[str | None] = asyncio.Queue()
        llm = self.providers.llm(self.config)

        async def llm_stage() -> None:
            chunker = SentenceChunker()
            try:
                stream = llm.stream(build_system_prompt(cfg), messages, max_tokens=100 * cfg.max_sentences)
                async for delta in stream:
                    marks.setdefault("first_token", now_ms())
                    reply.append(delta)
                    await self.send_json(p.reply_text(turn.id, delta))
                    for sentence in chunker.feed(delta):
                        marks.setdefault("first_sentence", now_ms())
                        sentences.put_nowait(sentence)
                for sentence in chunker.flush():
                    marks.setdefault("first_sentence", now_ms())
                    sentences.put_nowait(sentence)
                marks["llm_done"] = now_ms()
            finally:
                sentences.put_nowait(None)

        async def queued() -> AsyncIterator[str]:
            while (sentence := await sentences.get()) is not None:
                yield sentence

        try:
            async with asyncio.TaskGroup() as tg:
                tg.create_task(llm_stage())
                tg.create_task(self._speak(turn, queued(), marks))
        except BaseExceptionGroup as eg:
            raise eg.exceptions[0] from None

        answer = "".join(reply).strip()
        log.info("[%s] A: %s", self.id, answer)
        self.history.append((question, answer))

        def since(mark: str, ref: float) -> int | None:
            return round(marks[mark] - ref) if mark in marks else None

        timings = {
            "eos_ms": round(eos_ms),
            "llm_first_token_ms": since("first_token", t_end),
            "llm_first_sentence_ms": since("first_sentence", t_end),
            "tts_first_audio_ms": since("first_audio", marks.get("first_sentence", t_end)),
            "first_audio_ms": since("first_audio", t_end),
            "total_ms": round(eos_ms + marks["first_audio"] - t_end) if "first_audio" in marks else None,
            "llm_total_ms": since("llm_done", t_end),
        }
        log.info("[%s] timings %s", self.id, timings)
        await self.send_json(p.reply_done(turn.id, timings))

    async def _speak(self, turn: Turn, sentences: AsyncIterator[str], marks: dict[str, float]) -> None:
        """TTS each sentence and stream it to the phone in 20 ms frames."""
        buf = bytearray()
        i = 0
        async for sentence in sentences:
            async for pcm in self.tts.speak(sentence, f"{turn.id}-{i}"):
                if "first_audio" not in marks:
                    marks["first_audio"] = now_ms()
                    await self.send_json(p.reply_audio_start(turn.id))
                buf += pcm
                while len(buf) >= p.FRAME_BYTES:
                    await self.send_bytes(bytes(buf[: p.FRAME_BYTES]))
                    del buf[: p.FRAME_BYTES]
            i += 1
        if buf:
            await self.send_bytes(bytes(buf.ljust(p.FRAME_BYTES, b"\0")))

    async def _speak_notice(self, turn: Turn, text: str) -> bool:
        """Say a short fixed line (e.g. after an STT failure). True if it played."""

        async def one() -> AsyncIterator[str]:
            yield text

        marks: dict[str, float] = {}
        try:
            await self._speak(turn, one(), marks)
        except ProviderError:
            return False
        await self.send_json(p.reply_done(turn.id, {}))
        return "first_audio" in marks
