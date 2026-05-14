package com.proxymax.ui.dashboard

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.proxymax.core.*
import com.proxymax.core.stats.StatsCollector
import com.proxymax.data.model.PerAppMode
import com.proxymax.data.repository.ProfileDao
import com.proxymax.service.ProxyVpnService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val app:           Application,
    private val coreManager:   CoreManager,
    private val profileDao:    ProfileDao,
    private val statsCollector: StatsCollector
) : AndroidViewModel(app) {

    val state: StateFlow<CoreState> = coreManager.state

    // ── 实时流量（来自 StatsCollector，覆盖 CoreState 里的静态 stats）──────
    val liveStats: StateFlow<TrafficStats> = statsCollector.stats

    private val _noProfileError = MutableStateFlow(false)
    val noProfileError: StateFlow<Boolean> = _noProfileError.asStateFlow()
    fun clearNoProfileError() { _noProfileError.value = false }

    init {
        // 核心启动成功后，启动 StatsCollector
        viewModelScope.launch {
            state.collect { s ->
                when (s) {
                    is CoreState.Running -> {
                        statsCollector.start(
                            scope    = viewModelScope,
                            apiPort  = 9090,
                            secret   = "",
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
            startVpn()
        }
    }

    private fun startVpn() = viewModelScope.launch {
        val profile = profileDao.getActiveProfile() ?: run {
            _noProfileError.value = true
            return@launch
        }
        val core = coreManager.recommendCore(profile.rawConfig)
        val intent = Intent(app, ProxyVpnService::class.java).apply {
            action = ProxyVpnService.ACTION_START
            putExtra(ProxyVpnService.EXTRA_CORE,         core.name)
            putExtra(ProxyVpnService.EXTRA_CONFIG,       profile.rawConfig)
            putExtra(ProxyVpnService.EXTRA_API_PORT,     9090)
            putExtra(ProxyVpnService.EXTRA_PER_APP_MODE, PerAppMode.GLOBAL.name)
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
