package com.proxymax.ui.dashboard

import android.app.Application
import android.content.Intent
import android.net.VpnService
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.proxymax.core.*
import com.proxymax.core.stats.StatsCollector
import com.proxymax.data.model.PerAppMode
import com.proxymax.data.repository.ProfileDao
import com.proxymax.service.ProxyVpnService
import com.proxymax.ui.settings.SettingsKeys
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val app:            Application,
    private val coreManager:    CoreManager,
    private val profileDao:     ProfileDao,
    private val statsCollector: StatsCollector,
    private val dataStore:      DataStore<Preferences>
) : AndroidViewModel(app) {

    val state: StateFlow<CoreState> = coreManager.state
    val liveStats: StateFlow<TrafficStats> = statsCollector.stats

    private val _noProfileError = MutableStateFlow(false)
    val noProfileError: StateFlow<Boolean> = _noProfileError.asStateFlow()
    fun clearNoProfileError() { _noProfileError.value = false }

    /** 需要 Activity 弹 VPN 权限对话框时，发送 prepare intent */
    private val _vpnPermissionNeeded = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val vpnPermissionNeeded: SharedFlow<Intent> = _vpnPermissionNeeded.asSharedFlow()

    /** Activity 授权成功后回调此方法 */
    fun onVpnPermissionGranted() = viewModelScope.launch { doStartVpn() }

    init {
        viewModelScope.launch {
            state.collect { s ->
                when (s) {
                    is CoreState.Running -> {
                        val prefs  = dataStore.data.first()
                        val port   = prefs[SettingsKeys.apiPort]   ?: 9090
                        val secret = prefs[SettingsKeys.apiSecret] ?: ""
                        statsCollector.start(
                            scope    = viewModelScope,
                            apiPort  = port,
                            secret   = secret,
                            logLevel = "info"
                        )
                    }
                    is CoreState.Stopped,
                    is CoreState.Error -> statsCollector.stop()
                    else -> {}
                }
            }
        }
    }

    fun toggleVpn() = viewModelScope.launch {
        if (state.value is CoreState.Running || state.value is CoreState.Starting) {
            stopVpn()
        } else {
            // 先检查 VPN 权限
            val prepareIntent = VpnService.prepare(app)
            if (prepareIntent != null) {
                // 需要用户授权，发送事件给 Activity
                _vpnPermissionNeeded.emit(prepareIntent)
            } else {
                // 已授权，直接启动
                doStartVpn()
            }
        }
    }

    private suspend fun doStartVpn() {
        val profile = profileDao.getActiveProfile() ?: run {
            _noProfileError.value = true
            return
        }
        val prefs      = dataStore.data.first()
        val apiPort    = prefs[SettingsKeys.apiPort]    ?: 9090
        val perAppMode = prefs[SettingsKeys.perAppMode] ?: PerAppMode.GLOBAL.name
        val packages   = runCatching {
            com.google.gson.Gson().fromJson(
                prefs[SettingsKeys.perAppPackages] ?: "[]",
                Array<String>::class.java
            ).toList()
        }.getOrDefault(emptyList())

        val core = coreManager.recommendCore(profile.rawConfig)
        val intent = Intent(app, ProxyVpnService::class.java).apply {
            action = ProxyVpnService.ACTION_START
            putExtra(ProxyVpnService.EXTRA_CORE,         core.name)
            putExtra(ProxyVpnService.EXTRA_CONFIG,       profile.rawConfig)
            putExtra(ProxyVpnService.EXTRA_API_PORT,     apiPort)
            putExtra(ProxyVpnService.EXTRA_PER_APP_MODE, perAppMode)
            putStringArrayListExtra(ProxyVpnService.EXTRA_APP_PACKAGES, ArrayList(packages))
        }
        app.startForegroundService(intent)
    }

    fun stopVpn() {
        statsCollector.stop()
        app.startService(
            Intent(app, ProxyVpnService::class.java).setAction(ProxyVpnService.ACTION_STOP)
        )
    }

    fun switchCore(to: CoreType) = viewModelScope.launch {
        val profile = profileDao.getActiveProfile() ?: return@launch
        app.startService(
            Intent(app, ProxyVpnService::class.java).apply {
                action = ProxyVpnService.ACTION_SWITCH
                putExtra(ProxyVpnService.EXTRA_CORE,   to.name)
                putExtra(ProxyVpnService.EXTRA_CONFIG, profile.rawConfig)
            }
        )
    }
}
