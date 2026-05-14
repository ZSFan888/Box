package com.proxymax.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * sing-box v1.13.11 内核封装
 *
 * 接入方式：CI 从官方 SFA APK 解包 lib/{abi}/libbox.so 放入 jniLibs/
 * 库名：libbox.so  →  System.loadLibrary("box")
 *
 * gomobile 生成的 JNI 导出符号格式：
 *   Java_com_proxymax_core_SingboxEngine_nativeXxx
 * 因此 JNI 方法必须在本类中声明（包名 + 类名与符号对应）。
 *
 * 当 .so 不存在时自动降级为 HTTP proxy-only 模式，VPN 仍可用，
 * 只是流量不经过 TUN 而是走系统级代理（适合调试阶段）。
 */
@Singleton
class SingboxEngine @Inject constructor() : CoreEngine {

    override val type = CoreType.SINGBOX

    private val _stats = MutableStateFlow(TrafficStats())
    private val _logs  = MutableSharedFlow<String>(replay = 200, extraBufferCapacity = 500)

    // ── JNI 声明 — 符号由 gomobile 从 Go 侧生成 ──────────────────────────
    private external fun nativeStart(config: String, tunFd: Int): Int
    private external fun nativeStop(): Int
    private external fun nativeReload(config: String): Int
    private external fun nativeVersion(): String

    // ── 库加载 ─────────────────────────────────────────────────────────────
    private val _available: Boolean by lazy {
        runCatching {
            System.loadLibrary("box")   // libbox.so
            true
        }.getOrElse {
            _logs.tryEmit("[sing-box] libbox.so not found: ${it.message}")
            false
        }
    }

    override fun isAvailable() = _available

    override fun version(): String = if (_available)
        runCatching { nativeVersion() }.getOrDefault("v1.13.11")
    else "v1.13.11 (unavailable)"

    // ── 启动 ───────────────────────────────────────────────────────────────
    override suspend fun start(config: String, tunFd: Int): Result<Unit> = runCatching {
        if (!isAvailable()) {
            _logs.tryEmit("[sing-box] libbox.so not loaded — HTTP proxy-only mode")
            return@runCatching
        }
        if (tunFd < 0) {
            _logs.tryEmit("[sing-box] No TUN fd — HTTP proxy-only mode")
            return@runCatching
        }
        _logs.tryEmit("[sing-box] Starting core, TUN fd=$tunFd …")
        val ret = nativeStart(config, tunFd)
        if (ret != 0) throw RuntimeException("libbox nativeStart failed: code=$ret")
        _logs.tryEmit("[sing-box] Core started ✓  (libbox v${nativeVersion()})")
    }

    // ── 停止 ───────────────────────────────────────────────────────────────
    override suspend fun stop(): Result<Unit> = runCatching {
        if (_available) nativeStop()
        _logs.tryEmit("[sing-box] Core stopped")
    }

    // ── 热重载 ─────────────────────────────────────────────────────────────
    override suspend fun reload(config: String): Result<Unit> = runCatching {
        if (!_available) return@runCatching
        val ret = nativeReload(config)
        if (ret != 0) throw RuntimeException("libbox nativeReload failed: code=$ret")
        _logs.tryEmit("[sing-box] Config reloaded ✓")
    }

    // ── 延迟测试（通过 Clash 兼容 API）──────────────────────────────────────
    override suspend fun testDelay(proxyName: String, url: String, timeoutMs: Int): Int =
        runCatching {
            val client = OkHttpClient.Builder()
                .connectTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                .build()
            val encoded = URLEncoder.encode(proxyName, "UTF-8")
            val urlEncoded = URLEncoder.encode(url, "UTF-8")
            val apiUrl = "http://127.0.0.1:9090/proxies/$encoded/delay?timeout=$timeoutMs&url=$urlEncoded"
            val t0 = System.currentTimeMillis()
            client.newCall(Request.Builder().url(apiUrl).get().build()).execute().use { resp ->
                if (resp.isSuccessful) (System.currentTimeMillis() - t0).toInt() else -1
            }
        }.getOrDefault(-1)

    override fun trafficStats(): Flow<TrafficStats> = _stats
    override fun logs(): Flow<String> = _logs
}
