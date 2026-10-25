# Session protocol (`WS /v1/session`)

The message table is in [spec §6](../spec/spec.md#6-interfaces). This page covers the
details a client needs beyond that table. The source of truth in code is
`backend/raphael/protocol.py`; `config.schema.json` is generated from it
(`python backend/tools/export_schema.py`).

## Connect

```
ws://<host>:8000/v1/session            (laptop)
wss://<host>/v1/session?token=<token>  (when RAPHAEL_TOKEN is set on the server)
```

A wrong token closes the socket with code `4401`.

## Audio

- Binary frames only carry audio: 16 kHz, mono, PCM16 little-endian, 20 ms = 640 bytes.
- Phone → backend audio is accepted **only between `turn.start` and the end of the
  question**. Anything else is dropped and never stored.
- Backend → phone audio always comes in 640-byte frames, after `reply.audio_start`
  and before `reply.done`. The last frame is padded with silence.
- The backend sends reply audio as fast as TTS produces it, not in real time. The
  phone buffers it and plays it.

## A normal turn

```
phone                                   backend
  turn.start {turn_id, trigger}  ──▶
  [300 ms pre-roll] [live frames] ──▶
                                 ◀──  transcript.partial   (repeats; full text so far)
     ... user stops talking (endpoint_ms of silence) ...
                                 ◀──  transcript.final
                                 ◀──  reply.text {delta}   (repeats)
                                 ◀──  reply.audio_start
                                 ◀──  [binary audio frames]
                                 ◀──  reply.done {timings}
```

- `transcript.partial.text` is always the **whole question so far**, not a delta.
- `turn.end` (second tap) makes STT finalize immediately instead of waiting for silence.
- A new `turn.start` cancels any turn still running.
- `turn.cancel` (barge-in): the backend stops LLM and TTS for that turn and sends nothing
  more for it, not even `reply.done`. The phone flushes its own playback.
- Messages for a `turn_id` other than the current one are ignored.

## Hands-free (`trigger: "vad"`) turns

The phone's VAD heard speech; the backend checks whether it starts with a wake phrase from `config.wake.phrases`.

- Before the phrase is found nothing is sent to the phone (no partials).
- Found → `wake.detected {turn_id, phrase}`; the turn continues as a normal question. Partials and `transcript.final` contain only the question, with the wake phrase stripped.
- A pause right after the phrase doesn't end the turn; the backend waits up to 6 s for the question.
- Not found → `turn.dropped` with reason `no_wake`. The transcript is not logged.

## `turn.dropped` reasons

| reason | meaning |
|---|---|
| `no_wake` | `vad` turn without the wake phrase (speech ended, too many words, or 4 s) |
| `no_speech` | No words heard within 6 s of `turn.start` |
| `empty` | STT finished with an empty transcript |
| `asr_failed`, `llm_failed`, `tts_failed`, `internal` | A stage failed; an `error` message comes first |

After an STT failure the backend tries to say "Sorry, I didn't catch that." In that case
it sends `reply.audio_start` … `reply.done` (with empty timings) instead of `turn.dropped`.

## `reply.done.timings`

All values are milliseconds and are measured on the backend. `null` means the stage didn't happen.

| field | from → to |
|---|---|
| `eos_ms` | last transcript update → end of question detected (≈ silence wait) |
| `llm_first_token_ms` | end of question → first LLM token |
| `llm_first_sentence_ms` | end of question → first complete sentence |
| `tts_first_audio_ms` | first sentence → its first audio |
| `first_audio_ms` | end of question → first audio sent |
| `total_ms` | `eos_ms` + `first_audio_ms` (backend part of success criterion S1) |
| `llm_total_ms` | end of question → LLM finished |

## Errors

`error {code, message, retryable, turn_id?}`. Codes: `bad_message` (invalid JSON or
unknown type), `asr_failed`, `llm_failed`, `tts_failed`, `internal`.
