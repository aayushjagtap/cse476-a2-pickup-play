#!/usr/bin/env python3
"""
CI-only helper (not part of the app). Verifies A4 local persistence on an emulator: a created
session and join/leave choices survive the app process being killed and relaunched, and the
sample sessions are seeded once and never duplicated. Writes report.md + screenshots.
"""
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ui_check as u  # noqa: E402  (reuses the adb / UI Automator helpers)

PKG = u.PKG
CREATED = "Persist test match"
BASKETBALL_SAMPLES = ["Friday night runs", "Chill shootaround", "Competitive 5v5"]


def cold_restart(why):
    """Kill the whole app process (not just the activity) and launch it again."""
    u.sh(f"am force-stop {PKG}")
    time.sleep(1.0)
    u.check(u.sh("pidof " + PKG).strip() == "", f"App process fully stopped ({why})")
    u.sh(f"am start -W -n {PKG}/.MainActivity")
    time.sleep(2.0)
    u.check(u.wait_for("What do you want to play?") is not None, "Relaunched to the sport picker")


def list_titles():
    """Every session title in the current list, scrolling to the end."""
    seen = []
    for _ in range(6):
        root = u.dump()
        before = len(seen)
        for n in root.iter("node"):
            if (n.get("resource-id") or "").endswith(":id/session_title"):
                t = n.get("text")
                if t not in seen:
                    seen.append(t)
        if len(seen) == before:
            break
        u.swipe_up()
    for _ in range(4):
        u.swipe_down()
    return seen


def open_sport(name):
    u.tap(name)
    u.check(u.wait_for(f"{name} sessions", contains=True) is not None, f"{name} list opened")


def to_picker():
    for _ in range(4):
        if u.find("What do you want to play?") is not None:
            return
        u.back()


def main():
    u.sh("settings put global window_animation_scale 0")
    u.sh("settings put global transition_animation_scale 0")
    u.sh("settings put global animator_duration_scale 0")
    u.rotate(False)

    u.log("### First launch: sample sessions seeded")
    u.fresh_start()
    open_sport("Basketball")
    titles = list_titles()
    u.check(sorted(titles) == sorted(BASKETBALL_SAMPLES), f"Basketball shows the 3 samples: {titles}")

    u.log("\n### Join a session, then create one for another sport")
    u.tap("Chill shootaround", scroll=True)
    u.check(u.find_scrolling("3 / 8 players") is not None, "Before joining: 3 / 8 players")
    u.tap("Join session", scroll=True)
    u.check(u.wait_for("You're in! See you there.", timeout=4) is not None, "Joined")
    u.back()
    u.tap("Create session")
    u.type_into("title_input", CREATED)
    u.type_into("location_input", "Old College Field")
    u.hide_keyboard()
    u.tap("Tennis", scroll=True)  # create for a different sport than the list we came from
    u.tap_node(u.find_scrolling(rid="submit_button"))
    u.check(u.wait_for("Session created", contains=True, timeout=5) is not None, "Create confirmed")
    u.check(u.wait_for("Tennis sessions", contains=True) is not None, "Navigated to the Tennis list")
    u.check(u.find(CREATED) is not None, "Created session shown before restart")
    u.shot("a4_1_before_restart")

    u.log("\n### Restart 1: created session and join survive")
    cold_restart("restart 1")
    open_sport("Tennis")
    titles = list_titles()
    u.check(CREATED in titles, f"Created session still in Tennis list after restart: {titles}")
    u.check(titles.count(CREATED) == 1, "Created session not duplicated")
    u.check(u.find("Hosting") is not None, "Still marked Hosting")
    u.shot("a4_2_tennis_after_restart")
    u.tap(CREATED)
    u.check(u.wait_for("Old College Field") is not None, "Created session detail intact (location)")
    u.check(u.find_scrolling("1 / 10 players") is not None, "Created session detail intact (1 / 10 players)")
    to_picker()
    open_sport("Basketball")
    titles = list_titles()
    u.check(sorted(titles) == sorted(BASKETBALL_SAMPLES), f"Basketball samples not duplicated: {titles}")
    u.check(u.find("Joined") is not None, "'Joined' badge survived restart")
    u.tap("Chill shootaround", scroll=True)
    u.check(u.find_scrolling("4 / 8 players") is not None, "Join survived restart: 4 / 8 players")
    u.shot("a4_3_joined_after_restart")
    u.tap("Leave session", scroll=True)
    u.check(u.find_scrolling("3 / 8 players") is not None, "Left: back to 3 / 8 players")

    u.log("\n### Restart 2: leave survives, still no duplicates")
    cold_restart("restart 2")
    open_sport("Basketball")
    titles = list_titles()
    u.check(sorted(titles) == sorted(BASKETBALL_SAMPLES), f"Basketball samples still 3: {titles}")
    u.check(u.find("Joined") is None, "No 'Joined' badge after leaving + restart")
    u.tap("Chill shootaround", scroll=True)
    u.check(u.find_scrolling("3 / 8 players") is not None, "Leave survived restart: 3 / 8 players")
    u.check(u.find_scrolling("Join session") is not None, "Join button offered again")
    u.shot("a4_4_left_after_restart")
    to_picker()
    open_sport("Tennis")
    u.check(list_titles().count(CREATED) == 1, "Created session still present exactly once")

    u.log("\n### Rotation still works with database-backed data")
    u.rotate(True)
    time.sleep(2)
    u.check(u.find(CREATED) is not None, "Created session visible in landscape")
    u.rotate(False)

    bad, out = u.crashed()
    u.check(not bad, "No crash in logcat")
    if bad:
        u.log("```\n" + out[-4000:] + "\n```")
    u.log(f"\n**{len(u.failures)} failure(s)**")
    with open(os.path.join(u.OUT, "report.md"), "w") as f:
        f.write("\n".join(u.report) + "\n")
    sys.exit(1 if u.failures else 0)


if __name__ == "__main__":
    try:
        main()
    except Exception as e:  # always write a report
        u.check(False, f"aborted: {e}")
        u.shot("ERROR")
        with open(os.path.join(u.OUT, "report.md"), "w") as f:
            f.write("\n".join(u.report) + "\n")
        sys.exit(1)
