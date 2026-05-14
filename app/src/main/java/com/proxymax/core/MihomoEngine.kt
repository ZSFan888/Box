package com.proxymax.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mihomo (Clash Meta) 内核引擎
 * 当前版本委托给 SingboxEngine 执行（sing-box 支持 Clash YAML via external provider）。
 * 后续可替换为独立的 libmihomo.so JNI 实现。
 */
@Singleton
class MihomoEngine @Inject constructor(
    private val singbox: SingboxEngine
) : CoreEngine {

    override val type = CoreType.MIHOMO

    private val _logs = MutableSharedFlow<String>(replay = 200, extraBufferCapacity = 500)

    override fun isAvailable() = singbox.isAvailable()
    override fun version()     = "Mihomo (via sing-box ${singbox.version()})"

    override suspend fun start(config: String, tunFd: Int): Result<Unit> {
        _logs.tryEmit("[mihomo] Starting via sing-box Clash compat layer")
        return singbox.start(config, tunFd)
    }

    override suspend fun stop()           = singbox.stop()
    override suspend fun reload(config: String) = singbox.reload(config)
    override fun trafficStats()           = singbox.trafficStats()
    override fun logs(): Flow<String>     = singbox.logs()
    override suspend fun testDelay(proxyName: String, url: String, timeoutMs: Int) =
        singbox.testDelay(proxyName, url, timeoutMs)
}
