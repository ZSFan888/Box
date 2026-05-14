package com.proxymax.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ProxyMaxApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // WorkManager 由 Configuration.Provider 懒初始化，无需手动调用 schedule
        // schedule 在需要时由各入口调用
        try {
            com.proxymax.workers.AutoUpdateWorker.schedule(this)
        } catch (e: Exception) {
            Log.w("ProxyMaxApp", "WorkManager schedule deferred: ${e.message}")
        }
    }
}
