package com.proxymax.ui.settings

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.proxymax.core.CoreType
import com.proxymax.data.model.PerAppMode
import com.proxymax.data.repository.ProfileRepository
import com.proxymax.workers.AutoUpdateWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val autoSelectCore:     Boolean    = true,
    val defaultCore:        CoreType   = CoreType.MIHOMO,
    val enableFakeIp:       Boolean    = true,
    val enableIpv6:         Boolean    = false,
    val mixedPort:          Int        = 7890,
    val apiPort:            Int        = 9090,
    val geositeCnDirect:    Boolean    = true,
    val geoipPrivateDirect: Boolean    = true,
    val logLevel:           String     = "info",
    val startOnBoot:        Boolean    = false,
    val perAppMode:         PerAppMode = PerAppMode.GLOBAL,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val app:       Application,
    private val dataStore: DataStore<Preferences>,
    private val profileRepo: ProfileRepository
) : AndroidViewModel(app) {

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

    fun set(block: suspend (MutablePreferences) -> Unit) = viewModelScope.launch {
        dataStore.edit { block(it) }
    }

    fun toggleAutoSelectCore()       = set { it[SettingsKeys.autoSelectCore]     = !_ui.value.autoSelectCore }
    fun setDefaultCore(c: CoreType)  = set { it[SettingsKeys.defaultCore]        = c.name }
    fun toggleFakeIp()               = set { it[SettingsKeys.enableFakeIp]       = !_ui.value.enableFakeIp }
    fun toggleIpv6()                 = set { it[SettingsKeys.enableIpv6]         = !_ui.value.enableIpv6 }
    fun setMixedPort(p: Int)         = set { it[SettingsKeys.mixedPort]          = p }
    fun setApiPort(p: Int)           = set { it[SettingsKeys.apiPort]            = p }
    fun toggleGeositeCnDirect()      = set { it[SettingsKeys.geositeCnDirect]    = !_ui.value.geositeCnDirect }
    fun toggleGeoipPrivateDirect()   = set { it[SettingsKeys.geoipPrivateDirect] = !_ui.value.geoipPrivateDirect }
    fun setLogLevel(l: String)       = set { it[SettingsKeys.logLevel]           = l }
    fun setPerAppMode(m: PerAppMode) = set { it[SettingsKeys.perAppMode]         = m.name }

    /**
     * 开机自启：写入 DataStore + 同步触发 AutoUpdateWorker 调度（开机自启同时代表用户
     * 愿意保持长期运行，顺带确保 Worker 已注册）
     */
    fun toggleStartOnBoot() = viewModelScope.launch {
        val next = !_ui.value.startOnBoot
        dataStore.edit { it[SettingsKeys.startOnBoot] = next }
        if (next) AutoUpdateWorker.schedule(app) else syncAutoUpdateWorker()
    }

    /**
     * 设置单个订阅的自动更新开关，并同步 Worker 状态
     */
    fun setProfileAutoUpdate(profileId: Int, enable: Boolean) = viewModelScope.launch {
        val profiles = profileRepo.getAllProfiles().first()
        val profile  = profiles.find { it.id == profileId } ?: return@launch
        profileRepo.updateProfile(profile.copy(autoUpdate = enable))
        syncAutoUpdateWorker()
    }

    /** 根据 DB 中是否还有需要自动更新的订阅，决定 Worker 启停 */
    private suspend fun syncAutoUpdateWorker() {
        val hasAny = profileRepo.getAllProfiles().first()
            .any { it.autoUpdate && it.url.isNotBlank() }
        if (hasAny) AutoUpdateWorker.schedule(app)
        else        AutoUpdateWorker.cancel(app)
    }

    /** 供 ProfileListSheet 调用：刷新单条订阅（手动触发） */
    fun refreshProfile(profile: com.proxymax.data.model.ProxyProfile) = viewModelScope.launch {
        profileRepo.fetchAndSaveProfile(profile.name, profile.url)
    }
}
