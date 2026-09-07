# ADAM APK MAKER

English-only Android app UI for an APK builder.

Included:
- ZIP / HTML picker
- App icon picker
- App name + package name
- Build / Options / History tabs
- Fast Build key UI (`ADAM31`) with a 5-minute unlock timer
- AI Assistant panel
- Dark + teal neon visual style inspired by the supplied references, but with original branding/layout
- Clear error messages instead of crashing

Important:
This project is a client/UI prototype. A real APK compiler requires an Android build service or an embedded Android build toolchain. The current Build button intentionally does NOT create a fake APK; it displays the build workflow and timing UI.

Build setup:
- Android Gradle Plugin 8.6.1
- compileSdk 35
- minSdk 23
- targetSdk 35

The environment used to create this ZIP does not contain Gradle or the Gradle wrapper JAR, so I have not falsely claimed a verified APK build. The project files are structured for Android Studio / a Gradle runner.
