// nhzterm — complete Gradle Android project.
// Valence (own-Gradle tier, VALENCE-DEVDOC §3/§5) detects this file and
// builds the project with its own toolchain: local.properties injected,
// Gradle 8.7 auto-provisioned when no wrapper/system gradle exists.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "nhzterm"
include(":app")
include(":api")
