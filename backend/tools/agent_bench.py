"""Compare LLMs on Raphael's phone tasks (success, steps, time), on the Android emulator.

    ANDROID_SERIAL=emulator-5554 python tools/agent_bench.py gpt-4.1-mini gpt-4.1 gpt-5.4-mini gpt-5.5

Model names may carry a reasoning effort: "gpt-5.5:none". Each task resets the emulator
state first and checks the result on the device (not just what the model says).
"""

import asyncio
import logging
import os
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from dotenv import dotenv_values  # noqa: E402

from raphael import agent  # noqa: E402
from raphael.mcp_hub import McpHub  # noqa: E402
from raphael.protocol import AssistantConfig  # noqa: E402
from raphael.providers.llm.openai import OpenAILLM  # noqa: E402
from raphael.session import build_system_prompt  # noqa: E402
from raphael.settings import BACKEND_DIR, get_settings  # noqa: E402

ADB = [str(Path.home() / "Library/Android/sdk/platform-tools/adb"), "-s", os.environ.get("ANDROID_SERIAL", "emulator-5554")]


def sh(cmd: str) -> str:
    return subprocess.run([*ADB, "shell", cmd], capture_output=True, text=True).stdout


def dark_mode() -> bool:
    return "yes" in sh("cmd uimode night")


def reset(dark: bool) -> None:
    sh(f"cmd uimode night {'yes' if dark else 'no'}")
    sh("am force-stop com.android.settings")
    sh("input keyevent 3")
    time.sleep(1)


TASKS = [
    ("dark mode off (was on)", "switch dark mode off", lambda: reset(True), lambda answer: not dark_mode()),
    ("dark mode off (already off)", "switch dark mode off", lambda: reset(False), lambda answer: not dark_mode()),
    ("android version", "which android version is this phone running?", lambda: reset(False),
     lambda answer: "17" in answer or "seventeen" in answer.lower()),
    ("busy screen (About page)", "open the about phone page in settings and read me the device name shown there",
     lambda: reset(False), lambda answer: "sdk" in answer.lower() or "phone" in answer.lower() or "emulat" in answer.lower()),
    ("do not disturb", "turn on do not disturb", lambda: (reset(False), sh("cmd notification set_dnd off")),
     lambda answer: sh("settings get global zen_mode").strip() not in ("", "0")),
]


class CountingHub:
    def __init__(self, hub: McpHub):
        self.hub, self.calls = hub, 0

    @property
    def tools(self):
        return self.hub.tools

    async def call(self, name, args):
        self.calls += 1
        return await self.hub.call(name, args)

    async def call_with_images(self, name, args):
        self.calls += 1
        return await self.hub.call_with_images(name, args)


async def main(models: list[str]) -> None:
    logging.basicConfig(level=logging.WARNING)
    settings = get_settings()
    hub = McpHub()
    env = {k: v for k, v in dotenv_values(BACKEND_DIR / ".env").items() if v} | dict(os.environ)
    await hub.start(BACKEND_DIR / settings.mcp_config, env)
    system = build_system_prompt(AssistantConfig(), now=time.strftime("%A %d %B %Y, %I:%M %p"))
    rows = []
    try:
        for spec in models:
            model, _, effort = spec.partition(":")
            llm = OpenAILLM(model, settings.openai_api_key, effort or None)
            for name, question, setup, check in TASKS:
                setup()
                counting = CountingHub(hub)
                t0 = time.perf_counter()
                first_audio = None
                answer = ""
                try:
                    async for delta in agent.run(llm, counting, system, [{"role": "user", "content": question}], 400):
                        if first_audio is None and delta.strip():
                            first_audio = time.perf_counter() - t0
                        answer += delta
                    ok = check(answer)
                except Exception as e:  # noqa: BLE001
                    ok, answer = False, f"ERROR {e}"
                total = time.perf_counter() - t0
                rows.append((spec, name, ok, counting.calls, first_audio or total, total, answer.strip()[:70]))
                print(f"{spec:16} {name:28} {'PASS' if ok else 'FAIL'}  {counting.calls:2d} steps  "
                      f"first words {first_audio or total:4.1f}s  total {total:5.1f}s  | {answer.strip()[:60]!r}", flush=True)
    finally:
        await hub.close()
    print("\nSummary (per model): passed, avg steps, avg total s")
    for spec in models:
        r = [x for x in rows if x[0] == spec]
        print(f"  {spec:16} {sum(x[2] for x in r)}/{len(r)}  steps {sum(x[3] for x in r)/len(r):4.1f}  "
              f"total {sum(x[5] for x in r)/len(r):5.1f}s")


if __name__ == "__main__":
    asyncio.run(main(sys.argv[1:] or ["gpt-4.1-mini"]))
