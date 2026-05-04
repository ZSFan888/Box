package com.proxymax.ui.widget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proxymax.core.TrafficStats
import kotlinx.coroutines.delay

private const val HISTORY_SIZE = 60   // 60 个采样点（每秒一点，共 60 秒）

/**
 * 实时速率折线图
 * uploadHistory  / downloadHistory：最近 60 秒速率（bytes/s）
 */
@Composable
fun SpeedChart(
    stats:          TrafficStats,
    modifier:       Modifier = Modifier
) {
    val uploadHistory   = remember { ArrayDeque<Long>(HISTORY_SIZE) }
    val downloadHistory = remember { ArrayDeque<Long>(HISTORY_SIZE) }

    // 每秒追加一个采样点
    LaunchedEffect(stats) {
        uploadHistory.addLast(stats.uploadSpeed)
        downloadHistory.addLast(stats.downloadSpeed)
        if (uploadHistory.size   > HISTORY_SIZE) uploadHistory.removeFirst()
        if (downloadHistory.size > HISTORY_SIZE) downloadHistory.removeFirst()
    }

    val maxVal = remember(uploadHistory.toList(), downloadHistory.toList()) {
        maxOf(
            uploadHistory.maxOrNull()   ?: 1L,
            downloadHistory.maxOrNull() ?: 1L,
            1L
        ).toFloat()
    }

    val uploadColor   = Color(0xFF4FC3F7)   // 上传：蓝
    val downloadColor = Color(0xFF81C784)   // 下载：绿

    Column(modifier = modifier) {
        // ── 图例 ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LegendItem("↑ 上传", stats.uploadSpeed.toSpeedStr(),   uploadColor)
            LegendItem("↓ 下载", stats.downloadSpeed.toSpeedStr(), downloadColor)
            Text(
                text  = "峰值 ${maxVal.toLong().toSpeedStr()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ── 折线画布 ─────────────────────────────────────────────────────
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        ) {
            val w = size.width
            val h = size.height

            // 背景网格
            drawGrid(w, h)

            // 下载线（带填充渐变）
            drawSpeedLine(
                history = downloadHistory.toList(),
                max     = maxVal,
                w = w, h = h,
                lineColor = downloadColor,
                fillColor = downloadColor.copy(alpha = 0.15f)
            )

            // 上传线（带填充渐变）
            drawSpeedLine(
                history = uploadHistory.toList(),
                max     = maxVal,
                w = w, h = h,
                lineColor = uploadColor,
                fillColor = uploadColor.copy(alpha = 0.12f)
            )
        }
    }
}

private fun DrawScope.drawGrid(w: Float, h: Float) {
    val gridColor = Color.Gray.copy(alpha = 0.15f)
    val rows = 4
    repeat(rows + 1) { i ->
        val y = h * i / rows
        drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
    }
}

private fun DrawScope.drawSpeedLine(
    history:   List<Long>,
    max:       Float,
    w:         Float,
    h:         Float,
    lineColor: Color,
    fillColor: Color
) {
    if (history.size < 2) return
    val step = w / (HISTORY_SIZE - 1).toFloat()

    // 折线路径
    val linePath = Path()
    // 填充路径
    val fillPath = Path()

    history.forEachIndexed { i, v ->
        val x  = i * step
        val y  = h - (v.toFloat() / max * h).coerceIn(0f, h)
        if (i == 0) { linePath.moveTo(x, y); fillPath.moveTo(x, h) }
        linePath.lineTo(x, y)
        fillPath.lineTo(x, y)
    }
    // 填充路径闭合底部
    fillPath.lineTo((history.size - 1) * step, h)
    fillPath.close()

    drawPath(fillPath, fillColor)
    drawPath(linePath, lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
fun LegendItem(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.size(8.dp)) { drawCircle(color) }
        Text(label, style = MaterialTheme.typography.labelSmall,
             color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelSmall,
             color = color)
    }
}

fun Long.toSpeedStr(): String = when {
    this > 1_000_000 -> "${"%.1f".format(this / 1_000_000.0)} MB/s"
    this > 1_000     -> "${"%.0f".format(this / 1_000.0)} KB/s"
    else             -> "$this B/s"
}
