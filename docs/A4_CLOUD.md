# A4 Phase 2: Firebase Authentication + Cloud Firestore

Pickup Play keeps every session in its local Room database (Phase 1). Phase 2 adds an
authenticated, HTTPS-only round trip to Cloud Firestore for the sessions a user **creates**.

## 1. One-time Firebase Console setup (project owner)

The repository contains no Firebase credentials. Until these steps are done the app builds and
runs fully offline, and the Account screen says cloud sync is not set up.

1. **Create a project**: <https://console.firebase.google.com> > *Add project* (Google Analytics
   is not needed).
2. **Register the Android app**: *Project settings > Your apps > Add app > Android*
   - Package name: `com.aayushjagtap.pickupplay` (must match exactly)
   - Download **`google-services.json`** and save it as `app/google-services.json`.
     It is git-ignored on purpose (public repo), so each build machine adds its own copy.
3. **Enable email/password sign-in**: *Build > Authentication > Get started > Sign-in method >
   Email/Password > Enable*. (Leave "Email link" off.)
4. **Create the grading account**: *Authentication > Users > Add user*, with an email and a
   strong password. Share the credentials with the grader privately (not in the repo or report).
   The app has no sign-up screen; accounts are created by the project owner.
5. **Create the database**: *Build > Firestore Database > Create database* > Standard edition >
   pick a region (e.g. `nam5`) > start in **production mode** (never "test mode").
6. **Deploy the security rules** in this repo (next section).
7. Rebuild: `./gradlew assembleDebug`. The build log no longer prints
   "google-services.json not found".

## 2. Deploy and verify `firestore.rules`

```bash
cd firebase-tests
npm ci --ignore-scripts                 # pinned firebase-tools, no install scripts
npx firebase login                      # opens a browser, sign in as the project owner
npx firebase use --add                  # choose the project from step 1 (writes .firebaserc)
npx firebase deploy --only firestore:rules --config ../firebase.json
```

The deploy prints `cloud.firestore: rules file ../firestore.rules compiled successfully` and
`Deploy complete!`.

**Check that the rules are active** (any of these):

- Console: *Firestore Database > Rules* shows this file's text and a fresh "Last published" time.
- From any terminal, an unauthenticated REST read must be refused:

  ```bash
  curl -s "https://firestore.googleapis.com/v1/projects/<PROJECT_ID>/databases/(default)/documents/sessions"
  # -> "code": 403, "status": "PERMISSION_DENIED"
  ```
- In the app, while signed out: *Account > Test: read server data without signing in*. The
  result says "the server DENIED the unauthenticated read".

Re-run the automated rules tests at any time (they use the local emulator, not your project):

```bash
cd firebase-tests && npm ci --ignore-scripts && npm run test:emulator   # needs Java 21
```

## 3. Data model

Collection `sessions`, one document per user-created session; **document id = the session's
`remote_id` UUID**, which is also stored in Room (unique index), so a session can never be
duplicated on either side.

| Field | Type | Notes |
|---|---|---|
| `ownerUid` | string | Firebase Auth UID of the host; rules require `== request.auth.uid` |
| `sport`, `day`, `timeSlot`, `skill` | string | enum names, checked against fixed lists |
| `title` (1-60), `location` (1-80), `notes` (0-300), `hostName` (1-40) | string | length-checked |
| `maxPlayers` (2-30), `playerCount` (1..max) | int | |
| `createdAt` | int | client time (ms), orders the list |
| `serverCreatedAt` | timestamp | must be the server timestamp (`request.time`) |
| `schemaVersion` | int | `1` |

`hostName` is the account's display name or the part of the email before `@`, never the full
address.

## 4. How local and remote persistence interact

- **Room is always the source of truth for the screens.** The app works the same signed out or
  offline; signing out never deletes local data.
- Creating a session saves it to Room first (`sync_state = PENDING`, new UUID). If signed in, it
  is uploaded in the background and becomes `SYNCED` only after the server acknowledges it. On
  failure it stays in Room as `FAILED`, and the card, detail screen, Account screen and a
  Snackbar all say it is **not synced**.
- Every sync (on app start when signed in, after sign-in, and *Sync now*) uploads unsynced
  sessions, then reads the collection with **`Source.SERVER`**, so the result can never come
  from Firestore's local cache. Documents are merged by `remote_id`: new ones are inserted,
  existing ones are only confirmed (local join state is kept).
- All calls are coroutines with time limits (upload 15 s, read 20 s, sign-in 20 s), so progress
  indicators always end, and every Firebase exception becomes a readable message.
- Database v1 -> v2 is an explicit Room migration: existing rows are kept, sessions created in
  Phase 1 get a UUID and are queued for upload, and the samples stay local.

**Scope (not synchronised):** the 9 built-in sample sessions, and join/leave. Joining and leaving
remain local to the device (they still persist in Room across restarts). There is no
cross-device join synchronisation.

## 5. Grader demonstration (real Firebase project)

Install a build that contains `app/google-services.json`.

**Server round trip**
1. Open the app > account icon (top right) > sign in with the grading account. The status
   shows "Last sync ...: server read OK".
2. Back > pick a sport > *Create session* > fill in > *Create*. The new card shows
   "☁ Uploading…", then "☁ Synced to cloud".
3. Firebase Console > *Firestore Database > Data > sessions*: the new document, whose
   `ownerUid` equals the grading user's UID (*Authentication > Users*).
4. Prove a *later* app session reads it **from the server**: Android *Settings > Apps > Pickup
   Play > Storage > Clear storage* (wipes Room, the Firestore cache and the sign-in). Open the
   app: the session is gone. Account > sign in again. The status reads "server read OK.
   Sessions on the server: N. New on this device: 1.", and the session is back in its sport's list
   marked "☁ Synced to cloud". It could only have come from the server.
   (Or just force-stop and reopen: the sign-in persists and the startup sync reads the server.)

**Invalid authentication is denied**
1. Account > *Sign out* > enter the grading email with a wrong password > *Sign in*:
   "Wrong email or password", still signed out.
2. While signed out: *Test: read server data without signing in*: "the server DENIED the
   unauthenticated read (permission denied)".
3. Optional: the `curl` command in section 2 returns `403 PERMISSION_DENIED`.

**Offline / server failure**
1. Turn on airplane mode, force-stop and reopen: the app launches with all local data and a
   "Cloud sync failed: No internet connection" message. Creating a session marks it
   "☁ Not synced: saved on this device only".
2. Turn airplane mode off > Account > *Sync now*: "Uploaded 1 session(s)", card turns
   "☁ Synced to cloud".

## 6. Local emulator testing (no real project needed)

```bash
# Rules engine tests (19 cases)
cd firebase-tests && npm ci --ignore-scripts && npm run test:emulator

# Full app round trip against local Auth + Firestore emulators
./gradlew assembleEmulator
adb install -r app/build/outputs/apk/emulator/app-emulator.apk
python ci/a4_cloud_check.py out/cloud
```

The `emulator` build type is a separate APK (`app-emulator.apk`, launcher name
"Pickup Play (EMULATOR)", version suffix `-emulator`). It uses its own Firebase app instance with
the emulator's offline `demo-pickupplay` project and is the only build allowed plain HTTP, and
only to `10.0.2.2`/`localhost`. The **debug and release builds have no emulator switch at all**:
they always use the real project from `google-services.json`, over HTTPS (cleartext blocked).
Submit `app-debug.apk` (or a release build), never `app-emulator.apk`.
