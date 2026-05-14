package com.proxymax.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.proxymax.core.CoreManager
import com.proxymax.core.CoreState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class ProxyTileService : TileService() {
    @Inject lateinit var coreManager: CoreManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            coreManager.state.collect { state: com.proxymax.core.CoreState ->
                qsTile?.apply {
                    when (state) {
                        is CoreState.Running -> {
                            this.state = Tile.STATE_ACTIVE
                            label = state.core.displayName
                        }
                        is CoreState.Starting, is CoreState.Switching -> {
                            this.state = Tile.STATE_UNAVAILABLE
                            label = "切换中…"
                        }
                        else -> {
                            this.state = Tile.STATE_INACTIVE
                            label = "ProxyMax"
                        }
                    }
                    updateTile()
                }
            }
        }
    }

    override fun onClick() {
        super.onClick()
        val state = runBlocking { coreManager.state.first() }
        if (state is CoreState.Running) {
            startService(Intent(this, ProxyVpnService::class.java).setAction(ProxyVpnService.ACTION_STOP))
        }
        // 如果未运行，打开主界面让用户选择
    }

    override fun onStopListening() { scope.cancel(); super.onStopListening() }
}
