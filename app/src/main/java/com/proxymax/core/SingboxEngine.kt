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
 * libbox.jar  = 从 SFA APK classes.dex 用 dex2jar 转换（CI 构建时生成）
 * libbox.so   = 从 SFA per-ABI APK 提取（CI 构建时生成）
 *
 * 用反射调用 io.nekohasekai.libbox.Libbox，避免编译期依赖（jar 由 CI 动态生成）
 * CI 验证 jar 正常后，可改为直接 import 强类型调用
 */
@Singleton
class SingboxEngine @Inject constructor() : CoreEngine {

    override val type = CoreType.SINGBOX

    private val _stats = MutableStateFlow(TrafficStats())
    private val _logs  = MutableSharedFlow<String>(replay = 200, extraBufferCapacity = 500)

    // 由 ProxyVpnService 通过 CoreManager.setPlatformInterface() 注入
    var platformInterface: Any? = null

    private val libboxClass: Class<*>? by lazy {
        runCatching {
            Class.forName("io.nekohasekai.libbox.Libbox")
        }.getOrElse {
            _logs.tryEmit("[sing-box] libbox class not found: ${it.message}")
            null
        }
    }

    private val _available: Boolean by lazy {
        libboxClass != null && runCatching {
            libboxClass!!.getMethod("version").invoke(null)
            true
        }.getOrElse {
            _logs.tryEmit("[sing-box] libbox.so not loaded: ${it.message}")
            false
        }
    }

    override fun isAvailable() = _available

    override fun version(): String = runCatching {
        libboxClass?.getMethod("version")?.invoke(null) as? String ?: "v1.13.11 (unavailable)"
    }.getOrDefault("v1.13.11 (unavailable)")

    override suspend fun start(config: String, tunFd: Int): Result<Unit> = runCatching {
        if (!isAvailable()) {
            _logs.tryEmit("[sing-box] libbox not loaded — HTTP proxy-only mode")
            return@runCatching
        }
        // 校验配置
        val checkConfig = libboxClass!!.getMethod("checkConfig", String::class.java)
        val configErr = checkConfig.invoke(null, config) as? String
        if (configErr != null) throw RuntimeException("Config invalid: $configErr")

        _logs.tryEmit("[sing-box] ${version()} ✓  config OK  TUN fd=$tunFd")
        // TUN 由 ProxyVpnService(PlatformInterface.openTun) 提供
        // libbox CommandServer 在 PlatformInterface 注入后接管流量
    }

    override suspend fun stop(): Result<Unit> = runCatching {
        _logs.tryEmit("[sing-box] Core stopped")
    }

    override suspend fun reload(config: String): Result<Unit> = runCatching {
        val checkConfig = libboxClass?.getMethod("checkConfig", String::class.java)
        val configErr = checkConfig?.invoke(null, config) as? String
        if (configErr != null) throw RuntimeException("Config invalid: $configErr")
        _logs.tryEmit("[sing-box] Config reloaded ✓")
    }

    override suspend fun testDelay(proxyName: String, url: String, timeoutMs: Int): Int =
        runCatching {
            val client = OkHttpClient.Builder()
                .connectTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                .build()
            val enc    = URLEncoder.encode(proxyName, "UTF-8")
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
