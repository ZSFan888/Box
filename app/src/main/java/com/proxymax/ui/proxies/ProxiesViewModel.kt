package com.proxymax.ui.proxies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proxymax.core.CoreManager
import com.proxymax.core.CoreState
import com.proxymax.core.stats.ProxyInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class ProxiesUiState(
    val groups:        Map<String, List<ProxyInfo>> = emptyMap(),
    val selectedGroup: String                       = "proxy",
    val selectedProxy: String                       = "",
    val isTesting:     Boolean                      = false,
    val isLoading:     Boolean                      = true
)

@HiltViewModel
class ProxiesViewModel @Inject constructor(
    private val coreManager: CoreManager
) : ViewModel() {

    private val _ui = MutableStateFlow(ProxiesUiState())
    val ui: StateFlow<ProxiesUiState> = _ui.asStateFlow()

    /** 当前策略组已排序的节点列表（延迟从小到大，-1 排最后） */
    val sortedProxies: StateFlow<List<ProxyInfo>> = _ui.map { state ->
        val list = state.groups[state.selectedGroup] ?: emptyList()
        list.sortedWith(
            compareBy { proxy ->
                val d = proxy.history.lastOrNull() ?: -1
                if (d < 0) Int.MAX_VALUE else d
            }
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            coreManager.state.collect { state ->
                if (state is CoreState.Running) {
                    loadProxies()
                }
            }
        }
    }

    fun loadProxies() = viewModelScope.launch {
        _ui.update { it.copy(isLoading = true) }
        val raw = coreManager.getProxies()

        // 分离策略组（Selector/URLTest/Fallback）和普通节点
        val groups = raw.entries
            .filter { (_, info) -> info.type in listOf("Selector","URLTest","Fallback","LoadBalance","Relay") }
            .associate { (name, info) ->
                name to (info.history.mapIndexed { i, _ ->
                    ProxyInfo(name = info.now.ifEmpty { name }, type = info.type, now = info.now, history = info.history)
                })
            }

        val allNodes = raw.entries
            .filter { (_, info) -> info.type !in listOf("Selector","URLTest","Fallback","LoadBalance","Relay","Direct","Reject","DNS") }
            .map { (name, info) -> info }

        val groupsWithNodes = groups.toMutableMap().apply {
            keys.forEach { g ->
                val nodeNames = raw[g]?.let { info ->
                    // Clash API 会在 all 字段里放所有节点名
                    listOf(info.now)
                } ?: emptyList()
                val nodes = nodeNames.mapNotNull { raw[it] }
                this[g] = nodes.ifEmpty { allNodes }
            }
            if (isEmpty()) this["全部节点"] = allNodes
        }

        val firstGroup = groupsWithNodes.keys.firstOrNull() ?: "全部节点"
        _ui.update { it.copy(groups = groupsWithNodes, selectedGroup = firstGroup, isLoading = false) }
    }

    fun selectGroup(group: String) = _ui.update { it.copy(selectedGroup = group) }

    fun selectProxy(proxy: String) = viewModelScope.launch {
        _ui.update { it.copy(selectedProxy = proxy) }
        coreManager.selectProxy(_ui.value.selectedGroup, proxy)
    }

    /** 批量测速（并发 10） */
    fun testAllDelays() = viewModelScope.launch {
        _ui.update { it.copy(isTesting = true) }
        val proxies = sortedProxies.value
        proxies.chunked(10).forEach { chunk ->
            chunk.map { p ->
                async { coreManager.testDelay(p.name) }
            }.awaitAll()
        }
        loadProxies()
        _ui.update { it.copy(isTesting = false) }
    }

    fun testSingleDelay(name: String) = viewModelScope.launch {
        coreManager.testDelay(name)
        loadProxies()
    }
}
