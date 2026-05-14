package com.proxymax.ui.widget

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.proxymax.service.ProxyVpnService

private const val PREFS_NAME = "proxy_widget_prefs"
private const val KEY_CONNECTED = "connected"
private const val KEY_UP_SPEED  = "up_speed"
private const val KEY_DN_SPEED  = "dn_speed"

// ── Widget 状态持久化工具（从 VpnService 调用） ────────────────────────────
object WidgetState {
    fun save(ctx: Context, connected: Boolean, up: String = "0 KB/s", dn: String = "0 KB/s") {
        prefs(ctx).edit()
            .putBoolean(KEY_CONNECTED, connected)
            .putString(KEY_UP_SPEED, up)
            .putString(KEY_DN_SPEED, dn)
            .apply()
    }
    fun isConnected(ctx: Context) = prefs(ctx).getBoolean(KEY_CONNECTED, false)
    fun upSpeed(ctx: Context)     = prefs(ctx).getString(KEY_UP_SPEED, "0 KB/s") ?: "0 KB/s"
    fun dnSpeed(ctx: Context)     = prefs(ctx).getString(KEY_DN_SPEED, "0 KB/s") ?: "0 KB/s"
    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

// ── GlanceAppWidget ────────────────────────────────────────────────────────
class ProxyWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val connected = WidgetState.isConnected(context)
        val upSpeed   = WidgetState.upSpeed(context)
        val dnSpeed   = WidgetState.dnSpeed(context)
        provideContent {
            WidgetContent(connected, upSpeed, dnSpeed)
        }
    }
}

@Composable
private fun WidgetContent(connected: Boolean, upSpeed: String, dnSpeed: String) {
    val bgColor  = if (connected) Color(0xFF1A2B2B) else Color(0xFF1E2024)
    val dotColor = if (connected) Color(0xFF7AAA88) else Color(0xFF6B7280)
    val label    = if (connected) "已连接" else "未连接"

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(bgColor))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clickable(actionRunCallback<ToggleProxyAction>()),
        contentAlignment = Alignment.CenterStart
    ) {
        Column {
            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                Box(
                    GlanceModifier
                        .size(7.dp)
                        .background(ColorProvider(dotColor))
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text  = label,
                    style = TextStyle(
                        color      = ColorProvider(Color(0xFFE2E4E8)),
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
            if (connected) {
                Spacer(GlanceModifier.height(5.dp))
                Row {
                    Text(
                        "↑ $upSpeed",
                        style = TextStyle(ColorProvider(Color(0xFF9BA3AF)), fontSize = 11.sp)
                    )
                    Spacer(GlanceModifier.width(10.dp))
                    Text(
                        "↓ $dnSpeed",
                        style = TextStyle(ColorProvider(Color(0xFF9BA3AF)), fontSize = 11.sp)
                    )
                }
            }
        }
    }
}

// ── 点击动作：切换 VPN ─────────────────────────────────────────────────────
class ToggleProxyAction : ActionCallback {
    override suspend fun onAction(
        context:    Context,
        glanceId:   GlanceId,
        parameters: ActionParameters
    ) {
        val isOn   = WidgetState.isConnected(context)
        val action = if (isOn) ProxyVpnService.ACTION_STOP else ProxyVpnService.ACTION_START
        context.startForegroundService(
            Intent(context, ProxyVpnService::class.java).apply { this.action = action }
        )
    }
}

// ── Receiver ──────────────────────────────────────────────────────────────
class ProxyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProxyWidget()
}
