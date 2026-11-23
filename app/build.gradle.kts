plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// A4 Phase 2: Firebase is configured by app/google-services.json, downloaded from the Firebase
// console (it is git-ignored). Without it the app still builds and runs fully offline, and the
// Account screen explains that cloud sync is not configured.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
} else {
    logger.warn("app/google-services.json not found: building without Firebase (cloud sync off).")
}


android {
    namespace = "com.aayushjagtap.pickupplay"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aayushjagtap.pickupplay"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        // debug and release always use the real Firebase project from google-services.json,
        // over HTTPS. Neither has any switch that can point them at an emulator.
        debug {
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"\"")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"\"")
        }
        // Automated tests ONLY: talks to the local Firebase Emulator Suite. Built separately
        // (./gradlew assembleEmulator -> app-emulator.apk), named "Pickup Play (EMULATOR)" with a
        // "-emulator" version suffix, so it can never be mistaken for the graded APK.
        create("emulator") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            versionNameSuffix = "-emulator"
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"10.0.2.2\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    lint {
        abortOnError = false
    }

    testOptions {
        // Robolectric needs Android resources to run the Room tests on the local JVM.
        unitTests.isIncludeAndroidResources = true
    }
}

ksp {
    // Exported schemas document each database version and are needed for future migrations.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
    implementation("androidx.navigation:navigation-fragment-ktx:2.8.5")
    implementation("androidx.navigation:navigation-ui-ktx:2.8.5")

    // A4: local persistence (Room over SQLite) and coroutines for off-main-thread work.
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // A4 Phase 2: Firebase Authentication + Cloud Firestore (main modules via the BoM; the
    // discontinued -ktx modules are not used). play-services adds Task.await() for coroutines.
    // 34.12.0 is the newest BoM whose Auth library this project's Kotlin 2.1 compiler can read
    // (firebase-auth 24.1+ ships Kotlin 2.3 metadata). Moving to BoM 35.x needs Kotlin >= 2.3.
    implementation(platform("com.google.firebase:firebase-bom:34.12.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    // Local JVM tests (Robolectric runs Room against a real SQLite build).
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
