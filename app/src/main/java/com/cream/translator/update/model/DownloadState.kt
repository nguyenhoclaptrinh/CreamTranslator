package com.cream.translator.update.model

import java.io.File

sealed interface DownloadState {
    data object Idle : DownloadState

    data class Progress(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val percentage: Int,
    ) : DownloadState

    data class Completed(
        val apkFile: File,
    ) : DownloadState

    data class Error(
        val message: String,
    ) : DownloadState
}
