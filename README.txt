ZIP TO APK - Android WebView App
=================================

This is a ready-to-build Android Studio project that wraps this website
in a native Android app:

  https://zip-to-apk-ce53.onrender.com/

It loads the site in a full-screen WebView with pull-to-refresh and
back-button navigation support.

HOW TO BUILD THE ACTUAL .APK FILE
----------------------------------
1. Download and install Android Studio (free): https://developer.android.com/studio
2. Open Android Studio -> "Open" -> select this unzipped "ZipToApk" folder.
3. Let Gradle sync (first time may take a few minutes, downloads dependencies).
4. Go to Build -> Build Bundle(s) / APK(s) -> Build APK(s).
5. When it finishes, click "locate" in the notification, or find your APK at:
       app/build/outputs/apk/debug/app-debug.apk
6. Copy that APK to your Android phone and install it (you may need to
   enable "Install unknown apps" for whichever app you use to open it).

QUICK NO-CODE ALTERNATIVE
----------------------------------
If you don't want to install Android Studio, you can also get an APK by
using PWABuilder.com - paste in the URL and it can package a similar
WebView-based APK for you automatically, no coding required.

CUSTOMIZING
----------------------------------
- App name: app/src/main/res/values/strings.xml
- Target URL: MainActivity.java (TARGET_URL constant)
- Icon/colors: app/src/main/res/drawable/ic_launcher_*.xml and colors.xml
  (replace with your own logo image if you have one - just drop a PNG into
  mipmap folders and update AndroidManifest.xml android:icon reference)
- App package ID: currently "com.ziptoapk.app", change in app/build.gradle
  and move the MainActivity.java package folder to match if you rename it.

NOTES
----------------------------------
- Requires internet access to load the site (it is not bundled offline).
- Minimum Android version supported: Android 5.0 (API 21).
