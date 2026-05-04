package com.proxymax.ui.scan

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import com.proxymax.data.parser.SubscriptionParser
import com.proxymax.ui.theme.ProxyMaxTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 二维码扫描 + 剪贴板识别入口
 * 识别成功后将节点 URI 返回给调用方
 */
@AndroidEntryPoint
class ScanImportActivity : ComponentActivity() {

    private val qrLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.getStringExtra("SCAN_RESULT") ?: return@registerForActivityResult
            handleUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProxyMaxTheme {
                ImportMethodSheet(
                    onScanQR    = { launchQRScanner() },
                    onClipboard = { importFromClipboard() },
                    onDismiss   = { finish() }
                )
            }
        }
    }

    private fun launchQRScanner() {
        // 使用 ZXing Android Embedded 或 ML Kit
        // 这里提供 ZXing Intent 方式（需添加 com.journeyapps:zxing-android-embedded 依赖）
        val intent = Intent("com.google.zxing.client.android.SCAN").apply {
            putExtra("SCAN_MODE", "QR_CODE_MODE")
        }
        runCatching { qrLauncher.launch(intent) }
            .onFailure {
                // ZXing 未安装，fallback 到手动输入
                Toast.makeText(this, "请安装扫码工具或使用剪贴板导入", Toast.LENGTH_LONG).show()
            }
    }

    private fun importFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: run {
            Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show()
            return
        }
        handleUri(text.trim())
    }

    private fun handleUri(raw: String) {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val nodes = lines.mapNotNull { SubscriptionParser.parseUri(it, profileId = -1) }

        when {
            nodes.isEmpty() -> {
                Toast.makeText(this, "未识别到有效节点（支持 vmess/vless/ss/trojan/hy2/tuic）", Toast.LENGTH_LONG).show()
            }
            else -> {
                val result = Intent().apply {
                    putExtra("NODE_URIS",    ArrayList(lines))
                    putExtra("NODE_NAMES",   ArrayList(nodes.map { it.name }))
                    putExtra("NODE_COUNT",   nodes.size)
                }
                setResult(Activity.RESULT_OK, result)
                Toast.makeText(this, "✓ 识别到 ${nodes.size} 个节点", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportMethodSheet(onScanQR: () -> Unit, onClipboard: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("导入节点", style = MaterialTheme.typography.titleLarge,
                 modifier = Modifier.padding(bottom = 8.dp))

            OutlinedButton(
                onClick  = onScanQR,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.QrCodeScanner, null, Modifier.padding(end = 8.dp))
                Text("扫描二维码")
            }

            OutlinedButton(
                onClick  = onClipboard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ContentPaste, null, Modifier.padding(end = 8.dp))
                Text("从剪贴板导入")
            }
        }
    }
}
