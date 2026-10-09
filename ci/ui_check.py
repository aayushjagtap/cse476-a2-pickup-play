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

PKG = "com.aayushjagtap.pickupplay"
OUT = sys.argv[1] if len(sys.argv) > 1 else "out"
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


def restart_app():
    sh(f"am force-stop {PKG}")
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


# ---------------------------------------------------------------- scenarios

def run_tasks_and_screens(label, w, h, density, landscape):
    """Full pass of all three tasks with a screenshot of every screen."""
    log(f"\n### {label}: {w}x{h} px display, density {density}, {'landscape' if landscape else 'portrait'}")
    set_display(w, h, density)
    rotate(landscape)
    restart_app()
    tag = label.replace(" ", "_")

    # Screen 1 + Task 1: pick a sport
    check(wait_for("What do you want to play?") is not None, "Sport picker screen is shown on launch")
    shot(f"{tag}_1_sport_picker")
    tap("Basketball")
    check(wait_for("Basketball sessions", contains=True) is not None,
          "Task 1: list opens with heading '<emoji> Basketball sessions'")
    root = dump()
    check(find("Friday night runs", root=root) is not None and find("Small-sided soccer", root=root) is None,
          "Task 1: list shows basketball sessions only")
    shot(f"{tag}_2_session_list")

    # Screen 3 + Task 3: open two different items (list-detail), then join one
    tap("Friday night runs", scroll=True)
    check(wait_for("IM Sports-West, Court 2") is not None, "List-detail: 'Friday night runs' opens its own detail")
    back()
    tap("Chill shootaround", scroll=True)
    check(wait_for("IM Sports-Circle, Court 1") is not None, "List-detail: 'Chill shootaround' opens its own detail")
    check(find("3 / 8 players") is not None, "Task 3: detail shows 3 / 8 players before joining")
    shot(f"{tag}_3_session_detail")
    tap("Join session", scroll=landscape)
    check(wait_for("You're in! See you there.", timeout=4) is not None, "Task 3: status shows 'You're in! See you there.'")
    check(find("4 / 8 players", contains=False) is not None or find_scrolling("4 / 8 players") is not None,
          "Task 3: player count went up to 4 / 8")
    check(find("Leave session") is not None or find_scrolling("Leave session") is not None,
          "Task 3: button changed to 'Leave session'")
    shot(f"{tag}_3b_session_detail_joined")
    back()
    check(wait_for("Joined") is not None, "Task 3: list shows 'Joined' badge")

    # Screen 4 + Task 2: create a session
    tap("Create session")
    check(wait_for("Session name") is not None or find(rid="title_input") is not None, "Create form opens")
    type_into("title_input", "Thursday hoops")
    type_into("location_input", "IM West Court 3")
    hide_keyboard()
    swipe_down(); swipe_down()
    shot(f"{tag}_4_create_session")
    tap("Competitive", scroll=True)
    submit = find_scrolling(rid="submit_button")
    check(submit is not None, "Create button is reachable")
    tap_node(submit)
    check(wait_for("Session created", contains=True, timeout=4) is not None,
          "Task 2: confirmation 'Session created. You're hosting.' shown")
    root = dump()
    check(find("Thursday hoops", root=root) is not None, "Task 2: new session appears in the list")
    check(find("Hosting", root=root) is not None, "Task 2: new session shows 'Hosting' badge")
    shot(f"{tag}_5_list_after_create")

    bad, out = crashed()
    check(not bad, "No crash in logcat")
    if bad:
        log("```\n" + out[-4000:] + "\n```")


def rotation_check(label, w, h, density):
    log(f"\n### Rotation state check on {label} ({w}x{h} px, density {density})")
    set_display(w, h, density)
    rotate(False)
    restart_app()
    tap("Basketball")
    tap("Create session")
    type_into("title_input", "Rotation test")
    type_into("location_input", "Munn Field")
    hide_keyboard()
    tap("Soccer", scroll=True)
    tap("Tomorrow", scroll=True)
    tap("Competitive", scroll=True)
    swipe_down(); swipe_down(); swipe_down()

    def verify(stage):
        ok_text = field_text("title_input") == "Rotation test" and field_text("location_input") == "Munn Field"
        found = {}
        for chip in ["Soccer", "Tomorrow", "Competitive"]:
            n = find_scrolling(chip)
            found[chip] = n is not None and n.get("checked") == "true"
        swipe_down(); swipe_down(); swipe_down()
        ok_text = ok_text or (field_text("title_input") == "Rotation test" and field_text("location_input") == "Munn Field")
        check(ok_text, f"{stage}: typed text intact (name + location)")
        check(all(found.values()), f"{stage}: selections intact {found}")

    verify("Before rotation (portrait)")
    rotate(True)
    verify("After rotating to landscape")
    shot(f"rotation_{label.replace(' ', '_')}_landscape")
    rotate(False)
    verify("After rotating back to portrait")
    shot(f"rotation_{label.replace(' ', '_')}_portrait_again")


def main():
    sh("settings put global window_animation_scale 0")
    sh("settings put global transition_animation_scale 0")
    sh("settings put global animator_duration_scale 0")
    configs = [
        ("phone portrait", 360, 640, 160, False),
        ("phone landscape", 360, 640, 160, True),
        ("tablet portrait", 800, 1280, 213, False),
        ("tablet landscape", 800, 1280, 213, True),
    ]
    for cfg in configs:
        try:
            run_tasks_and_screens(*cfg)
        except Exception as e:  # keep going so we still get the other screenshots
            check(False, f"{cfg[0]}: scenario aborted: {e}")
            shot(f"ERROR_{cfg[0].replace(' ', '_')}")
    for label, w, h, d in [("phone", 360, 640, 160), ("tablet", 800, 1280, 213)]:
        try:
            rotation_check(label, w, h, d)
        except Exception as e:
            check(False, f"rotation {label}: aborted: {e}")
            shot(f"ERROR_rotation_{label}")

    sh("wm size reset")
    sh("wm density reset")
    log(f"\n**{len(failures)} failure(s)**")
    with open(os.path.join(OUT, "report.md"), "w") as f:
        f.write("\n".join(report) + "\n")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
