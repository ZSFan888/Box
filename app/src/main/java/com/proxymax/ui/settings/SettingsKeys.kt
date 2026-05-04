package com.proxymax.ui.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object SettingsKeys {
    // 内核
    val autoSelectCore        = booleanPreferencesKey("auto_select_core")
    val defaultCore           = stringPreferencesKey("default_core")
    // DNS
    val enableFakeIp          = booleanPreferencesKey("enable_fakeip")
    val enableIpv6            = booleanPreferencesKey("enable_ipv6")
    // 端口
    val mixedPort             = intPreferencesKey("mixed_port")
    val apiPort               = intPreferencesKey("api_port")
    // 分流规则
    val geositeCnDirect       = booleanPreferencesKey("geosite_cn_direct")
    val geoipPrivateDirect    = booleanPreferencesKey("geoip_private_direct")
    // 系统
    val logLevel              = stringPreferencesKey("log_level")
    val startOnBoot           = booleanPreferencesKey("start_on_boot")
    // PerApp
    val perAppMode            = stringPreferencesKey("per_app_mode")
    val perAppPackages        = stringPreferencesKey("per_app_packages")  // JSON 数组
}
