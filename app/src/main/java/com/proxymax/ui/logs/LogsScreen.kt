package com.proxymax.ui.logs

import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.proxymax.core.CoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LogsViewModel @Inject constructor(coreManager: CoreManager) : ViewModel() {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    init {
        viewModelScope.launch {
            coreManager.logs().collect { line ->
                _logs.update { (it + line).takeLast(2000) }
            }
        }
    }

    fun clear() = _logs.update { emptyList() }
}

// ── 日志区配色（与 MaterialTheme 表面色协调，使用柔和色调） ────────────────
private val LogBg          = Color(0xFF181A1F)   // 偏暖深灰，不纯黑
private val LogTextDefault = Color(0xFFB8BCC8)   // 主文字：中灰偏蓝，不刺眼
private val LogTextError   = Color(0xFFCC7A7A)   // 错误：降饱和玫瑰红
private val LogTextWarn    = Color(0xFFB89A6A)   // 警告：降饱和琥珀
private val LogTextDebug   = Color(0xFF7A8AAA)   // 调试：蓝灰，明显退后
private val LogTextSuccess = Color(0xFF7AAA88)   // 成功：降饱和绿

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(vm: LogsViewModel = hiltViewModel()) {
    val logs  by vm.logs.collectAsState()
    val state = rememberLazyListState()
    var autoScroll by remember { mutableStateOf(true) }
    var filter     by remember { mutableStateOf("") }

    val filtered = remember(logs, filter) {
        if (filter.isBlank()) logs else logs.filter { it.contains(filter, ignoreCase = true) }
    }

    LaunchedEffect(filtered.size) {
        if (autoScroll && filtered.isNotEmpty()) {
            state.animateScrollToItem(filtered.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title  = { Text("实时日志", style = MaterialTheme.typography.titleMedium) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    IconToggleButton(checked = autoScroll, onCheckedChange = { autoScroll = it }) {
                        Icon(
                            imageVector = if (autoScroll) Icons.Default.ArrowDownward
                                          else Icons.Default.PauseCircle,
                            contentDescription = if (autoScroll) "自动滚动开" else "自动滚动关",
                            tint = if (autoScroll) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = vm::clear) {
                        Icon(Icons.Default.DeleteSweep, "清除",
                             tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // 过滤输入框
            OutlinedTextField(
                value         = filter,
                onValueChange = { filter = it },
                placeholder   = { Text("关键词过滤…",
                    style = MaterialTheme.typography.bodySmall) },
                leadingIcon   = {
                    Icon(Icons.Default.Search, null,
                         Modifier.size(18.dp),
                         tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                singleLine    = true,
                textStyle     = MaterialTheme.typography.bodySmall,
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )

            // 日志列表
            SelectionContainer {
                LazyColumn(
                    state    = state,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LogBg)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    items(filtered, key = { it.hashCode().toString() + filtered.indexOf(it) }) { line ->
                        Text(
                            text       = line,
                            color      = logLineColor(line),
                            fontSize   = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 17.sp,
                            modifier   = Modifier.padding(vertical = 1.5.dp)
                        )
                    }

                    if (filtered.isEmpty()) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(top = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text  = if (filter.isBlank()) "暂无日志" else "无匹配结果",
                                    color = LogTextDebug,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun logLineColor(line: String): Color = when {
    line.contains("[ERR]",   ignoreCase = true) ||
    line.contains("[ERROR]", ignoreCase = true) ||
    line.contains("error",   ignoreCase = true)   -> LogTextError

    line.contains("[WARN]",  ignoreCase = true) ||
    line.contains("warning", ignoreCase = true)   -> LogTextWarn

    line.contains("[DEBUG]", ignoreCase = true) ||
    line.contains("debug",   ignoreCase = true)   -> LogTextDebug

    line.contains("[INFO]",  ignoreCase = true) &&
    (line.contains("✓") || line.contains("started") ||
     line.contains("connected"))                  -> LogTextSuccess

    else                                          -> LogTextDefault
}
