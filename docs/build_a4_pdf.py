"""
Builds docs/A4_Documentation.pdf (CSE 476 A4, Pickup Play) with ReportLab.

Test results are read from a JSON file (argv[2]) written from the actual test runs, so the
document never states a number that was not measured.

    python docs/build_a4_pdf.py docs/A4_Documentation.pdf docs/a4_results.json
"""
import json
import sys

from reportlab.graphics.shapes import Drawing, Line, Polygon, Rect, String
from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import letter
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import inch
from reportlab.platypus import (Image, KeepTogether, PageBreak, Paragraph, SimpleDocTemplate,
                                Spacer, Table, TableStyle)

OUT = sys.argv[1]
RESULTS = json.load(open(sys.argv[2], encoding="utf-8"))

GREEN = colors.HexColor("#1E6B4E")
LIGHT = colors.HexColor("#E8F3EE")
GREY = colors.HexColor("#5F6B66")
CLOUD = colors.HexColor("#E7EEF8")
CLOUD_EDGE = colors.HexColor("#3B6BA8")

ss = getSampleStyleSheet()
H1 = ParagraphStyle("H1", parent=ss["Heading1"], textColor=GREEN, fontSize=16, spaceBefore=6, spaceAfter=8)
H2 = ParagraphStyle("H2", parent=ss["Heading2"], textColor=GREEN, fontSize=12.5, spaceBefore=10, spaceAfter=4)
BODY = ParagraphStyle("Body", parent=ss["BodyText"], fontSize=9.6, leading=13.2, spaceAfter=5)
SMALL = ParagraphStyle("Small", parent=BODY, fontSize=8.4, leading=11)
CELL = ParagraphStyle("Cell", parent=BODY, fontSize=8.3, leading=10.4, spaceAfter=0)
CELLB = ParagraphStyle("CellB", parent=CELL, fontName="Helvetica-Bold")
CODE = ParagraphStyle("Code", parent=CELL, fontName="Courier", fontSize=7.9, leading=10)
BUL = ParagraphStyle("Bul", parent=BODY, leftIndent=12, bulletIndent=2, spaceAfter=2)


def p(text, style=BODY):
    return Paragraph(text, style)


def bullets(items):
    return [Paragraph(t, BUL, bulletText="•") for t in items]


def table(rows, widths, header=True, code_cols=()):
    data = []
    for r, row in enumerate(rows):
        cells = []
        for c, v in enumerate(row):
            style = CELLB if (header and r == 0) else (CODE if c in code_cols else CELL)
            cells.append(Paragraph(str(v), style))
        data.append(cells)
    t = Table(data, colWidths=widths, repeatRows=1 if header else 0)
    cmds = [
        ("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#C9D3CE")),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 4), ("RIGHTPADDING", (0, 0), (-1, -1), 4),
        ("TOPPADDING", (0, 0), (-1, -1), 3), ("BOTTOMPADDING", (0, 0), (-1, -1), 3),
    ]
    if header:
        cmds.append(("BACKGROUND", (0, 0), (-1, 0), LIGHT))
    t.setStyle(TableStyle(cmds))
    return t


# ------------------------------------------------------------------ architecture diagram

def box(d, x, y, w, h, title, lines=(), fill=colors.white, edge=GREEN):
    d.add(Rect(x, y, w, h, rx=5, ry=5, fillColor=fill, strokeColor=edge, strokeWidth=1))
    d.add(String(x + w / 2, y + h - 12, title, fontName="Helvetica-Bold", fontSize=8.2,
                 textAnchor="middle", fillColor=colors.black))
    for i, ln in enumerate(lines):
        d.add(String(x + w / 2, y + h - 24 - i * 9.5, ln, fontName="Helvetica", fontSize=6.9,
                     textAnchor="middle", fillColor=GREY))


def arrow(d, x1, y1, x2, y2, label=None, color=GREEN, dx=4):
    d.add(Line(x1, y1, x2, y2, strokeColor=color, strokeWidth=1))
    import math
    a = math.atan2(y2 - y1, x2 - x1)
    s = 5
    d.add(Polygon([x2, y2, x2 - s * math.cos(a - 0.4), y2 - s * math.sin(a - 0.4),
                   x2 - s * math.cos(a + 0.4), y2 - s * math.sin(a + 0.4)],
                  fillColor=color, strokeColor=color))
    if label:
        d.add(String((x1 + x2) / 2 + dx, (y1 + y2) / 2, label, fontName="Helvetica-Oblique",
                     fontSize=6.6, fillColor=GREY))


def architecture():
    W, H = 7.0 * inch, 6.0 * inch
    d = Drawing(W, H)
    # UI
    ui = [("SportPicker", "choose sport"), ("SessionList", "list, Nearest first"),
          ("SessionDetail", "join / leave"), ("CreateSession", "host a game"), ("Account", "sign in / sync")]
    for i, (t, sub) in enumerate(ui):
        box(d, 6 + i * 99, 384, 92, 42, t, ["Fragment", sub])
    # VMs
    box(d, 6, 306, 190, 42, "SessionsViewModel", ["sessions, join/leave, location (A3)"])
    box(d, 206, 306, 140, 42, "CreateSessionViewModel", ["form state, async save"])
    box(d, 356, 306, 140, 42, "AccountViewModel", ["sign-in + sync status"])
    # Data
    box(d, 6, 222, 190, 46, "SessionRepository", ["Flow<List<PickupSession>>", "create, toggleJoin"])
    box(d, 206, 222, 290, 46, "CloudSyncManager (process-wide)", ["state + messages, Mutex, time limits",
                                                                  "SessionSync: upload unsynced, Source.SERVER pull, merge"])
    # Storage / SDK
    box(d, 6, 128, 190, 52, "Room (SQLite) - source of truth", ["AppDatabase v2, SessionDao",
                                                               "table sessions, unique remote_id"])
    box(d, 206, 128, 140, 52, "FirebaseAuthGateway", ["AuthGateway interface", "FirebaseAuth (persisted)"])
    box(d, 356, 128, 140, 52, "FirestoreSessionSource", ["SessionRemoteSource", "set(), get(Source.SERVER)"])
    # Cloud
    box(d, 206, 28, 140, 50, "Firebase Authentication", ["email / password", "grading account"],
        fill=CLOUD, edge=CLOUD_EDGE)
    box(d, 356, 28, 140, 50, "Cloud Firestore", ["collection sessions", "firestore.rules enforced"],
        fill=CLOUD, edge=CLOUD_EDGE)
    # Arrows UI -> VM
    arrow(d, 52, 384, 52, 348); arrow(d, 151, 384, 120, 348); arrow(d, 250, 384, 150, 348)
    arrow(d, 349, 384, 290, 348); arrow(d, 448, 384, 426, 348)
    # VM -> data
    arrow(d, 100, 306, 100, 268)
    arrow(d, 276, 306, 120, 268, "save")
    arrow(d, 300, 306, 330, 268, "after save")
    arrow(d, 426, 306, 426, 268)
    # data -> storage
    arrow(d, 100, 222, 100, 180, "suspend DAO / Flow")
    arrow(d, 240, 222, 160, 180, "merge", dx=-24)
    arrow(d, 300, 222, 276, 180)
    arrow(d, 440, 222, 426, 180)
    # SDK -> cloud
    arrow(d, 276, 128, 276, 78, "HTTPS / TLS", color=CLOUD_EDGE)
    arrow(d, 426, 128, 426, 78, "HTTPS / TLS", color=CLOUD_EDGE)
    return d


def evidence_figures(paths):
    cells = [Image(pth, width=2.05 * inch, height=2.05 * inch * 2400 / 1080) for pth in paths]
    t = Table([cells], colWidths=[2.6 * inch] * len(cells))
    t.setStyle(TableStyle([("ALIGN", (0, 0), (-1, -1), "CENTER"),
                           ("BOX", (0, 0), (-1, -1), 0, colors.white)]))
    return t


# ------------------------------------------------------------------ document

def on_page(c, doc):
    c.saveState()
    c.setFont("Helvetica", 7.5)
    c.setFillColor(GREY)
    c.drawString(0.75 * inch, 0.5 * inch, "CSE 476 A4 – Pickup Play: local persistence and authenticated cloud sync")
    c.drawRightString(7.75 * inch, 0.5 * inch, f"Page {doc.page}")
    c.restoreState()


def build():
    R = RESULTS
    story = []

    # Title page
    story += [Spacer(1, 1.3 * inch),
              p("Pickup Play", ParagraphStyle("T", parent=ss["Title"], fontSize=30, leading=36, spaceAfter=10, textColor=GREEN)),
              p("CSE 476 – Assignment A4", ParagraphStyle("S", parent=ss["Title"], fontSize=16, textColor=colors.black)),
              p("Local persistence (Room) and an authenticated, secure remote round trip (Firebase)",
                ParagraphStyle("S2", parent=BODY, alignment=TA_CENTER, fontSize=11, textColor=GREY)),
              Spacer(1, 0.45 * inch),
              p("<b>Team</b>", ParagraphStyle("TH", parent=BODY, alignment=TA_CENTER, fontSize=11.5)),
              ]
    for name in R["team"]:
        story.append(p(name, ParagraphStyle("TN", parent=BODY, alignment=TA_CENTER, fontSize=11)))
    story += [Spacer(1, 0.35 * inch),
              table([["Item", "Value"],
                      ["Repository / branch", R["repo"] + " – branch <b>A4</b>"],
                      ["Firebase project", R["firebase_project"]],
                      ["Android", "Kotlin, XML layouts + View Binding, Jetpack Navigation, ViewModel/LiveData, "
                                  "coroutines; minSdk 29, target/compile SDK 36"],
                      ["Persistence", "Room 2.7.2 (SQLite), database version 2"],
                      ["Cloud", "Firebase BoM 34.12.0: firebase-auth + firebase-firestore (main modules)"],
                      ["Graded APK", R["apk_name"] + " (real Firebase backend, HTTPS only)"],
                      ["Date", R["date"]]],
                     [1.6 * inch, 4.6 * inch]),
              PageBreak()]

    # 1 Overview
    story += [p("1. What the app does", H1),
              p("Pickup Play helps students find and host pickup games around campus. A2 built the four-screen "
                "flow (choose a sport, browse sessions, view details and join/leave, host a new session) with "
                "responsive portrait/landscape/tablet layouts. A3 added <i>Nearest first</i>: live location with "
                "runtime permission handling and a jump filter that ignores implausible GPS jumps. A4 adds:"),
              *bullets([
                  "<b>Meaningful local persistence (Phase 1)</b>: sessions, created games and join/leave choices are "
                  "stored in Room and survive the app being closed; the sample sessions are seeded exactly once.",
                  "<b>Authenticated remote round trip (Phase 2)</b>: email/password sign-in with Firebase "
                  "Authentication; sessions the user creates are uploaded to Cloud Firestore under the user's UID "
                  "and retrieved again in a later app session with a server-only read (<font face='Courier'>Source.SERVER</font>).",
                  "<b>Security</b>: Firestore security rules deny all unauthenticated access, prevent impersonation "
                  "and validate every field; all traffic is HTTPS.",
                  "<b>Offline and failure handling</b>: the app stays fully usable from Room without a network; "
                  "every cloud failure is reported and nothing is ever shown as synced unless the server confirmed it.",
              ]),
              PageBreak(), p("2. Architecture", H1),
              p("Layered MVVM. Fragments observe ViewModels; ViewModels call a repository (Room) and a process-wide "
                "<font face='Courier'>CloudSyncManager</font>. Room is the single source of truth for every screen; "
                "the cloud is a synchronisation target, so the UI never waits on the network."),
              architecture(),
              p("Figure 1. Components and data flow. Rows from top: UI fragments (XML + View Binding), ViewModels "
                "(LiveData), data layer, storage and SDK adapters, Firebase services. Green: on the device; blue: "
                "Firebase in Google's cloud, reached over HTTPS.", SMALL),
              ]

    story += [p("Key design points", H2), *bullets([
        "All database work uses Room <font face='Courier'>suspend</font> DAO functions and a "
        "<font face='Courier'>Flow</font> that Room re-runs off the main thread after each write; all Firebase "
        "calls are coroutines on the SDK's Task API (<font face='Courier'>await()</font>). Nothing blocks the main thread.",
        "<font face='Courier'>CloudSyncManager</font> outlives screens, so an upload started on the create form "
        "finishes after the form closes; a <font face='Courier'>Mutex</font> serialises syncs.",
        "Every cloud operation has a time limit (upload 15 s, server read 20 s, sign-in 20 s), so progress "
        "indicators always end. Each Firebase exception maps to a readable <font face='Courier'>CloudError</font>.",
        "Firebase sits behind two small interfaces (<font face='Courier'>AuthGateway</font>, "
        "<font face='Courier'>SessionRemoteSource</font>) so the sync logic is unit-tested with fakes.",
    ])]

    # 3 Data models
    story += [p("3. Data models", H1),
              p("3.1 Room table <font face='Courier'>sessions</font> (database version 2)", H2),
              table([["Column", "Type", "Meaning"],
                     ["id", "INTEGER PK autoincrement", "local row id (used by navigation)"],
                     ["sport, day, time_slot, skill", "TEXT", "enum names (stable if enums are reordered)"],
                     ["title, location, notes, host_name", "TEXT", "session details"],
                     ["max_players, player_count", "INTEGER", "player limit is enforced in SQL on join"],
                     ["is_hosting, is_joined", "INTEGER (bool)", "the user's relationship to the session"],
                     ["latitude, longitude", "REAL, nullable", "A3 distance sorting (sample sessions)"],
                     ["created_at", "INTEGER", "orders the list; newest created first"],
                     ["remote_id", "TEXT, UNIQUE index", "UUID = Firestore document id; null for samples"],
                     ["owner_uid", "TEXT, nullable", "Firebase UID of the host account"],
                     ["sync_state", "TEXT, default LOCAL", "LOCAL, PENDING, UPLOADING, FAILED, SYNCED"],
                     ["synced_at", "INTEGER, nullable", "when the server last confirmed the row"]],
                    [1.9 * inch, 1.45 * inch, 3.15 * inch], code_cols=(0,)),
              Spacer(1, 4),
              p("Migration 1 → 2 adds the four sync columns and the unique index with ALTER TABLE; every "
                "existing row is kept, sessions created in Phase 1 receive a UUID and are queued for upload, and "
                "the samples stay local. There is no destructive fallback, so user data is never wiped. The "
                "exported schemas (<font face='Courier'>app/schemas/.../1.json, 2.json</font>) are committed."),
              p("3.2 Firestore collection <font face='Courier'>sessions</font>", H2),
              table([["Field", "Type", "Rule"],
                     ["(document id)", "string", "the session UUID, 8–64 chars"],
                     ["ownerUid", "string", "must equal request.auth.uid"],
                     ["sport / day / timeSlot / skill", "string", "must be one of the app's enum names"],
                     ["title / location / notes / hostName", "string", "1–60 / 1–80 / 0–300 / 1–40 chars"],
                     ["maxPlayers, playerCount", "int", "2–30; 1 ≤ playerCount ≤ maxPlayers"],
                     ["createdAt", "int", "client time (ms), > 0"],
                     ["serverCreatedAt", "timestamp", "must be the server timestamp (request.time)"],
                     ["schemaVersion", "int", "1; no other fields allowed"]],
                    [2.1 * inch, 0.8 * inch, 3.6 * inch], code_cols=(0,)),
              Spacer(1, 4),
              p("<font face='Courier'>hostName</font> is the account's display name or the part of the email "
                "before '@', never the full address. Passwords are never stored by the app (Firebase Auth keeps "
                "only its own session token)."),
              p("3.3 UI model", H2),
              p("<font face='Courier'>PickupSession</font> (Kotlin data class) is what the screens use; "
                "<font face='Courier'>SessionEntity.toModel()</font> and <font face='Courier'>RemoteSession.fromFields()</font> "
                "convert defensively (unknown enum values or malformed documents are skipped, never crash)."),
              ]

    # 4 Interfaces
    story += [p("4. Interfaces", H1),
              table([["Interface / class", "Operations"],
                     ["SessionDao (Room)", "observeAll(): Flow; getById; insert; toggleJoin (transaction: leave, or join "
                                           "only if not full); getUnsynced; markSynced; confirmByRemoteId; insertIfAbsent "
                                           "(IGNORE on duplicate remote_id); resetInterruptedUploads"],
                     ["SessionRepository", "sessions: Flow<List<PickupSession>>; createSession(); toggleJoin(); getSession()"],
                     ["SessionRemoteSource", "upload(RemoteSession) (completes only on server ack); fetchFromServer() "
                                             "(Source.SERVER); probeServerRead()"],
                     ["AuthGateway", "currentUser; signIn(email, password); signOut(); auth-state listener"],
                     ["SessionSync", "uploadUnsynced(user) → UploadReport; pullFromServer(user) → PullReport"],
                     ["CloudSyncManager", "state: StateFlow (configured, user, busy, last outcome); messages: Flow; "
                                          "startOnce(); signIn(); signOut(); syncNow(); onSessionCreated(); "
                                          "probeUnauthenticatedRead()"],
                     ["Firestore REST (rules)", "read sessions: signed-in only; create/update: own UID + valid fields; "
                                                "delete: never; any other path: denied"]],
                    [1.65 * inch, 4.85 * inch], code_cols=(0,)),
              ]

    # 5 Rationale
    story += [p("5. Design rationale", H1), *bullets([
        "<b>Room as the source of truth.</b> Screens always read Room, so the app behaves the same signed out, "
        "offline, or when the server is slow; the cloud only adds and confirms data.",
        "<b>Sync only user-created sessions.</b> They are the data a user would lose on a new phone. The 9 sample "
        "sessions stay local, and <b>join/leave stays local</b> (it persists in Room across restarts). Syncing "
        "shared player counts safely would need server-side transactions per join, which is out of scope; we do "
        "not claim cross-device join synchronisation.",
        "<b>UUID shared by Room and Firestore</b> (unique index + document id) makes uploads idempotent and makes "
        "duplicates impossible on either side; a retried upload just rewrites the same document.",
        "<b>Mark synced only on server acknowledgement.</b> An upload that times out is FAILED; if it actually "
        "landed, the next server read confirms it instead of duplicating it.",
        "<b>Ownership fixed at creation.</b> A session created under one account is never uploaded under another "
        "account that later signs in on the same device.",
        "<b>Source.SERVER for retrieval</b> so a successful sync proves the data came from the backend in this app "
        "session, not from Firestore's local cache.",
        "<b>Firebase</b> (managed Auth + Firestore) gives authenticated access, HTTPS and server-side rules "
        "without operating a server. Firebase BoM 34.12.0 is the newest release whose Auth library the project's "
        "Kotlin 2.1 compiler can read (newer Auth ships Kotlin 2.3 metadata).",
        "<b>Separate emulator build type.</b> Automated tests use the local Firebase Emulator Suite through a "
        "separate APK (“Pickup Play (EMULATOR)”, version suffix -emulator, its own Firebase app instance). "
        "The debug and release builds contain no emulator switch and block all cleartext traffic.",
    ]),
              p("6. Security", H1), *bullets([
        "Firestore rules (<font face='Courier'>firestore.rules</font>, deployed to " + R["firebase_project"] + "): "
        "no public access; signed-in users may read sessions; create/update only with "
        "<font face='Courier'>ownerUid == request.auth.uid</font>, an exact field set, enum and length checks, "
        "and a server timestamp; deletes and all other collections are denied.",
        "HTTPS only: Firebase SDKs use TLS; Android's default network policy blocks cleartext in the graded APK.",
        "No credentials in the app or repository. The grading account is created in the Firebase console and its "
        "password is given to the grader separately. Sign-in errors are logged without email or password; the "
        "password field is excluded from saved state and cleared after sign-in.",
        "<font face='Courier'>google-services.json</font> is kept out of the public GitHub repository; it is included "
        "only in the course submission so the source builds against the project (it identifies the project, it "
        "does not grant access; access is controlled by Auth and the rules).",
    ])]

    # 7 Offline
    story += [p("7. Offline and failure behaviour", H1),
              table([["Situation", "What the user sees"],
                     ["Signed out", "Full app works from Room; the Account screen offers sign-in. Created sessions "
                                    "show “Not synced: saved on this device only”."],
                     ["Launch with no network (signed in)", "App opens with all local data; message “Cloud sync failed: "
                                                            "No internet connection. Showing sessions saved on this device.”"],
                     ["Create while offline", "Saved in Room, card marked “Not synced”, message says it is NOT "
                                              "synced; Account shows the unsynced count. Sync now uploads it later."],
                     ["Server unreachable (network up)", "Upload times out after 15 s → “Not synced: upload failed, saved "
                                                         "on this device”; manual sync reports FAILED; no spinner left running."],
                     ["Wrong password", "“Wrong email or password”; still signed out."],
                     ["App killed during upload", "Row returns from UPLOADING to PENDING on next start and is retried."]],
                    [1.9 * inch, 4.6 * inch]),
              ]

    # 8 Testing
    story += [p("8. Testing", H1),
              p("Each row states where it ran. <b>Mocked</b> tests use a fake remote store; <b>emulator</b> tests use "
                "the official local Firebase Emulator Suite (real rules engine and real Android SDKs, offline demo "
                "project); <b>live</b> tests use the real Firebase project " + R["firebase_project"] + "."),
              table([["Suite", "Kind", "Code version", "Result"]] + R["tests"],
                    [2.05 * inch, 1.35 * inch, 1.25 * inch, 1.85 * inch]),
              Spacer(1, 4),
              p("Live round trip evidence", H2), *bullets(R["live_evidence"]),
              KeepTogether([evidence_figures(R["evidence_images"]),
                            p("Figure 2. Screenshots from the automated live run against " + R["firebase_project"]
                              + ": left, the session restored from Firestore after clearing app data and signing in "
                              "again; right, the signed-out server read denied by the deployed rules.", SMALL)]),
              p("Not verified / known issues", H2), *bullets(R["not_verified"]),
              ]

    # 9 Grader instructions
    story += [p("9. How to grade it", H1),
              p("<b>Install.</b> Enable installs from unknown sources (or use <font face='Courier'>adb install -r "
                + R["apk_name"] + "</font>) on Android 10 (API 29) or newer with Google Play services and internet. "
                "Use <i>" + R["apk_name"] + "</i>, not the emulator test APK."),
              p("<b>Remote round trip</b>"),
              *bullets([
                  "Tap the account icon (top right) → sign in with the grading account (credentials provided "
                  "separately with the submission). Status: “Last sync …: server read OK”.",
                  "Back → pick a sport → Create session → fill in → Create. The new card shows "
                  "“Synced to cloud” (the document is now in Firestore under the grading account's UID).",
                  "Force-stop and reopen: still signed in; the startup sync performs a Source.SERVER read.",
                  "Settings → Apps → Pickup Play → Storage → Clear storage. Reopen: the session is gone "
                  "locally. Sign in again: status “server read OK … New on this device: N” and the session "
                  "reappears marked “Synced to cloud” – it can only have come from the server.",
              ]),
              p("<b>Invalid authentication is denied</b>"),
              *bullets([
                  "Account → Sign out → sign in with a wrong password: “Wrong email or password”.",
                  "While signed out: <i>Test: read server data without signing in</i> → “the server DENIED the "
                  "unauthenticated read (permission denied)”.",
                  "Optional: <font face='Courier'>curl \"https://firestore.googleapis.com/v1/projects/" + R["firebase_project"]
                  + "/databases/(default)/documents/sessions\"</font> returns 403 PERMISSION_DENIED.",
              ]),
              p("<b>Offline</b>: enable airplane mode, force-stop and reopen → app works from Room and reports that "
                "sync failed; create a session → “Not synced”; disable airplane mode → Account → Sync now "
                "→ “Uploaded 1 session(s)”."),
              p("<b>Automated tests</b> (from the source folder): <font face='Courier'>./gradlew testDebugUnitTest</font>; "
                "<font face='Courier'>cd firebase-tests &amp;&amp; npm ci --ignore-scripts &amp;&amp; npm run test:emulator</font> "
                "(Java 21); device suites in <font face='Courier'>ci/</font> (see docs/A4_CLOUD.md)."),
              ]

    # 10 AI disclosure
    story += [p("10. AI use disclosure", H1)] + [p(t) for t in R["ai_disclosure"]]

    doc = SimpleDocTemplate(OUT, pagesize=letter, leftMargin=0.75 * inch, rightMargin=0.75 * inch,
                            topMargin=0.7 * inch, bottomMargin=0.75 * inch,
                            title="Pickup Play – CSE 476 A4", author=", ".join(R["team"]),
                            subject="CSE 476 Assignment A4 documentation")
    doc.build(story, onFirstPage=on_page, onLaterPages=on_page)


build()
print("wrote", OUT)
