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

    // Release signing identity, supplied out-of-band (CI secrets, or your own local
    // env vars) so the same key signs every release build - required for users to be
    // able to install updates over an existing copy without uninstalling first.
    // None of these values (nor the keystore file itself) are ever committed; see
    // README.md "Releasing" section for how CI provides them.
    val releaseStorePath = System.getenv("RELEASE_KEYSTORE_PATH")
    val releaseStorePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
    val releaseKeyAlias = System.getenv("RELEASE_KEY_ALIAS")
    val releaseKeyPassword = System.getenv("RELEASE_KEY_PASSWORD")
    val hasReleaseSigningEnv = !releaseStorePath.isNullOrBlank() &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

    signingConfigs {
        if (hasReleaseSigningEnv) {
            create("release") {
                storeFile = file(releaseStorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // When RELEASE_KEYSTORE_PATH/etc. are set (CI), sign with the real,
            // stable release key. Otherwise (local personal builds), fall back to
            // the auto-generated debug keystore so `assembleRelease` still works
            // with zero setup - it just won't be upgrade-compatible with the
            // "real" signed releases published on GitHub.
            signingConfig = if (hasReleaseSigningEnv) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
