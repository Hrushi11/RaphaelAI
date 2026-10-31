"""MCP server that lets Raphael's agent see and operate an Android phone over adb.

Forked from minhalvp/android-mcp-server (Apache-2.0, see LICENSE). Changes for Raphael:
- MCP SDK v2 (MCPServer instead of FastMCP);
- NO arbitrary `execute_adb_shell_command` tool: only narrow, named actions below;
- read_screen returns numbered elements so the agent taps by id, not raw coordinates;
- adds open_app, open_link, tap, type_text, press, swipe, read_notifications.

Run standalone for debugging:  python server.py   (stdio transport)
"""

from mcp.server.mcpserver import Image, MCPServer

from adbdevicemanager import AdbDeviceManager, ScreenBusy

mcp = MCPServer(
    "android",
    instructions=(
        "Operate the user's Android phone. Typical loop: open_app or open_link → read_screen → "
        "tap / type_text by element id → read_screen again to check. Always read_screen before tapping."
    ),
)
device: AdbDeviceManager | None = None


def safe(fn):
    """Return errors as text the agent can read and recover from (the SDK would hide the message)."""
    import functools

    @functools.wraps(fn)
    def wrapper(*args, **kwargs):
        try:
            return fn(*args, **kwargs)
        except Exception as e:  # noqa: BLE001
            return f"Error: {e}"

    return wrapper


def dev() -> AdbDeviceManager:
    global device
    if device is None:
        device = AdbDeviceManager()
    return device


@mcp.tool()
@safe
def list_apps() -> str:
    """List installed (non-system) apps by package name."""
    return "\n".join(dev().get_packages())


@mcp.tool()
@safe
def open_app(name: str) -> str:
    """Open an app by its everyday name, e.g. "WhatsApp", "Spotify", "Settings", "Maps"."""
    return dev().open_app(name)


@mcp.tool()
@safe
def open_link(uri: str) -> str:
    """Open a link or deep link, e.g. "https://www.youtube.com/results?search_query=lofi",
    "spotify:search:lofi", "geo:0,0?q=coffee", "tel:+911234567890" (opens the dialer, does not call)."""
    return dev().open_link(uri)


@mcp.tool()
def read_screen() -> list:
    """What is on the phone screen now: numbered text, buttons and input fields.
    Use the numbers with tap / type_text. Call again after every action to see the result.
    If the screen can't be read as text, you get a screenshot instead: use tap_point with its pixel coordinates."""
    try:
        return [dev().read_screen()]
    except ScreenBusy as e:
        try:
            data, w, h = dev().screenshot_jpeg()
        except Exception as e2:  # noqa: BLE001
            return [f"Error: {e} Screenshot also failed: {e2}"]
        return [f"{e} Here is a screenshot ({w}x{h} px) instead. To tap something, call tap_point with "
                f"x, y in this screenshot's pixels.", Image(data=data, format="jpeg")]
    except Exception as e:  # noqa: BLE001
        return [f"Error: {e}"]


@mcp.tool()
@safe
def tap_point(x: int, y: int) -> str:
    """Tap at pixel (x, y) of the last screenshot from read_screen (only when it returned a screenshot)."""
    return dev().tap_point(x, y)


@mcp.tool()
@safe
def tap(element_id: int) -> str:
    """Tap the element with this number from the last read_screen."""
    return dev().tap(element_id)


@mcp.tool()
@safe
def type_text(text: str, element_id: int | None = None, submit: bool = False) -> str:
    """Type text, optionally into input field `element_id` (from read_screen) first.
    submit=True presses Enter afterwards (e.g. to run a search)."""
    return dev().type_text(text, element_id, submit)


@mcp.tool()
@safe
def press(key: str) -> str:
    """Press a button: back, home, enter, recents, play_pause, volume_up, volume_down."""
    return dev().press(key)


@mcp.tool()
@safe
def swipe(direction: str) -> str:
    """Scroll the screen: up (see more below), down, left or right."""
    return dev().swipe(direction)


@mcp.tool()
@safe
def set_timer(seconds: int, label: str = "") -> str:
    """Start a countdown timer directly (no UI). Prefer this over tapping through the Clock app."""
    return dev().set_timer(seconds, label)


@mcp.tool()
@safe
def set_alarm(hour: int, minute: int, label: str = "") -> str:
    """Set an alarm directly (24-hour clock, no UI). Prefer this over tapping through the Clock app."""
    return dev().set_alarm(hour, minute, label)


@mcp.tool()
@safe
def device_info() -> str:
    """Android version, phone model and battery level. Use this instead of navigating Settings."""
    return dev().device_info()


@mcp.tool()
@safe
def set_setting(name: str, on: bool) -> str:
    """Turn a setting on/off directly (no UI): dark_mode, do_not_disturb, wifi, bluetooth, mobile_data."""
    return dev().set_setting(name, on)


@mcp.tool()
@safe
def open_settings(page: str) -> str:
    """Open a Settings page directly: about, display, wifi, bluetooth, battery, sound, apps, location,
    notifications, storage."""
    return dev().open_settings(page)


@mcp.tool()
@safe
def find_contact(name: str) -> str:
    """Look up contacts whose name contains `name` (name and phone number)."""
    hits = dev().find_contact(name)
    return "\n".join(f"{n}: {num}" for n, num in hits) if hits else f"No contact named '{name}'."


@mcp.tool()
@safe
def whatsapp_message(who: str, text: str) -> str:
    """Open the WhatsApp chat with `who` (contact name or phone number) with `text` typed in, NOT sent.
    Fastest way to message someone on WhatsApp. Then ask the user to confirm before tapping Send."""
    return dev().whatsapp_message(who, text)


@mcp.tool()
@safe
def sms_message(who: str, text: str) -> str:
    """Open an SMS to `who` (contact name or number) with `text` typed in, NOT sent. Ask before sending."""
    return dev().sms_message(who, text)


@mcp.tool()
@safe
def read_notifications() -> str:
    """Recent notifications on the phone: app, title (usually the sender) and text."""
    return dev().read_notifications()


@mcp.tool()
def get_screenshot() -> Image:
    """A small screenshot of the phone screen (only if read_screen isn't enough)."""
    return Image(path=str(dev().take_screenshot()))


@mcp.tool()
def get_package_action_intents(package_name: str) -> list[str]:
    """Intent actions an app can be launched with (advanced)."""
    return dev().get_package_action_intents(package_name)


if __name__ == "__main__":
    mcp.run(transport="stdio")
