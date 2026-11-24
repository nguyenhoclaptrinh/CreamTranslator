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
        // Android 14+ (API 34+) strictly requires registering a MediaProjection.Callback
        // before createVirtualDisplay() is called, otherwise throwing IllegalStateException.
        mediaProjection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
        lifecycleState = CaptureLifecycleState.CallbackRegistered
        updateScreenDimensions()
        setupVirtualDisplay()
    }

    private fun updateScreenDimensions() {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
    }

    private fun checkAndSyncDimensions() {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
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
            resizeVirtualDisplay()
        }
    }

    @SuppressLint("WrongConstant")
    private fun setupVirtualDisplay() {
        synchronized(displayLock) {
            if (virtualDisplay != null) {
                resizeVirtualDisplay()
                return
            }
            try {
                imageReader?.close()
                imageReader =
                    ImageReader.newInstance(
                        screenWidth,
                        screenHeight,
                        PixelFormat.RGBA_8888,
                        2,
                    )

                virtualDisplay =
                    mediaProjection.createVirtualDisplay(
                        "ScreenCaptureVirtualDisplay",
                        screenWidth,
                        screenHeight,
                        screenDensity,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        imageReader?.surface,
                        null,
                        null,
                    )
                lifecycleState = CaptureLifecycleState.Active(screenWidth, screenHeight, screenDensity)
                Timber.d("VirtualDisplay initialized: %dx%d @ %ddpi", screenWidth, screenHeight, screenDensity)
            } catch (e: SecurityException) {
                Timber.e(e, "SecurityException initializing VirtualDisplay")
            } catch (e: IllegalStateException) {
                Timber.e(e, "IllegalStateException initializing VirtualDisplay")
            }
        }
    }

    @SuppressLint("WrongConstant")
    private fun resizeVirtualDisplay() {
        synchronized(displayLock) {
            val currentDisplay =
                virtualDisplay ?: run {
                    setupVirtualDisplay()
                    return
                }
            try {
                val oldReader = imageReader
                val newReader =
                    ImageReader.newInstance(
                        screenWidth,
                        screenHeight,
                        PixelFormat.RGBA_8888,
                        2,
                    )
                imageReader = newReader
                currentDisplay.resize(screenWidth, screenHeight, screenDensity)
                currentDisplay.surface = newReader.surface
                oldReader?.close()
                lifecycleState = CaptureLifecycleState.Active(screenWidth, screenHeight, screenDensity)
                Timber.i("VirtualDisplay resized: %dx%d @ %ddpi", screenWidth, screenHeight, screenDensity)
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "IllegalArgumentException resizing VirtualDisplay")
            } catch (e: IllegalStateException) {
                Timber.e(e, "IllegalStateException resizing VirtualDisplay")
            }
        }
    }

    suspend fun captureFrame(): Bitmap? =
        withContext(Dispatchers.Default) {
            checkAndSyncDimensions()
            val reader = imageReader ?: return@withContext null
            var image: Image? = null
            var rawBitmap: Bitmap? = null
            try {
                image = reader.acquireLatestImage()
                if (image == null) {
                    // Wait briefly for a frame to render without blocking thread
                    delay(50)
                    image = reader.acquireLatestImage()
                }
                if (image == null) {
                    Timber.w("No frame captured from ImageReader")
                    return@withContext null
                }

                val planes = image.planes
                val buffer: ByteBuffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * screenWidth

                rawBitmap =
                    Bitmap.createBitmap(
                        screenWidth + rowPadding / pixelStride,
                        screenHeight,
                        Bitmap.Config.ARGB_8888,
                    )
                rawBitmap.copyPixelsFromBuffer(buffer)

                // Crop out padding if present
                if (rowPadding == 0) {
                    val result = rawBitmap
                    rawBitmap = null
                    result
                } else {
                    val cleanBitmap = Bitmap.createBitmap(rawBitmap, 0, 0, screenWidth, screenHeight)
                    rawBitmap.recycle()
                    rawBitmap = null
                    cleanBitmap
                }
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

    fun onConfigurationChanged() {
        updateScreenDimensions()
        resizeVirtualDisplay()
    }

    fun release() {
        synchronized(displayLock) {
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
                virtualDisplay = null
                imageReader?.close()
                imageReader = null
                mediaProjection.stop()
                Timber.d("ScreenCaptureManager released safely")
            } catch (e: IllegalStateException) {
                Timber.e(e, "Error releasing ScreenCaptureManager")
            }
        }
    }
}
