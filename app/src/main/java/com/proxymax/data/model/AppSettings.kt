package com.proxymax.data.model
import com.proxymax.data.model.PerAppMode

import com.proxymax.core.CoreType

data class AppSettings(
    val preferredCore:    CoreType = CoreType.MIHOMO,
    val autoSelectCore:   Boolean  = true,    // 智能自动选核
    val enableFakeIP:     Boolean  = true,
    val enableIPv6:       Boolean  = true,
    val bypassLan:        Boolean  = true,
    val startOnBoot:      Boolean  = false,
    val perAppProxyMode:  PerAppMode = PerAppMode.GLOBAL,
    val bypassedApps:     Set<String> = emptySet(),
    val proxiedApps:      Set<String> = emptySet(),
    val mixedPort:        Int = 7890,
    val socksPort:        Int = 7891,
    val apiPort:          Int = 9090,
    val apiSecret:        String = "",
    val logLevel:         String = "info"  // debug/info/warn/error/silent
)

