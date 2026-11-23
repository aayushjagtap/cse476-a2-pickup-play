#!/usr/bin/env python3
"""
A4 Phase 2 LIVE check against the REAL Firebase project in app/google-services.json, driving the
normal debug APK (app-debug.apk, which always uses the real backend over HTTPS).

Credentials for the dedicated grading account are read from a local file OUTSIDE the repository
(path in the PICKUP_GRADER_FILE environment variable; line 1 = email, line 2 = password). They
are only held in memory: never printed, logged, or written to the report.

Documents created by this run are titled "Live check <hex>" and stay in Firestore (the rules
forbid deletes); remove them in the Firebase console if you want a clean collection.

Usage: PICKUP_GRADER_FILE=/path/grader.local python ci/a4_live_check.py <out_dir>
"""
import json
import os
import secrets
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ui_check as u  # noqa: E402
import a4_cloud_check as c  # noqa: E402  (UI helpers; its emulator functions are not used)

PKG = u.PKG
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

with open(os.path.join(ROOT, "app", "google-services.json")) as f:
    _cfg = json.load(f)
PROJECT = _cfg["project_info"]["project_id"]
_API_KEY = _cfg["client"][0]["api_key"][0]["current_key"]
FS = f"https://firestore.googleapis.com/v1/projects/{PROJECT}/databases/(default)/documents"
IDT = "https://identitytoolkit.googleapis.com/v1"

with open(os.environ["PICKUP_GRADER_FILE"], encoding="utf-8") as f:
    _lines = [line.strip() for line in f.read().splitlines() if line.strip()]
EMAIL, PASSWORD = _lines[0], _lines[1]


def rest_sign_in(password):
    return c.http("POST", f"{IDT}/accounts:signInWithPassword?key={_API_KEY}",
                  {"email": EMAIL, "password": password, "returnSecureToken": True})


def server_docs_as_grader():
    """Reads the collection from the REAL server as the grading user (independent of the app)."""
    status, body = rest_sign_in(PASSWORD)
    assert status == 200, f"REST sign-in failed: {status}"
    token, uid = body["idToken"], body["localId"]
    docs, page = [], ""
    while True:
        s, b = c.http("GET", f"{FS}/sessions?pageSize=300{page}", headers={"Authorization": f"Bearer {token}"})
        assert s == 200, f"authenticated REST read failed: {s}"
        for d in b.get("documents", []):
            fl = d["fields"]
            docs.append({"id": d["name"].rsplit("/", 1)[1], "title": fl["title"]["stringValue"],
                         "ownerUid": fl["ownerUid"]["stringValue"]})
        if not b.get("nextPageToken"):
            return uid, docs
        page = "&pageToken=" + b["nextPageToken"]


def list_count(title):
    return sum(1 for n in u.nodes(u.dump()) if n.get("text") == title)


def main():
    u.sh("settings put global window_animation_scale 0")
    u.sh("settings put global transition_animation_scale 0")
    u.sh("settings put global animator_duration_scale 0")
    u.rotate(False)
    c.set_network(True)
    u.log(f"### LIVE project: {PROJECT} (real Firebase, HTTPS)")

    u.log("\n### Unauthenticated access is denied by the deployed rules")
    s, b = c.http("GET", f"{FS}/sessions")
    u.check(s == 403, f"REST read of /sessions with NO token -> HTTP {s} {b.get('error', {}).get('status', '')}")
    s, b = c.http("GET", f"{FS}/sessions?pageSize=1", headers={"Authorization": "Bearer not-a-real-token"})
    u.check(s in (401, 403), f"REST read with an INVALID token -> HTTP {s} {b.get('error', {}).get('status', '')}")
    s, b = rest_sign_in(PASSWORD + "-wrong")
    u.check(s == 400, f"Auth REST sign-in with a wrong password rejected: {b.get('error', {}).get('message')}")
    u.fresh_start()
    c.open_account()
    u.check(u.find(rid="sign_in_button") is not None, "App starts signed out; sign-in form shown")
    u.check(u.find("LOCAL Firebase emulator", contains=True) is None, "No emulator banner: real backend build")
    u.tap("Test: read server data without signing in", scroll=True)
    u.check(c.wait_status("DENIED", 30), f"In-app signed-out server read: '{c.status_text()}'")
    u.shot("live_1_probe_denied")

    u.log("\n### Invalid credentials are rejected (real Firebase Auth)")
    c.sign_in(EMAIL, PASSWORD + "-wrong")
    u.check(u.wait_for("Wrong email or password", timeout=25) is not None, "Wrong password -> 'Wrong email or password'")
    u.check(u.find(rid="sign_in_button") is not None, "Still signed out")
    u.shot("live_2_bad_password")

    u.log("\n### Sign in with the grading account and upload a session")
    c.sign_in(EMAIL, PASSWORD)
    u.check(u.wait_for("Signed in as", contains=True, timeout=30) is not None, "Signed in with the grading account")
    u.check(c.wait_status("server read OK", 45), f"Sign-in sync, Source.SERVER read: '{c.status_text()}'")
    u.shot("live_3_signed_in")
    title = f"Live check {secrets.token_hex(3)}"
    c.open_sport("Tennis")
    u.check(c.create_session(title), f"Created Tennis session '{title}'")
    u.check(c.wait_card(title, "Synced to cloud", timeout=45), "Card shows 'Synced to cloud'")
    u.shot("live_4_synced")
    uid, docs = server_docs_as_grader()
    mine = [d for d in docs if d["title"] == title]
    u.check(len(mine) == 1, f"Real Firestore has exactly one document for it (found {len(mine)})")
    u.check(bool(mine) and mine[0]["ownerUid"] == uid, "Document ownerUid == grading account UID")
    if mine:
        u.log(f"      Firestore document: sessions/{mine[0]['id']}")

    u.log("\n### Close and reopen: sign-in persists, startup Source.SERVER read")
    c.cold_restart()
    c.open_account()
    u.check(u.find("Signed in as", contains=True) is not None, "Still signed in after a full restart")
    u.check(c.wait_status("server read OK", 45), f"Startup server read: '{c.status_text()}'")
    u.tap("Sync now (read from server)", scroll=True)
    u.check(c.wait_status("Last sync", 45) and "server read OK" in c.status_text(),
            f"Manual Source.SERVER read: '{c.status_text()}'")
    u.shot("live_5_after_restart")
    c.open_sport("Tennis")
    u.check(list_count(title) == 1, "Session listed exactly once after restart")

    u.log("\n### Clear app data, sign in again: restored from Firestore")
    u.fresh_start()
    c.open_sport("Tennis")
    u.check(u.find(title) is None, "After clearing app data the session is gone locally")
    c.open_account()
    c.sign_in(EMAIL, PASSWORD)
    u.check(c.wait_status("server read OK", 45), f"Server read after re-sign-in: '{c.status_text()}'")
    st = c.status_text()
    u.check("New on this device: 0." not in st, f"Sessions were added from the server: '{st}'")
    u.shot("live_6_restored_status")
    c.open_sport("Tennis")
    u.check(c.wait_card(title, "Synced to cloud", timeout=15), "Session restored from Firestore and listed")
    u.shot("live_7_restored_list")

    u.log("\n### No duplicates (local + remote)")
    c.cold_restart()
    time.sleep(6)
    c.open_account()
    u.tap("Sync now (read from server)", scroll=True)
    u.check(c.wait_status("New on this device: 0.", 45), f"Repeat sync adds nothing: '{c.status_text()}'")
    c.open_sport("Tennis")
    u.check(list_count(title) == 1, "Still exactly one card for the session")
    _, docs = server_docs_as_grader()
    u.check(sum(d["title"] == title for d in docs) == 1, "Still exactly one Firestore document")

    u.log("\n### A. Offline launch against the real backend")
    c.set_network(False)
    c.cold_restart()
    u.check(u.wait_for("What do you want to play?", timeout=10) is not None, "App launches offline")
    c.open_sport("Tennis")
    u.check(u.find(title) is not None, "Synced session still available offline (Room)")
    off = f"Live offline {secrets.token_hex(2)}"
    u.check(c.create_session(off), "Created a session offline")
    u.check(c.wait_card(off, "Not synced", timeout=15), "Marked 'Not synced' offline")
    c.open_account()
    u.check(c.wait_status("No internet connection", 15), f"Offline reported: '{c.status_text()}'")
    c.set_network(True)
    u.tap("Sync now (read from server)", scroll=True)
    u.check(c.wait_status("Uploaded 1", 60), f"Back online, uploaded: '{c.status_text()}'")
    _, docs = server_docs_as_grader()
    u.check(sum(d["title"] == off for d in docs) == 1, "Offline session now in real Firestore, once")

    u.log("\n### B. Backend unreachable (network up, Firebase hosts do not resolve)")
    old_mode = u.sh("settings get global private_dns_mode").strip()
    old_spec = u.sh("settings get global private_dns_specifier").strip()
    try:
        u.sh("settings put global private_dns_mode hostname")
        u.sh("settings put global private_dns_specifier unreachable.invalid")
        time.sleep(5)
        down = f"Live unreachable {secrets.token_hex(2)}"
        c.open_sport("Tennis")
        u.check(c.create_session(down), "Created a session while the backend is unreachable")
        u.check(c.wait_card(down, "Not synced", timeout=60), "Marked 'Not synced' (no false success)")
        c.open_account()
        u.check(c.wait_status("FAILED", 60), f"Failure reported: '{c.status_text()}'")
        u.tap("Sync now (read from server)", scroll=True)
        u.check(c.wait_status("Last sync", 75) and "FAILED" in c.status_text(),
                f"Manual sync fails cleanly: '{c.status_text()}'")
        u.check(u.find(rid="cloud_progress") is None, "No spinner left running")
        u.shot("live_8_unreachable")
    finally:
        u.sh(f"settings put global private_dns_mode {old_mode if old_mode != 'null' else 'opportunistic'}")
        if old_spec and old_spec != "null":
            u.sh(f"settings put global private_dns_specifier {old_spec}")
        else:
            u.sh("settings delete global private_dns_specifier")
        time.sleep(5)
    u.tap("Sync now (read from server)", scroll=True)
    u.check(c.wait_status("Uploaded 1", 75), f"Backend reachable again, upload completes: '{c.status_text()}'")

    bad, out = u.crashed()
    u.check(not bad, "No crash in logcat")
    if bad:
        u.log("```\n" + out[-4000:] + "\n```")


def finish(code):
    c.set_network(True)
    u.log(f"\n**{len(u.failures)} failure(s)**")
    with open(os.path.join(u.OUT, "report.md"), "w") as f:
        f.write("\n".join(u.report) + "\n")
    sys.exit(code)


if __name__ == "__main__":
    try:
        main()
        finish(1 if u.failures else 0)
    except Exception as e:
        u.check(False, f"aborted: {type(e).__name__}: {e}")
        try:
            u.shot("ERROR")
        except Exception:
            pass
        finish(1)
