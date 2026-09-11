# CloudPhone

A simple Android app (project folder still named `FileZipApp`, app-facing
name is **CloudPhone**) that lets you select one or more files and compress
them into a single `.zip` file, saved wherever you choose on the device.
The launcher icon is your own penguin PNG, placed in `res/mipmap-*`.

## What it does
1. Tap **Select Files to Zip** → pick any files via the system file picker.
2. Enter a name for the output zip (defaults to `archive.zip`).
3. Tap **Create Zip File** → choose where to save it.
4. The app streams the selected files into a real `.zip` archive.

No storage permissions are required — it uses the Storage Access Framework
(`OpenMultipleDocuments` / `CreateDocument`), which works cleanly on modern
Android versions and on emulators/cloud-phone test devices alike.

## How to build & run (Android Studio)
1. Open Android Studio → **Open** → select the `FileZipApp` folder (this
   folder, the one containing `settings.gradle`).
2. Let Gradle sync (it will download the Gradle 8.4 distribution and
   dependencies the first time — needs internet access).
3. Create/start an emulator: **Tools → Device Manager → Create/Play** a
   virtual device (or connect your cloud-phone test device via ADB).
4. Click **Run ▶** (or **Build → Build Bundle(s)/APK(s) → Build APK(s)** to
   just get the `.apk` file, found afterward in
   `app/build/outputs/apk/debug/app-debug.apk`).
5. Install/test on the emulator or cloud phone as usual (`adb install
   app-debug.apk` if testing on a remote/cloud device manually).

## Running the instrumented test on the emulator
A basic instrumented test lives at
`app/src/androidTest/java/com/example/filezipapp/MainActivityInstrumentedTest.kt`.
It checks the app launches with the correct package name and that the core
UI (pick-files button, zip-name field, create-zip button) is present.

With an emulator running:
```
./gradlew connectedAndroidTest
```
or, in Android Studio, right-click the test file → **Run**.

## Project structure
```
FileZipApp/
├── app/
│   ├── build.gradle                # app module config (min/target SDK, deps, test runner)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/example/filezipapp/MainActivity.kt   # all app logic
│       │   └── res/
│       │       ├── mipmap-*/ic_launcher.png   # your penguin icon, per density
│       │       ├── layout/activity_main.xml
│       │       └── values/{strings.xml, themes.xml}
│       └── androidTest/
│           └── java/com/example/filezipapp/MainActivityInstrumentedTest.kt
├── build.gradle                    # project-level config
├── settings.gradle
└── gradle.properties
```

## Notes / things you may want to customize
- `applicationId` is `com.example.filezipapp` — change it in
  `app/build.gradle` before publishing anywhere (the visible app name is
  already "CloudPhone" via `strings.xml`).
- `minSdk 23` (Android 6.0+) covers essentially all real devices and
  emulator images.
- The launcher icon is your uploaded PNG, copied into every
  `res/mipmap-*dpi` folder (mdpi through xxxhdpi) at its original
  resolution (128×128) so Android scales it appropriately per device
  density. Swap the files there any time to change the icon.
- Currently only supports zipping individual files (not whole folders).
  Folder support would need `ACTION_OPEN_DOCUMENT_TREE` plus recursive
  traversal via `DocumentFile` — happy to add this if you need it.
