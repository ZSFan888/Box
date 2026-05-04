package com.proxymax.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // TODO: 读取 DataStore 设置，如果开机自启已开启则启动 VPN
            Log.d("BootReceiver", "Boot received, checking auto-start setting")
        }
    }
}
