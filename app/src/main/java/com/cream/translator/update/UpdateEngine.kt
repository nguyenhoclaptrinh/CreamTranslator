package com.cream.translator.update

import com.cream.translator.core.model.error.CreamError
import com.cream.translator.update.model.AppReleaseInfo
import com.cream.translator.update.model.DownloadState
import kotlinx.coroutines.CancellationException
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
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

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
                    if (responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                        val resetHeader = connection.getHeaderField("X-RateLimit-Reset")
                        val resetSeconds = resetHeader?.toLongOrNull()
                        val err = CreamError.UpdateError.RateLimited(resetSeconds)
                        Timber.w("GitHub API rate limit exceeded. Reset at: %s", resetSeconds)
                        return@withContext Result.failure(IllegalStateException(err.userFriendlyMessage))
                    }
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
                    Timber.w("No APK asset found in release %s", tagName)
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
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: UnknownHostException) {
                Timber.e(e, "Network unavailable when checking for updates")
                Result.failure(IllegalStateException(CreamError.UpdateError.NetworkUnavailable(e).userFriendlyMessage))
            } catch (e: SocketTimeoutException) {
                Timber.e(e, "Connection timeout when checking for updates")
                Result.failure(IllegalStateException("Hết thời gian kết nối tới máy chủ cập nhật"))
            } catch (e: Throwable) {
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

            val tempFile = File(destinationFile.parentFile, "${destinationFile.name}.tmp")
            var connection: HttpURLConnection? = null

            try {
                connection = resolveRedirectConnection(downloadUrl)
                if (connection == null) {
                    emit(
                        DownloadState.Error(
                            "Không thể thiết lập kết nối tải an toàn (chuyển hướng thất bại)",
                        ),
                    )
                    return@flow
                }

                val totalBytes = connection.contentLengthLong

                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        writeStreamWithProgress(input, output, totalBytes) { downloaded, percent ->
                            emit(DownloadState.Progress(downloaded, totalBytes, percent))
                        }
                    }
                }

                // Invariant: Data integrity check to prevent executing truncated/corrupt APKs
                if (totalBytes > 0 && tempFile.length() != totalBytes) {
                    val actualLength = tempFile.length()
                    tempFile.delete()
                    val err = CreamError.UpdateError.PayloadCorrupted(totalBytes, actualLength)
                    emit(DownloadState.Error(err.userFriendlyMessage))
                    return@flow
                }

                if (destinationFile.exists()) {
                    destinationFile.delete()
                }

                if (!tempFile.renameTo(destinationFile)) {
                    tempFile.delete()
                    emit(DownloadState.Error("Không thể hoàn tất lưu tệp cập nhật"))
                    return@flow
                }

                emit(DownloadState.Completed(destinationFile))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Throwable) {
                Timber.e(e, "Exception during APK download")
                emit(DownloadState.Error(e.message ?: "Lỗi tải tệp cài đặt"))
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
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

            val status = conn.responseCode
            if (status in REDIRECT_STATUS_CODES) {
                val newUrl = conn.getHeaderField("Location")
                conn.disconnect()
                if (newUrl == null) return null
                currentUrl = newUrl
                redirects++
            } else if (status == HttpURLConnection.HTTP_OK) {
                return conn
            } else {
                conn.disconnect()
                return null
            }
        }
        return null
    }

    private inline fun writeStreamWithProgress(
        input: InputStream,
        output: OutputStream,
        totalBytes: Long,
        onProgress: (downloaded: Long, percent: Int) -> Unit,
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        var totalRead = 0L

        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            totalRead += bytesRead
            val percent =
                if (totalBytes > 0) {
                    ((totalRead * 100) / totalBytes).toInt().coerceIn(0, 100)
                } else {
                    0
                }
            onProgress(totalRead, percent)
        }
        output.flush()
    }

    internal fun isNewerVersion(
        remoteVersion: String,
        currentVersion: String,
    ): Boolean {
        val remoteClean = remoteVersion.substringBefore("-").substringBefore("+")
        val currentClean = currentVersion.substringBefore("-").substringBefore("+")

        val remoteParts = remoteClean.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = currentClean.split(".").mapNotNull { it.toIntOrNull() }

        val maxLength = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLength) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }

        return false
    }

    companion object {
        private const val TIMEOUT_MS = 15_000
        private const val BUFFER_SIZE = 8 * 1024
        private const val MAX_REDIRECTS = 5
        private const val HTTP_TEMPORARY_REDIRECT = 307
        private const val HTTP_PERMANENT_REDIRECT = 308

        private val REDIRECT_STATUS_CODES =
            setOf(
                HttpURLConnection.HTTP_MOVED_TEMP,
                HttpURLConnection.HTTP_MOVED_PERM,
                HttpURLConnection.HTTP_SEE_OTHER,
                HTTP_TEMPORARY_REDIRECT,
                HTTP_PERMANENT_REDIRECT,
            )
    }
}
