package com.proxymax.core

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MihomoEngine @Inject constructor() : CoreEngine {

    override val type = CoreType.MIHOMO

    private val _stats  = MutableStateFlow(TrafficStats())
    private val _logs   = MutableSharedFlow<String>(replay = 200, extraBufferCapacity = 500)

    // ── JNI 声明 ─────────────────────────────────────────────────────────────
    private external fun nativeStart(homeDir: String, config: String, tunFd: Int): Int
    private external fun nativeStop(): Int
    private external fun nativeReload(config: String): Int
    private external fun nativeVersion(): String
    private external fun nativeQueryTraffic(): LongArray   // [upSpeed, downSpeed, totalUp, totalDown, conns]

    override fun isAvailable(): Boolean = runCatching {
        System.loadLibrary("mihomo"); true
    }.getOrDefault(false)

    override fun version(): String = runCatching { nativeVersion() }.getOrDefault("N/A")

    override suspend fun start(config: String, tunFd: Int): Result<Unit> {
        _logs.tryEmit("[Mihomo] Starting core...")
        return runCatching {
            val homeDir = MihomoEngine::class.java.classLoader!!
                .let { android.app.ActivityThread.currentApplication().filesDir.absolutePath + "/mihomo" }
            val ret = nativeStart(homeDir, config, tunFd)
            if (ret != 0) throw RuntimeException("Mihomo start failed: code=$ret")
            _logs.tryEmit("[Mihomo] Core started ✓")
        }
    }

    override suspend fun stop(): Result<Unit> = runCatching {
        nativeStop()
        _logs.tryEmit("[Mihomo] Core stopped")
    }

    override suspend fun reload(config: String): Result<Unit> = runCatching {
        val ret = nativeReload(config)
        if (ret != 0) throw RuntimeException("Mihomo reload failed: code=$ret")
        _logs.tryEmit("[Mihomo] Config reloaded ✓")
    }

    override suspend fun testDelay(proxyName: String, url: String, timeoutMs: Int): Int {
        // 调用 mihomo HTTP API /proxies/{name}/delay
        return runCatching {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(timeoutMs.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()
            val apiUrl = "http://127.0.0.1:9090/proxies/${java.net.URLEncoder.encode(proxyName, "UTF-8")}/delay?timeout=${timeoutMs}&url=${java.net.URLEncoder.encode(url, "UTF-8")}"
            val req = okhttp3.Request.Builder().url(apiUrl).get().build()
            val t0 = System.currentTimeMillis()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) (System.currentTimeMillis() - t0).toInt() else -1
            }
        }.getOrDefault(-1)
    }

    override fun trafficStats(): Flow<TrafficStats> = _stats
    override fun logs(): Flow<String> = _logs

    companion object {
        init { runCatching { System.loadLibrary("mihomo") } }
    }
}
