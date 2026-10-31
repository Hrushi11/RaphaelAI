"""Connects Raphael's agent to MCP servers listed in backend/mcp_servers.json.

Each server is either a local command (stdio, e.g. our Android phone server) or a remote
URL (Streamable HTTP, e.g. Tavily web search). Tools are exposed to the LLM as
"<server>__<tool>" so names never clash. Uses the official MCP Python SDK (v2 `Client`).
"""

import json
import logging
import os
import re
import sys
from contextlib import AsyncExitStack
from pathlib import Path

from mcp.client import Client
from mcp.client.stdio import StdioServerParameters

log = logging.getLogger("raphael.mcp")

BACKEND_DIR = Path(__file__).resolve().parent.parent
_VAR = re.compile(r"\$\{(\w+)\}")
MAX_RESULT_CHARS = 6000  # keep tool results from flooding the LLM context


def _resolve(value: str, env: dict[str, str]) -> tuple[str, set[str]]:
    missing: set[str] = set()

    def sub(m: re.Match) -> str:
        v = env.get(m.group(1), "")
        if not v:
            missing.add(m.group(1))
        return v

    return _VAR.sub(sub, value), missing


class McpHub:
    def __init__(self) -> None:
        self._stack = AsyncExitStack()
        self._clients: dict[str, Client] = {}
        self._tools: dict[str, tuple[str, str]] = {}  # exposed name -> (server, tool)
        self._schemas: list[dict] = []

    @property
    def servers(self) -> list[str]:
        """Names of the connected MCP servers."""
        return list(self._clients)

    @property
    def tools(self) -> list[dict]:
        """OpenAI-style function tool definitions."""
        return self._schemas

    async def start(self, config_path: Path, env: dict[str, str]) -> None:
        if not config_path.exists():
            log.info("no MCP config at %s", config_path)
            return
        config = json.loads(config_path.read_text())
        for spec in config.get("servers", []):
            if not spec.get("enabled", True):
                continue
            name = spec["name"]
            try:
                await self._connect(name, spec, env)
            except Exception as e:  # noqa: BLE001 - one bad server must not stop the others
                log.warning("MCP server %s not started: %s", name, e)
        log.info("MCP tools available: %s", ", ".join(self._tools) or "none")

    async def _connect(self, name: str, spec: dict, env: dict[str, str]) -> None:
        optional = set(spec.get("optional_env", []))
        if "url" in spec:
            url, missing = _resolve(spec["url"], env)
            if missing - optional:
                raise RuntimeError(f"missing {', '.join(sorted(missing))} in backend/.env")
            target: object = url
        else:
            server_env = {}
            for k, v in spec.get("env", {}).items():
                val, missing = _resolve(v, env)
                if missing - optional:
                    raise RuntimeError(f"missing {', '.join(sorted(missing))} in backend/.env")
                if val:
                    server_env[k] = val
            command = sys.executable if spec["command"] == "python" else spec["command"]
            target = StdioServerParameters(
                command=command,
                args=spec.get("args", []),
                cwd=str(BACKEND_DIR / spec["cwd"]) if spec.get("cwd") else None,
                env={**os.environ, **server_env},
            )
        client = await self._stack.enter_async_context(Client(target))
        listed = (await client.list_tools()).tools
        allowed = set(spec.get("tools") or [t.name for t in listed])
        tools, schemas = {}, []  # register all of a server's tools, or none
        for tool in listed:
            if tool.name not in allowed:
                continue
            exposed = f"{name}__{tool.name}"[:64]
            tools[exposed] = (name, tool.name)
            schemas.append({
                "type": "function",
                "function": {
                    "name": exposed,
                    "description": (tool.description or "")[:1000],
                    "parameters": tool.input_schema or {"type": "object", "properties": {}},
                },
            })
        self._tools.update(tools)
        self._schemas.extend(schemas)
        self._clients[name] = client
        log.info("MCP server %s: %d tools", name, sum(1 for s, _ in self._tools.values() if s == name))

    async def call(self, exposed: str, arguments: dict) -> str:
        """Text-only result (images dropped)."""
        text, _ = await self.call_with_images(exposed, arguments)
        return text

    async def call_with_images(self, exposed: str, arguments: dict) -> tuple[str, list[str]]:
        """Tool result as (text, images as data: URLs). Images come from e.g. the phone's screenshot fallback."""
        if exposed not in self._tools:
            return f"Error: unknown tool {exposed}", []
        server, tool = self._tools[exposed]
        try:
            result = await self._clients[server].call_tool(tool, arguments, read_timeout_seconds=45)
        except Exception as e:  # noqa: BLE001 - report to the LLM, let it recover
            return f"Error calling {tool}: {e}", []
        parts, images = [], []
        for c in result.content:
            kind = getattr(c, "type", "")
            if kind == "text":
                parts.append(c.text)
            elif kind == "image":
                images.append(f"data:{c.mime_type};base64,{c.data}")
            else:
                parts.append(f"[{kind or 'content'} omitted]")
        text = "\n".join(parts) or "(no output)"
        if result.is_error:
            text = "Error: " + text
        return text[:MAX_RESULT_CHARS], images

    async def close(self) -> None:
        await self._stack.aclose()
