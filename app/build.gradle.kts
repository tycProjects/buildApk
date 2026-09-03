plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nhztech.nhzterm"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nhztech.nhzterm"
        // 26+: notification channels, adaptive launcher icons, Process.pid()
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // jniLibs packaging is standard AGP (concept doc §12.1): prebuilt
    // libnhzsh.so per ABI under src/main/jniLibs/ lands in the APK's native
    // library directory automatically — that location is exec-permitted,
    // which is what sidesteps the Android 10+ execute restriction (§12.1).
    // No CMake/NDK step in this build: nhzsh is cross-compiled separately
    // via `make android` in nhzsh/ (see app/src/main/jniLibs/README.md).
}

dependencies {
    implementation(project(":api"))
}
