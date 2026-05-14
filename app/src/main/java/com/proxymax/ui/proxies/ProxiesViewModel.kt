package com.proxymax.ui.proxies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proxymax.core.CoreManager
import com.proxymax.core.CoreState
import com.proxymax.core.stats.ProxyInfo
import com.proxymax.data.model.ProxyProfile
import com.proxymax.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class ProxiesUiState(
    val groups:        Map<String, List<ProxyInfo>> = emptyMap(),
    val selectedGroup: String                       = "",
    val selectedProxy: String                       = "",
    val isTesting:     Boolean                      = false,
    val isLoading:     Boolean                      = false,
    val errorMsg:      String?                      = null
)

@HiltViewModel
class ProxiesViewModel @Inject constructor(
    private val coreManager: CoreManager,
    private val profileRepo: ProfileRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(ProxiesUiState())
    val ui: StateFlow<ProxiesUiState> = _ui.asStateFlow()

    /** 所有订阅 */
    val profiles: StateFlow<List<ProxyProfile>> = profileRepo.getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 当前策略组已排序节点 */
    val sortedProxies: StateFlow<List<ProxyInfo>> = _ui.map { state ->
        val list = state.groups[state.selectedGroup] ?: emptyList()
        list.sortedWith(compareBy { p: ProxyInfo ->
            val d = p.history.lastOrNull() ?: -1
            if (d < 0) Int.MAX_VALUE else d
        })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // 核心运行时自动加载节点
        viewModelScope.launch {
            coreManager.state.collect { state ->
                if (state is CoreState.Running) loadProxies()
            }
        }
    }

    // ── 订阅管理 ──────────────────────────────────────────────────────────

    fun addSubscriptionByUrl(name: String, url: String) = viewModelScope.launch {
        _ui.update { it.copy(isLoading = true) }
        profileRepo.fetchAndSaveProfile(name, url)
            .onSuccess { profile ->
                // 若是第一条订阅，自动激活
                if (profiles.value.isEmpty()) {
                    profileRepo.setActiveProfile(profile.id)
                }
                _ui.update { it.copy(isLoading = false) }
            }
            .onFailure { e ->
                _ui.update { it.copy(isLoading = false, errorMsg = "添加失败：${e.message}") }
            }
    }

    fun addSubscriptionByRaw(name: String, raw: String) = viewModelScope.launch {
        _ui.update { it.copy(isLoading = true) }
        runCatching { profileRepo.saveRawConfig(name, raw = raw) }
            .onSuccess { profile ->
                if (profiles.value.isEmpty()) profileRepo.setActiveProfile(profile.id)
                _ui.update { it.copy(isLoading = false) }
            }
            .onFailure { e ->
                _ui.update { it.copy(isLoading = false, errorMsg = "保存失败：${e.message}") }
            }
    }

    fun setActiveProfile(profileId: Int) = viewModelScope.launch {
        profileRepo.setActiveProfile(profileId)
        loadProxies()
    }

    fun deleteProfile(profile: ProxyProfile) = viewModelScope.launch {
        profileRepo.deleteProfile(profile)
    }

    fun refreshProfile(profile: ProxyProfile) = viewModelScope.launch {
        if (profile.url.isBlank()) return@launch
        _ui.update { it.copy(isLoading = true) }
        profileRepo.fetchAndSaveProfile(profile.name, profile.url)
            .onSuccess { _ui.update { it.copy(isLoading = false) } }
            .onFailure { e -> _ui.update { it.copy(isLoading = false, errorMsg = "更新失败：${e.message}") } }
    }

    fun clearError() = _ui.update { it.copy(errorMsg = null) }

    // ── 节点操作 ──────────────────────────────────────────────────────────

    fun loadProxies() = viewModelScope.launch {
        _ui.update { it.copy(isLoading = true) }
        val raw = coreManager.getProxies()

        val groups = raw.entries
            .filter { (_, v) -> v.type in listOf("Selector","URLTest","Fallback","LoadBalance","Relay") }
            .associate { (name, info) ->
                val nodeNames = raw[name]?.now?.let { listOf(it) } ?: emptyList()
                name to nodeNames.mapNotNull { raw[it] }
            }

        val allNodes = raw.entries
            .filter { (_, v) -> v.type !in listOf("Selector","URLTest","Fallback","LoadBalance","Relay","Direct","Reject","DNS") }
            .map { it.value }

        val final = groups.toMutableMap().apply {
            if (isEmpty()) this["全部节点"] = allNodes
            else forEach { (k, v) -> if (v.isEmpty()) this[k] = allNodes }
        }

        val firstGroup = final.keys.firstOrNull() ?: ""
        _ui.update { it.copy(groups = final, selectedGroup = firstGroup, isLoading = false) }
    }

    fun selectGroup(group: String) = _ui.update { it.copy(selectedGroup = group) }

    fun selectProxy(proxy: String) = viewModelScope.launch {
        _ui.update { it.copy(selectedProxy = proxy) }
        coreManager.selectProxy(_ui.value.selectedGroup, proxy)
    }

    fun testAllDelays() = viewModelScope.launch {
        _ui.update { it.copy(isTesting = true) }
        sortedProxies.value.chunked(10).forEach { chunk ->
            chunk.map { p -> async { coreManager.testDelay(p.name) } }.awaitAll()
        }
        loadProxies()
        _ui.update { it.copy(isTesting = false) }
    }

    fun testSingleDelay(name: String) = viewModelScope.launch {
        coreManager.testDelay(name)
        loadProxies()
    }
}
