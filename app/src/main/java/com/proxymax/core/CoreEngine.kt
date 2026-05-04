package com.proxymax.core

import kotlinx.coroutines.flow.Flow

/** 支持的内核类型 */
enum class CoreType(val displayName: String, val description: String) {
    MIHOMO("Mihomo",   "Clash Meta · 策略组 · YAML 订阅"),
    XRAY(  "Xray",     "XTLS/Reality · 自建节点首选"),
    SINGBOX("sing-box","全协议 · Hysteria2 · TUIC · NaïveProxy")
}

/** 内核运行状态 */
sealed class CoreState {
    object Idle        : CoreState()
    object Starting    : CoreState()
    data class Running(val core: CoreType, val stats: TrafficStats) : CoreState()
    data class Switching(val from: CoreType, val to: CoreType)      : CoreState()
    data class Error(val core: CoreType, val message: String)        : CoreState()
    object Stopped     : CoreState()
}

data class TrafficStats(
    val uploadSpeed:   Long = 0L,   // bytes/s
    val downloadSpeed: Long = 0L,
    val totalUpload:   Long = 0L,
    val totalDownload: Long = 0L,
    val connections:   Int  = 0
)

/** 所有内核必须实现的统一接口 */
interface CoreEngine {
    val type: CoreType

    /** 加载配置并启动内核。配置为该内核的原始格式（YAML / JSON）。*/
    suspend fun start(config: String, tunFd: Int): Result<Unit>

    /** 停止内核，释放所有资源 */
    suspend fun stop(): Result<Unit>

    /** 热重载配置，不断开 VPN（尽力而为）*/
    suspend fun reload(config: String): Result<Unit>

    /** 实时流量统计 */
    fun trafficStats(): Flow<TrafficStats>

    /** 实时日志 */
    fun logs(): Flow<String>

    /** 测试指定节点的延迟（ms），-1 表示超时 */
    suspend fun testDelay(proxyName: String, url: String, timeoutMs: Int = 5000): Int

    /** 当前内核版本号 */
    fun version(): String

    /** 内核是否已加载（.so 存在且可调用）*/
    fun isAvailable(): Boolean
}
