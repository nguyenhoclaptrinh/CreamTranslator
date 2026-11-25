package com.cream.translator.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.cream.translator.R
import com.cream.translator.core.capture.ScreenCaptureManager
import com.cream.translator.ui.MainActivity
import timber.log.Timber

class ScreenCaptureService : Service() {
    private val binder = LocalBinder()
    private var mediaProjection: MediaProjection? = null
    private var captureManager: ScreenCaptureManager? = null

    inner class LocalBinder : Binder() {
        fun getService(): ScreenCaptureService = this@ScreenCaptureService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP) {
            Timber.i("ScreenCaptureService nhận lệnh ACTION_STOP")
            TranslatorController.stop(this)
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent?.getParcelableExtra(EXTRA_RESULT_DATA)
            }

        if (resultCode != 0 && resultData != null) {
            initializeCaptureSession(resultCode, resultData)
        } else if (captureManager == null) {
            Timber.w("ScreenCaptureService nhận lệnh khởi chạy không có MediaProjection token hợp lệ")
            TranslatorController.stop(this)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun initializeCaptureSession(
        resultCode: Int,
        resultData: Intent,
    ) {
        releaseCaptureResources()
        try {
            startForegroundWithNotification()
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = projectionManager.getMediaProjection(resultCode, resultData)
            if (projection == null) {
                Timber.e("getMediaProjection trả về null")
                abortCaptureSession()
                return
            }
            mediaProjection = projection
            val manager =
                ScreenCaptureManager(this, projection).apply {
                    onStopCallback = {
                        Timber.i("MediaProjection đã bị thu hồi/dừng từ hệ thống")
                        TranslatorController.stop(this@ScreenCaptureService)
                    }
                }
            if (!manager.isProjectionActive) {
                Timber.e("Khởi tạo ScreenCaptureManager thất bại")
                manager.release()
                abortCaptureSession()
                return
            }
            captureManager = manager
            TranslatorController.updateServiceState(true)
            Timber.d("MediaProjection initialized in ScreenCaptureService")
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException khi khởi tạo ScreenCaptureService")
            abortCaptureSession()
        } catch (e: IllegalStateException) {
            Timber.e(e, "IllegalStateException khi khởi tạo ScreenCaptureService")
            abortCaptureSession()
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "IllegalArgumentException khi khởi tạo ScreenCaptureService")
            abortCaptureSession()
        }
    }

    private fun abortCaptureSession() {
        releaseCaptureResources()
        TranslatorController.stop(this)
        stopSelf()
    }

    private fun releaseCaptureResources() {
        captureManager?.onStopCallback = null
        captureManager?.release()
        captureManager = null
        mediaProjection?.let { projection ->
            try {
                projection.stop()
            } catch (e: IllegalStateException) {
                Timber.w(e, "IllegalStateException khi dừng MediaProjection")
            } catch (e: SecurityException) {
                Timber.w(e, "SecurityException khi dừng MediaProjection")
            }
        }
        mediaProjection = null
    }

    private fun startForegroundWithNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE,
            )

        val stopIntent =
            Intent(this, ScreenCaptureService::class.java).apply {
                action = ACTION_STOP
            }
        val stopPendingIntent =
            PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification: Notification =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(R.drawable.ic_notification_capture)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Dừng dịch",
                    stopPendingIntent,
                )
                .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = getString(R.string.notification_channel_desc)
                }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    suspend fun captureCurrentScreen(): Bitmap? = captureManager?.captureFrame()

    /**
     * Chụp toàn màn hình rồi crop đúng vùng [region].
     * Đảm bảo Bitmap toàn màn hình được recycle() ngay sau khi crop xong.
     * Trả về null nếu chụp thất bại hoặc vùng crop không hợp lệ.
     */
    suspend fun captureRegion(region: Rect): Bitmap? {
        val full = captureCurrentScreen() ?: return null
        if (full.width <= 0 || full.height <= 0) {
            full.recycle()
            return null
        }
        return try {
            val safeLeft = region.left.coerceIn(0, full.width - 1)
            val safeTop = region.top.coerceIn(0, full.height - 1)
            val safeRight = region.right.coerceIn(safeLeft + 1, full.width)
            val safeBottom = region.bottom.coerceIn(safeTop + 1, full.height)
            val w = safeRight - safeLeft
            val h = safeBottom - safeTop
            if (w <= 0 || h <= 0) {
                Timber.w("captureRegion: vùng crop kích thước không hợp lệ w=%d h=%d", w, h)
                full.recycle()
                return null
            }
            val cropped = Bitmap.createBitmap(full, safeLeft, safeTop, w, h)
            // createBitmap đôi khi trả về cùng đối tượng nếu vùng = toàn ảnh —
            // chỉ recycle full nếu chắc chắn là đối tượng khác biệt.
            if (cropped !== full) full.recycle()
            cropped
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "captureRegion: vùng crop không hợp lệ: %s", region)
            full.recycle()
            null
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        captureManager?.onConfigurationChanged()
        Timber.d("ScreenCaptureService configuration changed")
    }

    override fun onDestroy() {
        instance = null
        releaseCaptureResources()
        TranslatorController.updateServiceState(false)
        if (OverlayService.isRunning) {
            try {
                stopService(Intent(this, OverlayService::class.java))
            } catch (e: SecurityException) {
                Timber.w(e, "SecurityException khi dừng OverlayService từ ScreenCaptureService.onDestroy")
            } catch (e: IllegalStateException) {
                Timber.w(e, "IllegalStateException khi dừng OverlayService từ ScreenCaptureService.onDestroy")
            }
        }
        super.onDestroy()
        Timber.d("ScreenCaptureService destroyed")
    }

    companion object {
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val ACTION_STOP = "com.cream.translator.action.STOP_CAPTURE"
        private const val CHANNEL_ID = "cream_screen_translator_channel"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        var instance: ScreenCaptureService? = null
            private set
    }
}
