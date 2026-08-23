package com.ryan.download

import android.app.Application
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltAndroidApp
class TaiVideoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                YoutubeDL.getInstance().init(this@TaiVideoApp)
                try { YoutubeDL.getInstance().updateYoutubeDL(this@TaiVideoApp) } catch (_: Exception) {}
                Log.d("TaiVideo", "yt-dlp initialized")
            } catch (e: YoutubeDLException) {
                Log.e("TaiVideo", "Failed to init yt-dlp", e)
            }
        }
    }
}
