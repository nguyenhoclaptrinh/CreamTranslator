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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.cream.translator.BuildConfig
import com.cream.translator.R
import com.cream.translator.core.engine.MlKitTranslationEngine
import com.cream.translator.core.model.LanguagePair
import com.cream.translator.data.repository.FilterPreferencesRepository
import com.cream.translator.service.TranslatorController
import com.cream.translator.ui.settings.SettingsScreen
import com.cream.translator.update.UpdateInstaller
import com.cream.translator.update.UpdateUiState
import com.cream.translator.update.UpdateViewModel
import com.cream.translator.update.model.AppReleaseInfo
import kotlinx.coroutines.launch
import java.io.File

data class DashboardActions(
    val onRequestOverlayPermission: () -> Unit,
    val onStartService: (LanguagePair) -> Unit,
    val onStopService: () -> Unit,
    val onCheckUpdate: () -> Unit,
    val onStartDownload: (AppReleaseInfo) -> Unit,
    val onDismissUpdateDialog: () -> Unit,
    val onInstallApk: (File) -> Unit,
    val onDownloadModel: (LanguagePair, (Boolean) -> Unit) -> Unit,
    val onOpenSettings: () -> Unit,
)

enum class AppScreen {
    DASHBOARD,
    SETTINGS,
}

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

    private var pendingLanguagePair = LanguagePair("en", "vi")

    private val screenCaptureLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                TranslatorController.start(this, result.resultCode, data, pendingLanguagePair)
                Toast.makeText(this, getString(R.string.msg_screen_translator_started), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.msg_screen_capture_denied), Toast.LENGTH_SHORT).show()
            }
        }

    private var hasOverlayPermission by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var currentScreen by remember { mutableStateOf(AppScreen.DASHBOARD) }
                    val filterRepository = remember { FilterPreferencesRepository.getInstance(this@MainActivity) }

                    when (currentScreen) {
                        AppScreen.DASHBOARD -> {
                            val updateState by updateViewModel.uiState.collectAsState()
                            val isServiceRunning by TranslatorController.isServiceRunning.collectAsState()
                            val actions =
                                DashboardActions(
                                    onRequestOverlayPermission = { requestOverlayPermission() },
                                    onStartService = { pair ->
                                        pendingLanguagePair = pair
                                        startScreenCapture()
                                    },
                                    onStopService = {
                                        TranslatorController.stop(this@MainActivity)
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Screen Translator Stopped",
                                            Toast.LENGTH_SHORT,
                                        ).show()
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
                                    onOpenSettings = { currentScreen = AppScreen.SETTINGS },
                                )

                            TranslatorDashboard(
                                hasOverlayPermission = hasOverlayPermission,
                                isServiceRunning = isServiceRunning,
                                updateState = updateState,
                                actions = actions,
                            )
                        }

                        AppScreen.SETTINGS -> {
                            SettingsScreen(
                                repository = filterRepository,
                                onNavigateBack = { currentScreen = AppScreen.DASHBOARD },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun triggerApkInstall(apkFile: File) {
        if (!UpdateInstaller.canRequestPackageInstalls(this)) {
            Toast.makeText(
                this,
                getString(R.string.msg_grant_install_unknown_sources),
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
    val supportedPairs =
        remember {
            listOf(
                LanguagePair("en", "vi"),
                LanguagePair("ja", "vi"),
                LanguagePair("zh", "vi"),
                LanguagePair("ko", "vi"),
            )
        }
    var selectedPair by remember { mutableStateOf(supportedPairs.first()) }
    var isModelReady by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }

    LaunchedEffect(selectedPair) {
        isDownloading = true
        isModelReady = false
        actions.onDownloadModel(selectedPair) { success ->
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
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.tagline),
            style = MaterialTheme.typography.bodyMedium,
        )

        SystemStatusCard(
            hasOverlayPermission = hasOverlayPermission,
            selectedPair = selectedPair,
            isModelReady = isModelReady,
            isDownloading = isDownloading,
            isServiceRunning = isServiceRunning,
        )

        LanguagePairSelector(
            supportedPairs = supportedPairs,
            selectedPair = selectedPair,
            onSelect = { selectedPair = it },
        )

        TranslatorControlButtons(
            hasOverlayPermission = hasOverlayPermission,
            isServiceRunning = isServiceRunning,
            isModelReady = isModelReady,
            selectedPair = selectedPair,
            actions = actions,
            onTriggerModelDownload = {
                isDownloading = true
                actions.onDownloadModel(selectedPair) { success ->
                    isModelReady = success
                    isDownloading = false
                }
            },
        )

        OutlinedButton(
            onClick = actions.onOpenSettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.btn_filter_settings))
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
                    Text(stringResource(R.string.btn_checking_update))
                } else {
                    Text(stringResource(R.string.btn_check_update))
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
private fun SystemStatusCard(
    hasOverlayPermission: Boolean,
    selectedPair: LanguagePair,
    isModelReady: Boolean,
    isDownloading: Boolean,
    isServiceRunning: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.status_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text =
                    stringResource(
                        R.string.status_app_version,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                    ),
            )
            Text(
                text =
                    stringResource(
                        R.string.status_overlay_permission,
                        if (hasOverlayPermission) {
                            stringResource(R.string.status_permission_granted)
                        } else {
                            stringResource(R.string.status_permission_missing)
                        },
                    ),
            )
            Text(
                text =
                    stringResource(
                        R.string.status_offline_model,
                        selectedPair.displayName,
                        if (isModelReady) {
                            stringResource(R.string.status_model_ready)
                        } else if (isDownloading) {
                            stringResource(R.string.status_model_downloading)
                        } else {
                            stringResource(R.string.status_model_not_ready)
                        },
                    ),
            )
            Text(text = stringResource(R.string.status_anti_cheat))
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
}

@Composable
private fun TranslatorControlButtons(
    hasOverlayPermission: Boolean,
    isServiceRunning: Boolean,
    isModelReady: Boolean,
    selectedPair: LanguagePair,
    actions: DashboardActions,
    onTriggerModelDownload: () -> Unit,
) {
    if (!hasOverlayPermission) {
        Button(
            onClick = actions.onRequestOverlayPermission,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.btn_grant_overlay_permission))
        }
    } else if (isServiceRunning) {
        Button(
            onClick = actions.onStopService,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.btn_stop_service))
        }
    } else {
        Button(
            onClick = { actions.onStartService(selectedPair) },
            enabled = isModelReady,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.btn_start_service))
        }
    }

    OutlinedButton(
        onClick = onTriggerModelDownload,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.btn_reverify_model))
    }
}

@Composable
private fun LanguagePairSelector(
    supportedPairs: List<LanguagePair>,
    selectedPair: LanguagePair,
    onSelect: (LanguagePair) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Language Pair (Ngôn ngữ dịch):",
            style = MaterialTheme.typography.titleSmall,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            supportedPairs.forEach { pair ->
                FilterChip(
                    selected = pair == selectedPair,
                    onClick = {
                        if (pair != selectedPair) {
                            onSelect(pair)
                        }
                    },
                    label = { Text(pair.displayName) },
                )
            }
        }
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
                title = { Text(stringResource(R.string.update_dialog_title, release.tagName)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.update_dialog_size, release.apkSize / (1024 * 1024)))
                        Text(stringResource(R.string.update_dialog_release_notes))
                        Text(
                            text = release.releaseNotes.ifBlank { stringResource(R.string.update_dialog_no_notes) },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = { onStartDownload(release) }) {
                        Text(stringResource(R.string.update_dialog_btn_download))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.update_dialog_btn_later))
                    }
                },
            )
        }

        is UpdateUiState.Downloading -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.update_dialog_downloading_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LinearProgressIndicator(
                            progress = { updateState.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = stringResource(R.string.update_dialog_progress, updateState.progressPercent),
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
                title = { Text(stringResource(R.string.update_dialog_ready_title)) },
                text = { Text(stringResource(R.string.update_dialog_ready_message)) },
                confirmButton = {
                    Button(onClick = { onInstall(updateState.apkFile) }) {
                        Text(stringResource(R.string.update_dialog_btn_install))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.update_dialog_btn_close))
                    }
                },
            )
        }

        is UpdateUiState.NoUpdateAvailable -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.update_dialog_latest_title)) },
                text = { Text(stringResource(R.string.update_dialog_latest_message, updateState.currentVersion)) },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.update_dialog_btn_got_it))
                    }
                },
            )
        }

        is UpdateUiState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.update_dialog_error_title)) },
                text = { Text(updateState.message) },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.update_dialog_btn_close))
                    }
                },
            )
        }

        else -> Unit
    }
}
