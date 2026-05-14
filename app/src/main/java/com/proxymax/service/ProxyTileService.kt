package com.proxymax.service

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.proxymax.core.CoreManager
import com.proxymax.core.CoreState
import com.proxymax.data.repository.ProfileDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class ProxyTileService : TileService() {

    @Inject lateinit var coreManager: CoreManager
    @Inject lateinit var profileDao:  ProfileDao

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            coreManager.state.collect { state ->
                qsTile?.apply {
                    when (state) {
                        is CoreState.Running -> {
                            this.state = Tile.STATE_ACTIVE
                            label      = state.core.displayName
                            subtitle   = "已连接"
                        }
                        is CoreState.Starting, is CoreState.Switching -> {
                            this.state = Tile.STATE_UNAVAILABLE
                            label      = "切换中…"
                            subtitle   = ""
                        }
                        else -> {
                            this.state = Tile.STATE_INACTIVE
                            label      = "ProxyMax"
                            subtitle   = "点击连接"
                        }
                    }
                    updateTile()
                }
            }
        }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val state = coreManager.state.first()
            when {
                state is CoreState.Running -> {
                    // 直接停止
                    startService(
                        Intent(this@ProxyTileService, ProxyVpnService::class.java)
                            .setAction(ProxyVpnService.ACTION_STOP)
                    )
                }
                state is CoreState.Starting || state is CoreState.Switching -> {
                    // 忙状态，忽略点击
                }
                else -> {
                    // 未连接：检查是否有激活的 profile
                    val profile = profileDao.getActiveProfile()
                    if (profile == null) {
                        // 无订阅 → 打开主界面
                        val mainIntent = packageManager
                            .getLaunchIntentForPackage(packageName)
                            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (mainIntent != null) startActivityAndCollapse(
                            PendingIntent.getActivity(
                                this@ProxyTileService, 0, mainIntent,
                                PendingIntent.FLAG_IMMUTABLE
                            )
                        )
                    } else {
                        // 有订阅 → 打开主界面（需要用户确认 VPN 权限，不能在 Tile 里弹系统对话框）
                        val mainIntent = packageManager
                            .getLaunchIntentForPackage(packageName)
                            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            ?.putExtra("auto_connect", true)
                        if (mainIntent != null) startActivityAndCollapse(
                            PendingIntent.getActivity(
                                this@ProxyTileService, 1, mainIntent,
                                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                            )
                        )
                    }
                }
            }
        }
    }

    override fun onStopListening() {
        scope.cancel()
        super.onStopListening()
    }
}
