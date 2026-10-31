"""FastAPI app. Run from backend/:  uvicorn raphael.main:app --reload"""

import logging
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI, WebSocket

from dotenv import dotenv_values

from .mcp_hub import McpHub
from .providers import Providers
from .session import Session
from .settings import BACKEND_DIR, get_settings

from logging.handlers import RotatingFileHandler
from pathlib import Path

# Log to the terminal and to backend/logs/raphael.log (git-ignored, rotates at 2 MB).
_LOG_DIR = Path(__file__).resolve().parent.parent / "logs"
_LOG_DIR.mkdir(exist_ok=True)
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    handlers=[logging.StreamHandler(), RotatingFileHandler(_LOG_DIR / "raphael.log", maxBytes=2_000_000, backupCount=2)],
)
# HTTP client request logs include full URLs, and some MCP URLs carry API keys (e.g. Tavily): keep them out.
for _noisy in ("httpx", "httpx2", "mcp"):
    logging.getLogger(_noisy).setLevel(logging.WARNING)


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    if missing := settings.missing_keys():
        raise RuntimeError(f"Missing in backend/.env: {', '.join(missing)} (see backend/.env.example)")
    app.state.settings = settings
    app.state.providers = Providers(settings)
    # MCP tools for the agent (phone control, web search, …). ${VARS} come from backend/.env + environment.
    hub = McpHub()
    env = {k: v for k, v in dotenv_values(BACKEND_DIR / ".env").items() if v} | dict(os.environ)
    await hub.start(BACKEND_DIR / settings.mcp_config, env)
    app.state.providers.hub = hub
    try:
        yield
    finally:
        await hub.close()


app = FastAPI(title="Raphael", lifespan=lifespan)


@app.get("/healthz")
async def healthz() -> dict:
    return {"ok": True}


@app.websocket("/v1/session")
async def session(ws: WebSocket, token: str | None = None) -> None:
    settings = app.state.settings
    if settings.raphael_token and token != settings.raphael_token:
        await ws.close(code=4401)
        return
    await ws.accept()
    await Session(ws, app.state.providers, settings).run()

