package com.editorpro

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLES20
import android.os.Build
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import java.util.HashMap

class NIVEXDeviceProfileModule(context: ReactApplicationContext) : ReactContextBaseJavaModule(context) {
  override fun getName(): String = "NIVEXDeviceProfile"

  @ReactMethod
  fun getProfile(promise: Promise) {
    try {
      val context: Context = reactApplicationContext
      val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
      val memoryInfo = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
      val cpu = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
      val refresh = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) currentActivity?.display?.refreshRate ?: 60f else 60f
      } catch (_: Throwable) { 60f }
      val map = HashMap<String, Any>()
      map["ramMB"] = (memoryInfo.totalMem / (1024L * 1024L)).toInt()
      map["cpuCores"] = cpu
      map["refreshRate"] = refresh
      map["gpu"] = try { GLES20.glGetString(GLES20.GL_RENDERER) ?: "unknown" } catch (_: Throwable) { "unknown" }
      map["hardwareGpu"] = true
      map["thermal"] = "nominal"
      promise.resolve(map)
    } catch (t: Throwable) {
      promise.reject("NIVEX_DEVICE", t.message ?: "device profile failed", t)
    }
  }
}
