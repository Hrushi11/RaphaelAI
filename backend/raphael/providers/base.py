"""Provider interfaces (spec §4.4). Swapping a provider is a config change."""

from dataclasses import dataclass
from typing import AsyncIterator, Literal, Protocol, TypedDict


# ------------------------------------------------------------------ ASR events


@dataclass
class Partial:
    """Interim text for the segment currently being spoken (may still change)."""

    text: str
    speech_end_s: float | None = None  # end of last word, seconds since stream start


@dataclass
class Final:
    """A committed segment. The full question is all Finals joined."""

    text: str
    speech_end_s: float | None = None


@dataclass
class SpeechEnded:
    """End of question detected (endpointing, or flushed after finalize())."""


ASREvent = Partial | Final | SpeechEnded


class ASRProvider(Protocol):
    """One instance per turn."""

    async def open(self, sample_rate: int, language: str, endpoint_ms: int) -> None: ...
    async def send_audio(self, pcm: bytes) -> None: ...
    async def finalize(self) -> None:
        """Flush buffered audio now (user ended the question with a tap)."""
        ...

    def events(self) -> AsyncIterator[ASREvent]: ...
    async def close(self) -> None: ...


# ------------------------------------------------------------------ LLM


class Message(TypedDict):
    role: Literal["user", "assistant"]
    content: str


class LLMProvider(Protocol):
    async def warm(self) -> None:
        """Open the HTTPS connection early so the first turn doesn't pay for it."""
        ...

    def stream(self, system: str, messages: list[Message], max_tokens: int) -> AsyncIterator[str]: ...


# ------------------------------------------------------------------ TTS


class TTSProvider(Protocol):
    """One instance per session, so the connection stays warm between turns."""

    async def open(self, voice: str, sample_rate: int, language: str) -> None: ...
    def speak(self, text: str, context_id: str) -> AsyncIterator[bytes]:
        """Yield PCM16 chunks (any size) for `text`."""
        ...

    async def cancel(self, context_id: str) -> None: ...
    async def close(self) -> None: ...


class ProviderError(Exception):
    """A provider failed in a way the session should report to the phone."""

    def __init__(self, stage: str, message: str, retryable: bool = True) -> None:
        super().__init__(f"{stage}: {message}")
        self.stage = stage
        self.retryable = retryable
