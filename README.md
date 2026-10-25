# Raphael AI

Turns any Bluetooth headset into a voice assistant. Design: [`spec/spec.md`](spec/spec.md).

```text
backend/        Python 3.11 + FastAPI: STT (Deepgram) → LLM (Claude) → TTS (Cartesia)
  raphael/        the service
  tools/          test_client.py (laptop mic client), export_schema.py
  tests/
protocol/       session protocol notes + config JSON Schema (shared with the app)
spec/           design spec
app-android/    Kotlin + Compose app (currently: headset audio test)
```

> Open and build the Android project via the space-free path `~/raphael/app-android`
> (symlink to this repo); Gradle breaks on the space in `Raphael AI`.

## Backend: first run

```bash
# one-time: Python 3.11 venv at the repo root
/opt/homebrew/bin/python3.11 -m venv venv
venv/bin/pip install -e "backend[client,dev]"     # client extra needs: brew install portaudio

# keys
cp backend/.env.example backend/.env              # then fill in the three API keys

# run the server
cd backend
../venv/bin/uvicorn raphael.main:app --host 0.0.0.0 --port 8000
```

## Talk to it from the laptop

In a second terminal (use headphones):

```bash
cd backend
../venv/bin/python tools/test_client.py
```

Press **Enter** (your "earbud tap"), ask a question, then pause. Raphael answers out loud.
Press Enter while it's speaking to interrupt it. Each answer prints the per-stage latency.

Scripted test without a mic:

```bash
say -o q.wav --data-format=LEI16@16000 "What is eighteen percent of four lakh?"
../venv/bin/python tools/test_client.py --wav q.wav --save-reply reply.wav
```

## Tests

```bash
cd backend && ../venv/bin/python -m pytest -q
```
