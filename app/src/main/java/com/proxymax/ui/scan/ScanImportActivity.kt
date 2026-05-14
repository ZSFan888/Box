package com.proxymax.ui.scan

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.proxymax.data.parser.SubscriptionParser
import com.proxymax.data.repository.ProfileRepository
import com.proxymax.ui.theme.ProxyMaxTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import javax.inject.Inject

@AndroidEntryPoint
class ScanImportActivity : ComponentActivity() {

    @Inject lateinit var profileRepo: ProfileRepository

    private val importScope = kotlinx.coroutines.MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProxyMaxTheme {
                ScanImportScreen(
                    onImported = { name, uris ->
                        importScope.launch {
                            val raw = uris.joinToString("\n")
                            profileRepo.fetchAndSaveProfile(name, "").fold(
                                onSuccess = {},
                                onFailure = {}
                            )
                            // 直接用 saveRawConfig 写入 DB
                            runCatching { profileRepo.saveRawConfig(name, "", raw) }
                                .onSuccess { profile ->
                                    Toast.makeText(
                                        this@ScanImportActivity,
                                        "✓ 已导入 ${uris.size} 个节点",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    setResult(Activity.RESULT_OK, Intent().apply {
                                        putExtra("PROFILE_ID", profile.id)
                                        putExtra("NODE_COUNT", uris.size)
                                    })
                                    finish()
                                }
                                .onFailure { e ->
                                    Toast.makeText(
                                        this@ScanImportActivity,
                                        "导入失败：${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                        }
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        importScope.cancel()
        super.onDestroy()
    }
}

// ── 主界面：Tab 切换扫码 / 剪贴板 ────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanImportScreen(
    onImported: (name: String, uris: List<String>) -> Unit,
    onDismiss:  () -> Unit
) {
    var tabIndex by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("导入节点", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "关闭")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 },
                    text = { Text("扫描二维码", style = MaterialTheme.typography.labelMedium) })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 },
                    text = { Text("剪贴板 / 手动", style = MaterialTheme.typography.labelMedium) })
            }
            when (tabIndex) {
                0 -> QRScanTab(onResult = { raw -> processRaw(raw, onImported) })
                1 -> ClipboardTab(onImport = { raw -> processRaw(raw, onImported) })
            }
        }
    }
}

// ── 二维码扫描 Tab ─────────────────────────────────────────────────────────
@Composable
fun QRScanTab(onResult: (String) -> Unit) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission)
            permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasCameraPermission) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                   verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Default.CameraAlt, null,
                     Modifier.size(48.dp),
                     tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
                Text("需要相机权限才能扫码",
                     style = MaterialTheme.typography.bodyMedium,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("授予权限")
                }
            }
        }
        return
    }

    CameraPreview(onQRCode = onResult)
}

@Composable
fun CameraPreview(onQRCode: (String) -> Unit) {
    val context       = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var scanned       by remember { mutableStateOf(false) }
    val executor      = remember { Executors.newSingleThreadExecutor() }

    val options = remember {
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    }
    val scanner = remember { BarcodeScanning.getClient(options) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    imageAnalysis.setAnalyzer(executor) { imageProxy ->
                        if (scanned) { imageProxy.close(); return@setAnalyzer }
                        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return@setAnalyzer }
                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        scanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                barcodes.firstOrNull()?.rawValue?.let { raw ->
                                    if (!scanned) { scanned = true; onQRCode(raw) }
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    }
                    runCatching {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview, imageAnalysis
                        )
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // 扫码框指示
        Box(
            Modifier
                .size(240.dp)
                .align(Alignment.Center)
                .border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
        )
        Text(
            "将二维码对准框内",
            style  = MaterialTheme.typography.bodySmall,
            color  = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.align(Alignment.Center).offset(y = 140.dp)
        )
    }
}

// ── 剪贴板 / 手动输入 Tab ──────────────────────────────────────────────────
@Composable
fun ClipboardTab(onImport: (String) -> Unit) {
    val context = LocalContext.current
    var text    by remember { mutableStateOf("") }

    // 自动读取剪贴板
    LaunchedEffect(Unit) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty().trim()
        if (clip.contains("://") || clip.startsWith("proxies:") || clip.length > 20)
            text = clip
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("支持 vmess / vless / ss / trojan / hysteria2 / tuic / Clash YAML / Base64",
             style = MaterialTheme.typography.bodySmall,
             color = MaterialTheme.colorScheme.onSurfaceVariant)

        OutlinedTextField(
            value         = text,
            onValueChange = { text = it },
            label         = { Text("粘贴节点链接或配置") },
            modifier      = Modifier.fillMaxWidth().weight(1f),
            maxLines      = 20
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick  = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    text = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty().trim()
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.ContentPaste, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("粘贴")
            }
            Button(
                onClick  = { if (text.isNotBlank()) onImport(text) },
                enabled  = text.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Text("导入")
            }
        }
    }
}

// ── 工具函数 ───────────────────────────────────────────────────────────────
private fun processRaw(
    raw:        String,
    onImported: (name: String, uris: List<String>) -> Unit
) {
    val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val validUris = lines.filter { line ->
        SubscriptionParser.parseUri(line, profileId = -1) != null
    }
    val allUris = if (validUris.isNotEmpty()) validUris else lines
    onImported("扫码导入", allUris)
}
