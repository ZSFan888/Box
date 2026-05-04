package com.proxymax.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XrayEngine @Inject constructor() : CoreEngine {

    override val type = CoreType.XRAY

    private val _stats = MutableStateFlow(TrafficStats())
    private val _logs  = MutableSharedFlow<String>(replay = 200, extraBufferCapacity = 500)

    // ── JNI 声明 (libv2ray / libxray) ────────────────────────────────────────
    private external fun nativeInitV2Env(assetsPath: String)
    private external fun nativeStartV2Ray(config: String): Long          // returns handler
    private external fun nativeStopV2Ray()
    private external fun nativeVersion(): String
    private external fun nativeQueryStats(tag: String, direct: String): Long

    override fun isAvailable() = runCatching {
        System.loadLibrary("v2ray"); true
    }.getOrDefault(false)

    override fun version() = runCatching { nativeVersion() }.getOrDefault("N/A")

    override suspend fun start(config: String, tunFd: Int): Result<Unit> = runCatching {
        _logs.tryEmit("[Xray] Starting core...")
        nativeStartV2Ray(config)
        _logs.tryEmit("[Xray] Core started ✓")
    }

    override suspend fun stop(): Result<Unit> = runCatching {
        nativeStopV2Ray()
        _logs.tryEmit("[Xray] Core stopped")
    }

    override suspend fun reload(config: String): Result<Unit> = runCatching {
        nativeStopV2Ray()
        nativeStartV2Ray(config)
        _logs.tryEmit("[Xray] Config reloaded (restarted) ✓")
    }

    override suspend fun testDelay(proxyName: String, url: String, timeoutMs: Int): Int {
        return runCatching {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(timeoutMs.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .proxy(java.net.Proxy(java.net.Proxy.Type.SOCKS, java.net.InetSocketAddress("127.0.0.1", 10808)))
                .build()
            val req = okhttp3.Request.Builder().url(url).head().build()
            val t0 = System.currentTimeMillis()
            client.newCall(req).execute().use { (System.currentTimeMillis() - t0).toInt() }
        }.getOrDefault(-1)
    }

    override fun trafficStats(): Flow<TrafficStats> = _stats
    override fun logs(): Flow<String> = _logs

    companion object {
        init { runCatching { System.loadLibrary("v2ray") } }
    }
}
