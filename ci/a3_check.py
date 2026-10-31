#!/usr/bin/env python3
"""
CI-only helper (not part of the app). Exercises the A3 "Nearest first" location feature on an
emulator: permission denial, rationale + grant, live re-sorting as GPS input changes, the
implausible-jump filter, rotation, and location turned off. Writes report.md + screenshots.
"""
import os
import sys
import threading
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ui_check as u  # noqa: E402  (reuses the adb / UI Automator helpers)

PKG = u.PKG
BEAUMONT = (42.7284, -84.4822)   # center of campus
IM_EAST = (42.7236, -84.4655)    # next to IM Sports-East
DETROIT = (42.3314, -83.0458)    # ~75 mi away: an implausible instant jump

target = {"pos": BEAUMONT}
stop = threading.Event()


def gps_feeder():
    # Emulated GPS: resend the current target every second, like a real receiver would.
    while not stop.is_set():
        lat, lng = target["pos"]
        u.adb("emu", "geo", "fix", str(lng), str(lat))
        time.sleep(1.0)


def titles_in_order():
    root = u.dump()
    return [n.get("text") for n in root.iter("node")
            if (n.get("resource-id") or "").endswith(":id/session_title")]


def status_text():
    n = u.find(rid="location_status")
    return (n.get("text") or "") if n is not None else ""


def wait_status(fragment, timeout=12):
    end = time.time() + timeout
    while time.time() < end:
        if fragment in status_text():
            return True
        time.sleep(0.7)
    return False


def chip_checked():
    n = u.find(rid="nearest_chip")
    return n is not None and n.get("checked") == "true"


def main():
    u.sh("settings put global window_animation_scale 0")
    u.sh("settings put global transition_animation_scale 0")
    u.sh("settings put global animator_duration_scale 0")
    u.sh("cmd location set-location-enabled true")
    u.set_display(360, 640, 160)
    u.rotate(False)
    threading.Thread(target=gps_feeder, daemon=True).start()
    time.sleep(2)

    u.log("### A2 iteration: sport picker in phone landscape")
    u.restart_app()
    u.rotate(True)
    root = u.dump()
    u.check(all(u.find(s, root=root) is not None for s in
                ["Basketball", "Soccer", "Volleyball", "Tennis", "Ultimate Frisbee", "Pickleball"]),
            "All six sports visible at once in landscape (no scrolling)")
    u.shot("a2_iteration_sport_picker_landscape")
    u.rotate(False)

    u.log("\n### Capability failure: permission refused")
    u.restart_app()
    u.tap("Basketball")
    u.check(u.find(rid="nearest_chip") is not None, "Nearest first chip is on the session list")
    u.check(u.find("mi away", contains=True) is None, "No distances before the feature is used")
    u.check(u.find("While using", contains=True) is None, "No permission prompt at launch or on opening the list")
    u.shot("a3_0_list_before")
    u.tap(rid="nearest_chip")
    dialog = u.wait_for("While using the app", contains=True, timeout=8)
    u.check(dialog is not None, "System permission dialog appears when the chip is tapped")
    u.shot("a3_1_permission_dialog")
    deny = u.wait_for("allow", contains=True)
    root = u.dump()
    deny = next((n for n in root.iter("node")
                 if "Don" in (n.get("text") or "") and "allow" in (n.get("text") or "")), None)
    u.check(deny is not None, "Deny button found")
    if deny is not None:
        u.tap_node(deny)
    u.check(wait_status("Location permission denied"), f"Denied message shown: '{status_text()}'")
    u.check(not chip_checked(), "Chip turns back off after denial")
    u.check(u.find("mi away", contains=True) is None, "No distances shown after denial")
    u.shot("a3_2_permission_denied")
    u.tap("Friday night runs", scroll=True)
    u.check(u.wait_for("IM Sports-West, Court 2") is not None, "App still usable: detail screen opens after denial")
    u.back()

    u.log("\n### Rationale, then grant, then live input")
    u.tap(rid="nearest_chip")
    u.check(u.wait_for("Use your location?", timeout=6) is not None, "Rationale dialog shown on second attempt")
    u.shot("a3_3_rationale")
    u.tap("Continue")
    allow = u.wait_for("While using the app", contains=True, timeout=8)
    u.check(allow is not None, "System dialog shown again after rationale")
    if allow is not None:
        u.tap_node(allow)
    u.check(wait_status("Sorted nearest first"), f"Active status: '{status_text()}'")
    time.sleep(2)
    order = titles_in_order()
    u.check(order[:1] == ["Chill shootaround"], f"At Beaumont Tower, nearest is Chill shootaround: {order}")
    u.check(u.find("Walkable", contains=True) is not None, "Cards show distance and Walkable band")
    u.shot("a3_4_sorted_beaumont")

    target["pos"] = IM_EAST
    end = time.time() + 12
    while time.time() < end and titles_in_order()[:1] != ["Competitive 5v5"]:
        time.sleep(1)
    order = titles_in_order()
    u.check(order[:1] == ["Competitive 5v5"], f"After moving to IM East, list re-sorts: {order}")
    u.shot("a3_5_sorted_im_east")

    u.log("\n### Edge condition: implausible location jump")
    target["pos"] = DETROIT
    u.check(wait_status("Ignored a sudden", timeout=6), f"Jump ignored: '{status_text()}'")
    order = titles_in_order()
    u.check(order[:1] == ["Competitive 5v5"], f"Order unchanged while jump is unconfirmed: {order}")
    u.check(u.find("77", contains=True) is None or u.find("Walkable", contains=True) is not None,
            "Distances not replaced by the bogus location")
    u.shot("a3_6_jump_ignored")
    u.check(wait_status("held steady", timeout=15), f"Jump confirmed after holding steady: '{status_text()}'")
    time.sleep(4)
    u.check("held steady" in status_text(), "Confirmation message still readable a few seconds later")
    u.check(u.find("Drive", contains=True) is not None, "Distances updated to Detroit (Drive band)")
    u.shot("a3_7_jump_confirmed")
    target["pos"] = BEAUMONT
    wait_status("held steady", timeout=15)
    time.sleep(3)

    u.log("\n### Rotation keeps the feature on")
    u.rotate(True)
    time.sleep(3)
    u.check(chip_checked(), "Chip still on in landscape")
    u.check(u.find("mi away", contains=True) is not None, "Distances still shown in landscape")
    u.shot("a3_8_landscape")
    u.rotate(False)
    time.sleep(2)

    u.log("\n### Location turned off (capability unavailable)")
    u.tap(rid="nearest_chip")  # off
    u.sh("cmd location set-location-enabled false")
    time.sleep(2)
    close = u.find("Close")  # Android may show its own "No location access" dialog
    if close is not None:
        u.tap_node(close)
    u.tap(rid="nearest_chip")  # on again
    u.check(wait_status("Location is turned off", timeout=6), f"Location-off message: '{status_text()}'")
    u.check(u.find(rid="location_settings_button") is not None, "Settings button offered")
    u.check(u.find("Friday night runs") is not None, "List still shown")
    u.shot("a3_9_location_off")
    u.sh("cmd location set-location-enabled true")

    bad, out = u.crashed()
    u.check(not bad, "No crash in logcat")
    if bad:
        u.log("```\n" + out[-4000:] + "\n```")

    stop.set()
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
