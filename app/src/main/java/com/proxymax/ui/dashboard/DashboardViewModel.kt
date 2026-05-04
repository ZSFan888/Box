package com.proxymax.ui.dashboard

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.proxymax.core.*
import com.proxymax.data.model.PerAppMode
import com.proxymax.data.repository.ProfileDao
import com.proxymax.service.ProxyVpnService
import com.proxymax.ui.settings.SettingsViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val app:         Application,
    private val coreManager: CoreManager,
    private val profileDao:  ProfileDao
) : AndroidViewModel(app) {

    val state: StateFlow<CoreState> = coreManager.state

    // 代理节点列表（从 Clash API 获取，用于策略组显示）
    private val _proxies = MutableStateFlow<Map<String, com.proxymax.core.stats.ProxyInfo>>(emptyMap())
    val proxies: StateFlow<Map<String, com.proxymax.core.stats.ProxyInfo>> = _proxies.asStateFlow()

    init {
        // 运行中时自动拉取节点列表
        viewModelScope.launch {
            state.collect { s ->
                if (s is CoreState.Running) {
                    _proxies.value = coreManager.getProxies()
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
            // 没有激活配置 → 提示用户去添加订阅
            return@launch
        }
        val core = coreManager.recommendCore(profile.rawConfig)
        val intent = Intent(app, ProxyVpnService::class.java).apply {
            action = ProxyVpnService.ACTION_START
            putExtra(ProxyVpnService.EXTRA_CORE,     core.name)
            putExtra(ProxyVpnService.EXTRA_CONFIG,   profile.rawConfig)
            putExtra(ProxyVpnService.EXTRA_API_PORT, 9090)
            // PerApp 模式从 DataStore 读（此处默认全局）
            putExtra(ProxyVpnService.EXTRA_PER_APP_MODE, PerAppMode.GLOBAL.name)
        }
        app.startForegroundService(intent)
    }

    fun stopVpn() {
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

    fun selectProxy(group: String, proxy: String) = viewModelScope.launch {
        coreManager.selectProxy(group, proxy)
        // 刷新节点列表
        _proxies.value = coreManager.getProxies()
    }
}
