package com.proxymax.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.proxymax.core.*
import com.proxymax.ui.widget.SpeedChart
import com.proxymax.ui.widget.toSpeedStr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(vm: DashboardViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val noProfileError by vm.noProfileError.collectAsState()
    val scroll = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(noProfileError) {
        if (noProfileError) {
            snackbarHostState.showSnackbar("请先在「节点」页面添加订阅并激活")
            vm.clearNoProfileError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ProxyMax") },
                actions = {
                    // 内核版本信息
                    if (state is CoreState.Running) {
                        val core = (state as CoreState.Running).core
                        AssistChip(
                            onClick    = {},
                            label      = { Text(core.displayName) },
                            leadingIcon = { Icon(Icons.Default.Memory, null, Modifier.size(16.dp)) }
                        )
                    }
                }
            )
        }
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 主连接卡片 ──────────────────────────────────────────────────
            ConnectCard(state = state, onToggle = vm::toggleVpn, onSwitch = vm::switchCore)

            // ── 流量图表（仅运行中显示）──────────────────────────────────────
            if (state is CoreState.Running) {
                val stats = (state as CoreState.Running).stats
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("实时速率", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        SpeedChart(stats = stats, modifier = Modifier.fillMaxWidth())
                    }
                }

                // ── 流量累计 ───────────────────────────────────────────────
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem("↑ 总上传",  stats.totalUpload.toSpeedStr().replace("/s",""))
                        StatItem("↓ 总下载",  stats.totalDownload.toSpeedStr().replace("/s",""))
                        StatItem("🔗 连接数", "${stats.connections}")
                    }
                }
            }

            // ── 错误提示 ────────────────────────────────────────────────────
            if (state is CoreState.Error) {
                val err = state as CoreState.Error
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                        Column {
                            Text("${err.core.displayName} 启动失败",
                                 color = MaterialTheme.colorScheme.onErrorContainer,
                                 style = MaterialTheme.typography.titleSmall)
                            Text(err.message,
                                 color = MaterialTheme.colorScheme.onErrorContainer,
                                 style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectCard(state: CoreState, onToggle: () -> Unit, onSwitch: (CoreType) -> Unit) {
    val isRunning   = state is CoreState.Running
    val isSwitching = state is CoreState.Switching
    val isStarting  = state is CoreState.Starting

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = if (isRunning) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) else CardDefaults.cardColors()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = when (state) {
                            is CoreState.Running   -> "已连接"
                            is CoreState.Starting  -> "正在连接…"
                            is CoreState.Switching -> "切换内核…"
                            is CoreState.Error     -> "连接失败"
                            else                   -> "未连接"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (isRunning) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                    )
                    if (isSwitching) {
                        val s = state as CoreState.Switching
                        Text("${s.from.displayName} → ${s.to.displayName}",
                             style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // 连接按钮
                if (isStarting || isSwitching) {
                    CircularProgressIndicator(Modifier.size(48.dp))
                } else {
                    FilledIconButton(
                        onClick = onToggle,
                        modifier = Modifier.size(56.dp),
                        colors = if (isRunning)
                            IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        else IconButtonDefaults.filledIconButtonColors()
                    ) {
                        Icon(
                            if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isRunning) "断开" else "连接",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // ── 内核切换 Chips ────────────────────────────────────────────
            Text("切换内核",
                 style = MaterialTheme.typography.labelMedium,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CoreType.entries.forEach { core ->
                    val selected = state is CoreState.Running && (state as CoreState.Running).core == core
                    FilterChip(
                        selected = selected,
                        onClick  = { onSwitch(core) },
                        label    = { Text(core.displayName) },
                        enabled  = !isSwitching && !isStarting,
                        leadingIcon = if (selected) ({
                            Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                        }) else null
                    )
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall,
             color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
