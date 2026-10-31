"""Raphael's agent loop: the LLM may call MCP tools (phone, web, …) before answering.

Yields the spoken answer as text deltas, so the session's sentence chunker and TTS work
exactly as for a plain answer.

Speaking order (so the user never waits in silence, Oct 31 feedback):
1. As soon as the first step wants tools, we say a short acknowledgement ourselves
   ("Opening WhatsApp.", "Let me look that up."): we don't rely on the model to say it.
2. While tools run, the model's in-between text is NOT spoken (no running commentary).
3. Only the final answer (the step with no tool calls) is spoken.
"""

import json
import logging
import time
from typing import AsyncIterator

from .mcp_hub import McpHub
from .providers.base import Message

log = logging.getLogger("raphael.agent")

MAX_STEPS = 20       # real phone tasks (open → look → tap → look → …) need room
MAX_SECONDS = 60.0   # …but never keep the user waiting longer than this

TOOL_RULES = (
    "You can use tools: phone__* operates the user's Android phone, web__* searches the internet. "
    "Use them when the question needs live information or an action on the phone; otherwise just answer. "
    "Prefer direct shortcuts over tapping through screens: set_timer, set_alarm, device_info (Android version, "
    "battery), set_setting (dark mode, Wi-Fi, Bluetooth, Do Not Disturb), open_settings (a Settings page), "
    "whatsapp_message / sms_message (open the chat with the text typed), open_link (searches and deep links). "
    "If read_screen returns a screenshot instead of a list, look at it and use tap_point with its pixel coordinates. "
    "Phone tasks: open_app or open_link first, then read_screen, then tap / type_text using the element numbers. "
    "Element numbers are only valid for the screen you just read: after ANY action (open, tap, type, press, "
    "swipe) call read_screen again before the next tap. If what you need isn't on screen, swipe up (or tap a "
    "likely menu entry) and read_screen again; try several steps before giving up. "
    "Switches show their state as [on] / [off] / [switch on] / [switch off]: check it before tapping, and if it "
    "is already as the user wants, don't tap. "
    "Do not ask the user how to proceed or whether to continue: just continue. "
    "Opening apps, navigating, scrolling, searching and reading never need permission. "
    "Only these need a yes first: sending a message, posting, calling, paying or buying, deleting, changing an "
    "account. To send a message: use whatsapp_message or sms_message (or open the chat and type it), then STOP "
    "and ask in one short sentence, e.g. 'Send \"on my way\" to Mom?'. When the user then says yes, read_screen "
    "and tap the Send button. If several contacts match, ask which one. "
    "Adding to a cart or wishlist is fine without asking; placing an order is not. "
    "Do not narrate your steps: say nothing while you work. When done, give only the result in at most two "
    "short spoken sentences."
)


def acknowledgement(call: dict) -> str:
    """What to say right away for the first tool call, so the user hears Raphael start."""
    name = call["name"].split("__", 1)[-1]
    try:
        args = json.loads(call.get("arguments") or "{}")
    except json.JSONDecodeError:
        args = {}
    if name == "open_app" and args.get("name"):
        return f"Opening {args['name']}."
    return {
        "set_timer": "",          # instant: the result itself is the reply
        "set_alarm": "",
        "read_notifications": "Checking your notifications.",
        "tavily_search": "Let me look that up.",
        "read_screen": "Let me look.",
        "open_link": "On it.",
    }.get(name, "On it.")


async def run(llm, hub: McpHub, system: str, messages: list[Message], max_tokens: int) -> AsyncIterator[str]:
    convo: list[dict] = [{"role": "system", "content": system + "\n\n" + TOOL_RULES}, *messages]
    started = time.monotonic()
    acknowledged = False
    for step in range(MAX_STEPS):
        calls: list[dict] = []
        said: list[str] = []
        streamed_final = False
        async for kind, value in llm.stream_tools(convo, hub.tools, max_tokens):
            if kind == "text":
                said.append(value)
                # Stream text live only on the first step, before we know whether tools follow:
                # that keeps plain answers instant. Later steps are held until we know it's the end.
                if step == 0 and not acknowledged:
                    streamed_final = True
                    yield value
            else:
                calls = value  # type: ignore[assignment]
        if not calls:
            if not streamed_final:
                yield "".join(said)
            return
        if not acknowledged:
            acknowledged = True
            if not said:  # model went straight to tools: announce it ourselves
                ack = acknowledgement(calls[0])
                if ack:
                    yield ack + " "
            else:
                yield " "  # model already said something on step 0; separate it from the answer
        convo.append({
            "role": "assistant",
            "content": "".join(said) or None,
            "tool_calls": [
                {"id": c["id"], "type": "function", "function": {"name": c["name"], "arguments": c["arguments"] or "{}"}}
                for c in calls
            ],
        })
        for c in calls:
            try:
                args = json.loads(c["arguments"] or "{}")
            except json.JSONDecodeError:
                args = {}
            t0 = time.perf_counter()
            if hasattr(hub, "call_with_images"):
                result, images = await hub.call_with_images(c["name"], args)
            else:
                result, images = await hub.call(c["name"], args), []
            log.info("tool %s(%s) → %s%s (%.0f ms)", c["name"], json.dumps(args)[:120], result[:120].replace("\n", " | "),
                     f" [+{len(images)} screenshot]" if images else "", (time.perf_counter() - t0) * 1000)
            convo.append({"role": "tool", "tool_call_id": c["id"], "content": result})
            if images:
                # Chat Completions only accepts images in user messages, so show the screenshot right after.
                convo.append({"role": "user", "content": [
                    {"type": "text", "text": f"(Screenshot returned by {c['name']}.)"},
                    *({"type": "image_url", "image_url": {"url": url}} for url in images),
                ]})
        if time.monotonic() - started > MAX_SECONDS:
            log.info("agent: time budget used up after %d steps", step + 1)
            break
    # Out of steps or time: let the model say where it got to, without more tools.
    convo.append({"role": "user", "content": "(System: stop using tools now. In one short sentence, say what you "
                                             "did and what's left, so the user can decide.)"})
    async for kind, value in llm.stream_tools(convo, [], max_tokens):
        if kind == "text":
            yield value
