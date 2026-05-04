package com.proxymax.ui.widget

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.*
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.proxymax.service.ProxyVpnService
import android.content.Intent

class ProxyWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<androidx.datastore.preferences.core.Preferences>()
            val isRunning  = prefs[booleanPreferencesKey("widget_running")]  ?: false
            val coreName   = prefs[stringPreferencesKey("widget_core")]      ?: "ProxyMax"
            val upSpeed    = prefs[stringPreferencesKey("widget_up")]        ?: "0 B/s"
            val downSpeed  = prefs[stringPreferencesKey("widget_down")]      ?: "0 B/s"

            WidgetContent(
                isRunning = isRunning,
                coreName  = coreName,
                upSpeed   = upSpeed,
                downSpeed = downSpeed,
                onToggle  = actionStartService(
                    Intent(context, ProxyVpnService::class.java).apply {
                        action = if (isRunning) ProxyVpnService.ACTION_STOP else ProxyVpnService.ACTION_START
                    }
                )
            )
        }
    }
}

@Composable
fun WidgetContent(
    isRunning: Boolean,
    coreName:  String,
    upSpeed:   String,
    downSpeed: String,
    onToggle:  Action
) {
    val bgColor    = if (isRunning) Color(0xFF006874) else Color(0xFF2C2C2C)
    val textColor  = ColorProvider(Color.White)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bgColor)
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(onToggle)
    ) {
        Column(verticalAlignment = Alignment.CenterVertically) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = GlanceModifier.fillMaxWidth()
            ) {
                Text(
                    text  = if (isRunning) "● $coreName" else "○ 未连接",
                    style = TextStyle(color = textColor, fontSize = 14.sp,
                                      fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.defaultWeight()
                )
                Text(
                    text  = if (isRunning) "断开" else "连接",
                    style = TextStyle(color = textColor, fontSize = 12.sp)
                )
            }

            if (isRunning) {
                Spacer(GlanceModifier.height(6.dp))
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    Text("↑ $upSpeed",   style = TextStyle(color = ColorProvider(Color(0xFF80D8FF)), fontSize = 11.sp),
                         modifier = GlanceModifier.defaultWeight())
                    Text("↓ $downSpeed", style = TextStyle(color = ColorProvider(Color(0xFF69F0AE)), fontSize = 11.sp))
                }
            }
        }
    }
}

class ProxyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = ProxyWidget()
}
