package com.proxymax.ui.perrapp

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.proxymax.data.model.PerAppMode
import com.proxymax.ui.settings.SettingsViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class PerAppUiState(
    val apps:      List<AppInfo>    = emptyList(),
    val selected:  Set<String>      = emptySet(),
    val mode:      PerAppMode       = PerAppMode.GLOBAL,
    val query:     String           = "",
    val isLoading: Boolean          = false
)

@HiltViewModel
class PerAppViewModel @Inject constructor(
    private val app: Application
) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(PerAppUiState(isLoading = true))
    val ui: StateFlow<PerAppUiState> = _ui.asStateFlow()

    init { loadApps() }

    private fun loadApps() = viewModelScope.launch(Dispatchers.IO) {
        val pm    = app.packageManager
        val infos = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != app.packageName }
            .map { info ->
                AppInfo(
                    packageName = info.packageName,
                    label       = pm.getApplicationLabel(info).toString(),
                    isSystem    = (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
            .sortedWith(compareBy({ it.isSystem }, { it.label.lowercase() }))

        _ui.update { it.copy(apps = infos, isLoading = false) }
    }

    fun toggleApp(pkg: String) = _ui.update { state ->
        val newSelected = if (pkg in state.selected) state.selected - pkg
                          else state.selected + pkg
        state.copy(selected = newSelected)
    }

    fun setMode(mode: PerAppMode) = _ui.update { it.copy(mode = mode) }
    fun onQueryChange(q: String)  = _ui.update { it.copy(query = q) }

    fun selectAll() = _ui.update { state ->
        state.copy(selected = state.apps.map { it.packageName }.toSet())
    }
    fun clearAll() = _ui.update { it.copy(selected = emptySet()) }

    /** 返回保存后的包名集合，供 VpnService 使用 */
    fun getSelectedPackages(): Set<String> = _ui.value.selected
    fun getCurrentMode(): PerAppMode       = _ui.value.mode
}
