#!/usr/bin/env python3
"""
CI-only helper (not part of the app). Drives the installed app on an emulator through adb +
UI Automator to:
  * run the three core tasks and assert their success conditions,
  * verify typed text and a chip selection survive portrait -> landscape -> portrait,
  * capture screenshots of every screen at 360x640 and 800x1280 px in both orientations.
Writes PNGs and report.md into the output directory given as argv[1].
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "edu.msu.cse476.lakelog"  # set by the caller
OUT = "out"
SHOTS = os.path.join(OUT, "screenshots")


def configure(pkg, out):
    global PKG, OUT, SHOTS
    PKG, OUT = pkg, out
    SHOTS = os.path.join(OUT, "screenshots")
    os.makedirs(SHOTS, exist_ok=True)

report = []
failures = []


def log(msg):
    print(msg, flush=True)
    report.append(msg)


def check(cond, msg):
    log(("PASS  " if cond else "FAIL  ") + msg)
    if not cond:
        failures.append(msg)
    return cond


def adb(*args, check_rc=False):
    r = subprocess.run(["adb", *args], capture_output=True)
    if check_rc and r.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {r.stderr.decode(errors='replace')}")
    return r.stdout


def sh(cmd):
    return adb("shell", cmd).decode(errors="replace")


# ---------------------------------------------------------------- UI tree helpers

def dump():
    for _ in range(6):
        sh("uiautomator dump /sdcard/ui.xml >/dev/null 2>&1")
        raw = adb("exec-out", "cat", "/sdcard/ui.xml").decode("utf-8", errors="replace")
        if raw.strip().startswith("<?xml") and "</hierarchy>" in raw:
            try:
                return ET.fromstring(raw)
            except ET.ParseError:
                pass
        time.sleep(0.7)
    raise RuntimeError("uiautomator dump failed")


def nodes(root):
    return list(root.iter("node"))


def center(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    return (x1 + x2) // 2, (y1 + y2) // 2


def find(text=None, rid=None, contains=False, root=None):
    root = root if root is not None else dump()
    for n in nodes(root):
        t = n.get("text") or ""
        if rid and not (n.get("resource-id") or "").endswith(":id/" + rid):
            continue
        if text is not None:
            if contains and text not in t:
                continue
            if not contains and t != text:
                continue
        return n
    return None


def wait_for(text=None, rid=None, contains=False, timeout=8.0):
    end = time.time() + timeout
    while time.time() < end:
        n = find(text, rid, contains)
        if n is not None:
            return n
        time.sleep(0.5)
    return None


def screen_size():
    # The root node of the UI dump spans the whole (already rotated) screen.
    first = next(dump().iter("node"))
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", first.get("bounds")))
    return x2, y2


def swipe_up():
    hide_keyboard()
    w, h = screen_size()
    adb("shell", "input", "swipe", str(w // 2), str(int(h * 0.75)), str(w // 2), str(int(h * 0.35)), "300")
    time.sleep(0.8)


def swipe_down():
    hide_keyboard()
    w, h = screen_size()
    adb("shell", "input", "swipe", str(w // 2), str(int(h * 0.35)), str(w // 2), str(int(h * 0.80)), "300")
    time.sleep(0.8)


def find_scrolling(text=None, rid=None, contains=False, max_swipes=6):
    for i in range(max_swipes + 1):
        n = find(text, rid, contains)
        if n is not None:
            return n
        swipe_up()
    return None


def tap_node(n):
    x, y = center(n)
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(1.0)


def tap(text=None, rid=None, contains=False, scroll=False):
    n = find_scrolling(text, rid, contains) if scroll else wait_for(text, rid, contains)
    if n is None:
        raise RuntimeError(f"could not find {text or rid}")
    tap_node(n)


def type_into(rid, value):
    hide_keyboard()
    tap(rid=rid, scroll=True)
    adb("shell", "input", "text", value.replace(" ", "%s"))
    time.sleep(0.6)
    hide_keyboard()


def hide_keyboard():
    for _ in range(2):
        if "mInputShown=true" in sh("dumpsys input_method | grep mInputShown"):
            adb("shell", "input", "keyevent", "111")  # ESCAPE closes the IME without navigating back
            time.sleep(0.8)


def back():
    hide_keyboard()
    adb("shell", "input", "keyevent", "4")
    time.sleep(1.0)


def rotate(landscape):
    r = 1 if landscape else 0
    for attempt in range(4):
        sh("settings put system accelerometer_rotation 0")
        sh(f"settings put system user_rotation {r}")
        sh(f"wm user-rotation lock {r}")
        time.sleep(2.5)
        w, h = screen_size()
        if (w > h) == landscape:
            return
    raise RuntimeError(f"display did not rotate to {'landscape' if landscape else 'portrait'}")


def shot(name):
    hide_keyboard()
    time.sleep(0.6)
    png = adb("exec-out", "screencap", "-p")
    path = os.path.join(SHOTS, name + ".png")
    with open(path, "wb") as f:
        f.write(png)
    log(f"      screenshot {name}.png")


def set_display(w, h, density):
    sh(f"wm size {w}x{h}")
    sh(f"wm density {density}")
    time.sleep(1.5)


def restart_app(clear_log=True):
    sh(f"am force-stop {PKG}")
    if clear_log:
        sh("logcat -b all -c")
    sh(f"am start -W -n {PKG}/.MainActivity")
    time.sleep(2.0)


def chip_checked(text):
    n = find(text)
    return n is not None and n.get("checked") == "true"


def field_text(rid):
    n = find(rid=rid)
    return (n.get("text") or "") if n is not None else ""


def crashed():
    out = adb("logcat", "-d", "-b", "crash").decode(errors="replace")
    return PKG in out, out




def slow_network(on):
    """Like the exam appendix: Wi-Fi off so traffic uses the throttled emulated cell network."""
    if on:
        sh("svc wifi disable")
        sh("svc data enable")
        adb("emu", "network", "speed", "gsm")
        adb("emu", "network", "delay", "gprs")
        time.sleep(4)
    else:
        adb("emu", "network", "speed", "full")
        adb("emu", "network", "delay", "none")
        sh("svc wifi enable")
        time.sleep(6)
