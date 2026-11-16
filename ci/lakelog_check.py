#!/usr/bin/env python3
"""CI-only: drives LakeLog on an emulator against the 12 grading rows. argv[1] = output dir."""
import os
import re
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ui as u  # noqa: E402

PKG = "edu.msu.cse476.lakelog"
u.configure(PKG, sys.argv[1] if len(sys.argv) > 1 else "out/lakelog")

PHONE = (1080, 2340, 440)   # ~393 x 851 dp
TABLET = (1600, 2560, 320)  # 800 x 1280 dp


def descs(contains):
    root = u.dump()
    return [n for n in root.iter("node") if contains in (n.get("content-desc") or "")]


def rows():
    """Visible reading rows as (time, level, has_note) from their content descriptions."""
    out = []
    for n in u.dump().iter("node"):
        d = n.get("content-desc") or ""
        m = re.match(r"^(\w{3} \d+ \d\d:\d\d UTC), (.+?)(, has a note)?$", d)
        if m:
            out.append((m.group(1), m.group(2), bool(m.group(3))))
    return out


def find_desc_scrolling(contains, max_swipes=8):
    for _ in range(max_swipes + 1):
        hits = descs(contains)
        if hits:
            return hits[0]
        u.swipe_up()
    return None


def wait_rows(timeout=60):
    end = time.time() + timeout
    while time.time() < end:
        r = rows()
        if r:
            return r
        time.sleep(1)
    return []


def sensor_section():
    out = u.sh("dumpsys sensorservice")
    m = re.search(r"active connections(.*?)(\n\S[^\n]*:\n|\Z)", out, re.S | re.I)
    return (m.group(1) if m else out)


def light_in_use():
    return PKG in sensor_section()


def open_readings():
    u.tap(rid="readingsButton", scroll=True)


def main():
    for k in ["window_animation_scale", "transition_animation_scale", "animator_duration_scale"]:
        u.sh(f"settings put global {k} 0")
    u.sh("settings put system font_scale 1.0")
    u.sh("cmd connectivity airplane-mode disable")
    u.slow_network(False)
    u.set_display(*PHONE)
    u.sh(f"pm clear {PKG}")
    u.restart_app()
    u.rotate(False)

    u.log("### Row 1: light level in lux follows the environment")
    u.adb("emu", "sensor", "set", "light", "312")
    u.check(u.wait_for("312 lx", timeout=8) is not None, "Shows 312 lx after the light changes to 312")
    u.adb("emu", "sensor", "set", "light", "45")
    u.check(u.wait_for("45 lx", timeout=8) is not None, "Shows 45 lx after the light changes to 45")
    u.shot("row01_light_45lx")

    u.log("\n### Row 2: light sensor released when leaving the main Activity")
    u.check(light_in_use(), "Sensor service lists LakeLog's light listener while the main Activity is visible")
    open(os.path.join(u.OUT, "sensorservice_main_visible.txt"), "w").write(sensor_section())

    u.log("\n### Row 6 (part 1): observer name")
    u.type_into("observerInput", "Pat Rivera")

    u.log("\n### Row 3 + 7: Readings opens, downloads, lists time and level")
    open_readings()
    time.sleep(2)
    u.check(not light_in_use(), "Light listener released after opening Readings")
    open(os.path.join(u.OUT, "sensorservice_readings.txt"), "w").write(sensor_section())
    r = wait_rows(60)
    u.check(len(r) > 0, f"Readings listed: first rows {r[:3]}")
    u.check(all(re.match(r"^\d{3}\.\d{3} m$|^Unavailable$", lv) for _, lv, _ in r),
            "Each row shows a level in meters or 'Unavailable'")
    summ = u.find(rid="summary")
    u.log(f"      summary: {summ.get('text') if summ is not None else None}")
    u.shot("row07_readings_list")

    first_time, first_level, _ = r[0]
    second_time = r[1][0] if len(r) > 1 else None
    first_row = descs(first_time)[0]
    u.tap_node(first_row)
    u.check(u.wait_for(first_time, timeout=6) is not None, f"Note Activity shows the selected time {first_time}")
    u.check(u.find(first_level) is not None, f"Note Activity shows the level {first_level}")
    u.check(u.find("Observer: Pat Rivera") is not None, "Note Activity shows the observer's name (row 6)")
    u.back()
    if second_time:
        u.tap_node(descs(second_time)[0])
        u.check(u.wait_for(second_time, timeout=6) is not None, f"A different row opens its own reading ({second_time})")
        u.back()

    u.log("\n### Row 5: unsaved text survives rotation")
    u.tap_node(descs(first_time)[0])
    u.wait_for(first_time, timeout=6)
    u.type_into("noteInput", "seiche after the NW storm")
    u.rotate(True)
    u.check(u.field_text("noteInput") == "seiche after the NW storm" or
            (u.find_scrolling(rid="noteInput") is not None and u.field_text("noteInput") == "seiche after the NW storm"),
            "Unsaved note text kept in landscape")
    u.shot("row05_note_landscape")
    u.rotate(False)
    u.check(u.field_text("noteInput") == "seiche after the NW storm", "Unsaved note text kept after rotating back")

    u.log("\n### Row 4: Save marks the reading")
    u.tap(rid="saveButton", scroll=True)
    time.sleep(1.5)
    marked = [t for t, _, n in rows() if n]
    u.check(marked == [first_time], f"After Save the reading is marked: {marked}")
    u.shot("row04_marked")

    u.log("\n### Row 12: Back (button and system) discards; empty save deletes")
    u.tap_node(descs(first_time)[0])
    u.wait_for(first_time, timeout=6)
    u.check(u.field_text("noteInput") == "seiche after the NW storm", "Reopening shows the saved note (row 9)")
    u.type_into("noteInput", " EDITED")
    u.tap(rid="backButton", scroll=True)
    time.sleep(1)
    u.tap_node(descs(first_time)[0])
    u.wait_for(first_time, timeout=6)
    u.check(u.field_text("noteInput") == "seiche after the NW storm", "Back button discarded the edit")
    u.type_into("noteInput", " EDITED")
    u.back()  # system Back
    time.sleep(1)
    u.tap_node(descs(first_time)[0])
    u.wait_for(first_time, timeout=6)
    u.check(u.field_text("noteInput") == "seiche after the NW storm", "System Back discarded the edit")
    u.back()

    u.log("\n### Row 10: notes survive a refresh")
    u.tap(rid="refreshButton")
    time.sleep(2)
    wait_rows(60)
    time.sleep(1)
    found = find_desc_scrolling(first_time + ",")
    u.check(found is not None and "has a note" in found.get("content-desc", ""),
            f"After refresh the note is still marked: {found.get('content-desc') if found is not None else None}")
    for _ in range(4):
        u.swipe_down()

    u.log("\n### Row 9 + 6: notes and name survive force-stop and relaunch")
    u.restart_app(clear_log=False)
    u.check(u.wait_for("Pat Rivera", timeout=6) is not None, "Observer name restored after relaunch")
    open_readings()
    wait_rows(60)
    time.sleep(1)
    found = find_desc_scrolling(first_time + ",")
    u.check(found is not None and "has a note" in found.get("content-desc", ""), "Note still marked after relaunch")
    u.tap_node(found)
    u.wait_for(first_time, timeout=6)
    u.check(u.field_text("noteInput") == "seiche after the NW storm", "Note text restored after relaunch")

    u.log("\n### Row 12 (part 2): saving a blank note deletes it")
    n = u.find(rid="noteInput")
    u.tap_node(n)
    u.sh("input keyevent KEYCODE_MOVE_END")
    for _ in range(40):
        u.sh("input keyevent KEYCODE_DEL")
    u.type_into("noteInput", "   ")
    u.tap(rid="saveButton", scroll=True)
    time.sleep(1.5)
    u.check(not descs("has a note"), "Blank save removed the mark")
    u.back()

    u.log("\n### Row 2 (part 2): sensor released in the background")
    u.check(light_in_use(), "Light listener active again on the main Activity")
    u.sh("input keyevent KEYCODE_HOME")
    time.sleep(2)
    u.check(not light_in_use(), "Light listener released after pressing Home")
    open(os.path.join(u.OUT, "sensorservice_home.txt"), "w").write(sensor_section())

    u.log("\n### Row 7: slow network stays responsive, leaving mid-download doesn't crash")
    u.slow_network(True)
    u.restart_app()
    open_readings()
    time.sleep(1.5)
    u.check(u.find("Downloading", contains=True) is not None, "Shows 'Downloading…' on the slow network")
    u.shot("row07_downloading_slow")
    t0 = time.time()
    u.back()
    u.check(u.wait_for(rid="readingsButton", timeout=4) is not None,
            f"Back during the download returns at once ({time.time() - t0:.1f}s)")
    time.sleep(3)
    bad, out = u.crashed()
    u.check(not bad, "No crash after leaving mid-download")
    open_readings()
    r = wait_rows(150)
    u.check(len(r) > 0, "List finishes downloading on the slow network")
    u.slow_network(False)

    u.log("\n### Row 8: no network reports failure; Retry recovers")
    u.back()
    u.sh("cmd connectivity airplane-mode enable")
    time.sleep(3)
    open_readings()
    u.check(u.wait_for("Download failed", contains=True, timeout=40) is not None, "Failure reported with no network")
    u.check(u.find(rid="retryButton") is not None, "Retry button shown")
    u.shot("row08_failed")
    u.sh("cmd connectivity airplane-mode disable")
    time.sleep(8)
    u.tap(rid="retryButton")
    u.check(len(wait_rows(60)) > 0, "Retry downloads and lists readings after the network returns")

    u.log("\n### Row 11: scroll to the end")
    seen_last = None
    for i in range(80):
        u.adb("shell", "input", "swipe", "540", "1700", "540", "500", "120")
        r = rows()
        if r and r[-1][0] == seen_last:
            break
        seen_last = r[-1][0] if r else None
    u.check(seen_last is not None, f"Scrolled to the end; oldest visible reading {seen_last}")
    u.shot("row11_list_end")

    bad, out = u.crashed()
    u.check(not bad, "No crash in logcat during the functional run")
    if bad:
        u.log("```\n" + out[-3000:] + "\n```")

    u.log("\n### Row 11: every screen at font scale 2.0, phone and tablet, both orientations")
    u.sh("settings put system font_scale 2.0")
    for label, disp in [("phone", PHONE), ("tablet", TABLET)]:
        u.set_display(*disp)
        for land in (False, True):
            tag = f"font2_{label}_{'land' if land else 'port'}"
            u.restart_app(clear_log=False)
            u.rotate(land)
            time.sleep(1)
            u.shot(f"{tag}_1_main")
            ok = all(u.find_scrolling(rid=r_) is not None for r_ in ["lightValue", "observerInput", "readingsButton"])
            u.check(ok, f"{tag}: main controls all reachable")
            u.swipe_down(); u.swipe_down()
            open_readings()
            wait_rows(60)
            u.shot(f"{tag}_2_readings")
            u.check(u.find(rid="refreshButton") is not None and len(rows()) > 0,
                    f"{tag}: Readings list and Refresh visible ({len(rows())} rows on screen)")
            u.tap_node(descs("UTC")[0])
            u.wait_for(rid="noteInput", timeout=6)
            u.shot(f"{tag}_3_note")
            ok = all(u.find_scrolling(rid=r_) is not None for r_ in ["noteTime", "noteObserver", "noteInput", "saveButton", "backButton"])
            u.check(ok, f"{tag}: note controls all reachable")
            u.back()
            u.back()
    u.sh("settings put system font_scale 1.0")
    u.sh("wm size reset")
    u.sh("wm density reset")

    bad, out = u.crashed()
    u.check(not bad, "No crash in logcat")
    u.log(f"\n**{len(u.failures)} failure(s)**")
    with open(os.path.join(u.OUT, "report.md"), "w") as f:
        f.write("\n".join(u.report) + "\n")


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        u.check(False, f"aborted: {e!r}")
        u.shot("ERROR")
        with open(os.path.join(u.OUT, "report.md"), "w") as f:
            f.write("\n".join(u.report) + "\n")
