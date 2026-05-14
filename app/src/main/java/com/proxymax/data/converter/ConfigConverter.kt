package com.proxymax.data.converter

import com.google.gson.*
import com.proxymax.data.model.ProxyNode

/**
 * 统一配置转换层
 * Clash YAML → Xray JSON  (用于 XrayEngine)
 * Clash YAML → sing-box JSON (用于 SingboxEngine)
 * 单节点 ProxyNode → 各格式出站配置片段
 */
object ConfigConverter {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    // ════════════════════════════════════════════════════════════════════
    // Clash YAML → Xray JSON
    // ════════════════════════════════════════════════════════════════════
    fun clashToXray(yaml: String, apiPort: Int = 9090): String {
        val nodes = parseClashProxiesRaw(yaml)
        val outbounds = JsonArray().apply {
            nodes.forEach { add(nodeToXrayOutbound(it)) }
            // direct & block
            add(JsonObject().apply { addProperty("tag", "direct"); addProperty("protocol", "freedom") })
            add(JsonObject().apply { addProperty("tag", "block");  addProperty("protocol", "blackhole") })
        }
        val config = JsonObject().apply {
            add("log",      JsonObject().apply { addProperty("loglevel", "warning") })
            add("inbounds", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("tag", "mixed-in")
                    addProperty("port", 7890)
                    addProperty("protocol", "mixed")
                    add("settings", JsonObject().apply { addProperty("udp", true) })
                    add("sniffing",  JsonObject().apply {
                        addProperty("enabled", true)
                        add("destOverride", JsonArray().apply { add("http"); add("tls") })
                    })
                })
                add(JsonObject().apply {
                    addProperty("tag", "tun-in")
                    addProperty("protocol", "dokodemo-door")
                    addProperty("port", 12345)
                    add("settings", JsonObject().apply {
                        addProperty("network", "tcp,udp")
                        addProperty("followRedirect", true)
                    })
                })
            })
            add("outbounds", outbounds)
            add("routing", buildXrayRouting())
            add("api", JsonObject().apply {
                addProperty("tag", "api")
                add("services", JsonArray().apply {
                    add("HandlerService"); add("StatsService")
                })
            })
            add("stats", JsonObject())
            add("policy", JsonObject().apply {
                add("system", JsonObject().apply {
                    addProperty("statsInboundUplink",   true)
                    addProperty("statsInboundDownlink", true)
                    addProperty("statsOutboundUplink",  true)
                    addProperty("statsOutboundDownlink",true)
                })
            })
        }
        return gson.toJson(config)
    }

    private fun nodeToXrayOutbound(node: ProxyNode): JsonObject = JsonObject().apply {
        addProperty("tag", node.name)
        when (node.type.lowercase()) {
            "vmess" -> {
                addProperty("protocol", "vmess")
                val raw = runCatching { gson.fromJson(node.rawJson, JsonObject::class.java) }.getOrDefault(JsonObject())
                add("settings", JsonObject().apply {
                    add("vnext", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("address", node.server)
                            addProperty("port",    node.port)
                            add("users", JsonArray().apply {
                                add(JsonObject().apply {
                                    addProperty("id",       raw["id"]?.asString ?: "")
                                    addProperty("alterId",  raw["aid"]?.asInt ?: 0)
                                    addProperty("security", raw["scy"]?.asString ?: "auto")
                                })
                            })
                        })
                    })
                })
                add("streamSettings", buildStreamSettings(raw))
            }
            "vless" -> {
                addProperty("protocol", "vless")
                val raw = runCatching { gson.fromJson(node.rawJson, JsonObject::class.java) }.getOrDefault(JsonObject())
                add("settings", JsonObject().apply {
                    add("vnext", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("address", node.server)
                            addProperty("port",    node.port)
                            add("users", JsonArray().apply {
                                add(JsonObject().apply {
                                    addProperty("id",         raw["uuid"]?.asString ?: "")
                                    addProperty("encryption", "none")
                                    val flow = raw["flow"]?.asString ?: ""
                                    if (flow.isNotEmpty()) addProperty("flow", flow)
                                })
                            })
                        })
                    })
                })
                add("streamSettings", buildStreamSettings(raw))
            }
            "trojan" -> {
                addProperty("protocol", "trojan")
                val raw = runCatching { gson.fromJson(node.rawJson, JsonObject::class.java) }.getOrDefault(JsonObject())
                add("settings", JsonObject().apply {
                    add("servers", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("address",  node.server)
                            addProperty("port",     node.port)
                            addProperty("password", raw["password"]?.asString ?: "")
                        })
                    })
                })
                add("streamSettings", buildStreamSettings(raw))
            }
            "ss", "shadowsocks" -> {
                addProperty("protocol", "shadowsocks")
                val raw = runCatching { gson.fromJson(node.rawJson, JsonObject::class.java) }.getOrDefault(JsonObject())
                add("settings", JsonObject().apply {
                    add("servers", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("address",  node.server)
                            addProperty("port",     node.port)
                            addProperty("method",   raw["cipher"]?.asString ?: "aes-256-gcm")
                            addProperty("password", raw["password"]?.asString ?: "")
                        })
                    })
                })
            }
            else -> {
                addProperty("protocol", "freedom") // fallback
                addProperty("_note", "Protocol ${node.type} not supported by Xray, using direct")
            }
        }
    }

    private fun buildStreamSettings(raw: JsonObject): JsonObject = JsonObject().apply {
        val network = raw["network"]?.asString ?: raw["net"]?.asString ?: "tcp"
        addProperty("network", network)
        val security = raw["tls"]?.asString ?: raw["security"]?.asString ?: "none"
        addProperty("security", security)
        if (security == "tls" || security == "reality") {
            add("tlsSettings", JsonObject().apply {
                addProperty("serverName", raw["sni"]?.asString ?: raw["host"]?.asString ?: "")
                addProperty("allowInsecure", raw["skip-cert-verify"]?.asBoolean ?: false)
                val fp = raw["fingerprint"]?.asString ?: raw["fp"]?.asString
                if (!fp.isNullOrEmpty()) addProperty("fingerprint", fp)
            })
            if (security == "reality") {
                add("realitySettings", JsonObject().apply {
                    addProperty("serverName",  raw["sni"]?.asString ?: "")
                    addProperty("fingerprint", raw["fp"]?.asString  ?: "chrome")
                    addProperty("shortId",     raw["sid"]?.asString ?: "")
                    addProperty("publicKey",   raw["pbk"]?.asString ?: "")
                    addProperty("spiderX",     raw["spx"]?.asString ?: "/")
                })
            }
        }
        when (network) {
            "ws" -> add("wsSettings", JsonObject().apply {
                addProperty("path", raw["ws-opts"]?.let {
                    runCatching { gson.fromJson(it, JsonObject::class.java)["path"]?.asString }.getOrNull()
                } ?: raw["path"]?.asString ?: "/")
                add("headers", JsonObject().apply {
                    addProperty("Host", raw["host"]?.asString ?: raw["server"]?.asString ?: "")
                })
            })
            "grpc" -> add("grpcSettings", JsonObject().apply {
                addProperty("serviceName", raw["grpc-opts"]?.let {
                    runCatching { gson.fromJson(it, JsonObject::class.java)["grpc-service-name"]?.asString }.getOrNull()
                } ?: "")
            })
            "h2", "http" -> add("httpSettings", JsonObject().apply {
                addProperty("path", raw["path"]?.asString ?: "/")
                add("host", JsonArray().apply {
                    val h = raw["host"]?.asString ?: ""; if (h.isNotEmpty()) add(h)
                })
            })
        }
    }

    private fun buildXrayRouting(): JsonObject = JsonObject().apply {
        addProperty("domainStrategy", "IPIfNonMatch")
        add("rules", JsonArray().apply {
            // API inbound
            add(JsonObject().apply {
                addProperty("type",        "field")
                addProperty("inboundTag",  "api")
                addProperty("outboundTag", "api")
            })
            // CN direct
            add(JsonObject().apply {
                addProperty("type", "field")
                add("domain", JsonArray().apply { add("geosite:cn") })
                addProperty("outboundTag", "direct")
            })
            add(JsonObject().apply {
                addProperty("type", "field")
                add("ip", JsonArray().apply { add("geoip:cn"); add("geoip:private") })
                addProperty("outboundTag", "direct")
            })
        })
    }

    // ════════════════════════════════════════════════════════════════════
    // Clash YAML → sing-box JSON
    // ════════════════════════════════════════════════════════════════════
    fun clashToSingbox(yaml: String, apiPort: Int = 9090): String {
        val nodes = parseClashProxiesRaw(yaml)
        val outbounds = JsonArray().apply {
            // selector (手动选择)
            add(JsonObject().apply {
                addProperty("type", "selector")
                addProperty("tag",  "proxy")
                add("outbounds", JsonArray().apply {
                    nodes.forEach { add(it.name) }
                    add("direct")
                })
            })
            nodes.forEach { add(nodeToSingboxOutbound(it)) }
            add(JsonObject().apply { addProperty("type","direct");   addProperty("tag","direct") })
            add(JsonObject().apply { addProperty("type","block");    addProperty("tag","block")  })
            add(JsonObject().apply { addProperty("type","dns");      addProperty("tag","dns-out") })
        }
        val config = JsonObject().apply {
            add("log",  JsonObject().apply { addProperty("level", "warn") })
            add("dns",  buildSingboxDns())
            add("inbounds", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("type",          "tun")
                    addProperty("tag",           "tun-in")
                    addProperty("inet4_address", "172.19.0.1/30")
                    addProperty("inet6_address", "fdfe:dcba:9876::1/126")
                    addProperty("auto_route",    true)
                    addProperty("strict_route",  true)
                    addProperty("sniff",         true)
                })
                add(JsonObject().apply {
                    addProperty("type",         "mixed")
                    addProperty("tag",          "mixed-in")
                    addProperty("listen",       "127.0.0.1")
                    addProperty("listen_port",  7890)
                })
            })
            add("outbounds", outbounds)
            add("route", buildSingboxRoute())
            add("experimental", JsonObject().apply {
                add("clash_api", JsonObject().apply {
                    addProperty("external_controller", "127.0.0.1:$apiPort")
                    addProperty("secret", "")
                })
            })
        }
        return gson.toJson(config)
    }

    private fun nodeToSingboxOutbound(node: ProxyNode): JsonObject = JsonObject().apply {
        addProperty("tag", node.name)
        val raw = runCatching { gson.fromJson(node.rawJson, JsonObject::class.java) }.getOrDefault(JsonObject())
        when (node.type.lowercase()) {
            "vmess" -> {
                addProperty("type",     "vmess")
                addProperty("server",   node.server)
                addProperty("server_port", node.port)
                addProperty("uuid",     raw["id"]?.asString ?: "")
                addProperty("security", raw["scy"]?.asString ?: "auto")
                addProperty("alter_id", raw["aid"]?.asInt ?: 0)
                add("transport", buildSingboxTransport(raw))
                add("tls", buildSingboxTls(raw))
            }
            "vless" -> {
                addProperty("type",        "vless")
                addProperty("server",      node.server)
                addProperty("server_port", node.port)
                addProperty("uuid",        raw["uuid"]?.asString ?: "")
                val flow = raw["flow"]?.asString ?: ""
                if (flow.isNotEmpty()) addProperty("flow", flow)
                add("transport", buildSingboxTransport(raw))
                add("tls", buildSingboxTls(raw))
            }
            "trojan" -> {
                addProperty("type",        "trojan")
                addProperty("server",      node.server)
                addProperty("server_port", node.port)
                addProperty("password",    raw["password"]?.asString ?: "")
                add("tls", buildSingboxTls(raw))
            }
            "ss", "shadowsocks" -> {
                addProperty("type",        "shadowsocks")
                addProperty("server",      node.server)
                addProperty("server_port", node.port)
                addProperty("method",      raw["cipher"]?.asString ?: "aes-256-gcm")
                addProperty("password",    raw["password"]?.asString ?: "")
            }
            "hysteria2" -> {
                addProperty("type",        "hysteria2")
                addProperty("server",      node.server)
                addProperty("server_port", node.port)
                addProperty("password",    raw["password"]?.asString ?: raw["auth"]?.asString ?: "")
                add("tls", buildSingboxTls(raw))
            }
            "tuic" -> {
                addProperty("type",        "tuic")
                addProperty("server",      node.server)
                addProperty("server_port", node.port)
                addProperty("uuid",        raw["uuid"]?.asString ?: "")
                addProperty("password",    raw["password"]?.asString ?: "")
                add("tls", buildSingboxTls(raw))
            }
            "wireguard" -> {
                addProperty("type",        "wireguard")
                addProperty("server",      node.server)
                addProperty("server_port", node.port)
                addProperty("private_key", raw["private-key"]?.asString ?: "")
                addProperty("peer_public_key", raw["public-key"]?.asString ?: "")
                val ip = raw["ip"]?.asString ?: "172.16.0.2/32"
                add("local_address", JsonArray().apply { add(ip) })
            }
            else -> {
                addProperty("type",        "direct")
                addProperty("_note",       "Protocol ${node.type} fallback to direct")
            }
        }
    }

    private fun buildSingboxTransport(raw: JsonObject): JsonObject {
        val network = raw["network"]?.asString ?: raw["net"]?.asString ?: "tcp"
        return JsonObject().apply {
            when (network) {
                "ws" -> {
                    addProperty("type", "ws")
                    addProperty("path", raw["path"]?.asString ?: "/")
                    val host = raw["host"]?.asString ?: ""
                    if (host.isNotEmpty()) add("headers", JsonObject().apply { addProperty("Host", host) })
                }
                "grpc" -> {
                    addProperty("type", "grpc")
                    addProperty("service_name", raw["grpc-opts"]?.let {
                        runCatching { gson.fromJson(it, JsonObject::class.java)["grpc-service-name"]?.asString }.getOrNull()
                    } ?: "")
                }
                "h2" -> {
                    addProperty("type", "http")
                    addProperty("path", raw["path"]?.asString ?: "/")
                    add("host", JsonArray().apply {
                        val h = raw["host"]?.asString ?: ""; if (h.isNotEmpty()) add(h)
                    })
                }
                else -> addProperty("type", "tcp")
            }
        }
    }

    private fun buildSingboxTls(raw: JsonObject): JsonObject {
        val security = raw["tls"]?.asString ?: raw["security"]?.asString ?: "none"
        return JsonObject().apply {
            addProperty("enabled",     security == "tls" || security == "reality")
            addProperty("server_name", raw["sni"]?.asString ?: raw["host"]?.asString ?: "")
            addProperty("insecure",    raw["skip-cert-verify"]?.asBoolean ?: false)
            val fp = raw["fingerprint"]?.asString ?: raw["fp"]?.asString
            if (!fp.isNullOrEmpty()) add("utls", JsonObject().apply {
                addProperty("enabled",     true)
                addProperty("fingerprint", fp)
            })
            if (security == "reality") {
                add("reality", JsonObject().apply {
                    addProperty("enabled",    true)
                    addProperty("public_key", raw["pbk"]?.asString ?: "")
                    addProperty("short_id",   raw["sid"]?.asString ?: "")
                })
            }
        }
    }

    private fun buildSingboxDns(): JsonObject = JsonObject().apply {
        add("servers", JsonArray().apply {
            add(JsonObject().apply {
                addProperty("tag",     "google")
                addProperty("address", "https://8.8.8.8/dns-query")
                addProperty("detour",  "proxy")
            })
            add(JsonObject().apply {
                addProperty("tag",     "cn-dns")
                addProperty("address", "https://223.5.5.5/dns-query")
                addProperty("detour",  "direct")
            })
            add(JsonObject().apply {
                addProperty("tag",     "local")
                addProperty("address", "local")
                addProperty("detour",  "direct")
            })
            add(JsonObject().apply {
                addProperty("tag",     "fakeip")
                addProperty("address", "fakeip")
            })
        })
        add("rules", JsonArray().apply {
            add(JsonObject().apply {
                addProperty("geosite",  "cn")
                addProperty("server",   "cn-dns")
            })
        })
        add("fakeip", JsonObject().apply {
            addProperty("enabled",       true)
            addProperty("inet4_range",   "198.18.0.0/15")
            addProperty("inet6_range",   "fc00::/18")
        })
        addProperty("independent_cache", true)
        addProperty("final",             "google")
    }

    private fun buildSingboxRoute(): JsonObject = JsonObject().apply {
        add("rules", JsonArray().apply {
            add(JsonObject().apply { addProperty("protocol",  "dns");      addProperty("outbound","dns-out") })
            add(JsonObject().apply { addProperty("geosite",   "cn");       addProperty("outbound","direct")  })
            add(JsonObject().apply { addProperty("geoip",     "cn");       addProperty("outbound","direct")  })
            add(JsonObject().apply { addProperty("geoip",     "private");  addProperty("outbound","direct")  })
        })
        addProperty("final",           "proxy")
        addProperty("auto_detect_interface", true)
    }

    // ── 内部：从 Clash YAML 中快速提取节点（委托给 SubscriptionParser）──────
    private fun parseClashProxiesRaw(yaml: String): List<ProxyNode> =
        com.proxymax.data.parser.SubscriptionParser.parseClashYaml(yaml, profileId = 0)
}
