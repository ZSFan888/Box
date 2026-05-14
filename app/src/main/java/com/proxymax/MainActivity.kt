package com.proxymax

import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.proxymax.ui.ProxyMaxNavHost
import com.proxymax.ui.dashboard.DashboardViewModel
import com.proxymax.ui.theme.ProxyMaxTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val dashboardVm: DashboardViewModel by viewModels()

    // VPN 权限 launcher（磁贴 auto_connect 跳转时使用）
    private val vpnPermLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            dashboardVm.onVpnPermissionGranted()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 处理来自磁贴的 auto_connect extra
        if (intent?.getBooleanExtra("auto_connect", false) == true) {
            handleAutoConnect()
        }

        setContent {
            ProxyMaxTheme {
                ProxyMaxNavHost()
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("auto_connect", false)) {
            handleAutoConnect()
        }
    }

    private fun handleAutoConnect() {
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            vpnPermLauncher.launch(prepareIntent)
        } else {
            dashboardVm.onVpnPermissionGranted()
        }
    }
}
