package com.proxymax.core

import com.proxymax.data.converter.ConfigConverter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mihomo (Clash Meta) 内核引擎
 * 接收 Clash YAML → 转换为 sing-box JSON → 委托 SingboxEngine 执行
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
        _logs.tryEmit("[mihomo] Converting Clash YAML → sing-box JSON...")
        val singboxConfig = runCatching {
            ConfigConverter.clashToSingbox(config)
        }.getOrElse {
            _logs.tryEmit("[mihomo] Config conversion failed: ${it.message}")
            return Result.failure(RuntimeException("Clash→sing-box conversion failed: ${it.message}", it))
        }
        _logs.tryEmit("[mihomo] Conversion OK, handing off to sing-box...")
        return singbox.start(singboxConfig, tunFd)
    }

    override suspend fun stop()                 = singbox.stop()
    override suspend fun reload(config: String) = singbox.reload(
        runCatching { ConfigConverter.clashToSingbox(config) }.getOrDefault(config)
    )
    override fun trafficStats()                 = singbox.trafficStats()
    override fun logs(): Flow<String>           = singbox.logs()
    override suspend fun testDelay(proxyName: String, url: String, timeoutMs: Int) =
        singbox.testDelay(proxyName, url, timeoutMs)
}
