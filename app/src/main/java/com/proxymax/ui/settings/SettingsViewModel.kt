package com.proxymax.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import android.content.Context
import com.proxymax.core.CoreType
import com.proxymax.data.model.AppSettings
import com.proxymax.data.model.PerAppMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

val Context.dataStore by preferencesDataStore("settings")

@HiltViewModel
class SettingsViewModel @Inject constructor(app: Application) : AndroidViewModel(app) {

    private val ds = app.dataStore

    private val PREFERRED_CORE  = stringPreferencesKey("preferred_core")
    private val AUTO_SELECT     = booleanPreferencesKey("auto_select_core")
    private val FAKE_IP         = booleanPreferencesKey("enable_fake_ip")
    private val IPV6            = booleanPreferencesKey("enable_ipv6")
    private val BYPASS_LAN      = booleanPreferencesKey("bypass_lan")
    private val START_ON_BOOT   = booleanPreferencesKey("start_on_boot")
    private val PER_APP_MODE    = stringPreferencesKey("per_app_mode")
    private val MIXED_PORT      = intPreferencesKey("mixed_port")
    private val API_PORT        = intPreferencesKey("api_port")
    private val LOG_LEVEL       = stringPreferencesKey("log_level")

    val settings: StateFlow<AppSettings> = ds.data.map { prefs ->
        AppSettings(
            preferredCore   = runCatching { CoreType.valueOf(prefs[PREFERRED_CORE] ?: "MIHOMO") }.getOrDefault(CoreType.MIHOMO),
            autoSelectCore  = prefs[AUTO_SELECT]  ?: true,
            enableFakeIP    = prefs[FAKE_IP]       ?: true,
            enableIPv6      = prefs[IPV6]          ?: true,
            bypassLan       = prefs[BYPASS_LAN]    ?: true,
            startOnBoot     = prefs[START_ON_BOOT] ?: false,
            perAppProxyMode = runCatching { PerAppMode.valueOf(prefs[PER_APP_MODE] ?: "GLOBAL") }.getOrDefault(PerAppMode.GLOBAL),
            mixedPort       = prefs[MIXED_PORT]    ?: 7890,
            apiPort         = prefs[API_PORT]      ?: 9090,
            logLevel        = prefs[LOG_LEVEL]     ?: "info"
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    fun update(block: AppSettings.() -> AppSettings) = viewModelScope.launch {
        val new = settings.value.block()
        ds.edit { prefs ->
            prefs[PREFERRED_CORE] = new.preferredCore.name
            prefs[AUTO_SELECT]    = new.autoSelectCore
            prefs[FAKE_IP]        = new.enableFakeIP
            prefs[IPV6]           = new.enableIPv6
            prefs[BYPASS_LAN]     = new.bypassLan
            prefs[START_ON_BOOT]  = new.startOnBoot
            prefs[PER_APP_MODE]   = new.perAppProxyMode.name
            prefs[MIXED_PORT]     = new.mixedPort
            prefs[API_PORT]       = new.apiPort
            prefs[LOG_LEVEL]      = new.logLevel
        }
    }
}
