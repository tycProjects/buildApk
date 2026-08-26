# Foreground Service update

FloatingWidgetService now calls startForeground() with a low-importance notification channel.
Manifest includes FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE and POST_NOTIFICATIONS.
MainActivity requests notification permission on Android 13+ and uses startForegroundService() on Android 8+.
Static checks: Java braces PASS; XML parse PASS; resource IDs PASS.
Build limitation: sandbox has no Android SDK, so assembleDebug cannot run here.
