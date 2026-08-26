plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.videometaeditor"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.videometaeditor"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // FFmpegKit gives full read/write control over container + stream + per-stream
    // metadata (title, artist, comments, GPS location, creation time, custom keys, etc.)
    //
    // NOTE: the original com.arthenica:ffmpeg-kit-* artifacts were fully removed from
    // Maven Central on April 1, 2025 when the upstream project was retired — that
    // coordinate will now always fail with "Could not find com.arthenica:ffmpeg-kit-*".
    // This dependency is a community-maintained rebuild of the exact same source
    // (github.com/moizhassankh/ffmpeg-kit-android-16KB), published under a new Maven
    // coordinate but keeping the same com.arthenica.ffmpegkit package/API, so none of
    // the Kotlin code in this app needed to change — only this one line.
    implementation("com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1")
}
