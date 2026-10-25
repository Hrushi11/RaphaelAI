"""Claude via the official Anthropic SDK, streaming."""

from typing import AsyncIterator

import anthropic

from ..base import Message, ProviderError


class AnthropicLLM:
    def __init__(self, model: str, api_key: str | None = None) -> None:
        self._client = anthropic.AsyncAnthropic(api_key=api_key, max_retries=1, timeout=20.0)
        self._model = model

    def with_model(self, model: str | None) -> "AnthropicLLM":
        if not model or model == self._model:
            return self
        other = object.__new__(AnthropicLLM)
        other._client, other._model = self._client, model
        return other

    async def warm(self) -> None:
        try:
            await self._client.models.retrieve(self._model)
        except Exception:  # noqa: BLE001 - best effort; the real call reports errors
            pass

    async def stream(self, system: str, messages: list[Message], max_tokens: int) -> AsyncIterator[str]:
        try:
            async with self._client.messages.stream(
                model=self._model,
                system=system,
                messages=messages,
                max_tokens=max_tokens,
                # Spoken answers are short: skip thinking and keep effort low so
                # time-to-first-token stays small.
                thinking={"type": "disabled"},
                output_config={"effort": "low"},
            ) as stream:
                async for text in stream.text_stream:
                    yield text
        except anthropic.RateLimitError as e:
            raise ProviderError("llm", "rate limited", retryable=True) from e
        except anthropic.APIStatusError as e:
            raise ProviderError("llm", f"{e.status_code}: {e.message}", retryable=e.status_code >= 500) from e
        except anthropic.APIConnectionError as e:
            raise ProviderError("llm", "connection failed", retryable=True) from e
