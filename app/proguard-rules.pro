# KILL WIFI PROGUARD RULES
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep class com.zamzzz.killwifi.core.** { *; }
-keep class com.zamzzz.killwifi.service.** { *; }
-keep class androidx.** { *; }
-keep class com.google.android.material.** { *; }
-dontwarn com.airbnb.lottie.**
-dontwarn okhttp3.**
-dontwarn okio.**