"""Builds provider instances from server settings + the phone's config."""

import logging

from ..protocol import RaphaelConfig
from ..settings import Settings
from .asr.deepgram import DeepgramASR
from .base import ASRProvider, LLMProvider, TTSProvider
from .llm.anthropic import AnthropicLLM
from .llm.openai import OpenAILLM
from .tts.cartesia import CartesiaTTS

log = logging.getLogger(__name__)


class Providers:
    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        if settings.llm_provider == "openai":
            self._llm = OpenAILLM(settings.llm_model, settings.openai_api_key, settings.llm_reasoning_effort)
            self._model_prefixes: tuple[str, ...] = ("gpt-", "o")
        else:
            self._llm = AnthropicLLM(settings.llm_model, settings.anthropic_api_key)
            self._model_prefixes = ("claude-",)

    def new_asr(self, config: RaphaelConfig) -> ASRProvider:
        """A fresh STT stream for one turn."""
        if config.asr.provider == "sarvam":
            log.warning("Sarvam ASR not wired up yet; using Deepgram")
        keyterms = [k.strip() for k in self.settings.deepgram_keyterms.split(",") if k.strip()]
        # Bias Deepgram toward the wake phrase words so "Raphael" isn't heard as "rifle".
        for phrase in config.wake.phrases:
            keyterms += [w for w in phrase.split() if len(w) >= 4 and w.lower() not in (k.lower() for k in keyterms)]
        return DeepgramASR(self.settings.deepgram_api_key or "", self.settings.deepgram_model, keyterms)

    def llm(self, config: RaphaelConfig) -> LLMProvider:
        model = config.assistant.llm
        if model and not model.startswith(self._model_prefixes):
            log.warning("Unsupported LLM %r; using %s", model, self.settings.llm_model)
            model = None
        return self._llm.with_model(model)

    def new_tts(self) -> TTSProvider:
        """One TTS connection per session."""
        s = self.settings
        return CartesiaTTS(s.cartesia_api_key or "", s.cartesia_model, s.cartesia_version, s.cartesia_voice_id)

    def voice_for(self, config: RaphaelConfig) -> str:
        voice = config.assistant.voice or ""
        if voice.startswith("cartesia:") and len(voice) > len("cartesia:"):
            return voice.removeprefix("cartesia:")
        return self.settings.cartesia_voice_id
