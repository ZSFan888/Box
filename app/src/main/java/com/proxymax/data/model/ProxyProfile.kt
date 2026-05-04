package com.proxymax.data.model

import androidx.room.*

enum class ProfileType { CLASH_YAML, XRAY_JSON, SINGBOX_JSON, URI }

@Entity(tableName = "profiles")
data class ProxyProfile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name:          String,
    val type:          ProfileType,
    val url:           String  = "",   // 订阅 URL（可为空）
    val rawConfig:     String  = "",   // 原始配置内容
    val lastUpdated:   Long    = 0L,
    val autoUpdate:    Boolean = false,
    val updateInterval: Int   = 24,    // hours
    val isActive:      Boolean = false
)

@Entity(tableName = "proxy_nodes")
data class ProxyNode(
    @PrimaryKey val id:   String,      // UUID
    val profileId:        Int,
    val name:             String,
    val type:             String,      // vmess/vless/ss/trojan/hysteria2/tuic...
    val server:           String,
    val port:             Int,
    val latency:          Int    = -1, // ms, -1 = untested
    val isFavorite:       Boolean = false,
    val rawJson:          String  = "" // 原始节点 JSON/URI
)
