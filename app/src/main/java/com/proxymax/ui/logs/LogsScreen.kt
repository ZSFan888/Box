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
                title = { Text("实时日志") },
                actions = {
                    IconToggleButton(checked = autoScroll, onCheckedChange = { autoScroll = it }) {
                        Icon(if (autoScroll) Icons.Default.ArrowDownward else Icons.Default.PauseCircle,
                             if (autoScroll) "自动滚动开" else "自动滚动关")
                    }
                    IconButton(onClick = vm::clear) { Icon(Icons.Default.DeleteSweep, "清除") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = filter, onValueChange = { filter = it },
                placeholder = { Text("关键词过滤…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine  = true,
                modifier    = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            )

            SelectionContainer {
                LazyColumn(
                    state   = state,
                    modifier = Modifier.fillMaxSize()
                        .background(Color(0xFF1E1E2E))
                        .padding(horizontal = 12.dp)
                ) {
                    items(filtered) { line ->
                        Text(
                            text       = line,
                            color      = logLineColor(line),
                            fontSize   = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp,
                            modifier   = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

fun logLineColor(line: String): Color = when {
    line.contains("[ERROR]", ignoreCase = true) ||
    line.contains("error",   ignoreCase = true) -> Color(0xFFFF5555)
    line.contains("[WARN]",  ignoreCase = true) ||
    line.contains("warning", ignoreCase = true) -> Color(0xFFFFB86C)
    line.contains("[DEBUG]", ignoreCase = true) -> Color(0xFF6272A4)
    line.contains("✓") || line.contains("started") -> Color(0xFF50FA7B)
    else -> Color(0xFFF8F8F2)
}
