package com.proxymax.core

import android.util.Log
import com.proxymax.core.stats.ClashApiClient
import com.proxymax.core.stats.StatsCollector
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CoreManager"

@Singleton
class CoreManager @Inject constructor(
    private val mihomoEngine:  MihomoEngine,
    private val xrayEngine:    XrayEngine,
    private val singboxEngine: SingboxEngine,
    private val statsCollector: StatsCollector,
    private val apiClient:      ClashApiClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<CoreState>(CoreState.Idle)
    val state: StateFlow<CoreState> = _state.asStateFlow()

    private var currentEngine: CoreEngine? = null
    private var currentTunFd:  Int = -1
    private var currentApiPort: Int = 9090

    val availableCores: List<CoreType>
        get() = listOf(mihomoEngine, xrayEngine, singboxEngine)
            .filter { it.isAvailable() }.map { it.type }

    fun engineFor(type: CoreType): CoreEngine = when (type) {
        CoreType.MIHOMO  -> mihomoEngine
        CoreType.XRAY    -> xrayEngine
        CoreType.SINGBOX -> singboxEngine
    }

    // ── 启动 ──────────────────────────────────────────────────────────────
    suspend fun startCore(
        type:    CoreType,
        config:  String,
        tunFd:   Int,
        apiPort: Int = 9090,
        secret:  String = ""
    ): Result<Unit> {
        Log.d(TAG, "startCore: $type")
        _state.value = CoreState.Starting
        val engine = engineFor(type)

        return engine.start(config, tunFd).onSuccess {
            currentEngine  = engine
            currentTunFd   = tunFd
            currentApiPort = apiPort
            _state.value   = CoreState.Running(type, TrafficStats())

            // 启动流量统计（等 500ms 让 API 就绪）
            delay(500)
            statsCollector.start(scope, apiPort, secret)

            // 转发 stats → state
            scope.launch {
                statsCollector.stats.collect { s ->
                    (_state.value as? CoreState.Running)?.let { r ->
                        _state.value = r.copy(stats = s)
                    }
                }
            }
        }.onFailure {
            _state.value = CoreState.Error(type, it.message ?: "Unknown error")
        }
    }

    // ── 热切换（旧核先停，新核接管同一 TUN fd）────────────────────────────
    suspend fun switchCore(
        to:        CoreType,
        newConfig: String,
        tunFd:     Int = currentTunFd,
        apiPort:   Int = currentApiPort
    ): Result<Unit> {
        val from = currentEngine?.type
            ?: return Result.failure(IllegalStateException("No core running"))
        if (from == to) return Result.success(Unit)

        Log.d(TAG, "switchCore: $from → $to")
        _state.value = CoreState.Switching(from, to)

        statsCollector.stop()
        val newEngine = engineFor(to)
        val oldEngine = currentEngine

        return newEngine.start(newConfig, tunFd).onSuccess {
            oldEngine?.stop()
            currentEngine  = newEngine
            currentTunFd   = tunFd
            currentApiPort = apiPort
            _state.value   = CoreState.Running(to, TrafficStats())

            delay(500)
            statsCollector.start(scope, apiPort)
            Log.d(TAG, "switchCore: ✓ $to")
        }.onFailure {
            // 回滚
            _state.value = CoreState.Running(from, TrafficStats())
            statsCollector.start(scope, currentApiPort)
            Log.e(TAG, "switchCore failed, rolled back to $from", it)
        }
    }

    // ── 停止 ──────────────────────────────────────────────────────────────
    suspend fun stopCurrent(): Result<Unit> {
        statsCollector.stop()
        val engine = currentEngine ?: return Result.success(Unit)
        return engine.stop().also {
            currentEngine = null
            _state.value  = CoreState.Stopped
        }
    }

    // ── 智能推荐内核 ───────────────────────────────────────────────────────
    fun recommendCore(config: String): CoreType {
        val trimmed = config.trim()
        return when {
            trimmed.startsWith("proxies:") || trimmed.contains("proxy-groups:") ->
                if (mihomoEngine.isAvailable()) CoreType.MIHOMO else CoreType.SINGBOX
            trimmed.contains(""inbounds"") ->
                if (xrayEngine.isAvailable()) CoreType.XRAY else CoreType.SINGBOX
            trimmed.contains("hysteria2") || trimmed.contains("tuic") ->
                if (singboxEngine.isAvailable()) CoreType.SINGBOX else CoreType.MIHOMO
            else -> availableCores.firstOrNull() ?: CoreType.MIHOMO
        }
    }

    // ── 代理/延迟测试（转发到 API）────────────────────────────────────────
    suspend fun testDelay(
        proxyName: String,
        url:       String = "https://www.gstatic.com/generate_204"
    ): Int = apiClient.testDelay(proxyName, url)

    suspend fun selectProxy(group: String, proxy: String) =
        apiClient.selectProxy(group, proxy)

    suspend fun getProxies() = apiClient.getProxies()

    fun logs(): Flow<String> = statsCollector.logs
}
