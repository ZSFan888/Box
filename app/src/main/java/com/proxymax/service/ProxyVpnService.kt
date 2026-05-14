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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
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

    // ── PlatformInterface 动态代理 ──────────────────────────────────────
    // 用 Java 动态代理实现 io.nekohasekai.libbox.PlatformInterface
    // 避免编译期 import（jar 由 CI 动态生成）
    private fun buildPlatformInterface(): Any? {
        val ifaceClass = runCatching {
            Class.forName("io.nekohasekai.libbox.PlatformInterface")
        }.getOrNull() ?: run {
            Log.w(TAG, "PlatformInterface class not found — libbox.jar not loaded")
            return null
        }

        return Proxy.newProxyInstance(
            ifaceClass.classLoader,
            arrayOf(ifaceClass),
            object : InvocationHandler {
                override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
                    return when (method.name) {
                        // 核心方法：返回已建立的 TUN fd
                        // libbox 会 dup() 此 fd，我们保持原 fd 不变
                        "openTun" -> {
                            Log.d(TAG, "PlatformInterface.openTun → fd=$currentTunFd")
                            currentTunFd
                        }
                        // 自动接口控制：true = 让 libbox 自动选择出口接口
                        "usePlatformAutoDetectInterfaceControl" -> true
                        // 保护 fd 不走 VPN（防止环路）
                        "autoDetectInterfaceControl" -> {
                            val fd = args?.firstOrNull() as? Int ?: return@invoke -1
                            if (protect(fd)) 0 else -1
                        }
                        // 不使用 /proc/net（Android 用系统 API）
                        "useProcFS" -> false
                        // 不在 NetworkExtension 里（iOS 概念）
                        "underNetworkExtension" -> false
                        "includeAllNetworks"    -> false
                        // 其余方法：返回 null / 0 / false 安全默认值
                        else -> {
                            val rt = method.returnType
                            when {
                                rt == Void.TYPE      -> null
                                rt == Boolean::class.javaPrimitiveType -> false
                                rt == Int::class.javaPrimitiveType     -> 0
                                rt == Long::class.javaPrimitiveType    -> 0L
                                else -> null
                            }
                        }
                    }
                }
            }
        )
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
            PerAppMode.WHITELIST -> packages.forEach { pkg ->
                runCatching { builder.addAllowedApplication(pkg) }
                    .onFailure { Log.w(TAG, "Unknown package: $pkg") }
            }
            PerAppMode.BLACKLIST -> {
                packages.forEach { pkg ->
                    runCatching { builder.addDisallowedApplication(pkg) }
                        .onFailure { Log.w(TAG, "Unknown package: $pkg") }
                }
                builder.addDisallowedApplication(packageName)
            }
            PerAppMode.GLOBAL -> builder.addDisallowedApplication(packageName)
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
                Log.e(TAG, "TUN establish failed"); stopSelf(); return@launch
            }
            currentTunFd = fd

            // sing-box：注入 PlatformInterface 动态代理
            if (coreType == CoreType.SINGBOX) {
                buildPlatformInterface()?.let { coreManager.setPlatformInterface(it) }
            }

            coreManager.startCore(coreType, config, fd, apiPort, secret)
                .onFailure { e -> Log.e(TAG, "Core start failed", e); stopSelf() }
        }
    }

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
                buildPlatformInterface()?.let { coreManager.setPlatformInterface(it) }
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
                    "${state.core.displayName} · ↑${state.stats.uploadSpeed.toSpeedStr()} ↓${state.stats.downloadSpeed.toSpeedStr()}"
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
