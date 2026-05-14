package com.proxymax.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Singleton
class SingboxEngine @Inject constructor() : CoreEngine {

    override val type = CoreType.SINGBOX

    private val _stats = MutableStateFlow(TrafficStats())
    private val _logs  = MutableSharedFlow<String>(replay = 200, extraBufferCapacity = 500)

    // ── JNI 声明 (libsingbox) ────────────────────────────────────────────────
    private external fun nativeStart(config: String, tunFd: Int): Int
    private external fun nativeStop(): Int
    private external fun nativeVersion(): String
    private external fun nativeSetLogCallback(cb: (String) -> Unit)

    override fun isAvailable() = runCatching {
        System.loadLibrary("singbox"); true
    }.getOrDefault(false)

    override fun version() = runCatching { nativeVersion() }.getOrDefault("N/A")

    override suspend fun start(config: String, tunFd: Int): Result<Unit> = runCatching {
        _logs.tryEmit("[sing-box] Starting core...")
        nativeSetLogCallback { line -> _logs.tryEmit(line) }
        val ret = nativeStart(config, tunFd)
        if (ret != 0) throw RuntimeException("sing-box start failed: code=$ret")
        _logs.tryEmit("[sing-box] Core started ✓")
    }

    override suspend fun stop(): Result<Unit> = runCatching {
        nativeStop()
        _logs.tryEmit("[sing-box] Core stopped")
    }

    override suspend fun reload(config: String): Result<Unit> = runCatching {
        // sing-box supports hot reload via SIGHUP or API
        nativeStop(); nativeStart(config, -1)
        _logs.tryEmit("[sing-box] Config reloaded ✓")
    }

    override suspend fun testDelay(proxyName: String, url: String, timeoutMs: Int): Int {
        return runCatching {
            val client = OkHttpClient.Builder()
                .connectTimeout(timeoutMs.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()
            val apiUrl = "http://127.0.0.1:9090/proxies/${java.net.URLEncoder.encode(proxyName, "UTF-8")}/delay?timeout=${timeoutMs}&url=${java.net.URLEncoder.encode(url, "UTF-8")}"
            val req = Request.Builder().url(apiUrl).get().build()
            val t0 = System.currentTimeMillis()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) (System.currentTimeMillis() - t0).toInt() else -1
            }
        }.getOrDefault(-1)
    }

    override fun trafficStats(): Flow<TrafficStats> = _stats
    override fun logs(): Flow<String> = _logs

    companion object {
        init { runCatching { System.loadLibrary("singbox") } }
    }
}
