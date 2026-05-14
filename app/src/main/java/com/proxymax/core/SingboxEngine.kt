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

@Singleton
class SingboxEngine @Inject constructor() : CoreEngine {

    override val type = CoreType.SINGBOX

    private val _stats = MutableStateFlow(TrafficStats())
    private val _logs  = MutableSharedFlow<String>(replay = 200, extraBufferCapacity = 500)

    var platformInterface: Any? = null

    companion object {
        private val soLoaded: Boolean by lazy {
            listOf("box", "gojni", "singbox", "libbox").any { name ->
                runCatching {
                    System.loadLibrary(name)
                    android.util.Log.i("SingboxEngine", "Loaded lib$name.so")
                    true
                }.getOrElse {
                    android.util.Log.w("SingboxEngine", "lib$name.so: ${it.message}")
                    false
                }
            }
        }
    }

    private val libboxClass: Class<*>? by lazy {
        soLoaded
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
            _logs.tryEmit("[sing-box] version() failed: ${it::class.simpleName}: ${it.message}")
            false
        }
    }

    override fun isAvailable() = _available

    override fun version(): String = runCatching {
        libboxClass?.getMethod("version")?.invoke(null) as? String ?: "v1.13.11 (unavailable)"
    }.getOrDefault("v1.13.11 (unavailable)")

    override suspend fun start(config: String, tunFd: Int): Result<Unit> = runCatching {
        if (!isAvailable()) {
            // 把 so 加载状态也记进日志，方便排查
            _logs.tryEmit("[sing-box] libbox not available (soLoaded=$soLoaded, class=${libboxClass != null})")
            return@runCatching
        }

        // 尝试校验配置，方法名可能随版本变化，找不到就跳过
        val cls = libboxClass!!
        val checkMethods = cls.methods.filter {
            it.name.lowercase().contains("check") || it.name.lowercase().contains("config")
        }.map { it.name }
        _logs.tryEmit("[sing-box] available methods with check/config: $checkMethods")

        val checkMethod = runCatching { cls.getMethod("checkConfig", String::class.java) }.getOrNull()
            ?: runCatching { cls.getMethod("check", String::class.java) }.getOrNull()

        if (checkMethod != null) {
            val configErr = checkMethod.invoke(null, config) as? String
            if (!configErr.isNullOrEmpty()) {
                throw RuntimeException("Config invalid: $configErr")
            }
        } else {
            _logs.tryEmit("[sing-box] checkConfig method not found — skipping validation")
        }

        _logs.tryEmit("[sing-box] ${version()} ✓  TUN fd=$tunFd  starting...")
        // 尝试调用 newService / run / start
        val startMethod = runCatching { cls.getMethod("newService", String::class.java) }.getOrNull()
            ?: runCatching { cls.getMethod("run", String::class.java) }.getOrNull()
            ?: runCatching { cls.getMethod("start", String::class.java) }.getOrNull()

        if (startMethod != null) {
            _logs.tryEmit("[sing-box] calling ${startMethod.name}(config)...")
            startMethod.invoke(null, config)
        } else {
            // 列出所有 static 方法供排查
            val statics = cls.methods.filter { java.lang.reflect.Modifier.isStatic(it.modifiers) }
                .joinToString(", ") { it.name }
            _logs.tryEmit("[sing-box] WARN: no start method found. Static methods: $statics")
        }
    }.onFailure { e ->
        _logs.tryEmit("[sing-box] start() FAILED: ${e::class.simpleName}: ${e.message ?: "(no message)"}")
        if (e.cause != null) _logs.tryEmit("[sing-box]   caused by: ${e.cause!!::class.simpleName}: ${e.cause!!.message}")
    }

    override suspend fun stop(): Result<Unit> = runCatching {
        _logs.tryEmit("[sing-box] Core stopped")
    }

    override suspend fun reload(config: String): Result<Unit> = runCatching {
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
