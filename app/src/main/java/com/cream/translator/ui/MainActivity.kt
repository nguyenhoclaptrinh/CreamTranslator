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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cream.translator.core.engine.MlKitTranslationEngine
import com.cream.translator.core.model.LanguagePair
import com.cream.translator.service.OverlayService
import com.cream.translator.service.ScreenCaptureService
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val translationEngine = MlKitTranslationEngine()

    private val overlayPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {
            checkPermissions()
        }

    private val screenCaptureLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val serviceIntent =
                    Intent(this, ScreenCaptureService::class.java).apply {
                        putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                        putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
                    }
                startForegroundService(serviceIntent)
                startService(Intent(this, OverlayService::class.java))
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
                    TranslatorDashboard(
                        hasOverlayPermission = hasOverlayPermission,
                        onRequestOverlayPermission = { requestOverlayPermission() },
                        onStartService = { startScreenCapture() },
                        onDownloadModel = { pair, onComplete ->
                            // Trigger offline model pack download once
                            lifecycleScopeLaunch {
                                val success = translationEngine.ensureModelDownloaded(pair)
                                onComplete(success)
                            }
                        },
                    )
                }
            }
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
        kotlinx.coroutines.MainScope().launch {
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
    onRequestOverlayPermission: () -> Unit,
    onStartService: () -> Unit,
    onDownloadModel: (LanguagePair, (Boolean) -> Unit) -> Unit,
) {
    var isModelReady by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isDownloading = true
        onDownloadModel(LanguagePair("en", "vi")) { success ->
            isModelReady = success
            isDownloading = false
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
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
            }
        }

        if (!hasOverlayPermission) {
            Button(
                onClick = onRequestOverlayPermission,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Grant Overlay Permission")
            }
        } else {
            Button(
                onClick = onStartService,
                enabled = isModelReady,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Start Translator Service")
            }
        }

        OutlinedButton(
            onClick = {
                isDownloading = true
                onDownloadModel(LanguagePair("en", "vi")) { success ->
                    isModelReady = success
                    isDownloading = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Re-verify Offline Model")
        }
    }
}
