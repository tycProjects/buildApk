# WZH 2 APK — Native Java

This is the native rewrite of the supplied WZH 2 APK web UI. It does NOT load the original index.html and does NOT use WebView.

Implemented natively:
- Build / Options / History / Progress screens
- ZIP/HTML project picker
- Website-link source mode
- Java/Kotlin/HTML code entry and native project ZIP generation
- App options, splash choices, protection toggles and permission selection
- Native HTTP session, build-limit, build, status polling, cancel and history calls
- Multipart upload using projectZip and the same core server field names used by the original page

Server: https://zip-to-apk-ce53.onrender.com

If the backend has a strict browser Origin allowlist, it must allow native clients. The native app sends Origin: https://zip-to-apk-ce53.onrender.com, but a server should ideally authenticate the client independently of a browser Origin header.

The Gradle project uses AGP 8.7.2, Gradle 8.11.1, compileSdk 35 and Java/Kotlin 17.
