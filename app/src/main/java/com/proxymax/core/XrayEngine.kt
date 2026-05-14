package com.proxymax.core

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Xray 内核引擎（XTLS/Reality/VLESS）
 * 当前版本委托给 SingboxEngine（sing-box 原生支持 VLESS/XTLS/Reality）。
 * 后续可替换为独立的 libxray.so JNI 实现。
 */
@Singleton
class XrayEngine @Inject constructor(
    private val singbox: SingboxEngine
) : CoreEngine {

    override val type = CoreType.XRAY

    override fun isAvailable() = singbox.isAvailable()
    override fun version()     = "Xray (via sing-box ${singbox.version()})"

    override suspend fun start(config: String, tunFd: Int): Result<Unit> {
        return singbox.start(config, tunFd)
    }

    override suspend fun stop()                  = singbox.stop()
    override suspend fun reload(config: String)  = singbox.reload(config)
    override fun trafficStats()                  = singbox.trafficStats()
    override fun logs(): Flow<String>            = singbox.logs()
    override suspend fun testDelay(proxyName: String, url: String, timeoutMs: Int) =
        singbox.testDelay(proxyName, url, timeoutMs)
}
