"""Session protocol flow, with scripted stand-ins for the three providers.

These stand-ins exist only here, to test turn logic without network calls.
"""

import asyncio
import json

import pytest
from fastapi import FastAPI, WebSocket
from fastapi.testclient import TestClient

from raphael.providers.base import Final, Partial, SpeechEnded
from raphael.session import Session
from raphael.settings import Settings

FRAME = b"\x01\x00" * 320


class ScriptedASR:
    """Emits a scripted transcript once it has received `after_frames` frames."""

    def __init__(self, script, after_frames=3):
        self.script, self.after, self.frames = script, after_frames, 0
        self.q: asyncio.Queue = asyncio.Queue()

    async def open(self, sample_rate, language, endpoint_ms): ...

    async def send_audio(self, pcm):
        self.frames += 1
        if self.frames == self.after:
            for ev in self.script:
                self.q.put_nowait(ev)

    async def finalize(self):
        self.q.put_nowait(Final("ended early"))
        self.q.put_nowait(SpeechEnded())

    async def events(self):
        while (ev := await self.q.get()) is not None:
            yield ev

    async def close(self):
        self.q.put_nowait(None)


class ScriptedLLM:
    def __init__(self, reply, delay=0.0):
        self.reply, self.delay, self.calls = reply, delay, []

    async def warm(self): ...

    async def stream(self, system, messages, max_tokens):
        self.calls.append(messages)
        for word in self.reply.split(" "):
            await asyncio.sleep(self.delay)
            yield word + " "


class ScriptedTTS:
    def __init__(self):
        self.spoken, self.cancelled = [], []

    async def open(self, voice, sample_rate, language): ...

    async def speak(self, text, context_id):
        self.spoken.append(text)
        for _ in range(3):
            await asyncio.sleep(0)
            yield b"\x00\x01" * 500  # 1000 bytes, not frame-aligned on purpose

    async def cancel(self, context_id):
        self.cancelled.append(context_id)

    async def close(self): ...


class StubProviders:
    def __init__(self, asr_script, reply="Seventy two thousand. Anything else?", llm_delay=0.0):
        self.asr_script, self.tts = asr_script, ScriptedTTS()
        self._llm = ScriptedLLM(reply, llm_delay)

    def new_asr(self, config):
        return ScriptedASR(self.asr_script)

    def llm(self, config):
        return self._llm

    def new_tts(self):
        return self.tts

    def voice_for(self, config):
        return "voice"


def make_client(providers, **settings):
    app = FastAPI()
    cfg = Settings(_env_file=None, no_speech_timeout_s=1.0, finalize_grace_s=0.3, **settings)

    @app.websocket("/v1/session")
    async def ws_endpoint(ws: WebSocket):
        await ws.accept()
        await Session(ws, providers, cfg).run()

    return TestClient(app)


def recv_until(ws, kind):
    """Collect messages until one of type `kind`; binary frames as bytes."""
    got = []
    while True:
        msg = ws.receive()
        if msg.get("bytes") is not None:
            got.append(msg["bytes"])
            continue
        data = json.loads(msg["text"])
        got.append(data)
        if data["type"] == kind:
            return got


def start(ws):
    ws.send_text(json.dumps({"type": "session.start", "config": {}}))
    assert json.loads(ws.receive_text())["type"] == "session.ready"


def test_full_turn():
    script = [Partial("what is"), Final("what is eighteen percent of four lakh"), SpeechEnded()]
    providers = StubProviders(script)
    with make_client(providers).websocket_connect("/v1/session") as ws:
        start(ws)
        ws.send_text(json.dumps({"type": "turn.start", "turn_id": "t1", "trigger": "tap"}))
        for _ in range(5):
            ws.send_bytes(FRAME)
        got = recv_until(ws, "reply.done")

    types = [m["type"] if isinstance(m, dict) else "audio" for m in got]
    assert types.index("transcript.final") < types.index("reply.audio_start") < types.index("audio")
    final = next(m for m in got if isinstance(m, dict) and m["type"] == "transcript.final")
    assert final["text"] == "what is eighteen percent of four lakh"
    audio = [m for m in got if isinstance(m, bytes)]
    assert all(len(f) == 640 for f in audio)
    assert sum(map(len, audio)) >= 6000  # 2 sentences x 3 chunks x 1000 bytes, padded
    assert providers.tts.spoken == ["Seventy two thousand.", "Anything else?"]
    timings = got[-1]["timings"]
    assert timings["total_ms"] is not None and timings["llm_first_sentence_ms"] is not None


def test_history_is_sent_on_next_turn():
    script = [Final("hello"), SpeechEnded()]
    providers = StubProviders(script)
    with make_client(providers).websocket_connect("/v1/session") as ws:
        start(ws)
        for turn_id in ("t1", "t2"):
            ws.send_text(json.dumps({"type": "turn.start", "turn_id": turn_id}))
            for _ in range(4):
                ws.send_bytes(FRAME)
            recv_until(ws, "reply.done")
    second = providers._llm.calls[1]
    assert [m["role"] for m in second] == ["user", "assistant", "user"]


def test_turn_end_finalizes_early():
    providers = StubProviders([Partial("um so")])  # never ends on its own
    with make_client(providers).websocket_connect("/v1/session") as ws:
        start(ws)
        ws.send_text(json.dumps({"type": "turn.start", "turn_id": "t1"}))
        for _ in range(4):
            ws.send_bytes(FRAME)
        recv_until(ws, "transcript.partial")
        ws.send_text(json.dumps({"type": "turn.end", "turn_id": "t1"}))
        got = recv_until(ws, "transcript.final")
    assert got[-1]["text"] == "ended early"  # finalize commits the pending words


def test_no_speech_is_dropped():
    providers = StubProviders([])
    with make_client(providers).websocket_connect("/v1/session") as ws:
        start(ws)
        ws.send_text(json.dumps({"type": "turn.start", "turn_id": "t1"}))
        got = recv_until(ws, "turn.dropped")
    assert got[-1]["reason"] == "no_speech"


def test_cancel_stops_reply():
    script = [Final("tell me a long story"), SpeechEnded()]
    providers = StubProviders(script, reply="One. Two. Three. Four. Five. Six.", llm_delay=0.2)
    with make_client(providers).websocket_connect("/v1/session") as ws:
        start(ws)
        ws.send_text(json.dumps({"type": "turn.start", "turn_id": "t1"}))
        for _ in range(4):
            ws.send_bytes(FRAME)
        recv_until(ws, "reply.audio_start")
        ws.send_text(json.dumps({"type": "turn.cancel", "turn_id": "t1"}))
        ws.send_text(json.dumps({"type": "ping", "t": 1}))
        got = recv_until(ws, "pong")
    assert not any(isinstance(m, dict) and m["type"] == "reply.done" for m in got)
    assert len(providers.tts.spoken) < 6


def test_bad_message_reports_error():
    with make_client(StubProviders([])).websocket_connect("/v1/session") as ws:
        ws.send_text(json.dumps({"type": "nope"}))
        assert json.loads(ws.receive_text())["code"] == "bad_message"


@pytest.mark.parametrize("lang", ["en-IN", "hi-en"])
def test_system_prompt_has_rules(lang):
    from raphael.protocol import AssistantConfig
    from raphael.session import build_system_prompt

    prompt = build_system_prompt(AssistantConfig(language=lang, max_sentences=3))
    assert "at most 3 short sentences" in prompt


def vad_turn(script, frames=4):
    providers = StubProviders(script)
    with make_client(providers).websocket_connect("/v1/session") as ws:
        start(ws)
        ws.send_text(json.dumps({"type": "turn.start", "turn_id": "v1", "trigger": "vad"}))
        for _ in range(frames):
            ws.send_bytes(FRAME)
        got = []
        while True:
            msg = ws.receive()
            if msg.get("bytes") is not None:
                continue
            data = json.loads(msg["text"])
            got.append(data)
            if data["type"] in ("reply.done", "turn.dropped"):
                return got, providers


def test_vad_turn_with_wake_phrase_and_question():
    got, providers = vad_turn([Partial("hey rafael what"), Final("hey rafael what is the time"), SpeechEnded()])
    types = [m["type"] for m in got]
    assert types.index("wake.detected") < types.index("transcript.final")
    assert next(m for m in got if m["type"] == "transcript.final")["text"] == "what is the time"
    partials = [m["text"] for m in got if m["type"] == "transcript.partial"]
    assert all("rafael" not in t for t in partials)  # wake phrase stripped from what the user sees


def test_vad_turn_without_wake_phrase_is_dropped_silently():
    got, providers = vad_turn([Partial("so I was"), Final("so I was thinking we could go"), SpeechEnded()])
    assert got[-1] == {"type": "turn.dropped", "turn_id": "v1", "reason": "no_wake"}
    assert not any(m["type"] == "transcript.partial" for m in got)  # nothing shown
    assert providers._llm.calls == []


def test_vad_turn_pause_after_wake_phrase_waits_for_question():
    got, _ = vad_turn([Final("hey raphael"), SpeechEnded(), Final("how far is the moon"), SpeechEnded()])
    assert next(m for m in got if m["type"] == "transcript.final")["text"] == "how far is the moon"


def test_vad_turn_late_speech_end_does_not_cut_question():
    # Deepgram's late "speech ended" for the wake phrase arrives while the question is still interim.
    got, _ = vad_turn([Final("hey raphael"), Partial("what"), SpeechEnded(), Final("what is the capital of France"), SpeechEnded()])
    assert next(m for m in got if m["type"] == "transcript.final")["text"] == "what is the capital of France"
