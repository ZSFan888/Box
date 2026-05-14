package com.proxymax.app

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ProxyMaxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // WorkManager 调度延迟执行，避免与 Hilt 初始化竞争
        try {
            com.proxymax.workers.AutoUpdateWorker.schedule(this)
        } catch (e: Exception) {
            Log.w("ProxyMaxApp", "WorkManager schedule failed: ${e.message}")
        }
    }
}
