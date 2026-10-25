"""Cartesia streaming TTS over one WebSocket per session.

Several contexts can share the socket; a reader task routes incoming chunks to
the right speak() call by context_id.
"""

import asyncio
import base64
import json
import logging
from typing import AsyncIterator
from urllib.parse import urlencode

from websockets.asyncio.client import ClientConnection, connect
from websockets.exceptions import ConnectionClosed

from ..base import ProviderError

log = logging.getLogger(__name__)

URL = "wss://api.cartesia.ai/tts/websocket"
LANGUAGES = {"en-IN": "en", "hi-en": "hi"}


class CartesiaTTS:
    def __init__(self, api_key: str, model: str, version: str, default_voice: str) -> None:
        self._api_key = api_key
        self._model = model
        self._version = version
        self._voice = default_voice
        self._sample_rate = 16_000
        self._language = "en"
        self._ws: ClientConnection | None = None
        self._reader: asyncio.Task | None = None
        self._streams: dict[str, asyncio.Queue[bytes | Exception | None]] = {}
        self._connect_lock = asyncio.Lock()

    async def open(self, voice: str, sample_rate: int, language: str) -> None:
        self._voice = voice or self._voice
        self._sample_rate = sample_rate
        self._language = LANGUAGES.get(language, "en")
        await self._ensure_connected()

    async def speak(self, text: str, context_id: str) -> AsyncIterator[bytes]:
        await self._ensure_connected()
        assert self._ws
        q: asyncio.Queue[bytes | Exception | None] = asyncio.Queue()
        self._streams[context_id] = q
        finished = False
        try:
            await self._ws.send(json.dumps({
                "model_id": self._model,
                "transcript": text,
                "voice": {"mode": "id", "id": self._voice},
                "output_format": {"container": "raw", "encoding": "pcm_s16le", "sample_rate": self._sample_rate},
                "language": self._language,
                "context_id": context_id,
                "continue": False,
            }))
            while True:
                item = await q.get()
                if item is None:
                    finished = True
                    return
                if isinstance(item, Exception):
                    finished = True
                    raise item
                yield item
        finally:
            self._streams.pop(context_id, None)
            if not finished:  # cancelled mid-sentence (barge-in)
                await self.cancel(context_id)

    async def cancel(self, context_id: str) -> None:
        if self._ws:
            try:
                await self._ws.send(json.dumps({"context_id": context_id, "cancel": True}))
            except ConnectionClosed:
                pass

    async def close(self) -> None:
        if self._reader:
            self._reader.cancel()
        if self._ws:
            await self._ws.close()
        self._ws = None

    async def _ensure_connected(self) -> None:
        async with self._connect_lock:
            if self._ws and self._reader and not self._reader.done():
                return
            query = urlencode({"cartesia_version": self._version})
            try:
                self._ws = await connect(
                    f"{URL}?{query}",
                    additional_headers={"X-API-Key": self._api_key, "Cartesia-Version": self._version},
                    open_timeout=5,
                    max_size=None,
                )
            except Exception as e:  # noqa: BLE001
                raise ProviderError("tts", f"Cartesia connect failed: {e}") from e
            self._reader = asyncio.create_task(self._read(self._ws))

    async def _read(self, ws: ClientConnection) -> None:
        try:
            async for raw in ws:
                msg = json.loads(raw)
                q = self._streams.get(msg.get("context_id", ""))
                kind = msg.get("type")
                if kind == "error":
                    log.warning("cartesia error: %s", msg)
                    err = ProviderError("tts", msg.get("message") or msg.get("title") or "error")
                    if q:
                        q.put_nowait(err)
                    continue
                if q is None:
                    continue  # chunk for a cancelled context
                if kind == "chunk" and msg.get("data"):
                    q.put_nowait(base64.b64decode(msg["data"]))
                if msg.get("done") or kind == "done":
                    q.put_nowait(None)
        except ConnectionClosed as e:
            log.info("cartesia socket closed: %s", e)
        finally:
            for q in self._streams.values():
                q.put_nowait(ProviderError("tts", "Cartesia connection closed"))
