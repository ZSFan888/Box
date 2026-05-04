package com.proxymax.ui.proxies

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.proxymax.data.model.ProxyNode
import com.proxymax.data.model.ProxyProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxiesScreen(vm: ProxiesViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("节点管理") },
                actions = {
                    if (ui.isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 8.dp))
                    } else {
                        IconButton(onClick = vm::testAllLatencies) {
                            Icon(Icons.Default.Speed, "测速")
                        }
                    }
                    IconButton(onClick = vm::showAddDialog) {
                        Icon(Icons.Default.Add, "添加订阅")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            // ── 订阅列表（横向滚动 Tab）────────────────────────────────────
            if (ui.profiles.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = ui.profiles.indexOf(ui.selectedProfile).coerceAtLeast(0),
                    edgePadding = 12.dp
                ) {
                    ui.profiles.forEach { profile ->
                        Tab(
                            selected = ui.selectedProfile == profile,
                            onClick  = { vm.selectProfile(profile) },
                            text     = {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(profile.name, maxLines = 1)
                                    if (profile.url.isNotBlank()) {
                                        IconButton(
                                            onClick = { vm.refreshProfile(profile) },
                                            modifier = Modifier.size(18.dp)
                                        ) { Icon(Icons.Default.Refresh, "更新", modifier = Modifier.size(14.dp)) }
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // ── 加载状态 ────────────────────────────────────────────────────
            if (ui.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // ── 错误提示 ────────────────────────────────────────────────────
            ui.error?.let { err ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(err, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            // ── 空状态 ──────────────────────────────────────────────────────
            if (ui.profiles.isEmpty() && !ui.isLoading) {
                EmptySubscriptionHint(onAdd = vm::showAddDialog)
            }

            // ── 节点列表 ────────────────────────────────────────────────────
            LazyColumn(
                contentPadding     = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(ui.nodes, key = { it.id }) { node ->
                    NodeItem(node = node, onTest = { vm.testSingleLatency(node) })
                }
            }
        }
    }

    // ── 添加订阅 Dialog ──────────────────────────────────────────────────────
    if (ui.showAddDialog) {
        AddSubscriptionDialog(
            name    = ui.addDialogName,
            url     = ui.addDialogUrl,
            raw     = ui.addDialogRaw,
            onName  = vm::onNameChange,
            onUrl   = vm::onUrlChange,
            onRaw   = vm::onRawChange,
            onConfirm = vm::confirmAddSubscription,
            onDismiss = vm::hideAddDialog
        )
    }
}

// ── 节点卡片 ──────────────────────────────────────────────────────────────────
@Composable
fun NodeItem(node: ProxyNode, onTest: () -> Unit) {
    val latencyColor = when {
        node.latency < 0    -> MaterialTheme.colorScheme.onSurfaceVariant
        node.latency < 150  -> Color(0xFF4CAF50)
        node.latency < 400  -> Color(0xFFFF9800)
        else                -> Color(0xFFF44336)
    }
    val latencyText = when {
        node.latency < 0 -> "—"
        else             -> "${node.latency}ms"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 协议类型 Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(protocolColor(node.type).copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text  = node.type.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = protocolColor(node.type),
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(node.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                     style = MaterialTheme.typography.bodyMedium)
                Text("${node.server}:${node.port}",
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant,
                     maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            // 延迟显示
            Text(
                text  = latencyText,
                color = latencyColor,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .clickable { onTest() }
                    .padding(8.dp)
            )
        }
    }
}

@Composable
fun protocolColor(type: String): Color = when (type.lowercase()) {
    "vmess"     -> Color(0xFF2196F3)
    "vless"     -> Color(0xFF9C27B0)
    "trojan"    -> Color(0xFF009688)
    "ss", "shadowsocks" -> Color(0xFFFF5722)
    "hysteria2" -> Color(0xFFE91E63)
    "tuic"      -> Color(0xFF3F51B5)
    "wireguard" -> Color(0xFF607D8B)
    else        -> Color(0xFF9E9E9E)
}

// ── 空状态提示 ─────────────────────────────────────────────────────────────────
@Composable
fun EmptySubscriptionHint(onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(Icons.Default.CloudDownload, null,
             modifier = Modifier.size(64.dp),
             tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("还没有订阅", style = MaterialTheme.typography.titleMedium)
        Text("添加 Clash 订阅链接或粘贴节点配置",
             style = MaterialTheme.typography.bodySmall,
             color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onAdd) { Text("添加订阅") }
    }
}

// ── 添加订阅 Dialog ────────────────────────────────────────────────────────────
@Composable
fun AddSubscriptionDialog(
    name: String, url: String, raw: String,
    onName: (String) -> Unit, onUrl: (String) -> Unit, onRaw: (String) -> Unit,
    onConfirm: () -> Unit, onDismiss: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加订阅") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = onName,
                    label = { Text("订阅名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("URL 订阅") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("手动粘贴") })
                }

                if (tab == 0) {
                    OutlinedTextField(
                        value = url, onValueChange = onUrl,
                        label = { Text("订阅链接") },
                        placeholder = { Text("https://...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = raw, onValueChange = onRaw,
                        label = { Text("配置内容") },
                        placeholder = { Text("粘贴 Clash YAML / Base64 / URI") },
                        minLines = 5, maxLines = 10,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = name.isNotBlank() && (url.isNotBlank() || raw.isNotBlank())
            ) { Text("确认") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
