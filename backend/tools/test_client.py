"""Laptop stand-in for the phone app: mic -> backend -> speaker over /v1/session.

    python tools/test_client.py                   # talk with Enter as the "earbud tap"
    python tools/test_client.py --wav q.wav       # send a recorded question, then exit
    python tools/test_client.py --list-devices

Keys (press Enter after each):
    Enter   idle: start a turn (like tapping the earbud)
            listening: end the question now (otherwise it ends after a short pause)
            thinking/speaking: interrupt (barge-in)
    q       quit

Use headphones: the laptop mic is muted while Raphael speaks, but the
backend never hears your speakers that way either.
"""

import argparse
import asyncio
import collections
import json
import sys
import threading
import time
import uuid
import wave
from pathlib import Path

import numpy as np
import sounddevice as sd
from websockets.asyncio.client import connect

SAMPLE_RATE = 16_000
FRAME_SAMPLES = 320  # 20 ms
FRAME_BYTES = FRAME_SAMPLES * 2
PREROLL_FRAMES = 15  # 300 ms of audio from just before the trigger

DIM, BOLD, CYAN, GREEN, YELLOW, RED, RESET = "\033[2m", "\033[1m", "\033[36m", "\033[32m", "\033[33m", "\033[31m", "\033[0m"


class Player:
    """Plays PCM16 as it arrives; flush() drops everything queued (barge-in)."""

    def __init__(self, device=None) -> None:
        self._buf = bytearray()
        self._lock = threading.Lock()
        self._stream = sd.RawOutputStream(
            samplerate=SAMPLE_RATE, channels=1, dtype="int16", blocksize=FRAME_SAMPLES,
            device=device, callback=self._callback,
        )
        self._stream.start()

    def _callback(self, outdata, frames, _time, _status) -> None:
        n = frames * 2
        with self._lock:
            chunk = bytes(self._buf[:n])
            del self._buf[:n]
        outdata[: len(chunk)] = chunk
        outdata[len(chunk):] = b"\0" * (n - len(chunk))

    def write(self, pcm: bytes) -> None:
        with self._lock:
            self._buf += pcm

    def flush(self) -> None:
        with self._lock:
            self._buf.clear()

    @property
    def queued_s(self) -> float:
        with self._lock:
            return len(self._buf) / 2 / SAMPLE_RATE

    def close(self) -> None:
        self._stream.stop()
        self._stream.close()


class Client:
    def __init__(self, args) -> None:
        self.args = args
        self.state = "idle"  # idle | listening | thinking | speaking
        self.turn_id: str | None = None
        self.ws = None
        self.player: Player | None = None if args.no_play else Player(args.output_device)
        self.ring: collections.deque[bytes] = collections.deque(maxlen=PREROLL_FRAMES)
        self.reply_audio = bytearray()
        self.turn_done = asyncio.Event()
        # client-side latency marks
        self.t_question_end: float | None = None
        self.t_first_audio: float | None = None

    # ------------------------------------------------------------ output

    def show(self, text: str, end: str = "\n") -> None:
        sys.stdout.write("\r\033[K" + text + end)
        sys.stdout.flush()

    def set_state(self, state: str) -> None:
        self.state = state
        colors = {"idle": DIM, "listening": GREEN, "thinking": YELLOW, "speaking": CYAN}
        self.show(f"{colors[state]}● {state.upper()}{RESET}")

    # ------------------------------------------------------------ turn control

    async def start_turn(self) -> None:
        self.turn_id = uuid.uuid4().hex[:8]
        self.turn_done.clear()
        self.reply_audio.clear()
        self.t_question_end = self.t_first_audio = None
        await self.ws.send(json.dumps({"type": "turn.start", "turn_id": self.turn_id, "trigger": "tap",
                                       "preroll_ms": len(self.ring) * 20}))
        for frame in list(self.ring):
            await self.ws.send(frame)
        self.set_state("listening")

    async def end_turn(self) -> None:
        await self.ws.send(json.dumps({"type": "turn.end", "turn_id": self.turn_id}))

    async def cancel_turn(self) -> None:
        await self.ws.send(json.dumps({"type": "turn.cancel", "turn_id": self.turn_id}))
        if self.player:
            self.player.flush()
        self.turn_done.set()
        self.set_state("idle")

    # ------------------------------------------------------------ tasks

    async def receive(self) -> None:
        async for raw in self.ws:
            if isinstance(raw, bytes):
                if self.state != "speaking":
                    continue  # audio from a cancelled turn
                if self.t_first_audio is None:
                    self.t_first_audio = time.perf_counter()
                self.reply_audio += raw
                if self.player:
                    self.player.write(raw)
                continue
            msg = json.loads(raw)
            kind = msg.get("type")
            if msg.get("turn_id") not in (None, self.turn_id):
                continue
            if kind == "transcript.partial":
                self.show(f"{DIM}you: {msg['text']}{RESET}", end="")
            elif kind == "transcript.final":
                self.t_question_end = self.t_question_end or time.perf_counter()
                self.show(f"{BOLD}you:{RESET} {msg['text']}")
                self.set_state("thinking")
            elif kind == "reply.text":
                pass  # printed whole on reply.done
            elif kind == "reply.audio_start":
                self.state = "speaking"
            elif kind == "reply.done":
                asyncio.create_task(self.finish_reply(msg.get("timings", {})))
            elif kind == "turn.dropped":
                self.show(f"{YELLOW}turn dropped: {msg['reason']}{RESET}")
                self.set_state("idle")
                self.turn_done.set()
            elif kind == "error":
                self.show(f"{RED}error {msg['code']}: {msg['message']}{RESET}")
            elif kind == "session.ready":
                pass

    async def finish_reply(self, timings: dict) -> None:
        if self.state != "speaking":
            return
        heard = None
        if self.t_question_end and self.t_first_audio:
            heard = round((self.t_first_audio - self.t_question_end) * 1000)
        parts = [f"{k.removesuffix('_ms')}={v}" for k, v in timings.items() if v is not None]
        self.show(f"{DIM}server ms: {' '.join(parts)} | client: transcript→first audio={heard}{RESET}")
        while self.player and self.player.queued_s > 0 and self.state == "speaking":
            await asyncio.sleep(0.05)
        if self.state == "speaking":
            self.set_state("idle")
            self.turn_done.set()

    async def keyboard(self) -> None:
        loop = asyncio.get_running_loop()
        while True:
            line = await loop.run_in_executor(None, sys.stdin.readline)
            if line == "" or line.strip().lower() == "q":  # EOF or quit
                return
            if self.state == "idle":
                await self.start_turn()
            elif self.state == "listening":
                await self.end_turn()
            else:
                await self.cancel_turn()

    async def mic(self) -> None:
        loop = asyncio.get_running_loop()
        frames: asyncio.Queue[bytes] = asyncio.Queue()

        def callback(indata, _frames, _time, _status) -> None:
            loop.call_soon_threadsafe(frames.put_nowait, bytes(indata))

        with sd.RawInputStream(samplerate=SAMPLE_RATE, channels=1, dtype="int16", blocksize=FRAME_SAMPLES,
                               device=self.args.input_device, callback=callback):
            while True:
                frame = await frames.get()
                self.ring.append(frame)
                if self.state == "listening":
                    await self.ws.send(frame)

    # ------------------------------------------------------------ modes

    async def connect(self) -> None:
        url = self.args.url + (f"?token={self.args.token}" if self.args.token else "")
        self.ws = await connect(url, max_size=None)
        config = {"assistant": {"max_sentences": 2, "language": "en-IN"}, "asr": {"endpoint_ms": self.args.endpoint_ms}}
        await self.ws.send(json.dumps({"type": "session.start", "config": config, "client_version": "laptop-0.1"}))
        ready = json.loads(await self.ws.recv())
        if ready.get("type") != "session.ready":
            raise SystemExit(f"unexpected reply: {ready}")
        self.show(f"{GREEN}connected{RESET} session={ready['session_id']}")

    async def run_interactive(self) -> None:
        await self.connect()
        print("Press Enter, ask a question, pause. Enter again to interrupt. q + Enter quits.")
        self.set_state("idle")
        tasks = [asyncio.create_task(self.receive()), asyncio.create_task(self.mic())]
        try:
            await self.keyboard()
        finally:
            for t in tasks:
                t.cancel()
            await self.ws.close()

    async def run_wav(self, path: Path) -> None:
        pcm = load_wav(path)
        await self.connect()
        receiver = asyncio.create_task(self.receive())
        await self.start_turn()
        for i in range(0, len(pcm), FRAME_BYTES):  # real-time pace, like a mic
            await self.ws.send(pcm[i:i + FRAME_BYTES].ljust(FRAME_BYTES, b"\0"))
            await asyncio.sleep(0.02)
        self.t_question_end = time.perf_counter()  # end of the recorded speech
        silence = b"\0" * FRAME_BYTES
        while self.state == "listening":
            await self.ws.send(silence)
            await asyncio.sleep(0.02)
        try:
            await asyncio.wait_for(self.turn_done.wait(), 30)
        finally:
            receiver.cancel()
            await self.ws.close()
        if self.args.save_reply and self.reply_audio:
            save_wav(Path(self.args.save_reply), bytes(self.reply_audio))
            print(f"reply saved to {self.args.save_reply}")


def load_wav(path: Path) -> bytes:
    with wave.open(str(path)) as w:
        if w.getsampwidth() != 2:
            raise SystemExit("wav must be 16-bit PCM")
        data = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16)
        if w.getnchannels() > 1:
            data = data.reshape(-1, w.getnchannels()).mean(axis=1).astype(np.int16)
        if w.getframerate() != SAMPLE_RATE:
            n = int(len(data) * SAMPLE_RATE / w.getframerate())
            data = np.interp(np.linspace(0, len(data) - 1, n), np.arange(len(data)), data).astype(np.int16)
    return data.tobytes()


def save_wav(path: Path, pcm: bytes) -> None:
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SAMPLE_RATE)
        w.writeframes(pcm)


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--url", default="ws://localhost:8000/v1/session")
    ap.add_argument("--token", default=None)
    ap.add_argument("--wav", type=Path, help="send this recording as the question instead of using the mic")
    ap.add_argument("--save-reply", help="with --wav: write the reply audio to this file")
    ap.add_argument("--no-play", action="store_true", help="don't play reply audio")
    ap.add_argument("--endpoint-ms", type=int, default=500, help="silence that ends a question (200-1500)")
    ap.add_argument("--input-device", type=int, default=None)
    ap.add_argument("--output-device", type=int, default=None)
    ap.add_argument("--list-devices", action="store_true")
    args = ap.parse_args()

    if args.list_devices:
        print(sd.query_devices())
        return
    client = Client(args)
    try:
        asyncio.run(client.run_wav(args.wav) if args.wav else client.run_interactive())
    except KeyboardInterrupt:
        pass
    finally:
        if client.player:
            client.player.close()


if __name__ == "__main__":
    main()
