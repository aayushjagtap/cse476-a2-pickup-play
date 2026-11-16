#!/usr/bin/env python3
"""
CI-only: collects the same runtime evidence from a LakeKit build. Run once on the original
(plus measurement logging) and once on the repaired build. argv[1] = output dir, argv[2] = label.
"""
import os
import re
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ui as u  # noqa: E402

PKG = "edu.msu.cse476.lakekit"
OUT = sys.argv[1]
LABEL = sys.argv[2]
u.configure(PKG, OUT)
PHONE = (1080, 2340, 440)


def save(name, text):
    with open(os.path.join(OUT, name), "w") as f:
        f.write(text)


def logcat(tag_filter=None):
    out = u.adb("logcat", "-d", "-v", "time").decode(errors="replace")
    if tag_filter:
        out = "\n".join(l for l in out.splitlines() if re.search(tag_filter, l))
    return out


def summary_text():
    n = u.find(rid="summary")
    return (n.get("text") or "") if n is not None else ""


def dismiss_anr():
    for label in ["Wait", "Close app"]:
        n = u.find(label)
        if n is not None and label == "Wait":
            u.tap_node(n)
            return True
    return False


def main():
    for k in ["window_animation_scale", "transition_animation_scale", "animator_duration_scale"]:
        u.sh(f"settings put global {k} 0")
    u.sh("settings put system font_scale 1.0")
    u.adb("emu", "network", "speed", "full")
    u.adb("emu", "network", "delay", "none")
    u.set_display(*PHONE)
    u.sh(f"pm clear {PKG}")
    u.restart_app()
    u.rotate(False)
    time.sleep(2)

    u.log(f"## LakeKit {LABEL}")
    save("permissions.txt", u.sh(f"dumpsys package {PKG}"))

    u.log("\n### Baseline refresh on a fast network")
    u.tap(rid="refreshButton")
    end = time.time() + 40
    while time.time() < end and "readings" not in summary_text():
        time.sleep(1)
    u.check("readings" in summary_text(), f"Fast refresh works: '{summary_text()}'")
    u.shot("1_main_after_refresh")

    u.log("\n### Issue 1 (A): Refresh on a slow network (emulator GSM speed, GPRS latency)")
    u.adb("emu", "network", "speed", "gsm")
    u.adb("emu", "network", "delay", "gprs")
    u.sh("logcat -b all -c")
    switch_before = u.find(rid="recentOnly").get("checked")
    sw = u.find(rid="recentOnly")
    u.tap(rid="refreshButton")
    t_tap = time.time()
    time.sleep(1.0)
    u.tap_node(sw)  # try to use the UI while the refresh runs
    time.sleep(1.5)
    switch_after = u.find(rid="recentOnly").get("checked")
    responded = switch_after != switch_before
    u.log(f"      'Latest day only' switch toggled within 2.5 s of tapping it: {responded}")
    u.shot("2_slow_refresh_during")
    anr_seen = False
    end = time.time() + 120
    while time.time() < end:
        if u.find("isn't responding", contains=True) is not None:
            anr_seen = True
            u.shot("2_slow_refresh_anr_dialog")
            dismiss_anr()
        if "readings" in summary_text() or "failed" in summary_text():
            break
        time.sleep(2)
    u.log(f"      ANR dialog seen: {anr_seen}")
    u.log(f"      refresh finished after ~{time.time() - t_tap:.0f} s: '{summary_text()}'")
    measure = logcat("LakeKitMeasure.*click handler")
    anr = logcat(r"ANR in|Input dispatching timed out|Skipped \d+ frames")
    u.log("      logcat:\n```\n" + (measure + "\n" + anr).strip()[-2500:] + "\n```")
    save("issue1_logcat.txt", measure + "\n" + anr)
    u.adb("emu", "network", "speed", "full")
    u.adb("emu", "network", "delay", "none")

    u.log("\n### Issue 1 (A): leave mid-download (rotate and Back) on the slow network")
    u.adb("emu", "network", "speed", "gsm")
    u.adb("emu", "network", "delay", "gprs")
    u.sh("logcat -b all -c")
    u.tap(rid="refreshButton")
    time.sleep(1)
    u.rotate(True)
    time.sleep(2)
    u.rotate(False)
    time.sleep(2)
    if u.find("isn't responding", contains=True) is not None:
        dismiss_anr()
    u.back()
    time.sleep(3)
    bad, out = u.crashed()
    u.check(not bad, "No crash after rotating and leaving during a refresh")
    u.adb("emu", "network", "speed", "full")
    u.adb("emu", "network", "delay", "none")
    u.restart_app(clear_log=False)
    time.sleep(2)

    u.log("\n### Issue 3 (C): accessibility labels on icon buttons")
    root = u.dump()
    for rid in ["surveyButton", "aboutButton"]:
        n = u.find(rid=rid, root=root)
        u.log(f"      {rid}: content-desc='{n.get('content-desc') if n is not None else None}'")
    u.shot("3_main_icons")

    u.log("\n### Issue 2 (B) feature check: times in the list still format")
    times = [n.get("text") for n in u.dump().iter("node")
             if (n.get("resource-id") or "").endswith(":id/readingTime")]
    u.log(f"      first list times: {times[:3]}")
    u.check(all(re.match(r"^[A-Z][a-z]{2} \d{1,2}, \d\d:\d\d$", t or "") for t in times[:3]) and times,
            "Reading times formatted as 'MMM d, HH:mm'")
    ref = u.find(rid="refreshed")
    u.log(f"      refreshed line: '{ref.get('text') if ref is not None else None}'")

    u.log("\n### Issue 4 (A): Survey sweep for 10 s")
    u.sh("logcat -b all -c")
    u.tap(rid="surveyButton")
    time.sleep(1)
    u.sh(f"dumpsys gfxinfo {PKG} reset")
    time.sleep(10)
    gfx = u.sh(f"dumpsys gfxinfo {PKG}")
    save("issue4_gfxinfo.txt", gfx)
    sweep = logcat("LakeKitMeasure.*SweepView")
    gc = logcat(r"lakekit.*(GC freed|concurrent copying)|Background.*GC")
    u.log("      SweepView decode log (last lines):\n```\n" + "\n".join(sweep.splitlines()[-3:]) + "\n```")
    u.log(f"      GC log lines in 10 s: {len(gc.splitlines()) if gc.strip() else 0}")
    for key in ["Total frames rendered", "Janky frames"]:
        m = re.search(key + r":\s*([^\n]+)", gfx)
        u.log(f"      {key}: {m.group(1) if m else 'n/a'}")
    save("issue4_logcat.txt", sweep + "\n" + gc)
    u.shot("4_survey")
    u.log("\n### Export still works")
    u.tap(rid="exportButton")
    time.sleep(1.5)
    u.log(f"      export file: {u.sh(f'run-as {PKG} ls -l files').strip()}")
    root = u.dump()
    n = u.find(rid="exportButton", root=root)
    u.log(f"      exportButton content-desc='{n.get('content-desc') if n is not None else None}'")
    u.back()
    u.tap(rid="aboutButton")
    time.sleep(2)
    u.shot("5_about")
    u.back()

    bad, out = u.crashed()
    u.check(not bad, "No crash in logcat")
    if bad:
        u.log("```\n" + out[-3000:] + "\n```")
    u.sh("wm size reset")
    u.sh("wm density reset")
    with open(os.path.join(OUT, "report.md"), "w") as f:
        f.write("\n".join(u.report) + "\n")


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        u.check(False, f"aborted: {e!r}")
        u.shot("ERROR")
        with open(os.path.join(OUT, "report.md"), "w") as f:
            f.write("\n".join(u.report) + "\n")
