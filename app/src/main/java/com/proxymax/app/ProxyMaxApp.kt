package com.proxymax.app

import android.app.Application
import androidx.work.Configuration
import com.proxymax.workers.AutoUpdateWorker

class ProxyMaxApp : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        AutoUpdateWorker.schedule(this)
    }
}
