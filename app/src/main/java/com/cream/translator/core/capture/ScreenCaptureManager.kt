package com.cream.translator.core.capture

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.nio.ByteBuffer

sealed interface CaptureLifecycleState {
    data object Uninitialized : CaptureLifecycleState

    data object CallbackRegistered : CaptureLifecycleState

    data class Active(
        val width: Int,
        val height: Int,
        val density: Int,
    ) : CaptureLifecycleState

    data object Stopped : CaptureLifecycleState
}

class ScreenCaptureManager(
    private val context: Context,
    private val mediaProjection: MediaProjection,
) {
    private val displayLock = Any()
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenDensity: Int = DisplayMetrics.DENSITY_DEFAULT
    private var screenWidth: Int = 1080
    private var screenHeight: Int = 1920

    @Volatile
    var isProjectionActive: Boolean = true
        private set

    @Volatile
    private var isReleased: Boolean = false

    @Volatile
    var lifecycleState: CaptureLifecycleState = CaptureLifecycleState.Uninitialized
        private set

    var onStopCallback: (() -> Unit)? = null

    private val projectionCallback =
        object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                isProjectionActive = false
                lifecycleState = CaptureLifecycleState.Stopped
                release()
                onStopCallback?.invoke()
            }
        }

    init {
        try {
            // Android 14+ (API 34+) strictly requires registering a MediaProjection.Callback
            // before createVirtualDisplay() is called, otherwise throwing IllegalStateException.
            mediaProjection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
            lifecycleState = CaptureLifecycleState.CallbackRegistered
            updateScreenDimensions()
            synchronized(displayLock) {
                setupVirtualDisplayLocked()
            }
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException initializing ScreenCaptureManager")
            release()
        } catch (e: IllegalStateException) {
            Timber.e(e, "IllegalStateException initializing ScreenCaptureManager")
            release()
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "IllegalArgumentException initializing ScreenCaptureManager")
            release()
        }
    }

    private fun readDisplayMetrics(): DisplayMetrics {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    private fun updateScreenDimensions() {
        val metrics = readDisplayMetrics()
        synchronized(displayLock) {
            if (metrics.widthPixels > 0 && metrics.heightPixels > 0 && metrics.densityDpi > 0) {
                screenWidth = metrics.widthPixels
                screenHeight = metrics.heightPixels
                screenDensity = metrics.densityDpi
            }
        }
    }

    private fun checkAndSyncDimensions() {
        val metrics = readDisplayMetrics()
        synchronized(displayLock) {
            if (isReleased || !isProjectionActive || lifecycleState == CaptureLifecycleState.Stopped) {
                return
            }
            if (metrics.widthPixels <= 0 || metrics.heightPixels <= 0 || metrics.densityDpi <= 0) {
                return
            }
            val isDimensionChanged =
                metrics.widthPixels != screenWidth ||
                    metrics.heightPixels != screenHeight ||
                    metrics.densityDpi != screenDensity
            if (isDimensionChanged) {
                Timber.i(
                    "Display configuration change detected: %dx%d -> %dx%d (%ddpi)",
                    screenWidth,
                    screenHeight,
                    metrics.widthPixels,
                    metrics.heightPixels,
                    metrics.densityDpi,
                )
                screenWidth = metrics.widthPixels
                screenHeight = metrics.heightPixels
                screenDensity = metrics.densityDpi
                resizeVirtualDisplayLocked()
            }
        }
    }

    @SuppressLint("WrongConstant")
    private fun setupVirtualDisplayLocked() {
        if (isReleased || !isProjectionActive || virtualDisplay != null) {
            return
        }
        if (screenWidth <= 0 || screenHeight <= 0 || screenDensity <= 0) {
            Timber.w("setupVirtualDisplayLocked skipped: invalid dimensions %dx%d", screenWidth, screenHeight)
            releaseLocked()
            return
        }

        var createdReader: ImageReader? = null
        try {
            imageReader?.close()
            imageReader = null
            createdReader =
                ImageReader.newInstance(
                    screenWidth,
                    screenHeight,
                    PixelFormat.RGBA_8888,
                    2,
                )

            val display =
                mediaProjection.createVirtualDisplay(
                    "ScreenCaptureVirtualDisplay",
                    screenWidth,
                    screenHeight,
                    screenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    createdReader.surface,
                    null,
                    null,
                )
            if (display == null) {
                Timber.e("createVirtualDisplay returned null")
                createdReader.close()
                releaseLocked()
                return
            }
            imageReader = createdReader
            createdReader = null
            virtualDisplay = display
            lifecycleState = CaptureLifecycleState.Active(screenWidth, screenHeight, screenDensity)
            Timber.d("VirtualDisplay initialized: %dx%d @ %ddpi", screenWidth, screenHeight, screenDensity)
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException initializing VirtualDisplay")
            createdReader?.close()
            releaseLocked()
        } catch (e: IllegalStateException) {
            Timber.e(e, "IllegalStateException initializing VirtualDisplay")
            createdReader?.close()
            releaseLocked()
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "IllegalArgumentException initializing VirtualDisplay")
            createdReader?.close()
            releaseLocked()
        }
    }

    @SuppressLint("WrongConstant")
    private fun resizeVirtualDisplayLocked() {
        if (isReleased || !isProjectionActive || lifecycleState == CaptureLifecycleState.Stopped) {
            return
        }
        val currentDisplay = virtualDisplay
        if (currentDisplay == null) {
            Timber.w("resizeVirtualDisplayLocked skipped: virtualDisplay is null")
            return
        }
        if (screenWidth <= 0 || screenHeight <= 0 || screenDensity <= 0) {
            Timber.w(
                "resizeVirtualDisplayLocked skipped: invalid dimensions %dx%d @ %ddpi",
                screenWidth,
                screenHeight,
                screenDensity,
            )
            return
        }

        var newReader: ImageReader? = null
        try {
            newReader =
                ImageReader.newInstance(
                    screenWidth,
                    screenHeight,
                    PixelFormat.RGBA_8888,
                    2,
                )
            currentDisplay.resize(screenWidth, screenHeight, screenDensity)
            currentDisplay.surface = newReader.surface
            val oldReader = imageReader
            imageReader = newReader
            newReader = null
            closeReaderSafely(oldReader)
            lifecycleState = CaptureLifecycleState.Active(screenWidth, screenHeight, screenDensity)
            Timber.i("VirtualDisplay resized: %dx%d @ %ddpi", screenWidth, screenHeight, screenDensity)
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "IllegalArgumentException resizing VirtualDisplay")
            closeReaderSafely(newReader)
            releaseLocked()
        } catch (e: IllegalStateException) {
            Timber.e(e, "IllegalStateException resizing VirtualDisplay")
            closeReaderSafely(newReader)
            releaseLocked()
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException resizing VirtualDisplay")
            closeReaderSafely(newReader)
            releaseLocked()
        }
    }

    private fun closeReaderSafely(reader: ImageReader?) {
        try {
            reader?.close()
        } catch (e: IllegalStateException) {
            Timber.w(e, "Error closing ImageReader")
        }
    }

    suspend fun captureFrame(): Bitmap? =
        withContext(Dispatchers.Default) {
            if (isReleased || !isProjectionActive) return@withContext null
            checkAndSyncDimensions()

            val firstFrame = acquireAndCopyBitmap()
            if (firstFrame != null) {
                return@withContext firstFrame
            }

            // Wait briefly for a frame to render without holding displayLock across suspension
            delay(FRAME_RETRY_DELAY_MS)
            if (isReleased || !isProjectionActive) return@withContext null

            acquireAndCopyBitmap() ?: run {
                Timber.w("No frame captured from ImageReader")
                null
            }
        }

    private fun acquireAndCopyBitmap(): Bitmap? {
        synchronized(displayLock) {
            if (isReleased || !isProjectionActive) return null
            val reader = imageReader ?: return null
            var image: Image? = null
            var rawBitmap: Bitmap? = null
            return try {
                image = reader.acquireLatestImage() ?: return null
                val copied = copyImageToBitmap(image) { allocated -> rawBitmap = allocated }
                rawBitmap = null
                copied
            } catch (cancellation: CancellationException) {
                rawBitmap?.recycle()
                throw cancellation
            } catch (e: IllegalStateException) {
                rawBitmap?.recycle()
                Timber.e(e, "IllegalStateException capturing screen frame")
                null
            } catch (e: UnsupportedOperationException) {
                rawBitmap?.recycle()
                Timber.e(e, "UnsupportedOperationException capturing screen frame")
                null
            } catch (e: IllegalArgumentException) {
                rawBitmap?.recycle()
                Timber.e(e, "IllegalArgumentException capturing screen frame")
                null
            } finally {
                image?.close()
            }
        }
    }

    private inline fun copyImageToBitmap(
        image: Image,
        onRawAllocated: (Bitmap) -> Unit,
    ): Bitmap? {
        val imgWidth = image.width
        val imgHeight = image.height
        val planes = image.planes
        if (imgWidth <= 0 || imgHeight <= 0 || planes.isEmpty()) {
            return null
        }

        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        if (pixelStride <= 0) {
            return null
        }

        val rowPadding = (rowStride - pixelStride * imgWidth).coerceAtLeast(0)
        val rawWidth = imgWidth + rowPadding / pixelStride
        val rawBitmap =
            Bitmap.createBitmap(
                rawWidth,
                imgHeight,
                Bitmap.Config.ARGB_8888,
            )
        onRawAllocated(rawBitmap)
        rawBitmap.copyPixelsFromBuffer(buffer)

        return if (rawWidth == imgWidth) {
            rawBitmap
        } else {
            val cleanBitmap = Bitmap.createBitmap(rawBitmap, 0, 0, imgWidth, imgHeight)
            if (cleanBitmap !== rawBitmap) {
                rawBitmap.recycle()
            }
            cleanBitmap
        }
    }

    fun onConfigurationChanged() {
        val metrics = readDisplayMetrics()
        synchronized(displayLock) {
            if (isReleased || !isProjectionActive || lifecycleState == CaptureLifecycleState.Stopped) {
                return
            }
            if (metrics.widthPixels > 0 && metrics.heightPixels > 0 && metrics.densityDpi > 0) {
                screenWidth = metrics.widthPixels
                screenHeight = metrics.heightPixels
                screenDensity = metrics.densityDpi
            }
            resizeVirtualDisplayLocked()
        }
    }

    fun release() {
        synchronized(displayLock) {
            releaseLocked()
        }
    }

    private fun releaseLocked() {
        if (isReleased) return
        isReleased = true
        isProjectionActive = false
        lifecycleState = CaptureLifecycleState.Stopped

        try {
            mediaProjection.unregisterCallback(projectionCallback)
        } catch (_: IllegalArgumentException) {
            // Callback might already be unregistered or projection stopped
        } catch (_: IllegalStateException) {
            // Projection already stopped
        }

        try {
            virtualDisplay?.release()
        } catch (e: IllegalStateException) {
            Timber.e(e, "IllegalStateException releasing VirtualDisplay")
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException releasing VirtualDisplay")
        } finally {
            virtualDisplay = null
        }

        try {
            imageReader?.close()
        } catch (e: IllegalStateException) {
            Timber.e(e, "IllegalStateException closing ImageReader")
        } finally {
            imageReader = null
        }

        try {
            mediaProjection.stop()
        } catch (e: IllegalStateException) {
            Timber.e(e, "IllegalStateException stopping MediaProjection")
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException stopping MediaProjection")
        }

        Timber.d("ScreenCaptureManager released safely")
    }

    companion object {
        private const val FRAME_RETRY_DELAY_MS = 50L
    }
}
