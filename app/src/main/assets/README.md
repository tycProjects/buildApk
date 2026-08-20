# VIRN → Android APK

This folder is a ready-to-build Capacitor project wrapping your `index.html`.
I can't compile it inside this chat (no Android SDK / network here), but on
your own machine these steps produce a real, installable `.apk` using the
official Capacitor CLI — nothing hand-guessed.

## Prerequisites (one-time, on your computer)
- [Node.js](https://nodejs.org) (LTS)
- [Android Studio](https://developer.android.com/studio) — this also installs
  the Android SDK and a bundled JDK, so you don't need to install those separately

## Build steps
Unzip this project, `cd` into it, then run:

```bash
npm install
npx cap add android
npx cap sync android
npx cap open android
```

The last command opens the project in Android Studio. Once it finishes
Gradle sync (first time takes a few minutes — it's downloading build
tools), click **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

The debug APK lands at:
```
android/app/build/outputs/apk/debug/app-debug.apk
```
That file installs directly on any Android phone (enable "install unknown
apps" for whatever app you transfer it with). It's unsigned-for-release but
fully installable for testing/personal use.

For a **release** build (needed to publish on the Play Store), Android
Studio will walk you through generating a signing keystore under
**Build → Generate Signed Bundle / APK**.

## ⚠️ Important: Google Sign-In will break as-is
Your app signs in with:
```js
window.fb.signInWithPopup(window.auth, provider)
```
This is a browser-popup flow. Inside a native Android WebView:
1. `window.open` popups are unreliable/blocked, so the flow often just does nothing.
2. Even if the popup opened, Firebase's authorized-domains check is keyed to
   your real web domain — a Capacitor app doesn't run on that domain, so
   Firebase will likely reject it with an unauthorized-domain error.

Email/password sign-in (`signInWithEmailAndPassword`) is unaffected and
should work fine as-is.

**The real fix**: swap the Google sign-in call to the
[`@capacitor-firebase/authentication`](https://github.com/capawesome-team/capacitor-firebase)
plugin, which talks to native Google Sign-In directly instead of going
through a web popup. That's a native plugin install + a small code change
in the sign-in handler — happy to write that change if/when you're ready
for it, once this base project builds for you.

## Files in this project
- `www/index.html` — your app, unmodified
- `www/manifest.json` — generated app metadata (name, icons, theme color)
- `www/icon-192.png`, `www/icon-512.png` — placeholder icons (swap for your real logo — same filenames)
- `capacitor.config.json` — app id `com.virn.notebook`, app name `VIRN`
