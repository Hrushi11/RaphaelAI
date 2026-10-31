"""Server-side settings, read from environment variables or backend/.env.

Per-user settings (persona, wake word, ...) come from the phone in the session
config instead; see protocol.RaphaelConfig.
"""

from functools import lru_cache
from pathlib import Path
from typing import Literal

from pydantic_settings import BaseSettings, SettingsConfigDict

BACKEND_DIR = Path(__file__).resolve().parent.parent


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=BACKEND_DIR / ".env", env_file_encoding="utf-8", extra="ignore"
    )

    # Access control. If unset, any client may connect (fine for laptop testing).
    raphael_token: str | None = None

    # Deepgram (speech-to-text)
    deepgram_api_key: str | None = None
    deepgram_model: str = "nova-3"
    # Words Deepgram should expect (comma-separated). Fixes "four lakh" -> "forelock".
    deepgram_keyterms: str = "lakh,crore,Raphael"

    # LLM. gpt-4.1-mini had the fastest first sentence in our Oct 25 benchmark.
    llm_provider: Literal["openai", "anthropic"] = "openai"
    llm_model: str = "gpt-4.1-mini"
    # Only for reasoning models (gpt-5.x, o-series): "none" / "minimal" keeps them fast.
    llm_reasoning_effort: str | None = None
    openai_api_key: str | None = None
    anthropic_api_key: str | None = None

    # Cartesia (text-to-speech)
    cartesia_api_key: str | None = None
    cartesia_model: str = "sonic-3.6"
    cartesia_version: str = "2026-08-14"
    # Default voice if the phone config doesn't pick one. Browse voices at
    # https://play.cartesia.ai and paste an ID here.
    cartesia_voice_id: str = "a0e99841-438c-4a64-b679-ae501e7d6091"

    # Turn behaviour
    history_turns: int = 6  # previous Q/A pairs sent to the LLM
    no_speech_timeout_s: float = 6.0  # turn dropped if no words heard by then
    wake_check_s: float = 4.0  # "vad" turn dropped if no wake phrase within this time
    # DEBUG ONLY: log what was heard in speech bursts rejected by the wake check (off by default for privacy).
    debug_wake: bool = False
    max_turn_s: float = 30.0  # question force-ended after this long
    finalize_grace_s: float = 1.5  # wait for ASR after turn.end before giving up

    def missing_keys(self) -> list[str]:
        required = {
            "DEEPGRAM_API_KEY": self.deepgram_api_key,
            "CARTESIA_API_KEY": self.cartesia_api_key,
        }
        if self.llm_provider == "openai":
            required["OPENAI_API_KEY"] = self.openai_api_key
        else:
            required["ANTHROPIC_API_KEY"] = self.anthropic_api_key
        return [name for name, value in required.items() if not value]


@lru_cache
def get_settings() -> Settings:
    return Settings()
