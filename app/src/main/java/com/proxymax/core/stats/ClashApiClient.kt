package com.proxymax.core.stats

import com.google.gson.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 对接 Clash-compatible REST API（mihomo + sing-box 均实现此 API）
 * Base: http://127.0.0.1:{apiPort}
 * Docs: https://clash.gitbook.io/doc/restful-api
 */
@Singleton
class ClashApiClient @Inject constructor() {

    private val gson = Gson()
    private var apiPort = 9090
    private var secret  = ""

    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(0,  TimeUnit.SECONDS)   // SSE 长连接
        .build()

    fun configure(port: Int, sec: String) { apiPort = port; secret = sec }

    private fun baseUrl() = "http://127.0.0.1:$apiPort"

    private fun Request.Builder.auth() = apply {
        if (secret.isNotEmpty()) header("Authorization", "Bearer $secret")
    }

    // ── 实时流量（SSE 流）─────────────────────────────────────────────────
    fun trafficFlow(): Flow<TrafficData> = callbackFlow {
        val req = Request.Builder()
            .url("${baseUrl()}/traffic")
            .auth().build()

        val call = http.newCall(req)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                close(e)
            }
            override fun onResponse(call: Call, response: Response) {
                response.body?.source()?.let { src ->
                    try {
                        while (!src.exhausted()) {
                            val line = src.readUtf8Line() ?: break
                            if (line.isBlank()) continue
                            runCatching {
                                val j = gson.fromJson(line, JsonObject::class.java)
                                TrafficData(
                                    uploadSpeed   = j["up"]?.asLong   ?: 0L,
                                    downloadSpeed = j["down"]?.asLong ?: 0L
                                )
                            }.getOrNull()?.let { trySend(it) }
                        }
                    } finally {
                        response.close()
                        close()
                    }
                }
            }
        })
        awaitClose { call.cancel() }
    }

    // ── 实时日志（SSE 流）──────────────────────────────────────────────────
    fun logsFlow(level: String = "info"): Flow<String> = callbackFlow {
        val req = Request.Builder()
            .url("${baseUrl()}/logs?level=$level")
            .auth().build()

        val call = http.newCall(req)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) { close(e) }
            override fun onResponse(call: Call, response: Response) {
                response.body?.source()?.let { src ->
                    try {
                        while (!src.exhausted()) {
                            val line = src.readUtf8Line() ?: break
                            if (line.isBlank()) continue
                            runCatching {
                                val j = gson.fromJson(line, JsonObject::class.java)
                                val type = j["type"]?.asString?.uppercase() ?: "INFO"
                                val payload = j["payload"]?.asString ?: line
                                "[$type] $payload"
                            }.getOrElse { line }.let { trySend(it) }
                        }
                    } finally { response.close(); close() }
                }
            }
        })
        awaitClose { call.cancel() }
    }

    // ── 连接列表 ───────────────────────────────────────────────────────────
    suspend fun getConnections(): ConnectionsData = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("${baseUrl()}/connections").auth().build()
            http.newCall(req).execute().use { resp ->
                val j = gson.fromJson(resp.body?.string() ?: "{}", JsonObject::class.java)
                ConnectionsData(
                    downloadTotal = j["downloadTotal"]?.asLong ?: 0L,
                    uploadTotal   = j["uploadTotal"]?.asLong   ?: 0L,
                    connections   = j["connections"]?.asJsonArray?.size() ?: 0
                )
            }
        }.getOrDefault(ConnectionsData())
    }

    // ── 代理节点列表 ────────────────────────────────────────────────────────
    suspend fun getProxies(): Map<String, ProxyInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("${baseUrl()}/proxies").auth().build()
            http.newCall(req).execute().use { resp ->
                val j = gson.fromJson(resp.body?.string() ?: "{}", JsonObject::class.java)
                j["proxies"]?.asJsonObject?.entrySet()?.associate { (name, elem) ->
                    val obj = elem.asJsonObject
                    name to ProxyInfo(
                        name    = name,
                        type    = obj["type"]?.asString    ?: "",
                        now     = obj["now"]?.asString     ?: "",
                        history = obj["history"]?.asJsonArray
                            ?.map { it.asJsonObject["delay"]?.asInt ?: -1 }
                            ?: emptyList()
                    )
                } ?: emptyMap()
            }
        }.getOrDefault(emptyMap())
    }

    // ── 选择代理 ────────────────────────────────────────────────────────────
    suspend fun selectProxy(group: String, proxy: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val body = gson.toJson(mapOf("name" to proxy))
                .toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("${baseUrl()}/proxies/${java.net.URLEncoder.encode(group, "UTF-8")}")
                .auth().put(body).build()
            http.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    // ── 测试单节点延迟 ──────────────────────────────────────────────────────
    suspend fun testDelay(
        proxyName: String,
        url:       String = "https://www.gstatic.com/generate_204",
        timeout:   Int    = 5000
    ): Int = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = java.net.URLEncoder.encode(proxyName, "UTF-8")
            val apiUrl  = "${baseUrl()}/proxies/$encoded/delay?timeout=$timeout&url=${java.net.URLEncoder.encode(url,"UTF-8")}"
            val req     = Request.Builder().url(apiUrl).auth().build()
            http.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    gson.fromJson(resp.body?.string(), JsonObject::class.java)["delay"]?.asInt ?: -1
                } else -1
            }
        }.getOrDefault(-1)
    }

    // ── 关闭所有连接 ────────────────────────────────────────────────────────
    suspend fun closeAllConnections() = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("${baseUrl()}/connections").auth().delete().build()
            http.newCall(req).execute().close()
        }
    }

    // ── 热重载配置 ──────────────────────────────────────────────────────────
    suspend fun reloadConfig(configPath: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val body = gson.toJson(mapOf("path" to configPath))
                .toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("${baseUrl()}/configs?force=true")
                .auth().put(body).build()
            http.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }
}

data class TrafficData(val uploadSpeed: Long = 0, val downloadSpeed: Long = 0)
data class ConnectionsData(val downloadTotal: Long = 0, val uploadTotal: Long = 0, val connections: Int = 0)
data class ProxyInfo(val name: String, val type: String, val now: String, val history: List<Int>)
