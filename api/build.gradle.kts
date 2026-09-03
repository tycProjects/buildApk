plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nhztech.nhzterm.api"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// nhzterm-api has ZERO external dependencies (concept doc §6.7): the
// protocol is length-prefixed JSON over LocalSocket, both of which are
// Android framework APIs. Any app can vendor or depend on this module.
