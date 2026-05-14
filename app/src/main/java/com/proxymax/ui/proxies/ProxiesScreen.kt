package com.proxymax.ui.proxies

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.proxymax.core.stats.ProxyInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxiesScreen(vm: ProxiesViewModel = hiltViewModel()) {
    val proxies  by vm.proxies.collectAsState()
    val testing  by vm.testing.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val active   by vm.activeProfileId.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title   = { Text("节点", style = MaterialTheme.typography.titleMedium) },
                actions = {
                    IconButton(onClick = vm::testAllDelays) {
                        if (testing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Speed, "测速")
                    }
                    IconButton(onClick = vm::showAddSheet) {
                        Icon(Icons.Default.Add, "添加订阅")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (proxies.isEmpty() && profiles.isEmpty()) {
            EmptyProxies(onAdd = vm::showAddSheet, modifier = Modifier.padding(padding))
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
                    item {
                        SectionLabel("订阅")
                    }
                    items(profiles, key = { it.id }) { p ->
                        ProfileRow(
                            profile  = p,
                            isActive = p.id == active,
                            onActivate  = { vm.activateProfile(p.id) },
                            onRefresh   = { vm.refreshProfile(p) },
                            onDelete    = { vm.deleteProfile(p) }
                        )
                    }
                    item { Spacer(Modifier.height(4.dp)) }
                }

                // 代理节点列表（来自 Clash API）
                if (proxies.isNotEmpty()) {
                    item { SectionLabel("节点") }
                    proxies.entries.toList().let { entries ->
                        items(entries, key = { it.key }) { (name, info) ->
                            ProxyRow(
                                name   = name,
                                info   = info,
                                onTest = { vm.testDelay(name) }
                            )
                        }
                    }
                }
            }
        }
    }

    // 添加订阅 BottomSheet
    if (vm.showSheet) {
        AddSubscriptionSheet(vm = vm, onDismiss = vm::hideAddSheet)
    }
}

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
    profile:    com.proxymax.data.model.ProxyProfile,
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
                imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint    = if (isActive) MaterialTheme.colorScheme.primary
                          else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(profile.name,
                     style = MaterialTheme.typography.titleSmall,
                     color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                             else MaterialTheme.colorScheme.onSurface)
                if (profile.url.isNotBlank())
                    Text(profile.url,
                         style    = MaterialTheme.typography.bodySmall,
                         color    = MaterialTheme.colorScheme.onSurfaceVariant,
                         maxLines = 1)
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
fun ProxyRow(name: String, info: ProxyInfo, onTest: () -> Unit) {
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
                Text(name, style = MaterialTheme.typography.titleSmall,
                     color = MaterialTheme.colorScheme.onSurface)
                Text(info.type, style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val delay = info.delay
            Text(
                text  = if (delay > 0) "${delay}ms" else "—",
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    delay <= 0   -> MaterialTheme.colorScheme.onSurfaceVariant
                    delay < 200  -> MaterialTheme.colorScheme.primary
                    delay < 500  -> MaterialTheme.colorScheme.secondary
                    else         -> MaterialTheme.colorScheme.error
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
        modifier              = modifier.fillMaxSize(),
        verticalArrangement   = Arrangement.Center,
        horizontalAlignment   = Alignment.CenterHorizontally
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
