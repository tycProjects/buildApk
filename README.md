# Glass Files

A black & white, glassmorphism-styled file manager for Android, built with
Kotlin + Jetpack Compose.

## Features

- Browse device storage with breadcrumb navigation and back-stack
- List and grid views
- Search within the current folder
- Sort by name, date, size, or type (tap again to reverse)
- Multi-select: copy, move (clipboard + paste), rename, delete, share
- Create new folders/files
- File details (size, modified date, path)
- Storage usage bar (used / total device storage)
- Frosted "glass" cards throughout: real background blur on Android 12+
  (API 31+), graceful translucent fallback on older versions
- Handles storage permissions across API levels (legacy runtime permissions
  on API ≤ 29, granular media permissions on API 33+, and "All files access"
  via Settings on API 30+)

## Requirements

- Android Studio Koala/Ladybug or newer
- JDK 17
- compileSdk / targetSdk 34, minSdk 26

## Opening the project

1. Unzip and open the root folder in Android Studio.
2. Let Gradle sync. Android Studio will use the bundled Gradle wrapper.

### About the Gradle wrapper jar

This project ships the wrapper *launcher scripts* (`gradlew`, `gradlew.bat`)
and `gradle/wrapper/gradle-wrapper.properties` (pinned to Gradle 8.7), but
**not** the compiled `gradle/wrapper/gradle-wrapper.jar` binary, since it
couldn't be fetched in the environment this project was generated in.

- **In Android Studio:** this is a non-issue — on first sync, Studio detects
  the missing wrapper jar and regenerates it automatically (or prompts you
  to). You can also generate it yourself from a terminal with any local
  Gradle install:
  ```
  gradle wrapper --gradle-version 8.7 --distribution-type bin
  ```
- **In GitHub Actions:** the included workflow
  (`.github/workflows/android-build.yml`) provisions Gradle 8.7 directly and
  regenerates the wrapper jar as its first step, so CI builds work out of
  the box with no manual steps.

## Building from the command line

```
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Notes

- The app requests "All files access" (`MANAGE_EXTERNAL_STORAGE`) on
  Android 11+ so it can behave like a full file manager rather than being
  limited to the scoped-storage sandbox. You grant this via a system
  settings screen the app links to.
- Package name / applicationId: `com.blackglass.filemanager`
