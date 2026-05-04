package com.proxymax.core

import com.proxymax.core.stats.ClashApiClient
import com.proxymax.core.stats.StatsCollector
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoreManager @Inject constructor(
    private val singboxEngine: SingboxEngine,
    private val statsCollector: StatsCollector,
    private val apiClient: ClashApiClient
) {
    val state = singboxEngine.state
    fun startCore(config: String, tunFd: Int) = singboxEngine.start(config, tunFd)
    fun stopCurrent() = singboxEngine.stop()
    fun getProxies() = apiClient.getProxies()
    fun testDelay(name: String) = apiClient.testDelay(name)
    fun selectProxy(group: String, proxy: String) = apiClient.selectProxy(group, proxy)
}
