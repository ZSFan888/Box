package com.proxymax.ui.widget

import android.content.Context
import android.content.Intent
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

class ProxyWidget : GlanceAppWidget() {

    companion object {
        val KEY_CONNECTED = booleanPreferencesKey("connected")
        val KEY_UP_SPEED  = stringPreferencesKey("up_speed")
        val KEY_DN_SPEED  = stringPreferencesKey("dn_speed")
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { WidgetContent() }
    }
}

@Composable
private fun WidgetContent() {
    val prefs     = currentState<androidx.datastore.preferences.core.Preferences>()
    val connected = prefs[ProxyWidget.KEY_CONNECTED] ?: false
    val upSpeed   = prefs[ProxyWidget.KEY_UP_SPEED]  ?: "0 KB/s"
    val dnSpeed   = prefs[ProxyWidget.KEY_DN_SPEED]  ?: "0 KB/s"

    val bgColor   = if (connected) Color(0xFF1A2B2B) else Color(0xFF1E1E1E)
    val dotColor  = if (connected) Color(0xFF7AAA88) else Color(0xFF7C8798)
    val labelText = if (connected) "已连接" else "未连接"

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(bgColor))
            .padding(12.dp)
            .clickable(actionRunCallback<ToggleProxyAction>()),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                Box(
                    modifier = GlanceModifier
                        .size(8.dp)
                        .background(ColorProvider(dotColor))
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text  = labelText,
                    style = TextStyle(
                        color      = ColorProvider(Color.White),
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
            if (connected) {
                Spacer(GlanceModifier.height(6.dp))
                Row {
                    Text(
                        text  = "↑ $upSpeed",
                        style = TextStyle(
                            color    = ColorProvider(Color(0xFFB8BCC8)),
                            fontSize = 11.sp
                        )
                    )
                    Spacer(GlanceModifier.width(10.dp))
                    Text(
                        text  = "↓ $dnSpeed",
                        style = TextStyle(
                            color    = ColorProvider(Color(0xFFB8BCC8)),
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }
}

class ToggleProxyAction : ActionCallback {
    override suspend fun onAction(
        context:    Context,
        glanceId:   GlanceId,
        parameters: ActionParameters
    ) {
        val prefs    = getAppWidgetState<androidx.datastore.preferences.core.Preferences>(context, glanceId)
        val isOn     = prefs[ProxyWidget.KEY_CONNECTED] ?: false
        val action   = if (isOn) ProxyVpnService.ACTION_STOP else ProxyVpnService.ACTION_START
        context.startForegroundService(Intent(context, ProxyVpnService::class.java).apply {
            this.action = action
        })
    }
}

class ProxyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProxyWidget()
}
