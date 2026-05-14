package com.proxymax.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.proxymax.core.CoreType
import com.proxymax.data.model.PerAppMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm:               SettingsViewModel = hiltViewModel(),
    onNavigatePerApp: () -> Unit
) {
    val ui by vm.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title  = { Text("设置", style = MaterialTheme.typography.titleMedium) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // ── 核心设置 ────────────────────────────────────────────────────
            SettingsSectionHeader("内核")

            SettingsToggleRow(
                title    = "自动选择内核",
                subtitle = "根据配置格式自动推断最优内核",
                checked  = ui.autoSelectCore,
                onToggle = vm::toggleAutoSelectCore
            )

            if (!ui.autoSelectCore) {
                SettingsSectionHeader("默认内核", small = true)
                Row(
                    Modifier.padding(start = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CoreType.entries.forEach { core ->
                        FilterChip(
                            selected = ui.defaultCore == core,
                            onClick  = { vm.setDefaultCore(core) },
                            label    = { Text(core.displayName, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }
            }

            // ── 网络设置 ────────────────────────────────────────────────────
            SettingsSectionHeader("网络")

            SettingsToggleRow(
                title    = "FakeIP",
                subtitle = "通过虚假 IP 提升 DNS 性能",
                checked  = ui.enableFakeIp,
                onToggle = vm::toggleFakeIp
            )
            SettingsToggleRow(
                title    = "IPv6",
                subtitle = "启用 IPv6 隧道流量",
                checked  = ui.enableIpv6,
                onToggle = vm::toggleIpv6
            )

            var portDialog by remember { mutableStateOf<String?>(null) }

            SettingsClickRow(
                title    = "混合代理端口",
                subtitle = "${ui.mixedPort}",
                onClick  = { portDialog = "mixed" }
            )
            SettingsClickRow(
                title    = "API 端口",
                subtitle = "${ui.apiPort}",
                onClick  = { portDialog = "api" }
            )

            portDialog?.let { key ->
                val current = if (key == "mixed") ui.mixedPort else ui.apiPort
                PortInputDialog(
                    label    = if (key == "mixed") "混合代理端口" else "API 端口",
                    current  = current,
                    onConfirm = { p ->
                        if (key == "mixed") vm.setMixedPort(p) else vm.setApiPort(p)
                        portDialog = null
                    },
                    onDismiss = { portDialog = null }
                )
            }

            // ── 分流规则 ───────────────────────────────────────────────────
            SettingsSectionHeader("分流规则")

            SettingsToggleRow(
                title    = "国内直连（geosite:cn）",
                subtitle = "中国大陆域名直接连接",
                checked  = ui.geositeCnDirect,
                onToggle = vm::toggleGeositeCnDirect
            )
            SettingsToggleRow(
                title    = "私有地址直连",
                subtitle = "局域网 / 回环地址不走代理",
                checked  = ui.geoipPrivateDirect,
                onToggle = vm::toggleGeoipPrivateDirect
            )

            // ── 应用分流 ───────────────────────────────────────────────────
            SettingsSectionHeader("应用分流")

            val modeLabels = mapOf(
                PerAppMode.GLOBAL    to "全局",
                PerAppMode.WHITELIST to "白名单",
                PerAppMode.BLACKLIST to "黑名单"
            )
            Row(
                Modifier.padding(start = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PerAppMode.entries.forEach { mode ->
                    FilterChip(
                        selected = ui.perAppMode == mode,
                        onClick  = { vm.setPerAppMode(mode) },
                        label    = { Text(modeLabels[mode] ?: mode.name, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            if (ui.perAppMode != PerAppMode.GLOBAL) {
                SettingsClickRow(
                    title    = "选择应用",
                    subtitle = if (ui.perAppMode == PerAppMode.WHITELIST) "选中的应用走代理" else "选中的应用不走代理",
                    onClick  = onNavigatePerApp,
                    trailingIcon = Icons.Default.ChevronRight
                )
            }

            // ── 其他 ───────────────────────────────────────────────────────
            SettingsSectionHeader("其他")

            SettingsToggleRow(
                title    = "开机自启",
                subtitle = "设备启动时自动连接代理",
                checked  = ui.startOnBoot,
                onToggle = vm::toggleStartOnBoot
            )

            val levels = listOf("debug","info","warning","error","silent")
            var logExpanded by remember { mutableStateOf(false) }
            SettingsClickRow(
                title    = "日志级别",
                subtitle = ui.logLevel,
                onClick  = { logExpanded = true }
            )
            DropdownMenu(
                expanded        = logExpanded,
                onDismissRequest = { logExpanded = false },
                modifier        = Modifier.padding(horizontal = 16.dp)
            ) {
                levels.forEach { level ->
                    DropdownMenuItem(
                        text    = { Text(level, style = MaterialTheme.typography.bodyMedium) },
                        onClick = { vm.setLogLevel(level); logExpanded = false },
                        trailingIcon = if (ui.logLevel == level) ({
                            Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                        }) else null
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── 通用组件 ────────────────────────────────────────────────────────────────
@Composable
fun SettingsSectionHeader(text: String, small: Boolean = false) {
    Text(
        text     = text,
        style    = if (small) MaterialTheme.typography.labelSmall
                   else MaterialTheme.typography.labelMedium,
        color    = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
fun SettingsToggleRow(
    title:    String,
    subtitle: String,
    checked:  Boolean,
    onToggle: () -> Unit
) {
    Surface(
        shape          = MaterialTheme.shapes.medium,
        color          = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier       = Modifier.fillMaxWidth().clickable { onToggle() }
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium,
                     color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = { onToggle() },
                   modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun SettingsClickRow(
    title:         String,
    subtitle:      String,
    onClick:       () -> Unit,
    trailingIcon:  androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Edit
) {
    Surface(
        shape          = MaterialTheme.shapes.medium,
        color          = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier       = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium,
                     color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(trailingIcon, null,
                 Modifier.size(18.dp),
                 tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PortInputDialog(
    label:     String,
    current:   Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf(current.toString()) }
    val valid  = input.toIntOrNull()?.let { it in 1..65535 } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text(label, style = MaterialTheme.typography.titleSmall) },
        text    = {
            OutlinedTextField(
                value            = input,
                onValueChange    = { input = it.filter { c -> c.isDigit() } },
                singleLine       = true,
                isError          = !valid,
                supportingText   = { if (!valid) Text("请输入 1–65535 之间的端口") },
                keyboardOptions  = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick  = { input.toIntOrNull()?.let { onConfirm(it) } },
                enabled  = valid
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
