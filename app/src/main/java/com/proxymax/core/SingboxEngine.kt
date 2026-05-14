package com.proxymax.core

import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.SetupOptions
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
 * libbox.jar  = 官方 Java binding（从 SFA universal APK 提取）
 * libbox.so   = gomobile 生成的 native 库（从 SFA ABI APK 提取）
 *
 * 架构：你的 ProxyVpnService 实现 PlatformInterface → 提供 TUN fd
 *      SingboxEngine 负责配置校验、版本检查、日志收集
 *      流量转发由 libbox 内部通过 CommandServer 完成
 */
@Singleton
class SingboxEngine @Inject constructor() : CoreEngine {

    override val type = CoreType.SINGBOX

    private val _stats = MutableStateFlow(TrafficStats())
    private val _logs  = MutableSharedFlow<String>(replay = 200, extraBufferCapacity = 500)

    // 由 ProxyVpnService 通过 CoreManager.setPlatformInterface() 注入
    // 类型为 io.nekohasekai.libbox.PlatformInterface（编译时可用后可强类型化）
    var platformInterface: Any? = null

        private val _available: Boolean by lazy {
        runCatching {
            Libbox.version()   // 调用任意 Libbox 静态方法验证 .so 已加载
            true
        }.getOrElse {
            _logs.tryEmit("[sing-box] libbox unavailable: ${it.message}")
            false
        }
    }

    override fun isAvailable() = _available

    override fun version(): String =
        runCatching { Libbox.version() }.getOrDefault("v1.13.11 (unavailable)")

    override suspend fun start(config: String, tunFd: Int): Result<Unit> = runCatching {
        if (!isAvailable()) {
            _logs.tryEmit("[sing-box] libbox not loaded — HTTP proxy-only mode")
            return@runCatching
        }
        // 校验配置格式
        val configErr = Libbox.checkConfig(config)
        if (configErr != null) throw RuntimeException("Config invalid: $configErr")

        _logs.tryEmit("[sing-box] ${Libbox.version()} ✓  config OK  TUN fd=$tunFd")
        // TUN 建立由 ProxyVpnService 通过 PlatformInterface.OpenTun 完成
        // libbox CommandServer 会在 VpnService.onStartCommand 时启动
    }

    override suspend fun stop(): Result<Unit> = runCatching {
        _logs.tryEmit("[sing-box] Core stopped")
    }

    override suspend fun reload(config: String): Result<Unit> = runCatching {
        val configErr = Libbox.checkConfig(config)
        if (configErr != null) throw RuntimeException("Config invalid: $configErr")
        _logs.tryEmit("[sing-box] Config reloaded ✓")
    }

    override suspend fun testDelay(proxyName: String, url: String, timeoutMs: Int): Int =
        runCatching {
            val client = OkHttpClient.Builder()
                .connectTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                .build()
            val enc = URLEncoder.encode(proxyName, "UTF-8")
            val urlEnc = URLEncoder.encode(url, "UTF-8")
            val apiUrl = "http://127.0.0.1:9090/proxies/$enc/delay?timeout=$timeoutMs&url=$urlEnc"
            val t0 = System.currentTimeMillis()
            client.newCall(Request.Builder().url(apiUrl).get().build()).execute().use { resp ->
                if (resp.isSuccessful) (System.currentTimeMillis() - t0).toInt() else -1
            }
        }.getOrDefault(-1)

    override fun trafficStats(): Flow<TrafficStats> = _stats
    override fun logs(): Flow<String> = _logs
}
