package com.proxymax.ui.settings

import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Modifier
import com.proxymax.data.model.PerAppMode

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: SettingsViewModel = hiltViewModel(),
    onNavigatePerApp: () -> Unit = {}
) {
    val ui by vm.ui.collectAsState()
    val scroll = rememberScrollState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // ── 内核 ─────────────────────────────────────────────────
            SectionTitle("内核设置")
            SwitchItem("自动选择内核", ui.autoSelectCore, vm::toggleAutoSelectCore,
                       "根据配置格式自动推荐最合适的内核")
            if (!ui.autoSelectCore) {
                SegmentedItem("默认内核", CoreType.entries.map { it.displayName },
                    selected = ui.defaultCore.ordinal,
                    onSelect = { vm.setDefaultCore(CoreType.entries[it]) })
            }

            // ── DNS ──────────────────────────────────────────────────
            SectionTitle("DNS 设置")
            SwitchItem("FakeIP 模式", ui.enableFakeIp, vm::toggleFakeIp,
                       "开启后 DNS 流量由内核接管，防止 DNS 泄漏")
            SwitchItem("IPv6 支持", ui.enableIpv6, vm::toggleIpv6,
                       "开启后同时监听 IPv6 地址")

            // ── 端口 ─────────────────────────────────────────────────
            SectionTitle("本地端口")
            NumberItem("混合代理端口", ui.mixedPort, onConfirm = vm::setMixedPort)
            NumberItem("API 端口", ui.apiPort, onConfirm = vm::setApiPort)

            // ── 分流规则 ────────────────────────────────────────────
            SectionTitle("分流规则微调")
            SwitchItem(
                title    = "国内直连（geosite:cn / geoip:cn）",
                checked  = ui.geositeCnDirect,
                onToggle = vm::toggleGeositeCnDirect,
                desc     = "开启后国内域名/IP 直连，不走代理"
            )
            SwitchItem(
                title    = "局域网直连（geoip:private）",
                checked  = ui.geoipPrivateDirect,
                onToggle = vm::toggleGeoipPrivateDirect,
                desc     = "开启后局域网 IP 直连，不走代理"
            )

            // ── 应用分流 ────────────────────────────────────────────
            SectionTitle("应用分流")
            ListItem(
                headlineContent   = { Text("应用分流设置") },
                supportingContent = {
                    val modeText = when (ui.perAppMode) {
                        PerAppMode.GLOBAL    -> "全局代理"
                        PerAppMode.WHITELIST -> "白名单模式"
                        PerAppMode.BLACKLIST -> "黑名单模式"
                    }
                    Text("当前：$modeText")
                },
                trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                modifier = Modifier.clickable { onNavigatePerApp() }
            )

            // ── 系统 ─────────────────────────────────────────────────
            SectionTitle("系统")
            DropdownItem("日志级别",
                options  = listOf("debug", "info", "warning", "error"),
                selected = ui.logLevel,
                onSelect = vm::setLogLevel)
            SwitchItem("开机自启", ui.startOnBoot, vm::toggleStartOnBoot,
                       "开机后自动恢复上次连接状态")
        }
    }
}

// ── 通用控件 ──────────────────────────────────────────────────────────────

@Composable
fun SectionTitle(title: String) {
    Text(title,
        style    = MaterialTheme.typography.titleSmall,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
    HorizontalDivider()
}

@Composable
fun SwitchItem(title: String, checked: Boolean, onToggle: () -> Unit, desc: String = "") {
    ListItem(
        headlineContent   = { Text(title) },
        supportingContent = if (desc.isNotEmpty()) ({ Text(desc, style = MaterialTheme.typography.bodySmall) }) else null,
        trailingContent   = { Switch(checked = checked, onCheckedChange = { onToggle() }) }
    )
}

@Composable
fun SegmentedItem(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall,
             color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, opt ->
                SegmentedButton(
                    shape    = SegmentedButtonDefaults.itemShape(i, options.size),
                    selected = i == selected,
                    onClick  = { onSelect(i) },
                    label    = { Text(opt) }
                )
            }
        }
    }
}

@Composable
fun NumberItem(label: String, value: Int, onConfirm: (Int) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var text    by remember(value) { mutableStateOf(value.toString()) }

    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            if (editing) {
                OutlinedTextField(
                    value         = text,
                    onValueChange = { text = it },
                    singleLine    = true,
                    modifier      = Modifier.width(100.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    trailingIcon = {
                        IconButton(onClick = {
                            text.toIntOrNull()?.let { onConfirm(it) }
                            editing = false
                        }) { Icon(Icons.Default.Check, null) }
                    }
                )
            } else {
                TextButton(onClick = { editing = true }) { Text("$value") }
            }
        }
    )
}

@Composable
fun DropdownItem(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Box {
                TextButton(onClick = { expanded = true }) {
                    Text(selected)
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { opt ->
                        DropdownMenuItem(
                            text    = { Text(opt) },
                            onClick = { onSelect(opt); expanded = false },
                            leadingIcon = if (opt == selected) ({
                                Icon(Icons.Default.Check, null)
                            }) else null
                        )
                    }
                }
            }
        }
    )
}
