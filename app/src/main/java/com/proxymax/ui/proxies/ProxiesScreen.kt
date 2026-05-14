package com.proxymax.ui.proxies

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.proxymax.core.stats.ProxyInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxiesScreen(vm: ProxiesViewModel = hiltViewModel()) {
    val ui          by vm.ui.collectAsState()
    val proxies     by vm.sortedProxies.collectAsState()
    val profiles    by vm.profiles.collectAsState()

    // 对话框状态
    var showAddDialog   by remember { mutableStateOf(false) }
    var showProfileList by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // 当前订阅名称（可点击切换）
                    val activeName = profiles.find { it.isActive }?.name ?: "未选择订阅"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showProfileList = true }
                    ) {
                        Text("节点", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(8.dp))
                        AssistChip(
                            onClick = { showProfileList = true },
                            label = {
                                Text(
                                    activeName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 140.dp)
                                )
                            },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp)) }
                        )
                    }
                },
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
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon    = { Icon(Icons.Default.Add, null) },
                text    = { Text("添加订阅") }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            // ── 策略组横向 Tab ──────────────────────────────────────────
            if (ui.groups.isNotEmpty()) {
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
            }

            // ── 节点列表 ────────────────────────────────────────────────
            when {
                ui.isLoading -> SkeletonList()
                proxies.isEmpty() -> EmptyProxiesState(onAdd = { showAddDialog = true })
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
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

    // ── 添加订阅对话框 ───────────────────────────────────────────────────
    if (showAddDialog) {
        AddSubscriptionDialog(
            onDismiss = { showAddDialog = false },
            onAddUrl  = { name, url ->
                vm.addSubscriptionByUrl(name, url)
                showAddDialog = false
            },
            onAddRaw  = { name, raw ->
                vm.addSubscriptionByRaw(name, raw)
                showAddDialog = false
            }
        )
    }

    // ── 订阅列表管理 ─────────────────────────────────────────────────────
    if (showProfileList) {
        ProfileListSheet(
            profiles  = profiles,
            onSelect  = { vm.setActiveProfile(it); showProfileList = false },
            onDelete  = { vm.deleteProfile(it) },
            onRefresh = { vm.refreshProfile(it) },
            onDismiss = { showProfileList = false }
        )
    }

    // ── 错误 Snackbar ────────────────────────────────────────────────────
    ui.errorMsg?.let { msg ->
        LaunchedEffect(msg) {
            // show snackbar via ui
            vm.clearError()
        }
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                TextButton(onClick = vm::clearError) { Text("关闭") }
            }
        ) { Text(msg) }
    }
}

// ── 添加订阅对话框 ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSubscriptionDialog(
    onDismiss: () -> Unit,
    onAddUrl:  (name: String, url: String) -> Unit,
    onAddRaw:  (name: String, raw: String) -> Unit
) {
    var tabIndex by remember { mutableStateOf(0) }
    var name     by remember { mutableStateOf("") }
    var url      by remember { mutableStateOf("") }
    var raw      by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加订阅") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Tab 切换
                TabRow(selectedTabIndex = tabIndex) {
                    Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 },
                        text = { Text("订阅链接") })
                    Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 },
                        text = { Text("粘贴配置") })
                }

                // 名称（共用）
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text("名称（可选）") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )

                if (tabIndex == 0) {
                    // URL 订阅
                    OutlinedTextField(
                        value         = url,
                        onValueChange = { url = it },
                        label         = { Text("订阅链接") },
                        placeholder   = { Text("https://...") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth()
                    )
                } else {
                    // 粘贴配置
                    OutlinedTextField(
                        value         = raw,
                        onValueChange = { raw = it },
                        label         = { Text("粘贴 YAML / Base64 配置") },
                        placeholder   = { Text("proxies:\n  - name: ...") },
                        minLines      = 6,
                        maxLines      = 10,
                        modifier      = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            val enabled = if (tabIndex == 0) url.isNotBlank() else raw.isNotBlank()
            Button(
                onClick = {
                    val n = name.ifBlank { if (tabIndex == 0) "订阅" else "手动配置" }
                    if (tabIndex == 0) onAddUrl(n, url.trim())
                    else onAddRaw(n, raw.trim())
                },
                enabled = enabled
            ) { Text("确认") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// ── 订阅列表底部弹窗 ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileListSheet(
    profiles:  List<com.proxymax.data.model.ProxyProfile>,
    onSelect:  (Int) -> Unit,
    onDelete:  (com.proxymax.data.model.ProxyProfile) -> Unit,
    onRefresh: (com.proxymax.data.model.ProxyProfile) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 32.dp)) {
            Text(
                "订阅管理",
                style    = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            if (profiles.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无订阅", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                profiles.forEach { profile ->
                    ListItem(
                        headlineContent = { Text(profile.name) },
                        supportingContent = {
                            Text(
                                if (profile.url.isNotBlank()) profile.url else "手动配置",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingContent = {
                            if (profile.isActive) {
                                Icon(Icons.Default.CheckCircle, null,
                                     tint = MaterialTheme.colorScheme.primary)
                            } else {
                                Icon(Icons.Default.RadioButtonUnchecked, null)
                            }
                        },
                        trailingContent = {
                            Row {
                                if (profile.url.isNotBlank()) {
                                    IconButton(onClick = { onRefresh(profile) }) {
                                        Icon(Icons.Default.Refresh, "更新", Modifier.size(20.dp))
                                    }
                                }
                                IconButton(onClick = { onDelete(profile) }) {
                                    Icon(Icons.Default.Delete, "删除",
                                         Modifier.size(20.dp),
                                         tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        },
                        modifier = Modifier.clickable { onSelect(profile.id) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

// ── 空状态 ─────────────────────────────────────────────────────────────────

@Composable
fun EmptyProxiesState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.CloudOff, null,
             modifier = Modifier.size(64.dp),
             tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text("还没有订阅", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "点击下方「添加订阅」按钮，\n输入订阅链接或粘贴配置文件",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAdd) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("添加订阅")
        }
    }
}

// ── 节点 Item ───────────────────────────────────────────────────────────────

@Composable
fun ProxyItem(
    proxy:       ProxyInfo,
    selected:    Boolean,
    onSelect:    () -> Unit,
    onTestDelay: () -> Unit
) {
    val delay = proxy.history.lastOrNull() ?: -1
    val delayColor = when {
        delay < 0   -> Color.Gray
        delay < 150 -> Color(0xFF4CAF50)
        delay < 400 -> Color(0xFFFF9800)
        else        -> Color(0xFFF44336)
    }
    val delayText = if (delay < 0) "超时" else "${delay}ms"

    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(color = protocolColor(proxy.type), shape = MaterialTheme.shapes.small) {
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
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onTestDelay, contentPadding = PaddingValues(0.dp)) {
                    Text(delayText, color = delayColor, style = MaterialTheme.typography.labelMedium)
                }
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
                        Modifier.fillMaxWidth(0.6f).height(14.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.shapes.small)
                    )
                },
                trailingContent = {
                    Box(
                        Modifier.width(48.dp).height(14.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.shapes.small)
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
