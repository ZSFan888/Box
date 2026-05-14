package com.proxymax.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.widget.RemoteViews
import com.proxymax.R
import com.proxymax.service.ProxyVpnService

private const val PREFS_NAME    = "proxy_widget_prefs"
private const val KEY_CONNECTED = "connected"
private const val KEY_UP_SPEED  = "up_speed"
private const val KEY_DN_SPEED  = "dn_speed"
private const val ACTION_TOGGLE = "com.proxymax.widget.TOGGLE"

/** VpnService 调用此单例保存状态并触发 widget 刷新 */
object WidgetState {
    fun save(ctx: Context, connected: Boolean, up: String = "0 KB/s", dn: String = "0 KB/s") {
        prefs(ctx).edit()
            .putBoolean(KEY_CONNECTED, connected)
            .putString(KEY_UP_SPEED, up)
            .putString(KEY_DN_SPEED, dn)
            .apply()
        // 通知所有 widget 实例刷新
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, ProxyWidgetReceiver::class.java))
        if (ids.isNotEmpty()) {
            val intent = Intent(ctx, ProxyWidgetReceiver::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            ctx.sendBroadcast(intent)
        }
    }

    fun isConnected(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_CONNECTED, false)
    fun upSpeed(ctx: Context): String      = prefs(ctx).getString(KEY_UP_SPEED, "0 KB/s") ?: "0 KB/s"
    fun dnSpeed(ctx: Context): String      = prefs(ctx).getString(KEY_DN_SPEED, "0 KB/s") ?: "0 KB/s"
    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

class ProxyWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> updateWidget(ctx, mgr, id) }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == ACTION_TOGGLE) {
            val isOn   = WidgetState.isConnected(ctx)
            val action = if (isOn) ProxyVpnService.ACTION_STOP else ProxyVpnService.ACTION_START
            ctx.startForegroundService(
                Intent(ctx, ProxyVpnService::class.java).apply { this.action = action }
            )
        }
    }

    companion object {
        fun updateWidget(ctx: Context, mgr: AppWidgetManager, widgetId: Int) {
            val connected = WidgetState.isConnected(ctx)
            val upSpeed   = WidgetState.upSpeed(ctx)
            val dnSpeed   = WidgetState.dnSpeed(ctx)

            val views = RemoteViews(ctx.packageName, R.layout.widget_proxy)

            // 状态文字与颜色
            views.setTextViewText(R.id.widget_status, if (connected) "已连接" else "未连接")
            views.setTextColor(R.id.widget_status,
                if (connected) Color.parseColor("#7AAA88") else Color.parseColor("#9BA3AF"))
            views.setTextViewText(R.id.widget_speed,
                if (connected) "↑ $upSpeed   ↓ $dnSpeed" else "")
            views.setInt(R.id.widget_root, "setBackgroundColor",
                if (connected) Color.parseColor("#1A2B2B") else Color.parseColor("#1E2024"))

            // 点击切换
            val toggleIntent = Intent(ctx, ProxyWidgetReceiver::class.java).apply {
                action = ACTION_TOGGLE
            }
            val pi = PendingIntent.getBroadcast(
                ctx, 0, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pi)

            mgr.updateAppWidget(widgetId, views)
        }
    }
}
