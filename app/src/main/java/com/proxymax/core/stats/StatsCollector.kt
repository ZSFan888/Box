package com.proxymax.core.stats

import android.util.Log
import com.proxymax.core.TrafficStats
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "StatsCollector"

/**
 * 通过 Clash HTTP API SSE 流持续收集流量/连接统计
 * 由 CoreManager 在核心启动成功后调用 start()
 */
@Singleton
class StatsCollector @Inject constructor(
    private val api: ClashApiClient
) {
    private val _stats = MutableStateFlow(TrafficStats())
    val stats: StateFlow<TrafficStats> = _stats.asStateFlow()

    private var job: Job? = null

    fun start(scope: CoroutineScope, apiPort: Int, secret: String = "", logLevel: String = "info") {
        api.configure(apiPort, secret)
        job?.cancel()
        job = scope.launch {
            // 并发收集流量 + 连接数
            launch { collectTraffic() }
            launch { collectConnections() }
            launch { collectLogs(logLevel) }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _stats.value = TrafficStats()
    }

    private suspend fun collectTraffic() {
        // 等待核心 HTTP API 就绪（最多 5 秒重试）
        repeat(10) { attempt ->
            runCatching {
                api.trafficFlow()
                    .catch { e -> Log.w(TAG, "Traffic SSE error: ${e.message}, retrying…") }
                    .collect { t ->
                        _stats.update { it.copy(
                            uploadSpeed   = t.uploadSpeed,
                            downloadSpeed = t.downloadSpeed
                        )}
                    }
            }.onFailure {
                delay(500L * (attempt + 1))
            }
        }
    }

    private suspend fun collectConnections() {
        while (true) {
            runCatching {
                val conn = api.getConnections()
                _stats.update { it.copy(
                    connections   = conn.connections,
                    totalUpload   = conn.uploadTotal,
                    totalDownload = conn.downloadTotal
                )}
            }
            delay(2000)
        }
    }

    // 日志转发到外部 SharedFlow（由 CoreManager 暴露给 UI）
    private val _logs = MutableSharedFlow<String>(replay = 300, extraBufferCapacity = 1000)
    val logs: SharedFlow<String> = _logs.asSharedFlow()

    private suspend fun collectLogs(level: String) {
        repeat(10) { attempt ->
            runCatching {
                api.logsFlow(level)
                    .catch { e -> Log.w(TAG, "Logs SSE error: ${e.message}") }
                    .collect { line -> _logs.tryEmit(line) }
            }.onFailure { delay(500L * (attempt + 1)) }
        }
    }
}
