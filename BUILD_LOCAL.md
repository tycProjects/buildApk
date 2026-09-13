# YDK Client - Local Build

## Android Studio / Gradle
1. Open this folder as an Android/Gradle project.
2. Use JDK 17.
3. Let Gradle download its dependencies.
4. Run `./gradlew :app:assembleDebug`.
5. APK: `app/build/outputs/apk/debug/app-debug.apk`.

The project intentionally does not ship a private keystore. Debug builds use Android's standard debug signing.

## Features in this build
- YDK circular floating launcher.
- Drag to reposition; position is saved locally.
- Tap launcher to toggle the ClickGUI.
- Responsive ClickGUI for phone/tablet landscape sizes.
- Existing VoidClient module system and relay are retained.

Note: protocol/game compatibility depends on the underlying VoidClient release and its mappings; this source is not guaranteed to support every Minecraft Bedrock version.
