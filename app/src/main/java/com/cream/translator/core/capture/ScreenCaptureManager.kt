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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.nio.ByteBuffer

class ScreenCaptureManager(
    private val context: Context,
    private val mediaProjection: MediaProjection,
) {
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenDensity: Int = DisplayMetrics.DENSITY_DEFAULT
    private var screenWidth: Int = 1080
    private var screenHeight: Int = 1920

    private val projectionCallback =
        object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                release()
            }
        }

    init {
        // Android 14+ (API 34+) strictly requires registering a MediaProjection.Callback
        // before createVirtualDisplay() is called, otherwise throwing IllegalStateException.
        mediaProjection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
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

    @SuppressLint("WrongConstant")
    private fun setupVirtualDisplay() {
        imageReader?.close()
        imageReader =
            ImageReader.newInstance(
                screenWidth,
                screenHeight,
                PixelFormat.RGBA_8888,
                2,
            )

        virtualDisplay?.release()
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
        Timber.d("VirtualDisplay initialized: %dx%d @ %ddpi", screenWidth, screenHeight, screenDensity)
    }

    suspend fun captureFrame(): Bitmap? =
        withContext(Dispatchers.Default) {
            val reader = imageReader ?: return@withContext null
            var image: Image? = null
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

                val bitmap =
                    Bitmap.createBitmap(
                        screenWidth + rowPadding / pixelStride,
                        screenHeight,
                        Bitmap.Config.ARGB_8888,
                    )
                bitmap.copyPixelsFromBuffer(buffer)

                // Crop out padding if present
                if (rowPadding == 0) {
                    bitmap
                } else {
                    val cleanBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
                    bitmap.recycle()
                    cleanBitmap
                }
            } catch (e: Exception) {
                Timber.e(e, "Error capturing screen frame")
                null
            } finally {
                image?.close()
            }
        }

    fun onConfigurationChanged() {
        updateScreenDimensions()
        setupVirtualDisplay()
    }

    fun release() {
        try {
            mediaProjection.unregisterCallback(projectionCallback)
        } catch (_: Exception) {
            // Callback might already be unregistered or projection stopped
        }
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            mediaProjection.stop()
            Timber.d("ScreenCaptureManager released safely")
        } catch (e: Exception) {
            Timber.e(e, "Error releasing ScreenCaptureManager")
        }
    }
}
