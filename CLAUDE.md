# Raphael — project context for Claude

Raphael turns any Bluetooth headset into a voice assistant: wake word (or earbud tap) → speech-to-text → LLM → text-to-speech → answer in your ear in ~1.5 s. Named after the advisor in "That Time I Got Reincarnated as a Slime". Long-term vision: an ambient copilot that listens to live conversations (meetings, negotiations) and whispers useful facts at the right moment. v0 is only the wake-word assistant.

Built for the **Bolna Buildathon** (voice AI hackathon; sponsors include Deepgram, Sarvam, Cartesia, Plivo, Exotel). **Submission deadline: 2026-10-31.** Shortlist Nov 3, interviews Nov 5–7, top 3 pitch Nov 20 in Bengaluru; winner gets a YC interview.

## The spec is the source of truth

@spec/spec.md

Read it before making design decisions. If something we decide changes the design, update `spec/spec.md` in the same change.

## About me (the developer)

- Mid-senior engineer, learns fast when given clear direction.
- **No Android experience** (never built, tested or deployed an Android app). Explain Android concepts briefly when they first come up, prefer standard/well-documented approaches, and give exact steps for Android Studio, device setup and ADB.
- Comfortable with Python; backend work can move faster with less explanation.
- Working on macOS. Repo lives at `/Volumes/Raphael/Projects/Raphael AI`.
- Timezone: Asia/Kolkata (IST).

## Key decisions already made

- **v0 scope:** client app + voice pipeline + changeable wake word. Out of scope: context ingestion, memory, passive whisper mode, Bolna calls, iOS.
- **App:** native Android, Kotlin + Jetpack Compose. Visual design revised Oct 31: custom dark diamond look themed on top of Material 3 (`design/ui-prompts.md`). Min SDK 31. Reason: Bluetooth routing, foreground mic service, low-latency audio and sherpa-onnx are all native; a cross-platform framework would add bridge code without saving work.
- **Phone is the hub.** Headset = mic + speaker only. Phone runs VAD + wake word on-device; backend does STT/LLM/TTS.
- **Wake word (revised Oct 25):** on-device Silero VAD gates the mic; speech bursts go to the backend, which checks the Deepgram transcript for the wake phrase by sound (`backend/raphael/wakeword.py`) and drops the turn (`no_wake`) otherwise. On-device KWS (sherpa) and on-device Whisper failed on the developer's real voice; Picovoice needs a company email. Earbud tap via `MediaSession` is a second trigger.
- **Backend:** Python 3.11 + FastAPI, one WebSocket `/v1/session`, JSON control frames + binary 16 kHz mono PCM16 frames (20 ms / 640 bytes). Providers behind `ASRProvider` / `LLMProvider` / `TTSProvider` interfaces.
- **Providers:** Deepgram (STT, primary), Sarvam Saaras `codemix` (Hinglish alt), OpenAI `gpt-4.1-mini` (LLM, chosen by benchmark Oct 25; Claude kept as `LLM_PROVIDER=anthropic`), Cartesia (TTS). Bolna has no standalone TTS/STT API; it's an agent/phone-call platform — candidate use is a follow-up-call feature (pending user decision).
- **Agent + MCP (Oct 31):** the LLM is an agent with MCP tools (`backend/mcp_servers.json`, hub in `backend/raphael/mcp_hub.py`, loop in `backend/raphael/agent.py`). Phone control = forked android-mcp-server over adb (`backend/mcp/android-mcp-server`, no arbitrary shell tool); web search = Tavily MCP. Try it without voice: `python tools/agent_test.py "set a timer for 2 minutes"` (from `backend/`).
- **Privacy (revised Oct 25):** silence never leaves the phone; speech is sent only for the wake check and is discarded (not logged/stored) without the wake phrase. Backend stores no audio.

## How to work with me

- **Build order:** backend pipeline + laptop-mic test client first (proves the pipeline without Android), then the Android app as a thin mic/speaker client.
- **Biggest risk = Bluetooth headset audio.** First Android task is a tiny spike app: record 5 s from the headset mic (`setCommunicationDevice` + `AudioRecord` with `VOICE_COMMUNICATION`) and play it back. If it fails on my phone/headset, fall back to "phone mic" mode as default.
- Start the Android app from the **sherpa-onnx Android KWS demo** rather than a blank project where practical.
- Testing needs a **real Android phone** (Android 12+); the emulator can't use Bluetooth headsets.
- Distribution for the hackathon = sideloaded APK + demo video. No Play Store.
- Keep things demoable at every step; tap-to-talk is an acceptable fallback if the wake word slips.
- Small, working increments. Tell me what to run and what I should see.

## Known gotchas

- **The repo path contains a space** (`Raphael AI`). Gradle / Android NDK / shell scripts can break on this. Resolved: symlink `~/raphael` → repo. Always open/build the Android project via `~/raphael/app-android`.
- Android toolchain (from Android Studio 2026.2): AGP 9.4.1 (built-in Kotlin, no `kotlin-android` plugin), Kotlin compose plugin 2.2.10, Compose BOM 2026.02.01, Gradle 9.6.0, compileSdk 37, JDK = Studio's bundled JBR. CLI build: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug`.
- Test hardware: Google Pixel 9 + OnePlus Nord Buds 2.
- Classic Bluetooth headsets drop to call quality (HFP) while the mic is open; music degrades. Expected, not a bug.
- **Pixel 9 + Nord Buds 2 open the HFP link in narrowband (8 kHz, CVSD)**: no energy above 4 kHz (measured 2026-10-25). Deepgram copes, but the sherpa KWS wake word does not (0 detections on real buds audio; 12/12 → 5/12 on synthetic narrowband, sensitivity doesn't help). Wake word needs wideband audio: phone mic, or a headset with mSBC/LE Audio.
- Some phone brands (Xiaomi, Oppo, Vivo, OnePlus) aggressively kill background services — need battery-optimization exemption.
- `venv/` at the repo root is Python 3.11 (Homebrew `/opt/homebrew/bin/python3.11`); the backend is installed into it editable (`pip install -e "backend[client,dev]"`). Run backend commands from `backend/`.
- No fake/mock providers in the app. This is a live demo: if something is needed (API keys, accounts, devices), ask the developer for it. Test-only stand-ins live in `backend/tests/`.

## Open questions (update as resolved)

- Which LLM hits the 450 ms first-sentence target? Resolved: none from India; gpt-4.1-mini fastest (~0.78 s first sentence). End-to-end ≈1.4–1.9 s depending on endpoint_ms.
- sherpa-onnx false-trigger rate for "Hey Raphael" acceptable, or switch to Porcupine? (decide by Oct 25) — Oct 25: sherpa KWS misses the developer's real voice entirely (phone mic and buds); natural "Hey Raphael" is ambiguous even to Deepgram/Whisper. Proposed: Porcupine for the default phrase (awaiting Picovoice AccessKey + .ppn from developer).
- Hinglish in the demo? (needs Sarvam realtime beta access)
- Which phone + 3 headsets are we testing on? Pixel 9 + OnePlus Nord Buds 2 (headset spike PASSED 2026-10-25: HFP link ~0.8 s, mic + playback on buds); 2 more headsets TBD.
- Does the Oct 31 submission include passive whisper mode?

## Related

- Editable design doc: https://claude.ai/artifact/VLjjQ5tqK7j7mEQW3kcD7H
- Buildathon: https://www.bolna.ai/symphony/buildathon
