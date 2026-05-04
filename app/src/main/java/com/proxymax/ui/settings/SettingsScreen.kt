package com.proxymax.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("设置") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // ════ 内核 ════
            item { SectionHeader("内核设置") }
            item {
                ListItem(
                    headlineContent   = { Text("自动选择最优内核") },
                    supportingContent = { Text("根据配置格式和节点协议自动推荐内核") },
                    trailingContent   = {
                        Switch(s.autoSelectCore, onCheckedChange = { vm.update { copy(autoSelectCore = it) } })
                    }
                )
            }
            item {
                DropdownSetting(
                    title   = "默认内核",
                    value   = s.preferredCore.displayName,
                    options = com.proxymax.core.CoreType.entries.map { it.displayName },
                    onSelect = { idx ->
                        vm.update { copy(preferredCore = com.proxymax.core.CoreType.entries[idx]) }
                    }
                )
            }

            // ════ DNS ════
            item { SectionHeader("DNS 设置") }
            item {
                SwitchListItem(
                    title    = "FakeIP 模式",
                    subtitle = "虚假 IP 池，防 DNS 泄漏（推荐开启）",
                    checked  = s.enableFakeIP,
                    onCheck  = { vm.update { copy(enableFakeIP = it) } }
                )
            }
            item {
                SwitchListItem(
                    title   = "IPv6 支持",
                    subtitle = "TUN 接口监听 IPv6 流量",
                    checked = s.enableIPv6,
                    onCheck = { vm.update { copy(enableIPv6 = it) } }
                )
            }

            // ════ 代理端口 ════
            item { SectionHeader("本地端口") }
            item {
                NumberInputItem("混合代理端口 (HTTP+SOCKS5)", s.mixedPort) {
                    vm.update { copy(mixedPort = it) }
                }
            }
            item {
                NumberInputItem("API 端口", s.apiPort) {
                    vm.update { copy(apiPort = it) }
                }
            }

            // ════ 路由 ════
            item { SectionHeader("路由设置") }
            item {
                SwitchListItem(
                    title   = "绕过局域网",
                    subtitle = "192.168.x.x / 10.x.x.x 直连",
                    checked = s.bypassLan,
                    onCheck = { vm.update { copy(bypassLan = it) } }
                )
            }
            item {
                val labels = listOf("全局代理", "白名单（仅代理选中 App）", "黑名单（排除选中 App）")
                DropdownSetting(
                    title   = "应用分流模式",
                    value   = labels[s.perAppProxyMode.ordinal],
                    options = labels,
                    onSelect = { idx ->
                        vm.update { copy(perAppProxyMode = com.proxymax.data.model.PerAppMode.entries[idx]) }
                    }
                )
            }

            // ════ 日志 ════
            item { SectionHeader("日志") }
            item {
                DropdownSetting(
                    title   = "日志级别",
                    value   = s.logLevel,
                    options = listOf("debug", "info", "warn", "error", "silent"),
                    onSelect = { idx ->
                        val levels = listOf("debug", "info", "warn", "error", "silent")
                        vm.update { copy(logLevel = levels[idx]) }
                    }
                )
            }

            // ════ 系统 ════
            item { SectionHeader("系统") }
            item {
                SwitchListItem(
                    title   = "开机自启",
                    subtitle = "系统启动后自动连接上次使用的配置",
                    checked = s.startOnBoot,
                    onCheck = { vm.update { copy(startOnBoot = it) } }
                )
            }
        }
    }
}

// ── 通用 UI 组件 ──────────────────────────────────────────────────────────────
@Composable
fun SectionHeader(text: String) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.labelMedium,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp)
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
fun SwitchListItem(title: String, subtitle: String, checked: Boolean, onCheck: (Boolean) -> Unit) {
    ListItem(
        headlineContent   = { Text(title) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        trailingContent   = { Switch(checked, onCheck) }
    )
}

@Composable
fun NumberInputItem(label: String, value: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            OutlinedTextField(
                value         = text,
                onValueChange = { v ->
                    text = v
                    v.toIntOrNull()?.let { onChange(it) }
                },
                modifier  = Modifier.width(100.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium
            )
        }
    )
}

@Composable
fun DropdownSetting(title: String, value: String, options: List<String>, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = value, onValueChange = {},
                    readOnly = true, singleLine = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.menuAnchor().width(180.dp)
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEachIndexed { idx, opt ->
                        DropdownMenuItem(
                            text = { Text(opt) },
                            onClick = { onSelect(idx); expanded = false }
                        )
                    }
                }
            }
        }
    )
}
