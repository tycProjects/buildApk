plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.leonteam.overlay"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.leonteam.overlay"
        minSdk = 26          // TYPE_APPLICATION_OVERLAY yêu cầu API 26+
        targetSdk = 35       // Android 15
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Ký bằng debug key để APK release cài được ngay (chỉ dùng cá nhân/test).
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
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
}
