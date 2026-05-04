package com.proxymax.ui.proxies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proxymax.core.CoreManager
import com.proxymax.data.model.ProxyNode
import com.proxymax.data.model.ProxyProfile
import com.proxymax.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class ProxiesUiState(
    val profiles:        List<ProxyProfile> = emptyList(),
    val selectedProfile: ProxyProfile?      = null,
    val nodes:           List<ProxyNode>    = emptyList(),
    val isLoading:       Boolean            = false,
    val isTesting:       Boolean            = false,
    val error:           String?            = null,
    // 添加订阅对话框
    val showAddDialog:   Boolean            = false,
    val addDialogName:   String             = "",
    val addDialogUrl:    String             = "",
    val addDialogRaw:    String             = "",
)

@HiltViewModel
class ProxiesViewModel @Inject constructor(
    private val repo:        ProfileRepository,
    private val coreManager: CoreManager
) : ViewModel() {

    private val _ui = MutableStateFlow(ProxiesUiState())
    val ui: StateFlow<ProxiesUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            repo.getAllProfiles().collect { profiles ->
                _ui.update { it.copy(profiles = profiles) }
                // 自动选中第一个 profile
                if (_ui.value.selectedProfile == null && profiles.isNotEmpty()) {
                    selectProfile(profiles.first())
                }
            }
        }
    }

    fun selectProfile(profile: ProxyProfile) {
        _ui.update { it.copy(selectedProfile = profile) }
        viewModelScope.launch {
            repo.getNodesForProfile(profile.id).collect { nodes ->
                _ui.update { it.copy(nodes = nodes) }
            }
        }
    }

    // ── 添加订阅对话框 ─────────────────────────────────────────────────────
    fun showAddDialog()  = _ui.update { it.copy(showAddDialog = true) }
    fun hideAddDialog()  = _ui.update { it.copy(showAddDialog = false, addDialogName = "", addDialogUrl = "", addDialogRaw = "") }
    fun onNameChange(v: String) = _ui.update { it.copy(addDialogName = v) }
    fun onUrlChange(v: String)  = _ui.update { it.copy(addDialogUrl = v) }
    fun onRawChange(v: String)  = _ui.update { it.copy(addDialogRaw = v) }

    fun confirmAddSubscription() = viewModelScope.launch {
        val state = _ui.value
        _ui.update { it.copy(isLoading = true, error = null) }
        hideAddDialog()
        val result = when {
            state.addDialogUrl.isNotBlank() ->
                repo.fetchAndSaveProfile(state.addDialogName.ifBlank { "订阅" }, state.addDialogUrl)
            state.addDialogRaw.isNotBlank() ->
                runCatching { repo.saveRawConfig(state.addDialogName.ifBlank { "手动配置" }, raw = state.addDialogRaw) }
            else -> Result.failure(IllegalArgumentException("请输入 URL 或配置内容"))
        }
        _ui.update { it.copy(
            isLoading = false,
            error = result.exceptionOrNull()?.message
        )}
    }

    // ── 更新订阅 ───────────────────────────────────────────────────────────
    fun refreshProfile(profile: ProxyProfile) = viewModelScope.launch {
        if (profile.url.isBlank()) return@launch
        _ui.update { it.copy(isLoading = true) }
        repo.fetchAndSaveProfile(profile.name, profile.url)
        _ui.update { it.copy(isLoading = false) }
    }

    // ── 批量延迟测试 ───────────────────────────────────────────────────────
    fun testAllLatencies() = viewModelScope.launch {
        _ui.update { it.copy(isTesting = true) }
        val nodes = _ui.value.nodes
        // 并发测试，最多 10 并发
        nodes.chunked(10).forEach { chunk ->
            chunk.map { node ->
                async(Dispatchers.IO) {
                    val latency = coreManager.testDelay(node.name)
                    repo.updateLatency(node.id, latency)
                }
            }.awaitAll()
        }
        _ui.update { it.copy(isTesting = false) }
    }

    fun testSingleLatency(node: ProxyNode) = viewModelScope.launch(Dispatchers.IO) {
        val latency = coreManager.testDelay(node.name)
        repo.updateLatency(node.id, latency)
    }

    fun deleteProfile(profile: ProxyProfile) = viewModelScope.launch {
        repo.deleteProfile(profile)
    }
}
