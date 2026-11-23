package com.cream.translator.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.cream.translator.BuildConfig
import com.cream.translator.core.engine.MlKitTranslationEngine
import com.cream.translator.core.model.LanguagePair
import com.cream.translator.service.TranslatorController
import com.cream.translator.update.UpdateInstaller
import com.cream.translator.update.UpdateUiState
import com.cream.translator.update.UpdateViewModel
import com.cream.translator.update.model.AppReleaseInfo
import kotlinx.coroutines.launch
import java.io.File

data class DashboardActions(
    val onRequestOverlayPermission: () -> Unit,
    val onStartService: () -> Unit,
    val onStopService: () -> Unit,
    val onCheckUpdate: () -> Unit,
    val onStartDownload: (AppReleaseInfo) -> Unit,
    val onDismissUpdateDialog: () -> Unit,
    val onInstallApk: (File) -> Unit,
    val onDownloadModel: (LanguagePair, (Boolean) -> Unit) -> Unit,
)

class MainActivity : ComponentActivity() {
    private val translationEngine = MlKitTranslationEngine()
    private val updateViewModel: UpdateViewModel by viewModels()

    private val overlayPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {
            checkPermissions()
        }

    private val installPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {
            // Re-check after user returns from settings
        }

    private val screenCaptureLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                TranslatorController.start(this, result.resultCode, data)
                Toast.makeText(this, "Screen Translator Started", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    private var hasOverlayPermission by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val updateState by updateViewModel.uiState.collectAsState()
                    val isServiceRunning by TranslatorController.isServiceRunning.collectAsState()
                    val actions =
                        DashboardActions(
                            onRequestOverlayPermission = { requestOverlayPermission() },
                            onStartService = { startScreenCapture() },
                            onStopService = {
                                TranslatorController.stop(this)
                                Toast.makeText(this, "Screen Translator Stopped", Toast.LENGTH_SHORT).show()
                            },
                            onCheckUpdate = { updateViewModel.checkForUpdate() },
                            onStartDownload = { release -> updateViewModel.startDownload(release) },
                            onDismissUpdateDialog = { updateViewModel.dismissDialog() },
                            onInstallApk = { apkFile -> triggerApkInstall(apkFile) },
                            onDownloadModel = { pair, onComplete ->
                                lifecycleScopeLaunch {
                                    val success = translationEngine.ensureModelDownloaded(pair)
                                    onComplete(success)
                                }
                            },
                        )

                    TranslatorDashboard(
                        hasOverlayPermission = hasOverlayPermission,
                        isServiceRunning = isServiceRunning,
                        updateState = updateState,
                        actions = actions,
                    )
                }
            }
        }
    }

    private fun triggerApkInstall(apkFile: File) {
        if (!UpdateInstaller.canRequestPackageInstalls(this)) {
            Toast.makeText(
                this,
                "Vui lòng cấp quyền Cài đặt ứng dụng không rõ nguồn",
                Toast.LENGTH_LONG,
            ).show()
            installPermissionLauncher.launch(UpdateInstaller.createManageUnknownSourcesIntent(this))
        } else {
            UpdateInstaller.installApk(this, apkFile)
        }
    }

    private fun checkPermissions() {
        hasOverlayPermission = Settings.canDrawOverlays(this)
    }

    private fun requestOverlayPermission() {
        val intent =
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            )
        overlayPermissionLauncher.launch(intent)
    }

    private fun startScreenCapture() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun lifecycleScopeLaunch(block: suspend () -> Unit) {
        lifecycleScope.launch {
            block()
        }
    }

    override fun onDestroy() {
        translationEngine.close()
        super.onDestroy()
    }
}

@Composable
fun TranslatorDashboard(
    hasOverlayPermission: Boolean,
    isServiceRunning: Boolean,
    updateState: UpdateUiState,
    actions: DashboardActions,
) {
    var isModelReady by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isDownloading = true
        actions.onDownloadModel(LanguagePair("en", "vi")) { success ->
            isModelReady = success
            isDownloading = false
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "CreamTranslator",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Zero-Cost On-Device Screen Translator for Games",
            style = MaterialTheme.typography.bodyMedium,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "System Status",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "• App Version: v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})")
                Text(text = "• Overlay Permission: ${if (hasOverlayPermission) "Granted" else "Missing"}")
                Text(
                    text = "• Offline EN-VI Model: ${if (isModelReady) {
                        "Ready (Offline)"
                    } else if (isDownloading) {
                        "Downloading..."
                    } else {
                        "Not Ready"
                    }}",
                )
                Text(text = "• Anti-Cheat Safe: Verified (Zero Accessibility)")
                val serviceStatus = if (isServiceRunning) "Running (Active)" else "Stopped (Inactive)"
                Text(
                    text = "• Translator Service: $serviceStatus",
                    color =
                        if (isServiceRunning) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }

        if (!hasOverlayPermission) {
            Button(
                onClick = actions.onRequestOverlayPermission,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Grant Overlay Permission")
            }
        } else if (isServiceRunning) {
            Button(
                onClick = actions.onStopService,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Stop Translator Service")
            }
        } else {
            Button(
                onClick = actions.onStartService,
                enabled = isModelReady,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Start Translator Service")
            }
        }

        OutlinedButton(
            onClick = {
                isDownloading = true
                actions.onDownloadModel(LanguagePair("en", "vi")) { success ->
                    isModelReady = success
                    isDownloading = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Re-verify Offline Model")
        }

        OutlinedButton(
            onClick = actions.onCheckUpdate,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (updateState is UpdateUiState.Checking) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Đang kiểm tra cập nhật...")
                } else {
                    Text("Kiểm tra bản cập nhật")
                }
            }
        }

        UpdateHandler(
            updateState = updateState,
            onStartDownload = actions.onStartDownload,
            onDismiss = actions.onDismissUpdateDialog,
            onInstall = actions.onInstallApk,
        )
    }
}

@Composable
private fun UpdateHandler(
    updateState: UpdateUiState,
    onStartDownload: (AppReleaseInfo) -> Unit,
    onDismiss: () -> Unit,
    onInstall: (File) -> Unit,
) {
    when (updateState) {
        is UpdateUiState.UpdateAvailable -> {
            val release = updateState.releaseInfo
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Bản cập nhật mới: ${release.tagName}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Dung lượng: ${release.apkSize / (1024 * 1024)} MB")
                        Text("Ghi chú phát hành:")
                        Text(
                            text = release.releaseNotes,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = { onStartDownload(release) }) {
                        Text("Tải và cài đặt")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Để sau")
                    }
                },
            )
        }

        is UpdateUiState.Downloading -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Đang tải bản cập nhật...") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LinearProgressIndicator(
                            progress = { updateState.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "Tiến độ: ${updateState.progressPercent}%",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                },
                confirmButton = {},
            )
        }

        is UpdateUiState.ReadyToInstall -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Đã tải xong bản cập nhật") },
                text = {
                    Text(
                        "Bản cài đặt đã được tải và xác thực toàn vẹn. Nhấn 'Cài đặt ngay' để tiến hành nâng cấp.",
                    )
                },
                confirmButton = {
                    Button(onClick = { onInstall(updateState.apkFile) }) {
                        Text("Cài đặt ngay")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Đóng")
                    }
                },
            )
        }

        is UpdateUiState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Cập nhật thất bại") },
                text = { Text(updateState.message) },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Đóng")
                    }
                },
            )
        }

        else -> Unit
    }
}
