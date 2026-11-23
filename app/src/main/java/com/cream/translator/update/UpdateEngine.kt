package com.cream.translator.update

import com.cream.translator.update.model.AppReleaseInfo
import com.cream.translator.update.model.DownloadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class UpdateEngine(
    private val repoOwner: String = "nguyenhoclaptrinh",
    private val repoName: String = "CreamTranslator",
) {
    suspend fun checkForUpdate(currentVersionName: String): Result<AppReleaseInfo?> =
        withContext(Dispatchers.IO) {
            try {
                val apiUrl = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
                val connection =
                    (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = TIMEOUT_MS
                        readTimeout = TIMEOUT_MS
                        setRequestProperty("Accept", "application/vnd.github.v3+json")
                        setRequestProperty("User-Agent", "CreamTranslator-Android")
                    }

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    val errorMsg = "GitHub API returned HTTP $responseCode"
                    Timber.e(errorMsg)
                    return@withContext Result.failure(IllegalStateException(errorMsg))
                }

                val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseBody)

                val tagName = json.getString("tag_name")
                val releaseNotes = json.optString("body", "Không có ghi chú phát hành.")
                val remoteVersion = tagName.removePrefix("v").removePrefix("V")

                val assets = json.getJSONArray("assets")
                var apkDownloadUrl: String? = null
                var apkSize: Long = 0L

                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.getString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkDownloadUrl = asset.getString("browser_download_url")
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }

                if (apkDownloadUrl == null) {
                    Timber.w("No APK asset found in release $tagName")
                    return@withContext Result.success(null)
                }

                if (isNewerVersion(remoteVersion, currentVersionName)) {
                    val info =
                        AppReleaseInfo(
                            tagName = tagName,
                            versionName = remoteVersion,
                            releaseNotes = releaseNotes,
                            downloadUrl = apkDownloadUrl,
                            apkSize = apkSize,
                        )
                    Result.success(info)
                } else {
                    Result.success(null)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error checking for updates")
                Result.failure(e)
            }
        }

    fun downloadApk(
        downloadUrl: String,
        destinationFile: File,
    ): Flow<DownloadState> =
        flow {
            emit(DownloadState.Progress(0L, 0L, 0))
            destinationFile.parentFile?.mkdirs()

            var connection: HttpURLConnection? = null
            try {
                connection = resolveRedirectConnection(downloadUrl)
                if (connection == null) {
                    emit(DownloadState.Error("Too many redirects or failed connection"))
                    return@flow
                }

                val totalBytes = connection.contentLengthLong

                connection.inputStream.use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        writeStreamWithProgress(input, output, totalBytes) { downloaded, percent ->
                            emit(DownloadState.Progress(downloaded, totalBytes, percent))
                        }
                    }
                }

                emit(DownloadState.Completed(destinationFile))
            } catch (e: Exception) {
                Timber.e(e, "Exception during APK download")
                emit(DownloadState.Error(e.message ?: "Lỗi tải tệp cài đặt"))
            } finally {
                connection?.disconnect()
            }
        }.flowOn(Dispatchers.IO)

    private fun resolveRedirectConnection(initialUrl: String): HttpURLConnection? {
        var currentUrl = initialUrl
        var redirects = 0

        while (redirects < MAX_REDIRECTS) {
            val conn =
                (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    setRequestProperty("User-Agent", "CreamTranslator-Android")
                    instanceFollowRedirects = false
                }
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                code == HttpURLConnection.HTTP_MOVED_TEMP ||
                code == HTTP_SEE_OTHER
            ) {
                currentUrl = conn.getHeaderField("Location")
                conn.disconnect()
                redirects++
            } else if (code == HttpURLConnection.HTTP_OK) {
                return conn
            } else {
                conn.disconnect()
                return null
            }
        }
        return null
    }

    private suspend fun writeStreamWithProgress(
        input: InputStream,
        output: FileOutputStream,
        totalBytes: Long,
        onProgress: suspend (Long, Int) -> Unit,
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        var bytesDownloaded = 0L
        var lastReportedPercent = -1

        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            bytesDownloaded += bytesRead
            val percent =
                if (totalBytes > 0) {
                    ((bytesDownloaded * 100) / totalBytes).toInt()
                } else {
                    0
                }

            if (percent != lastReportedPercent) {
                lastReportedPercent = percent
                onProgress(bytesDownloaded, percent)
            }
        }
    }

    fun isNewerVersion(
        remoteVersion: String,
        currentVersion: String,
    ): Boolean {
        val remoteParts = remoteVersion.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = currentVersion.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    companion object {
        private const val TIMEOUT_MS = 15000
        private const val BUFFER_SIZE = 8192
        private const val MAX_REDIRECTS = 5
        private const val HTTP_SEE_OTHER = 303
    }
}
