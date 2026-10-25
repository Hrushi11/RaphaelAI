"""Deepgram live streaming STT (wss://api.deepgram.com/v1/listen)."""

import asyncio
import json
import logging
from typing import AsyncIterator
from urllib.parse import urlencode

from websockets.asyncio.client import ClientConnection, connect
from websockets.exceptions import ConnectionClosed

from ..base import ASREvent, Final, Partial, ProviderError, SpeechEnded

log = logging.getLogger(__name__)

URL = "wss://api.deepgram.com/v1/listen"

# Raphael language setting -> Deepgram language code.
LANGUAGES = {"en-IN": "en-IN", "hi-en": "multi"}


class DeepgramASR:
    def __init__(self, api_key: str, model: str = "nova-3", keyterms: list[str] | None = None) -> None:
        self._api_key = api_key
        self._model = model
        self._keyterms = keyterms or []
        self._ws: ClientConnection | None = None
        self._events: asyncio.Queue[ASREvent | None] = asyncio.Queue()
        self._reader: asyncio.Task | None = None
        self._has_text = False

    async def open(self, sample_rate: int, language: str, endpoint_ms: int) -> None:
        params = {
            "model": self._model,
            "encoding": "linear16",
            "sample_rate": sample_rate,
            "channels": 1,
            "language": LANGUAGES.get(language, "en"),
            "interim_results": "true",
            "endpointing": endpoint_ms,  # silence (ms) before speech_final
            "utterance_end_ms": 1000,  # backup end signal; works in noise
            "vad_events": "true",
            "smart_format": "true",
            "punctuate": "true",
        }
        query = list(params.items())
        if params["language"] != "multi":  # keyterm prompting is English-only
            query += [("keyterm", k) for k in self._keyterms]
        try:
            self._ws = await connect(
                f"{URL}?{urlencode(query)}",
                additional_headers={"Authorization": f"Token {self._api_key}"},
                open_timeout=5,
            )
        except Exception as e:  # noqa: BLE001 - surface any connect failure
            raise ProviderError("asr", f"Deepgram connect failed: {e}") from e
        self._reader = asyncio.create_task(self._read())

    async def send_audio(self, pcm: bytes) -> None:
        if self._ws:
            try:
                await self._ws.send(pcm)
            except ConnectionClosed as e:
                raise ProviderError("asr", "Deepgram connection closed") from e

    async def finalize(self) -> None:
        if self._ws:
            await self._ws.send(json.dumps({"type": "Finalize"}))

    async def events(self) -> AsyncIterator[ASREvent]:
        while (ev := await self._events.get()) is not None:
            yield ev

    async def close(self) -> None:
        if self._ws:
            try:
                await self._ws.send(json.dumps({"type": "CloseStream"}))
            except ConnectionClosed:
                pass
            await self._ws.close()
        if self._reader:
            self._reader.cancel()

    async def _read(self) -> None:
        assert self._ws
        try:
            async for raw in self._ws:
                msg = json.loads(raw)
                kind = msg.get("type")
                if kind == "Results":
                    self._on_results(msg)
                elif kind == "UtteranceEnd" and self._has_text:
                    self._events.put_nowait(SpeechEnded())
                elif kind == "Error":
                    log.warning("deepgram error: %s", msg)
        except ConnectionClosed as e:
            if e.rcvd and e.rcvd.code != 1000:
                log.warning("deepgram closed: %s %s", e.rcvd.code, e.rcvd.reason)
        finally:
            self._events.put_nowait(None)

    def _on_results(self, msg: dict) -> None:
        alts = msg.get("channel", {}).get("alternatives") or [{}]
        text = (alts[0].get("transcript") or "").strip()
        words = alts[0].get("words") or []
        end_s = words[-1].get("end") if words else None
        if msg.get("is_final"):
            if text:
                self._has_text = True
                self._events.put_nowait(Final(text, end_s))
            if (msg.get("speech_final") or msg.get("from_finalize")) and self._has_text:
                self._events.put_nowait(SpeechEnded())
        elif text:
            self._events.put_nowait(Partial(text, end_s))
