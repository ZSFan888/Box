package com.proxymax.core

import com.proxymax.core.stats.ClashApiClient
import com.proxymax.core.stats.ProxyInfo
import com.proxymax.core.stats.StatsCollector
import com.proxymax.core.stats.TrafficData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoreManager @Inject constructor(
    private val singboxEngine: SingboxEngine,
    private val statsCollector: StatsCollector,
    private val apiClient: ClashApiClient
) {
    private val _state = MutableStateFlow<CoreState>(CoreState.Idle)
    val state: StateFlow<CoreState> = _state.asStateFlow()

    suspend fun startCore(
        coreType: CoreType,
        config: String,
        tunFd: Int,
        apiPort: Int = 9090,
        secret: String = ""
    ): Result<Unit> {
        _state.value = CoreState.Starting
        apiClient.configure(apiPort, secret)
        return if (singboxEngine.isAvailable() && tunFd >= 0) {
            singboxEngine.start(config, tunFd)
        } else {
            // 无 .so 或未获得 VPN 授权时，直接标记为运行中（仅代理模式）
            Result.success(Unit)
        }.also { result ->
            _state.value = if (result.isSuccess)
                CoreState.Running(coreType, TrafficStats())
            else
                CoreState.Error(coreType, result.exceptionOrNull()?.message ?: "Unknown error")
        }
    }

    suspend fun stopCurrent(): Result<Unit> {
        return singboxEngine.stop().also {
            _state.value = CoreState.Stopped
        }
    }

    suspend fun switchCore(
        toType: CoreType,
        config: String,
        tunFd: Int = -1,
        apiPort: Int = 9090,
        secret: String = ""
    ): Result<Unit> {
        val from = (_state.value as? CoreState.Running)?.core ?: toType
        _state.value = CoreState.Switching(from, toType)
        singboxEngine.stop()
        return startCore(toType, config, tunFd, apiPort, secret)
    }

    suspend fun getProxies(): Map<String, ProxyInfo> = apiClient.getProxies()

    suspend fun testDelay(name: String): Int = apiClient.testDelay(name)

    suspend fun selectProxy(group: String, proxy: String): Boolean =
        apiClient.selectProxy(group, proxy)

    fun logs(): Flow<String> = singboxEngine.logs()

    fun trafficFlow(): Flow<TrafficData> = apiClient.trafficFlow()

    /** 根据配置内容推荐最合适的内核 */
    fun recommendCore(rawConfig: String): CoreType = when {
        rawConfig.trimStart().startsWith("{") &&
            rawConfig.contains("\"outbounds\"") -> CoreType.SINGBOX
        rawConfig.contains("\"inbounds\"") -> CoreType.XRAY
        else -> CoreType.MIHOMO
    }
}
