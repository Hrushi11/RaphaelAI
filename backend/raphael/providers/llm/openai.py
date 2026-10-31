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

    supports_tools = True

    async def stream_tools(
        self, messages: list[dict], tools: list[dict], max_tokens: int
    ) -> AsyncIterator[tuple[str, object]]:
        """One model step with tools. Yields ("text", delta) as it streams, then at most one
        ("tool_calls", [{"id", "name", "arguments"}]) when the model wants to use tools.
        `messages` are OpenAI chat messages including the system message."""
        extra = {"reasoning_effort": self._effort} if self._effort else {}
        calls: dict[int, dict] = {}
        try:
            stream = await self._client.chat.completions.create(
                model=self._model,
                messages=messages,
                tools=tools or openai.NOT_GIVEN,
                # One action per step, so the agent looks at the screen again before the next tap.
                parallel_tool_calls=False if tools else openai.NOT_GIVEN,
                max_completion_tokens=max_tokens,
                stream=True,
                **extra,
            )
            async for event in stream:
                if not event.choices:
                    continue
                delta = event.choices[0].delta
                if delta.content:
                    yield "text", delta.content
                for tc in delta.tool_calls or []:
                    slot = calls.setdefault(tc.index, {"id": "", "name": "", "arguments": ""})
                    if tc.id:
                        slot["id"] = tc.id
                    if tc.function and tc.function.name:
                        slot["name"] += tc.function.name
                    if tc.function and tc.function.arguments:
                        slot["arguments"] += tc.function.arguments
        except openai.RateLimitError as e:
            raise ProviderError("llm", "rate limited", retryable=True) from e
        except openai.APIStatusError as e:
            raise ProviderError("llm", f"{e.status_code}: {e.message}", retryable=e.status_code >= 500) from e
        except openai.APIConnectionError as e:
            raise ProviderError("llm", "connection failed", retryable=True) from e
        if calls:
            yield "tool_calls", [calls[i] for i in sorted(calls)]

