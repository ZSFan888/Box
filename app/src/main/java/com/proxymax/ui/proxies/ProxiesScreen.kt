package com.proxymax.ui.proxies

import androidx.compose.foundation.background
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Modifier
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxiesScreen(vm: ProxiesViewModel = hiltViewModel()) {
    val ui      by vm.ui.collectAsState()
    val proxies by vm.sortedProxies.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("节点") },
                actions = {
                    if (ui.isTesting) {
                        CircularProgressIndicator(Modifier.size(24.dp).padding(end = 8.dp))
                    } else {
                        IconButton(onClick = vm::testAllDelays) {
                            Icon(Icons.Default.Speed, "批量测速")
                        }
                    }
                    IconButton(onClick = vm::loadProxies) {
                        Icon(Icons.Default.Refresh, "刷新")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            // ── 策略组横向 Tab ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ui.groups.keys.forEach { group ->
                    FilterChip(
                        selected = group == ui.selectedGroup,
                        onClick  = { vm.selectGroup(group) },
                        label    = { Text(group) },
                        leadingIcon = if (group == ui.selectedGroup) ({
                            Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                        }) else null
                    )
                }
            }

            HorizontalDivider()

            // ── 骨架 / 节点列表 ────────────────────────────────────────
            if (ui.isLoading) {
                SkeletonList()
            } else if (proxies.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无节点，请先添加订阅", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn {
                    items(proxies, key = { it.name }) { proxy ->
                        ProxyItem(
                            proxy       = proxy,
                            selected    = proxy.name == ui.selectedProxy,
                            onSelect    = { vm.selectProxy(proxy.name) },
                            onTestDelay = { vm.testSingleDelay(proxy.name) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProxyItem(
    proxy:       com.proxymax.core.stats.ProxyInfo,
    selected:    Boolean,
    onSelect:    () -> Unit,
    onTestDelay: () -> Unit
) {
    val delay = proxy.history.lastOrNull() ?: -1
    val delayColor = when {
        delay < 0   -> Color.Gray
        delay < 150 -> Color(0xFF4CAF50)   // 绿
        delay < 400 -> Color(0xFFFF9800)   // 橙
        else        -> Color(0xFFF44336)   // 红
    }
    val delayText = if (delay < 0) "超时" else "${delay}ms"

    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // 协议 Badge
                Surface(
                    color  = protocolColor(proxy.type),
                    shape  = MaterialTheme.shapes.small
                ) {
                    Text(
                        proxy.type.uppercase(),
                        style    = MaterialTheme.typography.labelSmall,
                        color    = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                Text(proxy.name, style = MaterialTheme.typography.bodyMedium)
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 延迟按钮（点击测速）
                TextButton(onClick = onTestDelay, contentPadding = PaddingValues(0.dp)) {
                    Text(delayText, color = delayColor, style = MaterialTheme.typography.labelMedium)
                }
                // 选中按钮
                RadioButton(selected = selected, onClick = onSelect)
            }
        },
        modifier = Modifier.clickable { onSelect() }
    )
}

@Composable
fun SkeletonList() {
    LazyColumn {
        items(10) {
            ListItem(
                headlineContent = {
                    Box(
                        Modifier
                            .fillMaxWidth(0.6f)
                            .height(14.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.shapes.small
                            )
                    )
                },
                trailingContent = {
                    Box(
                        Modifier
                            .width(48.dp)
                            .height(14.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.shapes.small
                            )
                    )
                }
            )
        }
    }
}

fun protocolColor(type: String): Color = when (type.uppercase()) {
    "VMESS"      -> Color(0xFF1976D2)
    "VLESS"      -> Color(0xFF7B1FA2)
    "TROJAN"     -> Color(0xFF388E3C)
    "SS","SHADOWSOCKS" -> Color(0xFFE64A19)
    "HYSTERIA2"  -> Color(0xFFD81B60)
    "TUIC"       -> Color(0xFF00897B)
    "WIREGUARD"  -> Color(0xFF5D4037)
    else         -> Color(0xFF546E7A)
}
