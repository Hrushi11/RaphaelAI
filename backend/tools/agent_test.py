"""Try Raphael's agent from the terminal: same LLM, prompt and MCP tools as a voice turn, no audio.

    python tools/agent_test.py "what time is it?"
    python tools/agent_test.py "open settings and tell me the android version"
    ANDROID_SERIAL=emulator-5554 python tools/agent_test.py "open youtube and search lofi"

Tool calls are printed as they happen (the agent logs them), then the spoken answer.
"""

import asyncio
import logging
import os
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from dotenv import dotenv_values  # noqa: E402

from raphael import agent  # noqa: E402
from raphael.mcp_hub import McpHub  # noqa: E402
from raphael.protocol import AssistantConfig  # noqa: E402
from raphael.providers import Providers  # noqa: E402
from raphael.protocol import RaphaelConfig  # noqa: E402
from raphael.session import build_system_prompt  # noqa: E402
from raphael.settings import BACKEND_DIR, get_settings  # noqa: E402


async def main(question: str) -> None:
    logging.basicConfig(level=logging.INFO, format="  %(name)s: %(message)s")
    for noisy in ("httpx", "httpx2", "mcp", "openai"):
        logging.getLogger(noisy).setLevel(logging.WARNING)
    settings = get_settings()
    providers = Providers(settings)
    hub = McpHub()
    env = {k: v for k, v in dotenv_values(BACKEND_DIR / ".env").items() if v} | dict(os.environ)
    await hub.start(BACKEND_DIR / settings.mcp_config, env)
    try:
        from datetime import datetime
        from zoneinfo import ZoneInfo

        now = datetime.now(ZoneInfo(settings.timezone)).strftime(f"%A %d %B %Y, %I:%M %p ({settings.timezone})")
        system = build_system_prompt(AssistantConfig(), now=now)
        llm = providers.llm(RaphaelConfig())
        print(f"\nYou: {question}\nRaphael: ", end="", flush=True)
        t0 = time.perf_counter()
        async for delta in agent.run(llm, hub, system, [{"role": "user", "content": question}], max_tokens=400):
            print(delta, end="", flush=True)
        print(f"\n\n({time.perf_counter() - t0:.1f} s)")
    finally:
        await hub.close()


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    asyncio.run(main(" ".join(sys.argv[1:])))
