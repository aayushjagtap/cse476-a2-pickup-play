#!/usr/bin/env python3
"""
A4 Phase 2 end-to-end check: the REAL Android app and REAL Firebase SDKs talking to the LOCAL
Firebase Emulator Suite (Auth + Firestore with firestore.rules). It is not the production
project: run the manual steps in docs/A4_CLOUD.md for that.

Prerequisites:
  * an Android emulator (adb on PATH) with the separate emulator-build-type APK installed:
        ./gradlew assembleEmulator
        adb install -r app/build/outputs/apk/emulator/app-emulator.apk
    (the normal debug/release APKs always use the real Firebase project and cannot be used here);
  * Java 21 for the Firestore emulator and firebase-tools in firebase-tests/node_modules
    (cd firebase-tests && npm ci).
This script starts and stops the Firebase emulators itself. The test account is created in the
Auth emulator at runtime with a random password that is never printed.

Usage: python ci/a4_cloud_check.py <out_dir>
"""
import json
import os
import secrets
import subprocess
import sys
import time
import urllib.error
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ui_check as u  # noqa: E402

PKG = u.PKG
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROJECT = "demo-pickupplay"
FS = f"http://127.0.0.1:8080/v1/projects/{PROJECT}/databases/(default)/documents"
AUTH = "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1"

emulators = None


# ---------------------------------------------------------------- Firebase emulator control

def http(method, url, body=None, headers=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers={
        "Content-Type": "application/json", **(headers or {})})
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.status, json.loads(r.read() or b"{}")
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read() or b"{}")


def start_emulators():
    global emulators
    tools = os.path.join(ROOT, "firebase-tests")
    cmd = ["npx.cmd" if os.name == "nt" else "npx", "firebase", "emulators:start",
           "--config", os.path.join(ROOT, "firebase.json"), "--only", "auth,firestore",
           "--project", PROJECT]
    flags = subprocess.CREATE_NEW_PROCESS_GROUP if os.name == "nt" else 0
    emulators = subprocess.Popen(cmd, cwd=tools, stdout=subprocess.DEVNULL,
                                 stderr=subprocess.DEVNULL, creationflags=flags)
    for _ in range(90):
        try:
            urllib.request.urlopen("http://127.0.0.1:8080/", timeout=2)
            urllib.request.urlopen("http://127.0.0.1:9099/", timeout=2)
            return
        except Exception:
            time.sleep(1)
    raise RuntimeError("Firebase emulators did not start")


def stop_emulators():
    """Makes the backend unreachable (the 'server down' scenario)."""
    global emulators
    if emulators is None:
        return
    if os.name == "nt":
        subprocess.run(["taskkill", "/PID", str(emulators.pid), "/T", "/F"], capture_output=True)
    else:
        emulators.terminate()
    emulators.wait(timeout=30)
    emulators = None
    time.sleep(2)


def create_test_account():
    email = f"grader-e2e-{secrets.token_hex(3)}@example.com"
    password = secrets.token_urlsafe(12).replace("-", "a").replace("_", "b")
    status, body = http("POST", f"{AUTH}/accounts:signUp?key=emulator",
                        {"email": email, "password": password, "returnSecureToken": True})
    assert status == 200, f"emulator sign-up failed: {status}"
    return email, password, body["localId"]


def server_sessions_admin():
    """All session documents, read with the emulator's admin bypass (verification only)."""
    status, body = http("GET", f"{FS}/sessions?pageSize=300", headers={"Authorization": "Bearer owner"})
    assert status == 200, status
    out = []
    for d in body.get("documents", []):
        f = d["fields"]
        out.append({"id": d["name"].rsplit("/", 1)[1], "title": f["title"]["stringValue"],
                    "ownerUid": f["ownerUid"]["stringValue"]})
    return out


# ---------------------------------------------------------------- UI helpers

def find_desc(desc):
    for n in u.nodes(u.dump()):
        if (n.get("content-desc") or "") == desc:
            return n
    return None


def open_account():
    for _ in range(4):
        if u.find("Cloud sync") is not None:
            return
        n = find_desc("Account and cloud sync")
        if n is not None:
            u.tap_node(n)
            if u.wait_for("Cloud sync", timeout=6) is not None:
                return
        u.back()
    raise RuntimeError("could not open the Account screen")


def to_picker():
    for _ in range(5):
        if u.find("What do you want to play?") is not None:
            return
        u.back()


def clear_field(rid):
    u.tap(rid=rid, scroll=True)
    u.adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    u.adb("shell", "input", "keyevent", *(["KEYCODE_DEL"] * 60))


def status_text():
    n = u.find(rid="cloud_status")
    return (n.get("text") or "") if n is not None else ""


def wait_status(fragment, timeout=40):
    end = time.time() + timeout
    while time.time() < end:
        if fragment in status_text():
            return True
        time.sleep(1)
    return False


def sign_in(email, password):
    clear_field("email_input")
    u.adb("shell", "input", "text", email)
    clear_field("password_input")
    u.adb("shell", "input", "text", password)  # never logged
    u.hide_keyboard()
    u.tap("Sign in", scroll=True)


def wait_card(title, fragment, timeout=40):
    """Waits until the session card for `title` shows sync text containing `fragment`."""
    end = time.time() + timeout
    while time.time() < end:
        root = u.dump()
        cards = list(root.iter("node"))
        for i, n in enumerate(cards):
            if n.get("text") == title:
                # The sync line is a later sibling in the same card.
                for m in cards[i:i + 12]:
                    if (m.get("resource-id") or "").endswith(":id/session_sync") and fragment in (m.get("text") or ""):
                        return True
        time.sleep(1)
    return False


def create_session(title, sport="Tennis"):
    u.tap("Create session")
    u.type_into("title_input", title)
    u.type_into("location_input", "Old College Field")
    u.hide_keyboard()
    u.tap(sport, scroll=True)
    u.tap_node(u.find_scrolling(rid="submit_button"))
    return u.wait_for(f"{sport} sessions", contains=True, timeout=8) is not None


def open_sport(name):
    to_picker()
    u.tap(name)
    return u.wait_for(f"{name} sessions", contains=True) is not None


def cold_restart():
    u.sh(f"am force-stop {PKG}")
    time.sleep(1)
    u.sh(f"am start -W -n {PKG}/.MainActivity")
    time.sleep(2)


def set_network(on):
    u.sh(f"cmd connectivity airplane-mode {'disable' if on else 'enable'}")
    time.sleep(6 if on else 3)


# ---------------------------------------------------------------- scenario

def main():
    u.sh("settings put global window_animation_scale 0")
    u.sh("settings put global transition_animation_scale 0")
    u.sh("settings put global animator_duration_scale 0")
    u.rotate(False)
    set_network(True)

    u.log("### Setup: local Firebase emulators (Auth + Firestore with firestore.rules)")
    start_emulators()
    email, password, uid = create_test_account()
    u.check(True, "Emulator test account created (random password, not printed)")

    u.log("\n### Unauthenticated access is denied")
    status, body = http("GET", f"{FS}/sessions")
    u.check(status == 403, f"REST read of /sessions with NO auth token -> HTTP {status} "
                           f"{body.get('error', {}).get('status', '')}")
    u.fresh_start()
    open_account()
    u.check(u.find("Sign in") is not None, "Signed out: sign-in form shown, app still usable")
    u.tap("Test: read server data without signing in", scroll=True)
    u.check(wait_status("DENIED", 25), f"In-app signed-out server read denied: '{status_text()}'")
    u.shot("cloud_1_probe_denied")

    u.log("\n### Invalid credentials are rejected")
    sign_in(email, password + "x")
    u.check(u.wait_for("Wrong email or password", timeout=20) is not None, "Wrong password -> 'Wrong email or password'")
    u.check(u.find(rid="sign_in_button") is not None, "Still signed out after the failed attempt")
    u.shot("cloud_2_bad_password")

    u.log("\n### Sign in and upload a new session")
    sign_in(email, password)
    u.check(u.wait_for("Signed in as", contains=True, timeout=25) is not None, "Signed in with the emulator account")
    u.check(wait_status("server read OK"), f"Sign-in sync did a server read: '{status_text()}'")
    u.shot("cloud_3_signed_in")
    title = f"Cloud trip {secrets.token_hex(2)}"
    open_sport("Tennis")
    u.check(create_session(title), "Created a Tennis session")
    u.check(wait_card(title, "Synced to cloud"), "Card shows 'Synced to cloud' after upload")
    u.shot("cloud_4_synced_card")
    docs = [d for d in server_sessions_admin() if d["title"] == title]
    u.check(len(docs) == 1, f"Exactly one Firestore document for the session: {len(docs)}")
    u.check(bool(docs) and docs[0]["ownerUid"] == uid, "Document ownerUid is the signed-in user's UID")

    u.log("\n### Later app session: sign-in persisted, startup server read")
    cold_restart()
    open_account()
    u.check(u.find("Signed in as", contains=True) is not None, "Still signed in after a full restart")
    u.check(wait_status("server read OK"), f"Startup sync read from the server: '{status_text()}'")
    before = status_text()
    u.rotate(True)
    time.sleep(2)
    # In landscape the status line can be below the fold: scroll to it before reading.
    still_in = u.find_scrolling("Signed in as", contains=True) is not None
    node = u.find_scrolling(rid="cloud_status")
    u.check(still_in and node is not None and (node.get("text") or "") == before,
            "Account screen state survives rotation")
    u.rotate(False)

    u.log("\n### Fresh install: data comes back from the server (not from Room or cache)")
    u.fresh_start()  # wipes Room, the Firestore cache and the saved sign-in
    open_sport("Tennis")
    u.check(u.find(title) is None, "After clearing app data the session is gone locally")
    open_account()
    sign_in(email, password)
    u.check(wait_status("server read OK"), f"Server read after sign-in: '{status_text()}'")
    u.check("New on this device: 1." in status_text(), "Exactly 1 session added from the server")
    u.shot("cloud_5_server_read")
    open_sport("Tennis")
    u.check(wait_card(title, "Synced to cloud", timeout=10), "Retrieved session shown in the Tennis list")
    u.shot("cloud_6_retrieved")
    u.sh(f"am force-stop {PKG}")
    u.sh(f"am start -W -n {PKG}/.MainActivity")
    time.sleep(4)
    open_sport("Tennis")
    titles = [n.get("text") for n in u.nodes(u.dump()) if n.get("text") == title]
    u.check(len(titles) == 1, "No duplicate after another restart + sync")

    u.log("\n### A. Launch with networking disabled")
    set_network(False)
    cold_restart()
    u.check(u.wait_for("What do you want to play?", timeout=10) is not None, "App launches offline")
    open_sport("Basketball")
    u.check(u.find("Friday night runs") is not None, "Local sessions usable offline")
    offline_title = f"Offline game {secrets.token_hex(2)}"
    open_sport("Tennis")
    u.check(create_session(offline_title), "Created a session while offline")
    u.check(wait_card(offline_title, "Not synced", timeout=15), "Offline session marked 'Not synced'")
    u.shot("cloud_7_offline_not_synced")
    open_account()
    u.check(wait_status("No internet connection", 15), f"Offline reported: '{status_text()}'")
    u.check(u.find("not synced yet", contains=True) is not None, "Account shows unsynced count")
    u.shot("cloud_8_offline_account")
    set_network(True)
    u.tap("Sync now (read from server)", scroll=True)
    u.check(wait_status("server read OK", 45), f"Back online, sync succeeds: '{status_text()}'")
    u.check("Uploaded 1" in status_text(), "The offline session was uploaded")
    u.check(any(d["title"] == offline_title for d in server_sessions_admin()), "Offline session now on the server")

    u.log("\n### B. Backend unreachable during sync (network up, server down)")
    stop_emulators()
    down_title = f"Server down {secrets.token_hex(2)}"
    open_sport("Tennis")
    u.check(create_session(down_title), "Created a session while the backend is down")
    u.check(wait_card(down_title, "Not synced", timeout=40), "Card says 'Not synced' (no false success)")
    u.shot("cloud_9_server_down_card")
    open_account()
    u.check(wait_status("FAILED", 40), f"Upload failure reported: '{status_text()}'")
    u.tap("Sync now (read from server)", scroll=True)
    u.check(wait_status("Last sync", 60) and "FAILED" in status_text(), f"Manual sync fails cleanly: '{status_text()}'")
    u.check(u.find(rid="cloud_progress") is None, "No spinner left running")
    u.shot("cloud_10_server_down_account")

    bad, out = u.crashed()
    u.check(not bad, "No crash in logcat")
    if bad:
        u.log("```\n" + out[-4000:] + "\n```")


def finish(code):
    stop_emulators()
    set_network(True)
    u.log(f"\n**{len(u.failures)} failure(s)**")
    with open(os.path.join(u.OUT, "report.md"), "w") as f:
        f.write("\n".join(u.report) + "\n")
    sys.exit(code)


if __name__ == "__main__":
    try:
        main()
        finish(1 if u.failures else 0)
    except Exception as e:
        u.check(False, f"aborted: {e}")
        try:
            u.shot("ERROR")
        except Exception:
            pass
        finish(1)
