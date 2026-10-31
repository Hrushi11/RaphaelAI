"""Wire protocol for WS /v1/session (spec §6).

Text frames carry the JSON messages below. Binary frames carry audio only:
16 kHz mono PCM16 little-endian, 20 ms (640 bytes) per frame.
"""

from typing import Annotated, Literal, Union

from pydantic import BaseModel, ConfigDict, Field, TypeAdapter

SAMPLE_RATE = 16_000
FRAME_MS = 20
FRAME_BYTES = SAMPLE_RATE * FRAME_MS // 1000 * 2  # 640

DEFAULT_PERSONA = (
    "You are Raphael, a calm, precise advisor speaking privately into the user's "
    "earpiece. Answer in at most two short spoken sentences. No lists or formatting. "
    "Say numbers as they are spoken. If you don't know, say so in a few words."
)


# ---------------------------------------------------------------- config (§6.3)


class _Model(BaseModel):
    model_config = ConfigDict(extra="ignore")


class DeviceConfig(_Model):
    headset_id: str | None = None
    mic_source: Literal["headset", "phone"] = "headset"


class WakeConfig(_Model):
    phrases: list[str] = Field(default_factory=lambda: ["hey raphael"], min_length=1, max_length=3)
    sensitivity: float = Field(0.5, ge=0.0, le=1.0)
    tap_trigger: bool = True
    follow_up_window_s: int = Field(5, ge=0, le=15)


class AssistantConfig(_Model):
    persona: str = DEFAULT_PERSONA
    max_sentences: int = Field(2, ge=1, le=6)
    language: Literal["en-IN", "hi-en"] = "en-IN"
    llm: str | None = None  # None = server default
    voice: str | None = None  # "cartesia:<voice-id>"; None = server default


class ASRConfig(_Model):
    provider: Literal["deepgram", "sarvam"] = "deepgram"
    endpoint_ms: int = Field(500, ge=200, le=1500)


class PrivacyConfig(_Model):
    store_transcripts: bool = False


class RaphaelConfig(_Model):
    version: int = 1
    device: DeviceConfig = Field(default_factory=DeviceConfig)
    wake: WakeConfig = Field(default_factory=WakeConfig)
    assistant: AssistantConfig = Field(default_factory=AssistantConfig)
    asr: ASRConfig = Field(default_factory=ASRConfig)
    privacy: PrivacyConfig = Field(default_factory=PrivacyConfig)


# ---------------------------------------------------------- phone -> backend


class SessionStart(_Model):
    type: Literal["session.start"]
    config: RaphaelConfig = Field(default_factory=RaphaelConfig)
    client_version: str | None = None


class ConfigUpdate(_Model):
    type: Literal["config.update"]
    config: RaphaelConfig


class TurnStart(_Model):
    type: Literal["turn.start"]
    turn_id: str
    # tap: user pressed/tapped. vad: on-device VAD heard speech; backend checks for the wake phrase.
    trigger: Literal["wake", "tap", "vad"] = "tap"
    preroll_ms: int = 0


class TurnEnd(_Model):
    type: Literal["turn.end"]
    turn_id: str


class TurnCancel(_Model):
    type: Literal["turn.cancel"]
    turn_id: str


class Ping(_Model):
    type: Literal["ping"]
    t: float | int | None = None


ClientMessage = Annotated[
    Union[SessionStart, ConfigUpdate, TurnStart, TurnEnd, TurnCancel, Ping],
    Field(discriminator="type"),
]
client_message = TypeAdapter(ClientMessage)


# ---------------------------------------------------------- backend -> phone
# Plain dict builders: cheap, and keep the outgoing shapes in one place.


def session_ready(session_id: str, tools: list[str] | None = None) -> dict:
    """`tools`: names of the MCP servers the agent can use (e.g. ["phone", "web"]), for the app's dashboard."""
    return {"type": "session.ready", "session_id": session_id, "tools": tools or []}


def wake_detected(turn_id: str, phrase: str) -> dict:
    return {"type": "wake.detected", "turn_id": turn_id, "phrase": phrase}


def transcript_partial(turn_id: str, text: str) -> dict:
    return {"type": "transcript.partial", "turn_id": turn_id, "text": text}


def transcript_final(turn_id: str, text: str) -> dict:
    return {"type": "transcript.final", "turn_id": turn_id, "text": text}


def reply_text(turn_id: str, delta: str) -> dict:
    return {"type": "reply.text", "turn_id": turn_id, "delta": delta}


def reply_audio_start(turn_id: str, sample_rate: int = SAMPLE_RATE) -> dict:
    return {"type": "reply.audio_start", "turn_id": turn_id, "sample_rate": sample_rate}


def reply_done(turn_id: str, timings: dict[str, int]) -> dict:
    return {"type": "reply.done", "turn_id": turn_id, "timings": timings}


def turn_dropped(turn_id: str, reason: str) -> dict:
    return {"type": "turn.dropped", "turn_id": turn_id, "reason": reason}


def error(code: str, message: str, retryable: bool = False, turn_id: str | None = None) -> dict:
    msg = {"type": "error", "code": code, "message": message, "retryable": retryable}
    if turn_id:
        msg["turn_id"] = turn_id
    return msg


def pong(t: float | int | None) -> dict:
    return {"type": "pong", "t": t}
