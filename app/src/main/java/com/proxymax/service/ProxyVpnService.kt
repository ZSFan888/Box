package com.proxymax.service

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.proxymax.MainActivity
import com.proxymax.core.*
import com.proxymax.data.converter.ConfigConverter
import com.proxymax.ui.widget.WidgetState
import com.proxymax.data.model.PerAppMode
import dagger.hilt.android.AndroidEntryPoint
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import io.nekohasekai.libbox.ConnectionOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@AndroidEntryPoint
class ProxyVpnService : VpnService() {

    @Inject lateinit var coreManager: CoreManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tunInterface: ParcelFileDescriptor? = null
    private var currentTunFd: Int = -1

    companion object {
        private const val TAG = "ProxyVpnService"
        const val ACTION_START   = "com.proxymax.START"
        const val ACTION_STOP    = "com.proxymax.STOP"
        const val ACTION_SWITCH  = "com.proxymax.SWITCH"
        const val EXTRA_CORE     = "core_type"
        const val EXTRA_CONFIG   = "config"
        const val EXTRA_API_PORT = "api_port"
        const val EXTRA_SECRET   = "api_secret"
        const val EXTRA_PER_APP_MODE  = "per_app_mode"
        const val EXTRA_APP_PACKAGES  = "app_packages"
        const val NOTIF_CHANNEL = "proxymax_vpn"
        const val NOTIF_ID      = 1001
    }

    // ── PlatformInterface 实现 ────────────────────────────────────────────
    // sing-box libbox 通过这个接口回调 Android 平台 API
    // OpenTun：libbox 需要建立 TUN 时，直接返回已有的 tunFd（我们自己建的）
    inner class LibboxPlatformInterface : PlatformInterface {

        override fun openTun(options: TunOptions?): Int {
            // TUN 已经由 buildTun() 建立，直接返回 fd
            // libbox 会 dup() 这个 fd，我们保持原 fd 不变
            Log.d(TAG, "LibboxPlatformInterface.openTun called, fd=$currentTunFd")
            return currentTunFd
        }

        override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

        override fun autoDetectInterfaceControl(fd: Int): Int {
            // 让 Android 系统自动保护这个 fd（走底层网络，不进 VPN）
            return if (protect(fd)) 0 else -1
        }

        override fun useProcFS(): Boolean = false

        override fun findConnectionOwner(
            ipProtocol: Int,
            sourceAddress: String?,
            sourcePort: Int,
            destinationAddress: String?,
            destinationPort: Int
        ): ConnectionOwner? = null

        override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener?): Int = 0

        override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener?): Int = 0

        override fun getInterfaces(): NetworkInterfaceIterator? = null

        override fun underNetworkExtension(): Boolean = false

        override fun includeAllNetworks(): Boolean = false

        override fun readWIFIState(): WIFIState? = null

        override fun systemCertificates(): StringIterator? = null

        override fun clearDNSCache() {}

        override fun sendNotification(notification: Notification?): Int = 0

        override fun localDNSTransport(): LocalDNSTransport? = null
    }

    // ── TUN 建立（带应用分流）────────────────────────────────────────────
    private fun buildTun(
        perAppMode: PerAppMode,
        packages:   List<String>
    ): ParcelFileDescriptor? = try {
        val builder = Builder()
            .addAddress("172.19.0.1", 30)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .addDnsServer("198.18.0.2")
            .setMtu(9000)
            .setBlocking(true)
            .setSession("ProxyMax")

        when (perAppMode) {
            PerAppMode.WHITELIST -> {
                packages.forEach { pkg ->
                    runCatching { builder.addAllowedApplication(pkg) }
                        .onFailure { Log.w(TAG, "Unknown package: $pkg") }
                }
            }
            PerAppMode.BLACKLIST -> {
                packages.forEach { pkg ->
                    runCatching { builder.addDisallowedApplication(pkg) }
                        .onFailure { Log.w(TAG, "Unknown package: $pkg") }
                }
                builder.addDisallowedApplication(packageName)
            }
            PerAppMode.GLOBAL -> {
                builder.addDisallowedApplication(packageName)
            }
        }
        builder.establish()
    } catch (e: Exception) {
        Log.e(TAG, "buildTun failed", e)
        null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        observeState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START  -> handleStart(intent)
            ACTION_STOP   -> handleStop()
            ACTION_SWITCH -> handleSwitch(intent)
        }
        return START_STICKY
    }

    // ── 启动 ──────────────────────────────────────────────────────────────
    private fun handleStart(intent: Intent) {
        val coreTypeName = intent.getStringExtra(EXTRA_CORE) ?: CoreType.MIHOMO.name
        val rawConfig    = intent.getStringExtra(EXTRA_CONFIG) ?: return
        val apiPort      = intent.getIntExtra(EXTRA_API_PORT, 9090)
        val secret       = intent.getStringExtra(EXTRA_SECRET) ?: ""
        val perAppMode   = runCatching {
            PerAppMode.valueOf(intent.getStringExtra(EXTRA_PER_APP_MODE) ?: "GLOBAL")
        }.getOrDefault(PerAppMode.GLOBAL)
        val packages     = intent.getStringArrayListExtra(EXTRA_APP_PACKAGES) ?: arrayListOf()
        val coreType     = runCatching { CoreType.valueOf(coreTypeName) }.getOrDefault(CoreType.MIHOMO)

        startForeground(NOTIF_ID, buildNotification("正在启动 ${coreType.displayName}…"))

        scope.launch(Dispatchers.IO) {
            val config = when (coreType) {
                CoreType.XRAY    -> runCatching { ConfigConverter.clashToXray(rawConfig, apiPort) }.getOrDefault(rawConfig)
                CoreType.SINGBOX -> runCatching { ConfigConverter.clashToSingbox(rawConfig, apiPort) }.getOrDefault(rawConfig)
                CoreType.MIHOMO  -> rawConfig
            }

            tunInterface?.close()
            tunInterface = buildTun(perAppMode, packages)
            val fd = tunInterface?.fd ?: run {
                Log.e(TAG, "TUN establish failed")
                stopSelf(); return@launch
            }

            currentTunFd = fd

            // sing-box 模式下注入 PlatformInterface
            if (coreType == CoreType.SINGBOX) {
                coreManager.setPlatformInterface(LibboxPlatformInterface())
            }

            coreManager.startCore(coreType, config, fd, apiPort, secret)
                .onFailure { e ->
                    Log.e(TAG, "Core start failed", e)
                    stopSelf()
                }
        }
    }

    // ── 停止 ──────────────────────────────────────────────────────────────
    private fun handleStop() {
        scope.launch(Dispatchers.IO) {
            coreManager.stopCurrent()
            tunInterface?.close()
            tunInterface = null
            currentTunFd = -1
            withContext(Dispatchers.Main) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    // ── 热切换内核 ─────────────────────────────────────────────────────────
    private fun handleSwitch(intent: Intent) {
        val coreTypeName = intent.getStringExtra(EXTRA_CORE) ?: return
        val rawConfig    = intent.getStringExtra(EXTRA_CONFIG) ?: return
        val apiPort      = intent.getIntExtra(EXTRA_API_PORT, 9090)
        val toType       = runCatching { CoreType.valueOf(coreTypeName) }.getOrElse { return }

        scope.launch(Dispatchers.IO) {
            val config = when (toType) {
                CoreType.XRAY    -> runCatching { ConfigConverter.clashToXray(rawConfig, apiPort) }.getOrDefault(rawConfig)
                CoreType.SINGBOX -> runCatching { ConfigConverter.clashToSingbox(rawConfig, apiPort) }.getOrDefault(rawConfig)
                CoreType.MIHOMO  -> rawConfig
            }
            if (toType == CoreType.SINGBOX) {
                coreManager.setPlatformInterface(LibboxPlatformInterface())
            }
            coreManager.switchCore(
                    toType  = toType,
                    config  = config,
                    tunFd   = currentTunFd,
                    apiPort = apiPort,
                    secret  = intent.getStringExtra(EXTRA_SECRET) ?: ""
                ).onSuccess { Log.d(TAG, "Switched to $toType") }
                 .onFailure { Log.e(TAG, "Switch failed", it) }
        }
    }

    private fun observeState() {
        coreManager.state.onEach { state: CoreState ->
            val text = when (state) {
                is CoreState.Running   ->
                    "${"${state.core.displayName}"} · ↑${state.stats.uploadSpeed.toSpeedStr()} ↓${state.stats.downloadSpeed.toSpeedStr()}"
                is CoreState.Switching -> "切换 ${state.from.displayName}→${state.to.displayName}"
                is CoreState.Starting  -> "启动中…"
                is CoreState.Error     -> "❌ ${state.message}"
                else                   -> "ProxyMax"
            }
            val connected = state is CoreState.Running
            val up = if (state is CoreState.Running) state.stats.uploadSpeed.toSpeedStr() else "0 KB/s"
            val dn = if (state is CoreState.Running) state.stats.downloadSpeed.toSpeedStr() else "0 KB/s"
            WidgetState.save(this@ProxyVpnService, connected, up, dn)
            startForeground(NOTIF_ID, buildNotification(text))
        }.launchIn(scope)
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel(NOTIF_CHANNEL, "ProxyMax VPN", NotificationManager.IMPORTANCE_LOW)
        ch.description = "VPN 运行中通知"
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, ProxyVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIF_CHANNEL)
            .setContentTitle("ProxyMax")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_power_off)
            .setContentIntent(pi)
            .addAction(0, "断开", stopPi)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        tunInterface?.close()
        super.onDestroy()
    }

    private fun Long.toSpeedStr() = when {
        this > 1_000_000 -> "${"%.1f".format(this / 1_000_000.0)} MB/s"
        this > 1_000     -> "${"%.0f".format(this / 1_000.0)} KB/s"
        else             -> "$this B/s"
    }
}
