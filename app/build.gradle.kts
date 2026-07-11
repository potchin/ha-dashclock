plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "uk.co.potchin.hadashclock"
    compileSdk = 34

    defaultConfig {
        applicationId = "uk.co.potchin.hadashclock"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Personal / sideload build: reuse the auto-generated debug keystore so
            // `assembleRelease` produces an installable, self-signed APK with zero
            // extra keystore setup. Replace with a dedicated signingConfig if you
            // ever need a stable signing identity across machines.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // The original DashClock API interfaces (DashClockExtension, ExtensionData),
    // published to Maven Central by the romannurik/dashclock project in 2013 and
    // never removed. The upstream repo is archived and its JitPack build is broken,
    // but this artifact is stable and exactly what both DashClock and Chronus expect.
    implementation("com.google.android.apps.dashclock:dashclock-api:2.0.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // EncryptedSharedPreferences + the modern MasterKey builder API. This coordinate
    // has been "alpha" for years without a GA release, but it's the only version
    // that ships MasterKey (1.0.0 GA only has the older/deprecated MasterKeys utility).
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // org.json is part of the Android platform - no dependency needed for JSON parsing.
}
