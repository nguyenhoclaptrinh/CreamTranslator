package com.cream.translator.service

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Điều phối trung tâm cho toàn bộ vòng đời của các dịch vụ phiên dịch CreamTranslator.
 * Đảm bảo ScreenCaptureService và OverlayService luôn được đồng bộ trạng thái khởi chạy và dừng.
 */
object TranslatorController {
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    fun updateServiceState(isRunning: Boolean) {
        _isServiceRunning.value = isRunning
    }

    /**
     * Khởi chạy cả ScreenCaptureService và OverlayService.
     */
    fun start(
        context: Context,
        resultCode: Int,
        resultData: Intent,
    ) {
        try {
            val captureIntent =
                Intent(context, ScreenCaptureService::class.java).apply {
                    putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                    putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, resultData)
                }
            context.startForegroundService(captureIntent)

            val overlayIntent = Intent(context, OverlayService::class.java)
            context.startService(overlayIntent)

            _isServiceRunning.value = true
            Timber.i("TranslatorController: Bắt đầu toàn bộ dịch vụ phiên dịch màn hình")
        } catch (e: Throwable) {
            Timber.e(e, "TranslatorController: Lỗi khi khởi chạy dịch vụ")
            _isServiceRunning.value = false
        }
    }

    /**
     * Dừng toàn bộ các dịch vụ dịch màn hình, gỡ bỏ các lớp phủ và thu hồi tài nguyên.
     */
    fun stop(context: Context) {
        try {
            _isServiceRunning.value = false

            val stopCaptureIntent =
                Intent(context, ScreenCaptureService::class.java).apply {
                    action = ScreenCaptureService.ACTION_STOP
                }
            context.startService(stopCaptureIntent)
            context.stopService(Intent(context, ScreenCaptureService::class.java))

            val stopOverlayIntent =
                Intent(context, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_STOP
                }
            context.startService(stopOverlayIntent)
            context.stopService(Intent(context, OverlayService::class.java))

            Timber.i("TranslatorController: Đã gửi lệnh dừng toàn bộ dịch vụ phiên dịch")
        } catch (e: Throwable) {
            Timber.e(e, "TranslatorController: Lỗi khi dừng dịch vụ")
        }
    }
}
