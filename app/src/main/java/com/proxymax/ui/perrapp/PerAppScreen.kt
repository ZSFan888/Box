package com.proxymax.ui.perrapp

import androidx.compose.ui.Modifier
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.proxymax.data.model.PerAppMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerAppScreen(vm: PerAppViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("应用分流") },
                actions = {
                    if (ui.isLoading) {
                        CircularProgressIndicator(Modifier.size(24.dp).padding(end = 8.dp))
                    }
                    IconButton(onClick = vm::selectAll) {
                        Icon(Icons.Default.SelectAll, "全选")
                    }
                    IconButton(onClick = vm::clearAll) {
                        Icon(Icons.Default.Deselect, "清除")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            ModeSelector(
                mode     = ui.mode,
                onChange = vm::setMode
            )

            OutlinedTextField(
                value         = ui.query,
                onValueChange = vm::onQueryChange,
                placeholder   = { Text("搜索应用…") },
                leadingIcon   = { Icon(Icons.Default.Search, null) },
                singleLine    = true,
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )

            val hint = when (ui.mode) {
                PerAppMode.WHITELIST -> "✅ 仅以下选中的 App 走代理，其余直连"
                PerAppMode.BLACKLIST -> "🚫 以下选中的 App 直连，其余走代理"
                else                 -> "所有 App 走代理（全局模式）"
            }
            Text(hint, style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant,
                 modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            val filtered = remember(ui.apps, ui.query) {
                if (ui.query.isBlank()) ui.apps
                else ui.apps.filter {
                    it.label.contains(ui.query, ignoreCase = true) ||
                    it.packageName.contains(ui.query, ignoreCase = true)
                }
            }
            if (ui.isLoading) {
                LazyColumn {
                    items(12) {
                        ListItem(
                            headlineContent = {
                                Box(Modifier.fillMaxWidth(0.5f).height(14.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small))
                            },
                            supportingContent = {
                                Box(Modifier.fillMaxWidth(0.7f).height(11.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small))
                            },
                            leadingContent = {
                                Box(Modifier.size(40.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium))
                            }
                        )
                    }
                }
            } else LazyColumn {
                items(filtered, key = { it.packageName }) { app ->
                    AppItem(
                        app      = app,
                        checked  = app.packageName in ui.selected,
                        onToggle = { vm.toggleApp(app.packageName) },
                        enabled  = ui.mode != PerAppMode.GLOBAL
                    )
                }
            }
        }
    }
}

@Composable
fun ModeSelector(mode: PerAppMode, onChange: (PerAppMode) -> Unit) {
    val labels = mapOf(
        PerAppMode.GLOBAL    to "全局代理",
        PerAppMode.WHITELIST to "白名单",
        PerAppMode.BLACKLIST to "黑名单"
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PerAppMode.entries.forEach { m ->
            FilterChip(
                selected = mode == m,
                onClick  = { onChange(m) },
                label    = { Text(labels[m] ?: "") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

data class AppInfo(
    val packageName: String,
    val label:       String,
    val isSystem:    Boolean
)

@Composable
fun AppItem(app: AppInfo, checked: Boolean, onToggle: () -> Unit, enabled: Boolean) {
    val ctx = LocalContext.current
    val icon: Drawable? = remember(app.packageName) {
        runCatching { ctx.packageManager.getApplicationIcon(app.packageName) }.getOrNull()
    }

    ListItem(
        headlineContent   = {
            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(app.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                 style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        leadingContent = {
            if (icon != null) {
                AsyncImage(
                    model             = icon,
                    contentDescription = app.label,
                    modifier          = Modifier.size(40.dp)
                )
            } else {
                Icon(Icons.Default.Android, null, modifier = Modifier.size(40.dp))
            }
        },
        trailingContent = {
            Checkbox(
                checked         = checked,
                onCheckedChange = { onToggle() },
                enabled         = enabled
            )
        },
        modifier = Modifier.clickable(enabled = enabled) { onToggle() }
    )
}
