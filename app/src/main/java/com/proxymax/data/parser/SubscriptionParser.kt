package com.proxymax.data.parser

import android.util.Base64
import com.google.gson.Gson
import com.proxymax.data.model.ProxyNode
import com.proxymax.data.model.ProfileType
import java.net.URI
import java.util.UUID

object SubscriptionParser {

    private val gson = Gson()

    /**
     * 自动识别格式并解析节点列表
     * 返回 (ProfileType, List<ProxyNode>)
     */
    fun parse(raw: String, profileId: Int): Pair<ProfileType, List<ProxyNode>> {
        val trimmed = raw.trim()
        return when {
            // Clash YAML
            trimmed.startsWith("proxies:") ||
            trimmed.contains("proxy-groups:") ||
            trimmed.contains("mixed-port:") ->
                ProfileType.CLASH_YAML to parseClashYaml(trimmed, profileId)

            // Xray / v2ray JSON
            trimmed.startsWith("{") && trimmed.contains("\"inbounds\"") ->
                ProfileType.XRAY_JSON to emptyList() // JSON 格式不拆分节点，整体使用

            // sing-box JSON
            trimmed.startsWith("{") && trimmed.contains("\"outbounds\"") ->
                ProfileType.SINGBOX_JSON to emptyList()

            // Base64 编码的节点列表（常见机场格式）
            isBase64(trimmed) -> {
                val decoded = String(Base64.decode(trimmed, Base64.DEFAULT))
                ProfileType.URI to parseUriList(decoded, profileId)
            }

            // 多行 URI 列表
            trimmed.contains("vmess://") || trimmed.contains("vless://") ||
            trimmed.contains("ss://")    || trimmed.contains("trojan://") ||
            trimmed.contains("hysteria2://") || trimmed.contains("tuic://") ->
                ProfileType.URI to parseUriList(trimmed, profileId)

            else -> ProfileType.CLASH_YAML to emptyList()
        }
    }

    // ── Clash YAML 解析 ────────────────────────────────────────────────────
    fun parseClashYaml(yaml: String, profileId: Int): List<ProxyNode> {
        val nodes = mutableListOf<ProxyNode>()
        var inProxies = false
        var currentBlock = StringBuilder()

        for (line in yaml.lines()) {
            when {
                line.startsWith("proxies:") -> { inProxies = true; continue }
                inProxies && (line.startsWith("  - ") || line.startsWith("- ")) -> {
                    if (currentBlock.isNotEmpty()) {
                        parseClashProxyBlock(currentBlock.toString(), profileId)
                            ?.let { nodes.add(it) }
                    }
                    currentBlock = StringBuilder(line)
                }
                inProxies && line.startsWith("    ") -> currentBlock.appendLine(line)
                inProxies && !line.startsWith(" ")  -> {
                    if (currentBlock.isNotEmpty()) {
                        parseClashProxyBlock(currentBlock.toString(), profileId)
                            ?.let { nodes.add(it) }
                        currentBlock = StringBuilder()
                    }
                    inProxies = false
                }
                inProxies -> currentBlock.appendLine(line)
            }
        }
        if (currentBlock.isNotEmpty()) {
            parseClashProxyBlock(currentBlock.toString(), profileId)?.let { nodes.add(it) }
        }
        return nodes
    }

    private fun parseClashProxyBlock(block: String, profileId: Int): ProxyNode? {
        return runCatching {
            val map = parseYamlMap(block)
            ProxyNode(
                id        = UUID.randomUUID().toString(),
                profileId = profileId,
                name      = map["name"] ?: return null,
                type      = map["type"] ?: "unknown",
                server    = map["server"] ?: return null,
                port      = map["port"]?.toIntOrNull() ?: 443,
                rawJson   = gson.toJson(map)
            )
        }.getOrNull()
    }

    /** 极简 YAML map 解析（单层 key: value）*/
    private fun parseYamlMap(block: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (line in block.lines()) {
            val clean = line.trimStart('-', ' ')
            val colonIdx = clean.indexOf(':')
            if (colonIdx < 0) continue
            val key = clean.substring(0, colonIdx).trim()
            val value = clean.substring(colonIdx + 1).trim().trim('"').trim('\'')
            if (key.isNotEmpty() && value.isNotEmpty()) map[key] = value
        }
        return map
    }

    // ── URI 列表解析 ───────────────────────────────────────────────────────
    fun parseUriList(text: String, profileId: Int): List<ProxyNode> =
        text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { parseUri(it, profileId) }

    fun parseUri(uri: String, profileId: Int): ProxyNode? {
        return runCatching {
            when {
                uri.startsWith("vmess://")     -> parseVmess(uri, profileId)
                uri.startsWith("vless://")     -> parseVlessOrTrojan("vless", uri, profileId)
                uri.startsWith("trojan://")    -> parseVlessOrTrojan("trojan", uri, profileId)
                uri.startsWith("ss://")        -> parseShadowsocks(uri, profileId)
                uri.startsWith("hysteria2://") -> parseHysteria2(uri, profileId)
                uri.startsWith("hy2://")       -> parseHysteria2(uri.replace("hy2://", "hysteria2://"), profileId)
                uri.startsWith("tuic://")      -> parseTuic(uri, profileId)
                else                           -> null
            }
        }.getOrNull()
    }

    private fun parseVmess(uri: String, profileId: Int): ProxyNode? {
        val b64 = uri.removePrefix("vmess://")
        val json = String(Base64.decode(b64, Base64.DEFAULT or Base64.URL_SAFE))
        @Suppress("UNCHECKED_CAST")
        val map = gson.fromJson(json, Map::class.java) as Map<String, Any>
        val name   = map["ps"]?.toString() ?: map["add"]?.toString() ?: "VMess"
        val server = map["add"]?.toString() ?: return null
        val port   = map["port"]?.toString()?.toIntOrNull() ?: 443
        return ProxyNode(UUID.randomUUID().toString(), profileId, name, "vmess", server, port, rawJson = json)
    }

    private fun parseVlessOrTrojan(type: String, uri: String, profileId: Int): ProxyNode? {
        val noScheme = uri.removePrefix("$type://")
        val atIdx    = noScheme.lastIndexOf('@')
        if (atIdx < 0) return null
        val hostPart = noScheme.substring(atIdx + 1).split("?", "#").first()
        val colonIdx = hostPart.lastIndexOf(':')
        val server   = if (colonIdx > 0) hostPart.substring(0, colonIdx) else hostPart
        val port     = if (colonIdx > 0) hostPart.substring(colonIdx + 1).toIntOrNull() ?: 443 else 443
        val name     = if (uri.contains('#')) java.net.URLDecoder.decode(uri.substringAfterLast('#'), "UTF-8") else server
        return ProxyNode(UUID.randomUUID().toString(), profileId, name, type, server, port, rawJson = uri)
    }

    private fun parseShadowsocks(uri: String, profileId: Int): ProxyNode? {
        val noScheme = uri.removePrefix("ss://")
        val hashIdx  = noScheme.indexOf('#')
        val name     = if (hashIdx >= 0) java.net.URLDecoder.decode(noScheme.substring(hashIdx + 1), "UTF-8") else "SS"
        val main     = if (hashIdx >= 0) noScheme.substring(0, hashIdx) else noScheme
        val atIdx    = main.lastIndexOf('@')
        val hostPort = if (atIdx >= 0) main.substring(atIdx + 1) else {
            // base64 encoded user@host:port
            runCatching { String(Base64.decode(main, Base64.DEFAULT)) }.getOrNull() ?: main
        }
        val colonIdx = hostPort.lastIndexOf(':')
        val server   = if (colonIdx > 0) hostPort.substring(0, colonIdx) else hostPort
        val port     = if (colonIdx > 0) hostPort.substring(colonIdx + 1).toIntOrNull() ?: 8388 else 8388
        return ProxyNode(UUID.randomUUID().toString(), profileId, name, "ss", server, port, rawJson = uri)
    }

    private fun parseHysteria2(uri: String, profileId: Int): ProxyNode? {
        val noScheme = uri.removePrefix("hysteria2://")
        val atIdx    = noScheme.lastIndexOf('@')
        if (atIdx < 0) return null
        val hostPart = noScheme.substring(atIdx + 1).split("/", "?", "#").first()
        val colonIdx = hostPart.lastIndexOf(':')
        val server   = if (colonIdx > 0) hostPart.substring(0, colonIdx) else hostPart
        val port     = if (colonIdx > 0) hostPart.substring(colonIdx + 1).toIntOrNull() ?: 443 else 443
        val name     = if (uri.contains('#')) java.net.URLDecoder.decode(uri.substringAfterLast('#'), "UTF-8") else server
        return ProxyNode(UUID.randomUUID().toString(), profileId, name, "hysteria2", server, port, rawJson = uri)
    }

    private fun parseTuic(uri: String, profileId: Int): ProxyNode? {
        val noScheme = uri.removePrefix("tuic://")
        val atIdx    = noScheme.lastIndexOf('@')
        if (atIdx < 0) return null
        val hostPart = noScheme.substring(atIdx + 1).split("?", "#").first()
        val colonIdx = hostPart.lastIndexOf(':')
        val server   = if (colonIdx > 0) hostPart.substring(0, colonIdx) else hostPart
        val port     = if (colonIdx > 0) hostPart.substring(colonIdx + 1).toIntOrNull() ?: 443 else 443
        val name     = if (uri.contains('#')) java.net.URLDecoder.decode(uri.substringAfterLast('#'), "UTF-8") else server
        return ProxyNode(UUID.randomUUID().toString(), profileId, name, "tuic", server, port, rawJson = uri)
    }

    private fun isBase64(s: String): Boolean {
        // 机场 base64 不一定有 padding，只要解码后含协议头就认定
        val cleaned = s.trim().replace("\n", "").replace("\r", "")
        if (cleaned.length < 16) return false
        return runCatching {
            val decoded = String(Base64.decode(cleaned, Base64.URL_SAFE or Base64.DEFAULT))
            decoded.contains("://") || decoded.contains("proxies:")
        }.getOrDefault(false)
    }
}
