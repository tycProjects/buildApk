package com.ryan.vietsubai.core

import android.app.Application
import com.ryan.vietsubai.data.AppDatabase

/** Application entry point. Keeps process-wide infrastructure initialization out of UI code. */
class VietsubAiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppDatabase.get(this)
    }
}
