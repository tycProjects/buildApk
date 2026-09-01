# WZH 2 APK — Permission Test Project

This is a small Android Gradle project designed to test the permission options shown in the WZH 2 APK UI.

## Included permission groups

- Network: Internet, Network state
- System: Notifications, Keep screen awake, Background tasks, Biometric
- Media: Camera, Microphone, Photos, Videos, Audio files
- Location: Precise, Approximate, Background location
- Storage: Legacy files/media
- Contacts & Calendar: Read/Edit contacts and calendar
- Phone & SMS: Calls, phone state, call log, read/send SMS
- Connectivity & Sensors: Bluetooth, Nearby Wi-Fi devices, body sensors, physical activity, vibrate

## Important

Not every item is a normal runtime permission.

Some are:
- normal permissions
- special system permissions
- role-restricted permissions
- version-specific permissions
- capabilities that are represented by multiple Android APIs rather than one permission

The app intentionally does not perform calls, send SMS, read private data, or start background work automatically. It mainly declares the permissions and lets Android show the normal runtime permission flow.

## Build

Open the project in Android Studio and sync Gradle.

Or from the project directory:

    ./gradlew assembleDebug

The debug APK will be:

    app/build/outputs/apk/debug/app-debug.apk
