"""ADB access to one Android device.

Forked from minhalvp/android-mcp-server (Apache-2.0, see LICENSE) for Raphael:
- calls the `adb` binary directly (path from $ADB, else the Android SDK default) instead of
  pure-python-adb, and selects the device from $ANDROID_SERIAL when several are connected;
- temp files go to a private temp dir, not the working directory;
- adds structured helpers for safe, narrow actions (tap, type, keys, swipe, open app/link,
  notifications) so the MCP server never needs to expose arbitrary shell commands.
"""

import os
import re
import shlex
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

DEFAULT_ADB = Path.home() / "Library/Android/sdk/platform-tools/adb"

# Spoken app names → package, for apps whose package name doesn't contain the name.
APP_ALIASES = {
    "youtube": "com.google.android.youtube",
    "maps": "com.google.android.apps.maps",
    "google maps": "com.google.android.apps.maps",
    "gmail": "com.google.android.gm",
    "chrome": "com.android.chrome",
    "camera": "com.google.android.GoogleCamera",
    "settings": "com.android.settings",
    "phone": "com.google.android.dialer",
    "dialer": "com.google.android.dialer",
    "messages": "com.google.android.apps.messaging",
    "sms": "com.google.android.apps.messaging",
    "photos": "com.google.android.apps.photos",
    "calendar": "com.google.android.calendar",
    "clock": "com.google.android.deskclock",
    "alarm": "com.google.android.deskclock",
    "contacts": "com.google.android.contacts",
    "play store": "com.android.vending",
    "whatsapp": "com.whatsapp",
    "spotify": "com.spotify.music",
    "instagram": "com.instagram.android",
}

DUMP_TIMEOUT_S = 5.0  # longer than a normal dump (~2 s), much shorter than uiautomator giving up (~12 s)

KEYS = {"back": 4, "home": 3, "enter": 66, "recents": 187, "play_pause": 85, "volume_up": 24, "volume_down": 25}


class ScreenBusy(RuntimeError):
    """uiautomator can't dump screens that never go idle (animations, live content)."""


@dataclass
class Element:
    id: int
    kind: str
    label: str
    clickable: bool
    editable: bool
    center: tuple[int, int]


class AdbDeviceManager:
    def __init__(self, device_name: str | None = None) -> None:
        self.adb = os.environ.get("ADB") or str(DEFAULT_ADB)
        if not Path(self.adb).exists():
            raise RuntimeError(f"adb not found at {self.adb}; set $ADB")
        devices = self.get_available_devices()
        if not devices:
            raise RuntimeError("No Android device connected over adb (USB or Wi-Fi debugging).")
        wanted = device_name or os.environ.get("ANDROID_SERIAL")
        if wanted:
            if wanted not in devices:
                raise RuntimeError(f"Device {wanted} not found. Connected: {devices}")
            self.serial = wanted
        elif len(devices) == 1:
            self.serial = devices[0]
        else:
            raise RuntimeError(f"Several devices connected {devices}; set $ANDROID_SERIAL.")
        self.tmp = Path(tempfile.mkdtemp(prefix="raphael-adb-"))
        self.elements: dict[int, Element] = {}  # from the last read_screen()
        self.shot_scale = 0.0  # screenshot px → device px, for tap_point

    # ---------------------------------------------------------------- plumbing

    def get_available_devices(self) -> list[str]:
        out = subprocess.run([self.adb, "devices"], capture_output=True, text=True, timeout=10).stdout
        return [line.split()[0] for line in out.splitlines()[1:] if line.strip().endswith("device")]

    def shell(self, command: str, timeout: float = 15) -> str:
        r = subprocess.run([self.adb, "-s", self.serial, "shell", command], capture_output=True, text=True, timeout=timeout)
        return (r.stdout or "") + (r.stderr or "")

    def pull(self, remote: str, local: Path) -> None:
        subprocess.run([self.adb, "-s", self.serial, "pull", remote, str(local)], capture_output=True, timeout=15, check=True)

    # ---------------------------------------------------------------- apps

    def get_packages(self, third_party_only: bool = True) -> list[str]:
        out = self.shell("pm list packages -3" if third_party_only else "pm list packages")
        return sorted(line[8:].strip() for line in out.splitlines() if line.startswith("package:"))

    def find_package(self, name: str) -> str | None:
        key = name.lower().strip()
        all_pkgs = self.get_packages(third_party_only=False)
        if key in APP_ALIASES and APP_ALIASES[key] in all_pkgs:
            return APP_ALIASES[key]
        squashed = re.sub(r"[^a-z0-9]", "", key)
        hits = [p for p in all_pkgs if squashed and squashed in p.replace(".", "").lower()]
        return min(hits, key=len) if hits else None

    def open_app(self, name: str) -> str:
        pkg = self.find_package(name)
        if not pkg:
            return f"No installed app matches '{name}'."
        self._screen_changed()
        out = self.shell(f"monkey -p {shlex.quote(pkg)} -c android.intent.category.LAUNCHER 1")
        if "No activities found" in out:
            return f"{pkg} has no launchable screen."
        return f"Opened {pkg}."

    def open_link(self, uri: str) -> str:
        self._screen_changed()
        out = self.shell(f"am start -a android.intent.action.VIEW -d {shlex.quote(uri)}")
        return "Opened." if "Error" not in out else out.strip()[-300:]

    def get_package_action_intents(self, package_name: str) -> list[str]:
        output = self.shell(f"dumpsys package {shlex.quote(package_name)}")
        start = output.find("Activity Resolver Table:")
        if start == -1:
            return []
        section = output[start:]
        nd = section.find("\n  Non-Data Actions:")
        if nd == -1:
            return []
        end = section[nd:].find("\n\n")
        block = section[nd:] if end == -1 else section[nd:nd + end]
        return [ln.strip() for ln in block.split("\n") if ln.strip().startswith(("android.", "com."))]

    # ---------------------------------------------------------------- screen

    def read_screen(self, max_elements: int = 60) -> str:
        """Numbered list of what's on screen: visible text, buttons and input fields."""
        # uiautomator waits for the UI to go idle and takes ~12 s to give up on screens that never
        # are (animations, live content). Cap it: if it doesn't finish in DUMP_TIMEOUT_S, the caller
        # falls back to a screenshot, which is quicker than waiting.
        local = self.tmp / "window_dump.xml"
        try:
            out = self.shell("uiautomator dump --compressed /sdcard/raphael_dump.xml", timeout=DUMP_TIMEOUT_S)
        except subprocess.TimeoutExpired:
            out = "timeout"
        if "dumped to" not in out:
            raise ScreenBusy("Android can't read this screen as text right now (it never stops changing).")
        self.pull("/sdcard/raphael_dump.xml", local)
        self.shell("rm /sdcard/raphael_dump.xml")
        root = ET.parse(local).getroot()

        self.elements = {}
        lines: list[str] = []
        package = ""
        for node in root.iter("node"):
            pkg = node.get("package", "")
            if pkg == "com.android.systemui":
                continue  # status bar / navigation, not the app
            package = package or pkg
            text = (node.get("text") or "").strip()
            desc = (node.get("content-desc") or "").strip()
            if node.get("checkable") == "true":
                state = "on" if node.get("checked") == "true" else "off"
                if not (text or desc) and lines:
                    # A bare switch/checkbox: its label is the row text just before it.
                    lines[-1] += f" [switch {state}]"
                    continue
                text = text or desc
                desc = ""
                checked_note = f" [{state}]"
            else:
                checked_note = ""
            clickable = node.get("clickable") == "true" or node.get("long-clickable") == "true"
            editable = "EditText" in node.get("class", "") or node.get("focusable") == "true" and "Edit" in node.get("class", "")
            if not (text or desc or editable):
                continue
            m = re.findall(r"\[(-?\d+),(-?\d+)\]", node.get("bounds", ""))
            if len(m) != 2:
                continue
            (x1, y1), (x2, y2) = [tuple(map(int, p)) for p in m]
            if x2 <= x1 or y2 <= y1:
                continue
            eid = len(self.elements) + 1
            kind = node.get("class", "").rsplit(".", 1)[-1] or "View"
            label = (text or desc)[:90]
            self.elements[eid] = Element(eid, kind, label, clickable, editable, ((x1 + x2) // 2, (y1 + y2) // 2))
            flags = ", ".join(f for f, on in (("tap", clickable), ("editable", editable)) if on)
            extra = f" — {desc[:60]}" if text and desc and desc != text else ""
            lines.append(f"[{eid}] {kind} \"{label}\"{extra}{checked_note}" + (f" ({flags})" if flags else ""))
            if eid >= max_elements:
                lines.append("… (more elements not shown; scroll or be more specific)")
                break
        header = f"App on screen: {package or 'unknown'}"
        return header + "\n" + ("\n".join(lines) if lines else "(no readable elements)")

    def take_screenshot(self) -> Path:
        self.shell("screencap -p /sdcard/raphael_shot.png")
        local = self.tmp / "screenshot.png"
        self.pull("/sdcard/raphael_shot.png", local)
        self.shell("rm /sdcard/raphael_shot.png")
        from PIL import Image as PILImage

        out = self.tmp / "compressed_screenshot.png"
        with PILImage.open(local) as img:
            img.resize((int(img.width * 0.3), int(img.height * 0.3)), PILImage.Resampling.LANCZOS).save(out, "PNG", optimize=True)
        return out

    def screenshot_jpeg(self, width: int = 540) -> tuple[bytes, int, int]:
        """A downscaled JPEG of the screen for the vision fallback; tap_point maps back."""
        import io

        from PIL import Image as PILImage

        self.shell("screencap -p /sdcard/raphael_shot.png")
        local = self.tmp / "screenshot.png"
        self.pull("/sdcard/raphael_shot.png", local)
        self.shell("rm /sdcard/raphael_shot.png")
        with PILImage.open(local) as img:
            scale = width / img.width
            small = img.convert("RGB").resize((width, int(img.height * scale)), PILImage.Resampling.LANCZOS)
        self.shot_scale = scale
        buf = io.BytesIO()
        small.save(buf, "JPEG", quality=70)
        return buf.getvalue(), small.width, small.height

    def tap_point(self, x: int, y: int) -> str:
        if not self.shot_scale:
            raise ValueError("tap_point needs a screenshot first (read_screen returns one when text isn't available).")
        dx, dy = int(x / self.shot_scale), int(y / self.shot_scale)
        self.shell(f"input tap {dx} {dy}")
        self._screen_changed()
        self.shot_scale = 0.0
        return f"Tapped at ({x}, {y}) on the screenshot. Call read_screen to see the result."

    # ---------------------------------------------------------------- actions

    def _element(self, element_id: int) -> Element:
        if not self.elements:
            raise ValueError("The screen changed since the last read_screen (or it was never read): "
                             "call read_screen first, then use the new numbers.")
        if element_id not in self.elements:
            raise ValueError(f"No element [{element_id}] on the last read_screen.")
        return self.elements[element_id]

    def _screen_changed(self) -> None:
        """Element numbers belong to one screen read; any action may change the screen."""
        self.elements = {}

    def tap(self, element_id: int) -> str:
        e = self._element(element_id)
        self.shell(f"input tap {e.center[0]} {e.center[1]}")
        self._screen_changed()
        return f"Tapped [{element_id}] \"{e.label}\". Call read_screen to see the result."

    def type_text(self, text: str, element_id: int | None = None, submit: bool = False) -> str:
        if not text.isascii():
            return "Only plain English (ASCII) text can be typed over adb."
        if element_id is not None:
            e = self._element(element_id)
            self.shell(f"input tap {e.center[0]} {e.center[1]}")
        # `input text` needs spaces as %s and shell-safe quoting
        escaped = text.replace("%", "\\%").replace(" ", "%s")
        self.shell(f"input text {shlex.quote(escaped)}")
        if submit:
            self.shell(f"input keyevent {KEYS['enter']}")
        self._screen_changed()
        return f"Typed \"{text}\"" + (" and pressed Enter." if submit else ".")

    def press(self, key: str) -> str:
        code = KEYS.get(key)
        if code is None:
            return f"Unknown key '{key}'. Use one of: {', '.join(KEYS)}."
        self.shell(f"input keyevent {code}")
        self._screen_changed()
        return f"Pressed {key}."

    def swipe(self, direction: str) -> str:
        size = re.search(r"(\d+)x(\d+)", self.shell("wm size"))
        w, h = (int(size.group(1)), int(size.group(2))) if size else (1080, 2400)
        cx, cy = w // 2, h // 2
        moves = {
            "up": (cx, int(h * 0.75), cx, int(h * 0.3)),     # content moves up = scroll down the page
            "down": (cx, int(h * 0.3), cx, int(h * 0.75)),
            "left": (int(w * 0.85), cy, int(w * 0.15), cy),
            "right": (int(w * 0.15), cy, int(w * 0.85), cy),
        }
        if direction not in moves:
            return "Direction must be up, down, left or right."
        x1, y1, x2, y2 = moves[direction]
        self.shell(f"input swipe {x1} {y1} {x2} {y2} 300")
        self._screen_changed()
        return f"Swiped {direction}."

    # ---------------------------------------------------------------- shortcuts (intents)
    # Direct Android intents: one call, no tapping through screens, so fast and reliable.

    def set_timer(self, seconds: int, label: str = "") -> str:
        cmd = (f"am start -a android.intent.action.SET_TIMER --ei android.intent.extra.alarm.LENGTH {int(seconds)} "
               f"--ez android.intent.extra.alarm.SKIP_UI true")
        if label:
            cmd += f" --es android.intent.extra.alarm.MESSAGE {shlex.quote(label)}"
        out = self.shell(cmd)
        return f"Timer set for {int(seconds)} seconds." if "Error" not in out else out.strip()[-300:]

    def set_alarm(self, hour: int, minute: int, label: str = "") -> str:
        cmd = (f"am start -a android.intent.action.SET_ALARM --ei android.intent.extra.alarm.HOUR {int(hour)} "
               f"--ei android.intent.extra.alarm.MINUTES {int(minute)} --ez android.intent.extra.alarm.SKIP_UI true")
        if label:
            cmd += f" --es android.intent.extra.alarm.MESSAGE {shlex.quote(label)}"
        out = self.shell(cmd)
        return f"Alarm set for {int(hour):02d}:{int(minute):02d}." if "Error" not in out else out.strip()[-300:]

    # ---------------------------------------------------------------- device & settings shortcuts

    def device_info(self) -> str:
        prop = lambda k: self.shell(f"getprop {k}").strip()  # noqa: E731
        battery = self.shell("dumpsys battery")
        level = re.search(r"level: (\d+)", battery)
        charging = re.search(r"(AC|USB|Wireless) powered: true", battery)
        return (f"Android {prop('ro.build.version.release')}, {prop('ro.product.manufacturer')} {prop('ro.product.model')}, "
                f"battery {level.group(1) if level else '?'}%{' (charging)' if charging else ''}.")

    SETTINGS = {
        "dark_mode": ("cmd uimode night yes", "cmd uimode night no"),
        "do_not_disturb": ("cmd notification set_dnd on", "cmd notification set_dnd off"),
        "wifi": ("svc wifi enable", "svc wifi disable"),
        "bluetooth": ("cmd bluetooth_manager enable", "cmd bluetooth_manager disable"),
        "mobile_data": ("svc data enable", "svc data disable"),
    }

    def set_setting(self, name: str, on: bool) -> str:
        cmds = self.SETTINGS.get(name)
        if not cmds:
            return f"Unknown setting '{name}'. Use one of: {', '.join(self.SETTINGS)}."
        self.shell(cmds[0] if on else cmds[1])
        return f"{name.replace('_', ' ').capitalize()} turned {'on' if on else 'off'}."

    SETTINGS_PAGES = {
        "about": "android.settings.DEVICE_INFO_SETTINGS", "display": "android.settings.DISPLAY_SETTINGS",
        "wifi": "android.settings.WIFI_SETTINGS", "bluetooth": "android.settings.BLUETOOTH_SETTINGS",
        "battery": "android.intent.action.POWER_USAGE_SUMMARY", "sound": "android.settings.SOUND_SETTINGS",
        "apps": "android.settings.APPLICATION_SETTINGS", "location": "android.settings.LOCATION_SOURCE_SETTINGS",
        "notifications": "android.settings.NOTIFICATION_SETTINGS", "storage": "android.settings.INTERNAL_STORAGE_SETTINGS",
    }

    def open_settings(self, page: str) -> str:
        action = self.SETTINGS_PAGES.get(page)
        if not action:
            return f"Unknown page '{page}'. Use one of: {', '.join(self.SETTINGS_PAGES)}."
        self._screen_changed()
        self.shell(f"am start -a {action}")
        return f"Opened {page} settings."

    # ---------------------------------------------------------------- contacts & messaging shortcuts

    def find_contact(self, name: str, limit: int = 5) -> list[tuple[str, str]]:
        out = self.shell("content query --uri content://com.android.contacts/data/phones --projection display_name:data1")
        rows = re.findall(r"display_name=(.*?), data1=(.*)", out)
        key = name.lower().strip()
        hits = [(n.strip(), num.strip()) for n, num in rows if key in n.lower()]
        seen, unique = set(), []
        for n, num in hits:
            digits = re.sub(r"\D", "", num)
            if digits not in seen:
                seen.add(digits)
                unique.append((n, num))
        return unique[:limit]

    def _resolve_number(self, who: str) -> tuple[str, str] | str:
        digits = re.sub(r"\D", "", who)
        if len(digits) >= 10:
            return who, digits
        hits = self.find_contact(who)
        if not hits:
            return f"No contact named '{who}'."
        if len({h[0] for h in hits}) > 1:
            return "Several contacts match: " + "; ".join(f"{n} ({num})" for n, num in hits) + ". Ask which one."
        return hits[0][0], re.sub(r"\D", "", hits[0][1])

    def whatsapp_message(self, who: str, text: str) -> str:
        r = self._resolve_number(who)
        if isinstance(r, str):
            return r
        name, digits = r
        if len(digits) == 10:
            digits = "91" + digits  # Indian mobile without country code
        from urllib.parse import quote

        self._screen_changed()
        self.shell(f"am start -a android.intent.action.VIEW -d {shlex.quote(f'https://wa.me/{digits}?text={quote(text)}')}")
        return (f"WhatsApp chat with {name} opened with the message typed but NOT sent. Ask the user to confirm; "
                f"after a yes, read_screen and tap Send.")

    def sms_message(self, who: str, text: str) -> str:
        r = self._resolve_number(who)
        if isinstance(r, str):
            return r
        name, digits = r
        self._screen_changed()
        self.shell(f"am start -a android.intent.action.SENDTO -d smsto:{digits} --es sms_body {shlex.quote(text)}")
        return (f"SMS to {name} opened with the message typed but NOT sent. Ask the user to confirm; "
                f"after a yes, read_screen and tap Send.")

    # ---------------------------------------------------------------- notifications

    def read_notifications(self, limit: int = 10) -> str:
        """Recent notifications (app, title, text) from the system notification list."""
        out = self.shell("dumpsys notification --noredact", timeout=20)
        items = []
        for block in re.split(r"\n\s*NotificationRecord\(", out)[1:]:
            pkg = re.search(r"pkg=(\S+)", block)
            title = re.search(r"android\.title=\S+ \((.*?)\)\n", block) or re.search(r"android\.title=String \((.*?)\)", block)
            text = re.search(r"android\.text=\S+ \((.*?)\)\n", block) or re.search(r"android\.text=String \((.*?)\)", block)
            if not (title or text):
                continue
            p = pkg.group(1) if pkg else "?"
            if p in {"android", "com.android.systemui", "ai.raphael"}:
                continue
            items.append(f"- {p}: {title.group(1) if title else ''} — {text.group(1) if text else ''}".strip())
            if len(items) >= limit:
                break
        return "\n".join(items) if items else "No notifications."
