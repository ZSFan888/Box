package com.proxymax.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.proxymax.data.repository.ProfileDao
import com.proxymax.ui.settings.SettingsKeys
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var dataStore: DataStore<Preferences>
    @Inject lateinit var profileDao: ProfileDao

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = dataStore.data.first()
                val autoStart = prefs[SettingsKeys.startOnBoot] ?: false
                if (!autoStart) return@launch

                val profile = profileDao.getActiveProfile()
                if (profile == null) {
                    Log.d("BootReceiver", "No active profile, skipping auto-start")
                    return@launch
                }
                Log.d("BootReceiver", "Auto-starting VPN on boot")
                context.startForegroundService(
                    Intent(context, ProxyVpnService::class.java).apply {
                        action = ProxyVpnService.ACTION_START
                        putExtra(ProxyVpnService.EXTRA_CONFIG, profile.rawConfig)
                    }
                )
            } finally {
                pending.finish()
            }
        }
    }
}
