package com.proxymax.ui.proxies

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.proxymax.core.stats.ProxyInfo
import com.proxymax.data.model.ProxyProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxiesScreen(vm: ProxiesViewModel = hiltViewModel()) {
    val ui       by vm.ui.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val proxies  by vm.sortedProxies.collectAsState()

    // 本地 UI 状态（不需要放进 ViewModel）
    var showAddDialog   by remember { mutableStateOf(false) }
    var showScanImport  by remember { mutableStateOf(false) }
    val activeProfileId = profiles.firstOrNull { it.isActive }?.id

    // 错误 snackbar
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(ui.errorMsg) {
        ui.errorMsg?.let { msg ->
            snackbar.showSnackbar(msg)
            vm.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title   = { Text("节点", style = MaterialTheme.typography.titleMedium) },
                actions = {
                    IconButton(onClick = vm::testAllDelays) {
                        if (ui.isTesting)
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else
                            Icon(Icons.Default.Speed, "测速")
                    }
                    IconButton(onClick = { showScanImport = true }) {
                        Icon(Icons.Default.QrCodeScanner, "扫码导入")
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, "添加订阅")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost   = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (proxies.isEmpty() && profiles.isEmpty() && !ui.isLoading) {
            EmptyProxies(
                onAdd    = { showAddDialog = true },
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start  = 16.dp, end = 16.dp,
                    top    = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 订阅列表
                if (profiles.isNotEmpty()) {
                    item { SectionLabel("订阅") }
                    items(profiles, key = { it.id }) { p ->
                        ProfileRow(
                            profile    = p,
                            isActive   = p.id == activeProfileId,
                            onActivate = { vm.setActiveProfile(p.id) },
                            onRefresh  = { vm.refreshProfile(p) },
                            onDelete   = { vm.deleteProfile(p) }
                        )
                    }
                    item { Spacer(Modifier.height(4.dp)) }
                }

                // 代理节点
                if (proxies.isNotEmpty()) {
                    item { SectionLabel("节点") }
                    items(proxies, key = { it.name }) { info ->
                        ProxyRow(
                            info   = info,
                            onTest = { vm.testSingleDelay(info.name) }
                        )
                    }
                }

                // 加载中占位
                if (ui.isLoading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }

    // 添加订阅弹窗
    // 扫码导入 launcher
    val context = androidx.compose.ui.platform.LocalContext.current
    val scanLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            vm.refreshProfiles()
        }
    }
    if (showScanImport) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            val intent = android.content.Intent(context,
                com.proxymax.ui.scan.ScanImportActivity::class.java)
            scanLauncher.launch(intent)
            showScanImport = false
        }
    }

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
}

// ── 通用行组件 ──────────────────────────────────────────────────────────────

@Composable
fun SectionLabel(text: String) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.labelMedium,
        color    = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
fun ProfileRow(
    profile:    ProxyProfile,
    isActive:   Boolean,
    onActivate: () -> Unit,
    onRefresh:  () -> Unit,
    onDelete:   () -> Unit
) {
    Surface(
        shape          = MaterialTheme.shapes.medium,
        color          = if (isActive) MaterialTheme.colorScheme.primaryContainer
                         else MaterialTheme.colorScheme.surface,
        tonalElevation = if (isActive) 0.dp else 1.dp,
        modifier       = Modifier.fillMaxWidth().clickable { onActivate() }
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector        = if (isActive) Icons.Default.CheckCircle
                                     else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint     = if (isActive) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    profile.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                )
                if (profile.url.isNotBlank())
                    Text(
                        profile.url,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
            }
            IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Refresh, "刷新", Modifier.size(18.dp),
                     tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.DeleteOutline, "删除", Modifier.size(18.dp),
                     tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun ProxyRow(info: ProxyInfo, onTest: () -> Unit) {
    val latency = info.history.lastOrNull() ?: -1
    Surface(
        shape          = MaterialTheme.shapes.medium,
        color          = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier       = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(info.name,
                     style = MaterialTheme.typography.titleSmall,
                     color = MaterialTheme.colorScheme.onSurface)
                Text(info.type,
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text  = if (latency > 0) "${latency}ms" else "—",
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    latency <= 0  -> MaterialTheme.colorScheme.onSurfaceVariant
                    latency < 200 -> MaterialTheme.colorScheme.primary
                    latency < 500 -> MaterialTheme.colorScheme.secondary
                    else          -> MaterialTheme.colorScheme.error
                }
            )
            IconButton(onClick = onTest, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.NetworkCheck, "测速", Modifier.size(18.dp),
                     tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun EmptyProxies(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.CloudOff, null,
             Modifier.size(48.dp),
             tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        Spacer(Modifier.height(16.dp))
        Text("暂无订阅",
             style = MaterialTheme.typography.titleSmall,
             color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        Text("添加订阅链接以开始使用",
             style = MaterialTheme.typography.bodySmall,
             color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onAdd) { Text("添加订阅") }
    }
}

// ── 添加订阅弹窗 ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSubscriptionDialog(
    onDismiss: () -> Unit,
    onAddUrl:  (name: String, url: String)  -> Unit,
    onAddRaw:  (name: String, raw: String)  -> Unit
) {
    var tabIndex by remember { mutableStateOf(0) }
    var name     by remember { mutableStateOf("") }
    var url      by remember { mutableStateOf("") }
    var raw      by remember { mutableStateOf("") }

    val valid = name.isNotBlank() && when (tabIndex) {
        0    -> url.isNotBlank()
        else -> raw.isNotBlank()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("添加订阅", style = MaterialTheme.typography.titleSmall) },
        text    = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TabRow(selectedTabIndex = tabIndex) {
                    Tab(selected = tabIndex == 0,
                        onClick  = { tabIndex = 0 },
                        text     = { Text("订阅 URL", style = MaterialTheme.typography.labelMedium) })
                    Tab(selected = tabIndex == 1,
                        onClick  = { tabIndex = 1 },
                        text     = { Text("粘贴配置", style = MaterialTheme.typography.labelMedium) })
                }
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text("名称") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
                if (tabIndex == 0) {
                    OutlinedTextField(
                        value         = url,
                        onValueChange = { url = it },
                        label         = { Text("订阅链接") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )
                } else {
                    OutlinedTextField(
                        value         = raw,
                        onValueChange = { raw = it },
                        label         = { Text("配置内容") },
                        minLines      = 4,
                        maxLines      = 8,
                        modifier      = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick  = {
                    if (tabIndex == 0) onAddUrl(name.trim(), url.trim())
                    else               onAddRaw(name.trim(), raw.trim())
                },
                enabled  = valid
            ) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
