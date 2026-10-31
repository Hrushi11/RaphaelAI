"""End-to-end test of the cloud wake word, without the phone.

Acts like the Android app's hands-free mode: opens a session, sends `turn.start` with
trigger "vad", streams speech in real time, and reports whether the backend confirmed the
wake phrase (`wake.detected`), how fast, and what question it extracted. Each turn is
cancelled right after the question is transcribed, so no LLM/TTS cost is spent.

    python tools/wake_test.py                          # built-in cases, macOS `say` voices
    python tools/wake_test.py --mic                    # live: talk to your Mac's mic, like the phone
    python tools/wake_test.py --url ws://localhost:8001/v1/session
    python tools/wake_test.py --wav my_recording.wav --expect wake

Run the backend with DEBUG_WAKE=1 to also log what Deepgram heard in rejected bursts.
"""

import argparse
import asyncio
import json
import subprocess
import tempfile
import time
import uuid
import wave
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from websockets.asyncio.client import connect

SR = 16_000
FRAME = 640  # 20 ms of PCM16


@dataclass
class Case:
    name: str
    parts: list  # strings to speak, or floats = seconds of silence
    voice: str = "Rishi"
    expect: str = "wake"  # "wake" or "ignore"
    narrowband: bool = False  # simulate a Bluetooth call link (8 kHz)


CASES = [
    Case("one breath (Indian English)", ["Hey Raphael, what's the time?"]),
    Case("one breath (US)", ["Hey Raphael, what's the time?"], voice="Samantha"),
    Case("one breath (UK)", ["Hey Raphael, what's the time?"], voice="Daniel"),
    Case("pause after wake phrase", ["Hey Raphael.", 1.2, "What is the capital of France?"]),
    Case("word before wake phrase", ["Okay. Hey Raphael, how far is the moon?"]),
    Case("fast, no comma", ["hey raphael whats eighteen percent of four lakh"]),
    Case("earbuds call quality (8 kHz)", ["Hey Raphael, what's the time?"], narrowband=True),
    Case("name only", ["Raphael, what's the time?"]),
    Case("ignore: normal speech", ["I think we should leave at six today."], expect="ignore"),
    Case("ignore: sound-alike", ["Her file is on the table, can you check it?"], expect="ignore"),
    Case("ignore: hey + other name", ["Hey Rachel, how are you doing?"], expect="ignore"),
    Case("ignore: name-like word mid-sentence", ["The rifle was old and rusty."], expect="ignore"),
]


def say(text: str, voice: str) -> np.ndarray:
    with tempfile.NamedTemporaryFile(suffix=".wav") as f:
        subprocess.run(["say", "-v", voice, "-o", f.name, "--data-format=LEI16@16000", text], check=True)
        return read_wav(Path(f.name))


def read_wav(path: Path) -> np.ndarray:
    with wave.open(str(path)) as w:
        a = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32)
        if w.getnchannels() > 1:
            a = a.reshape(-1, w.getnchannels()).mean(axis=1)
        if w.getframerate() != SR:
            n = int(len(a) * SR / w.getframerate())
            a = np.interp(np.linspace(0, len(a) - 1, n), np.arange(len(a)), a)
    return a


def narrowband(a: np.ndarray) -> np.ndarray:
    spec = np.fft.rfft(a)
    spec[np.fft.rfftfreq(len(a), 1 / SR) > 3800] = 0
    return np.fft.irfft(spec, len(a))


def build_audio(case: Case) -> bytes:
    chunks = [np.zeros(int(0.3 * SR))]  # like the phone's pre-roll before speech
    for p in case.parts:
        chunks.append(np.zeros(int(p * SR)) if isinstance(p, (int, float)) else say(p, case.voice))
    a = np.concatenate(chunks)
    if case.narrowband:
        a = narrowband(a)
    return np.clip(a, -32768, 32767).astype("<i2").tobytes()


async def run_case(url: str, phrase: str, name: str, pcm: bytes) -> dict:
    async with connect(url, max_size=None) as ws:
        config = {"wake": {"phrases": [phrase]}, "asr": {"endpoint_ms": 500}}
        await ws.send(json.dumps({"type": "session.start", "config": config, "client_version": "wake-test"}))
        assert json.loads(await ws.recv())["type"] == "session.ready"
        turn = uuid.uuid4().hex[:8]
        await ws.send(json.dumps({"type": "turn.start", "turn_id": turn, "trigger": "vad", "preroll_ms": 300}))
        t0 = time.perf_counter()
        result = {"case": name, "outcome": "timeout", "wake_ms": None, "question": "", "reason": ""}

        async def sender():
            for i in range(0, len(pcm), FRAME):
                await ws.send(pcm[i:i + FRAME].ljust(FRAME, b"\0"))
                await asyncio.sleep(0.02)
            while True:  # keep streaming silence, like a live mic
                await ws.send(b"\0" * FRAME)
                await asyncio.sleep(0.02)

        send_task = asyncio.create_task(sender())
        try:
            async with asyncio.timeout(20):
                async for raw in ws:
                    if isinstance(raw, bytes):
                        continue
                    m = json.loads(raw)
                    if m.get("turn_id") != turn:
                        continue
                    if m["type"] == "wake.detected":
                        result["wake_ms"] = round((time.perf_counter() - t0) * 1000)
                        result["outcome"] = "wake"
                    elif m["type"] == "transcript.final":
                        result["question"] = m["text"]
                        await ws.send(json.dumps({"type": "turn.cancel", "turn_id": turn}))  # skip LLM/TTS
                        break
                    elif m["type"] == "turn.dropped":
                        result["outcome"] = "ignored" if m["reason"] == "no_wake" else "dropped"
                        result["reason"] = m["reason"]
                        break
        except TimeoutError:
            pass
        finally:
            send_task.cancel()
        return result


# ---------------------------------------------------------------- live mic mode

VAD_MODEL = Path(__file__).resolve().parents[2] / "app-android/app/src/main/assets/vad/silero_vad.onnx"
PREROLL_FRAMES = 35  # 700 ms, same as the phone


def make_vad():
    """Silero VAD with the same settings as the phone (app-android/.../wake/WakeWord.kt)."""
    import sherpa_onnx

    cfg = sherpa_onnx.VadModelConfig()
    cfg.silero_vad.model = str(VAD_MODEL)
    cfg.silero_vad.threshold = 0.5
    cfg.silero_vad.min_silence_duration = 0.3
    cfg.silero_vad.min_speech_duration = 0.2
    cfg.silero_vad.window_size = 512
    cfg.silero_vad.max_speech_duration = 20
    cfg.sample_rate = SR
    return sherpa_onnx.VoiceActivityDetector(cfg, buffer_size_in_seconds=30)


async def mic_mode(url: str, phrase: str, answer: bool) -> None:
    """Listen on the Mac mic exactly like the phone's hands-free mode and report each wake check."""
    import collections

    import sounddevice as sd

    vad = make_vad()
    loop = asyncio.get_running_loop()
    frames: asyncio.Queue[bytes] = asyncio.Queue()
    ring: collections.deque[bytes] = collections.deque(maxlen=PREROLL_FRAMES)
    state = {"turn": None, "busy": False, "wait_silence": False, "t0": 0.0}

    async with connect(url, max_size=None) as ws:
        config = {"wake": {"phrases": [phrase]}, "asr": {"endpoint_ms": 500}}
        await ws.send(json.dumps({"type": "session.start", "config": config, "client_version": "wake-test-mic"}))
        assert json.loads(await ws.recv())["type"] == "session.ready"
        print(f"Listening on the Mac mic for “{phrase}”. Speak normally; Ctrl+C to stop.\n")

        def end_turn():
            state["turn"], state["busy"] = None, False
            vad.reset()

        async def receiver():
            async for raw in ws:
                if isinstance(raw, bytes):
                    continue
                m = json.loads(raw)
                if m.get("turn_id") != state["turn"]:
                    continue
                dt = round((time.perf_counter() - state["t0"]) * 1000)
                kind = m["type"]
                if kind == "wake.detected":
                    print(f"   ✓ WAKE “{m['phrase']}” confirmed {dt} ms after you started speaking")
                elif kind == "transcript.partial":
                    print(f"     … {m['text']}", end="\r")
                elif kind == "transcript.final":
                    print(f"   ? question: {m['text']!r}" + " " * 20)
                    if not answer:
                        await ws.send(json.dumps({"type": "turn.cancel", "turn_id": state["turn"]}))
                        end_turn()
                elif kind == "reply.done":
                    end_turn()
                elif kind == "reply.text":
                    print(f"     {m['delta']}", end="")
                elif kind == "turn.dropped":
                    why = {"no_wake": "no wake phrase heard (backend log shows what with DEBUG_WAKE=1)"}.get(m["reason"], m["reason"])
                    print(f"   ✗ ignored: {why}")
                    end_turn()
                elif kind == "error":
                    print(f"   ! error {m.get('code')}: {m.get('message')}")

        def callback(indata, _frames, _time, _status):
            loop.call_soon_threadsafe(frames.put_nowait, bytes(indata))

        recv_task = asyncio.create_task(receiver())
        window = np.zeros(0, dtype=np.float32)
        with sd.RawInputStream(samplerate=SR, channels=1, dtype="int16", blocksize=FRAME // 2, callback=callback):
            try:
                while True:
                    frame = await frames.get()
                    if state["busy"]:
                        await ws.send(frame)  # mid-turn: stream everything, like the phone
                        continue
                    ring.append(frame)
                    window = np.concatenate([window, np.frombuffer(frame, dtype="<i2").astype(np.float32) / 32768])
                    while len(window) >= 512:
                        vad.accept_waveform(window[:512])
                        window = window[512:]
                        while not vad.empty():
                            vad.pop()
                    speech = vad.is_speech_detected()
                    if state["wait_silence"]:
                        state["wait_silence"] = speech
                    elif speech:
                        state["wait_silence"] = True  # one turn per utterance
                        turn = uuid.uuid4().hex[:8]
                        state.update(turn=turn, busy=True, t0=time.perf_counter() - len(ring) * 0.02)
                        print(f"● speech detected → wake check (turn {turn})")
                        await ws.send(json.dumps({"type": "turn.start", "turn_id": turn, "trigger": "vad", "preroll_ms": len(ring) * 20}))
                        for f in list(ring):
                            await ws.send(f)
                        ring.clear()
            finally:
                recv_task.cancel()


async def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--url", default="ws://localhost:8000/v1/session")
    ap.add_argument("--phrase", default="hey raphael")
    ap.add_argument("--wav", type=Path, help="test one recording instead of the built-in cases")
    ap.add_argument("--expect", choices=["wake", "ignore"], default="wake")
    ap.add_argument("--mic", action="store_true", help="live test with your voice on the Mac mic")
    ap.add_argument("--answer", action="store_true", help="with --mic: let Raphael answer (costs LLM/TTS); default stops after the question")
    args = ap.parse_args()

    if args.mic:
        await mic_mode(args.url, args.phrase, args.answer)
        return

    if args.wav:
        runs = [(Case(args.wav.name, [], expect=args.expect), np.clip(read_wav(args.wav), -32768, 32767).astype("<i2").tobytes())]
    else:
        runs = [(c, build_audio(c)) for c in CASES]

    passed = 0
    for case, pcm in runs:
        r = await run_case(args.url, args.phrase, case.name, pcm)
        ok = (r["outcome"] == "wake") == (case.expect == "wake")
        passed += ok
        detail = f"wake after {r['wake_ms']} ms, question: {r['question']!r}" if r["outcome"] == "wake" else (r["reason"] or r["outcome"])
        print(f"{'PASS' if ok else 'FAIL'}  {case.name:32s} expect {case.expect:6s} → {r['outcome']:8s} {detail}")
    print(f"\n{passed}/{len(runs)} passed")


if __name__ == "__main__":
    asyncio.run(main())
