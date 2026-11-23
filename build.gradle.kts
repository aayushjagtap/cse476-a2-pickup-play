plugins {
    id("com.android.application") version "8.11.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.21" apply false
    // A4: annotation processing for the Room database.
    id("com.google.devtools.ksp") version "2.1.21-2.0.2" apply false
    // A4 Phase 2: reads app/google-services.json (applied in :app only when that file exists).
    id("com.google.gms.google-services") version "4.4.4" apply false
}
