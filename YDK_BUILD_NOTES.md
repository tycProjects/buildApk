# YDK Client source modification

This source has been modified so the in-game overlay uses a single circular **YDK** launcher button.

## Behavior
- Floating circular button labeled `YDK`.
- Drag the button to reposition it.
- Tap the button to open the existing VoidClient ClickGUI.
- Per-module shortcut buttons are disabled so only the YDK launcher is shown.
- App branding changed to `YDK Client`.

## Build
Open this project in Android Studio with a working JDK 17 + Android SDK/Gradle setup and run:

```bash
./gradlew :app:assembleDebug
```

The debug APK will normally be under:

`app/build/outputs/apk/debug/app-debug.apk`

The build could not be performed in this environment because Gradle 8.13 was not cached and this environment has no network access to download it.
