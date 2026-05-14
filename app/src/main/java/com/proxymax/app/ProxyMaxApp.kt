package com.proxymax.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import androidx.work.Configuration
import com.proxymax.workers.AutoUpdateWorker

@HiltAndroidApp
class ProxyMaxApp : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        AutoUpdateWorker.schedule(this)
    }
}
