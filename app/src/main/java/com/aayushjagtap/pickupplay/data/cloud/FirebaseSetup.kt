package com.aayushjagtap.pickupplay.data.cloud

import android.content.Context
import android.util.Log
import com.aayushjagtap.pickupplay.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Finds the Firebase app for this build.
 *
 * - Normal builds: Firebase initialises itself from app/google-services.json (via the Google
 *   Services Gradle plugin). Without that file there is no Firebase app and cloud sync is off.
 * - The separate `emulator` build type only (app-emulator.apk, used by automated tests):
 *   connects to the local Firebase Emulator Suite. The "demo-" project id is the emulator's
 *   offline-only mode; it is not a real project and these options are not credentials. The
 *   debug and release builds can never take this path.
 */
object FirebaseSetup {

    data class Services(val auth: FirebaseAuth, val firestore: FirebaseFirestore, val usingEmulator: Boolean)

    private const val TAG = "FirebaseSetup"
    private const val EMULATOR_BUILD_TYPE = "emulator"
    private const val EMULATOR_PROJECT = "demo-pickupplay"
    private const val EMULATOR_APP_NAME = "local-emulator"
    private const val AUTH_EMULATOR_PORT = 9099
    private const val FIRESTORE_EMULATOR_PORT = 8080

    fun connect(context: Context): Services? = try {
        val emulatorHost = BuildConfig.FIREBASE_EMULATOR_HOST
        val existing = FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
        if (BuildConfig.BUILD_TYPE == EMULATOR_BUILD_TYPE && emulatorHost.isNotEmpty()) {
            // A separately named app with the offline demo project, even when google-services.json
            // is present, so the emulator APK never talks to the real project.
            val app = FirebaseApp.getApps(context).firstOrNull { it.name == EMULATOR_APP_NAME }
                ?: FirebaseApp.initializeApp(
                    context,
                    FirebaseOptions.Builder()
                        .setProjectId(EMULATOR_PROJECT)
                        .setApplicationId("1:000000000000:android:0000000000000000")
                        .setApiKey("emulator-only")
                        .build(),
                    EMULATOR_APP_NAME,
                )
            val auth = FirebaseAuth.getInstance(app).apply { useEmulator(emulatorHost, AUTH_EMULATOR_PORT) }
            val firestore = FirebaseFirestore.getInstance(app)
                .apply { useEmulator(emulatorHost, FIRESTORE_EMULATOR_PORT) }
            Log.w(TAG, "Using the LOCAL Firebase emulator at $emulatorHost (emulator build type)")
            Services(auth, firestore, usingEmulator = true)
        } else if (existing != null) {
            Services(FirebaseAuth.getInstance(existing), FirebaseFirestore.getInstance(existing), false)
        } else {
            Log.i(TAG, "No google-services.json in this build: cloud sync disabled")
            null
        }
    } catch (e: Exception) {
        Log.e(TAG, "Firebase could not be initialised; cloud sync disabled", e)
        null
    }
}
