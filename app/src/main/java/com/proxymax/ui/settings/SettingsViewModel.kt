package com.proxymax.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proxymax.core.CoreType
import com.proxymax.data.model.PerAppMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val autoSelectCore:     Boolean  = true,
    val defaultCore:        CoreType = CoreType.MIHOMO,
    val enableFakeIp:       Boolean  = true,
    val enableIpv6:         Boolean  = false,
    val mixedPort:          Int      = 7890,
    val apiPort:            Int      = 9090,
    val geositeCnDirect:    Boolean  = true,
    val geoipPrivateDirect: Boolean  = true,
    val logLevel:           String   = "info",
    val startOnBoot:        Boolean  = false,
    val perAppMode:         PerAppMode = PerAppMode.GLOBAL,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    private val _ui = MutableStateFlow(SettingsUiState())
    val ui: StateFlow<SettingsUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            dataStore.data.collect { prefs ->
                _ui.value = SettingsUiState(
                    autoSelectCore     = prefs[SettingsKeys.autoSelectCore]     ?: true,
                    defaultCore        = runCatching {
                        CoreType.valueOf(prefs[SettingsKeys.defaultCore] ?: "MIHOMO")
                    }.getOrDefault(CoreType.MIHOMO),
                    enableFakeIp       = prefs[SettingsKeys.enableFakeIp]       ?: true,
                    enableIpv6         = prefs[SettingsKeys.enableIpv6]         ?: false,
                    mixedPort          = prefs[SettingsKeys.mixedPort]          ?: 7890,
                    apiPort            = prefs[SettingsKeys.apiPort]            ?: 9090,
                    geositeCnDirect    = prefs[SettingsKeys.geositeCnDirect]    ?: true,
                    geoipPrivateDirect = prefs[SettingsKeys.geoipPrivateDirect] ?: true,
                    logLevel           = prefs[SettingsKeys.logLevel]           ?: "info",
                    startOnBoot        = prefs[SettingsKeys.startOnBoot]        ?: false,
                    perAppMode         = runCatching {
                        PerAppMode.valueOf(prefs[SettingsKeys.perAppMode] ?: "GLOBAL")
                    }.getOrDefault(PerAppMode.GLOBAL)
                )
            }
        }
    }

    fun set(block: suspend (Preferences.Editor) -> Unit) = viewModelScope.launch {
        dataStore.edit { block(it) }
    }

    fun toggleAutoSelectCore()     = set { it[SettingsKeys.autoSelectCore]     = !_ui.value.autoSelectCore }
    fun setDefaultCore(c: CoreType)= set { it[SettingsKeys.defaultCore]        = c.name }
    fun toggleFakeIp()             = set { it[SettingsKeys.enableFakeIp]       = !_ui.value.enableFakeIp }
    fun toggleIpv6()               = set { it[SettingsKeys.enableIpv6]         = !_ui.value.enableIpv6 }
    fun setMixedPort(p: Int)       = set { it[SettingsKeys.mixedPort]          = p }
    fun setApiPort(p: Int)         = set { it[SettingsKeys.apiPort]            = p }
    fun toggleGeositeCnDirect()    = set { it[SettingsKeys.geositeCnDirect]    = !_ui.value.geositeCnDirect }
    fun toggleGeoipPrivateDirect() = set { it[SettingsKeys.geoipPrivateDirect] = !_ui.value.geoipPrivateDirect }
    fun setLogLevel(l: String)     = set { it[SettingsKeys.logLevel]           = l }
    fun toggleStartOnBoot()        = set { it[SettingsKeys.startOnBoot]        = !_ui.value.startOnBoot }
    fun setPerAppMode(m: PerAppMode) = set { it[SettingsKeys.perAppMode]       = m.name }
}
