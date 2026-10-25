"""OpenAI chat models via the official SDK, streaming."""

from typing import AsyncIterator

import openai

from ..base import Message, ProviderError


class OpenAILLM:
    def __init__(self, model: str, api_key: str | None = None, reasoning_effort: str | None = None) -> None:
        self._client = openai.AsyncOpenAI(api_key=api_key, max_retries=1, timeout=20.0)
        self._model = model
        self._effort = reasoning_effort

    def with_model(self, model: str | None) -> "OpenAILLM":
        if not model or model == self._model:
            return self
        other = object.__new__(OpenAILLM)
        other._client, other._model, other._effort = self._client, model, self._effort
        return other

    async def warm(self) -> None:
        try:
            await self._client.models.retrieve(self._model)
        except Exception:  # noqa: BLE001 - best effort; the real call reports errors
            pass

    async def stream(self, system: str, messages: list[Message], max_tokens: int) -> AsyncIterator[str]:
        extra = {"reasoning_effort": self._effort} if self._effort else {}
        try:
            stream = await self._client.chat.completions.create(
                model=self._model,
                messages=[{"role": "system", "content": system}, *messages],
                max_completion_tokens=max_tokens,
                stream=True,
                **extra,
            )
            async for event in stream:
                if event.choices and (delta := event.choices[0].delta.content):
                    yield delta
        except openai.RateLimitError as e:
            raise ProviderError("llm", "rate limited", retryable=True) from e
        except openai.APIStatusError as e:
            raise ProviderError("llm", f"{e.status_code}: {e.message}", retryable=e.status_code >= 500) from e
        except openai.APIConnectionError as e:
            raise ProviderError("llm", "connection failed", retryable=True) from e
