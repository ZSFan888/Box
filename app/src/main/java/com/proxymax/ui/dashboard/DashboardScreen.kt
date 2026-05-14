package com.proxymax.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import com.proxymax.core.*
import com.proxymax.ui.widget.SpeedChart
import com.proxymax.ui.widget.toSpeedStr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(vm: DashboardViewModel = hiltViewModel()) {
    val state          by vm.state.collectAsState()
    val liveStats      by vm.liveStats.collectAsState()
    val noProfileError by vm.noProfileError.collectAsState()
    val snackbar       = remember { SnackbarHostState() }

    // VPN 权限请求 launcher
    val vpnPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vm.onVpnPermissionGranted()
        }
    }

    // 监听 ViewModel 发出的权限请求事件
    LaunchedEffect(Unit) {
        vm.vpnPermissionNeeded.collect { prepareIntent ->
            vpnPermLauncher.launch(prepareIntent)
        }
    }

    LaunchedEffect(noProfileError) {
        if (noProfileError) {
            snackbar.showSnackbar("请先在「节点」页面添加订阅并激活")
            vm.clearNoProfileError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ProxyMax", style = MaterialTheme.typography.titleMedium) },
                actions = {
                    if (state is CoreState.Running) {
                        val core = (state as CoreState.Running).core
                        SuggestionChip(
                            onClick  = {},
                            label    = { Text(core.displayName, style = MaterialTheme.typography.labelMedium) },
                            icon     = { Icon(Icons.Default.Memory, null, Modifier.size(14.dp)) },
                            modifier = Modifier.padding(end = 12.dp)
                        )
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
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ConnectCard(state = state, onToggle = vm::toggleVpn, onSwitch = vm::switchCore)

            AnimatedVisibility(visible = state is CoreState.Running) {
                if (state is CoreState.Running) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // 实时速率图
                        Surface(
                            shape          = MaterialTheme.shapes.medium,
                            color          = MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp,
                            modifier       = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    "实时速率",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(10.dp))
                                SpeedChart(stats = liveStats, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        // 流量统计
                        Surface(
                            shape          = MaterialTheme.shapes.medium,
                            color          = MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp,
                            modifier       = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                StatItem("上传",  liveStats.totalUpload.toSpeedStr().replace("/s",""))
                                StatDivider()
                                StatItem("下载",  liveStats.totalDownload.toSpeedStr().replace("/s",""))
                                StatDivider()
                                StatItem("连接",  "${liveStats.connections}")
                            }
                        }
                    }
                }
            }

            // 错误卡片
            if (state is CoreState.Error) {
                val err = state as CoreState.Error
                Surface(
                    shape    = MaterialTheme.shapes.medium,
                    color    = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.ErrorOutline, null,
                            tint     = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${err.core.displayName} 启动失败",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                            Text(err.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectCard(state: CoreState, onToggle: () -> Unit, onSwitch: (CoreType) -> Unit) {
    val isRunning = state is CoreState.Running
    val isBusy    = state is CoreState.Switching || state is CoreState.Starting

    Surface(
        shape          = MaterialTheme.shapes.large,
        color          = if (isRunning) MaterialTheme.colorScheme.primaryContainer
                         else MaterialTheme.colorScheme.surface,
        tonalElevation = if (isRunning) 0.dp else 1.dp,
        modifier       = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 状态行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier          = Modifier.fillMaxWidth()
            ) {
                // 状态指示点
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isRunning -> MaterialTheme.colorScheme.primary
                                isBusy    -> MaterialTheme.colorScheme.secondary
                                else      -> MaterialTheme.colorScheme.outline
                            }
                        )
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = when (state) {
                            is CoreState.Running   -> "已连接"
                            is CoreState.Starting  -> "正在连接…"
                            is CoreState.Switching -> "切换内核…"
                            is CoreState.Error     -> "连接失败"
                            else                   -> "未连接"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (isRunning) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                    )
                    if (state is CoreState.Switching) {
                        val s = state as CoreState.Switching
                        Text(
                            "${s.from.displayName} → ${s.to.displayName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // 主按钮
                if (isBusy) {
                    CircularProgressIndicator(
                        Modifier.size(44.dp),
                        strokeWidth = 2.dp,
                        color       = MaterialTheme.colorScheme.primary
                    )
                } else {
                    FilledIconButton(
                        onClick  = onToggle,
                        modifier = Modifier.size(52.dp),
                        colors   = if (isRunning)
                            IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                contentColor   = MaterialTheme.colorScheme.error
                            )
                        else
                            IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor   = MaterialTheme.colorScheme.onPrimary
                            )
                    ) {
                        Icon(
                            imageVector        = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isRunning) "断开" else "连接",
                            modifier           = Modifier.size(24.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // 内核切换
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "内核",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CoreType.entries.forEach { core ->
                        val selected = isRunning && (state as CoreState.Running).core == core
                        FilterChip(
                            selected    = selected,
                            onClick     = { onSwitch(core) },
                            label       = { Text(core.displayName, style = MaterialTheme.typography.labelMedium) },
                            enabled     = !isBusy,
                            leadingIcon = if (selected) ({
                                Icon(Icons.Default.Check, null, Modifier.size(14.dp))
                            }) else null
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium,
             color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall,
             color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun StatDivider() {
    Box(
        Modifier
            .height(28.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}
