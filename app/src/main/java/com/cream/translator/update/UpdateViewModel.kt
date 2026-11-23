package com.cream.translator.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cream.translator.BuildConfig
import com.cream.translator.update.model.AppReleaseInfo
import com.cream.translator.update.model.DownloadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateUiState {
    data object Idle : UpdateUiState

    data object Checking : UpdateUiState

    data class NoUpdateAvailable(
        val currentVersion: String,
    ) : UpdateUiState

    data class UpdateAvailable(
        val releaseInfo: AppReleaseInfo,
    ) : UpdateUiState

    data class Downloading(
        val releaseInfo: AppReleaseInfo,
        val progressPercent: Int,
    ) : UpdateUiState

    data class ReadyToInstall(
        val apkFile: File,
    ) : UpdateUiState

    data class Error(
        val message: String,
    ) : UpdateUiState
}

class UpdateViewModel(
    application: Application,
    private val engine: UpdateEngine = UpdateEngine(),
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    fun checkForUpdate() {
        viewModelScope.launch {
            _uiState.value = UpdateUiState.Checking
            val result = engine.checkForUpdate(BuildConfig.VERSION_NAME)
            result
                .onSuccess { releaseInfo ->
                    if (releaseInfo != null) {
                        _uiState.value = UpdateUiState.UpdateAvailable(releaseInfo)
                    } else {
                        _uiState.value = UpdateUiState.NoUpdateAvailable(BuildConfig.VERSION_NAME)
                    }
                }.onFailure { error ->
                    _uiState.value = UpdateUiState.Error(error.message ?: "Kiểm tra cập nhật thất bại")
                }
        }
    }

    fun startDownload(releaseInfo: AppReleaseInfo) {
        viewModelScope.launch {
            // Cache Hygiene: purge legacy cached APK and temporary files before downloading new version
            val updateDir = File(getApplication<Application>().cacheDir, "updates")
            if (updateDir.exists()) {
                updateDir.listFiles()?.forEach { file ->
                    if (file.isFile && (file.name.endsWith(".apk") || file.name.endsWith(".tmp"))) {
                        file.delete()
                    }
                }
            }

            val destinationFile = File(updateDir, "CreamTranslator-${releaseInfo.tagName}.apk")

            engine.downloadApk(releaseInfo.downloadUrl, destinationFile).collect { state ->
                when (state) {
                    is DownloadState.Progress -> {
                        _uiState.value = UpdateUiState.Downloading(releaseInfo, state.percentage)
                    }
                    is DownloadState.Completed -> {
                        _uiState.value = UpdateUiState.ReadyToInstall(state.apkFile)
                    }
                    is DownloadState.Error -> {
                        _uiState.value = UpdateUiState.Error(state.message)
                    }
                    DownloadState.Idle -> Unit
                }
            }
        }
    }

    fun dismissDialog() {
        _uiState.value = UpdateUiState.Idle
    }
}
