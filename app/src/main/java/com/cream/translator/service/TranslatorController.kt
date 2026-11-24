package com.cream.translator.service

import android.content.Context
import android.content.Intent
import com.cream.translator.core.model.LanguagePair
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
        languagePair: LanguagePair = LanguagePair("en", "vi"),
    ) {
        try {
            val captureIntent =
                Intent(context, ScreenCaptureService::class.java).apply {
                    putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                    putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, resultData)
                }
            context.startForegroundService(captureIntent)

            val overlayIntent =
                Intent(context, OverlayService::class.java).apply {
                    putExtra(OverlayService.EXTRA_SOURCE_LANG, languagePair.sourceLanguage)
                    putExtra(OverlayService.EXTRA_TARGET_LANG, languagePair.targetLanguage)
                }
            context.startService(overlayIntent)

            _isServiceRunning.value = true
            Timber.i("TranslatorController: Bắt đầu toàn bộ dịch vụ phiên dịch màn hình")
        } catch (e: SecurityException) {
            Timber.e(e, "TranslatorController: Không đủ quyền khởi chạy dịch vụ")
            _isServiceRunning.value = false
        } catch (e: IllegalStateException) {
            Timber.e(e, "TranslatorController: Không thể khởi chạy dịch vụ ở trạng thái hiện tại")
            _isServiceRunning.value = false
        }
    }

    private var isStopping = false

    /**
     * Dừng toàn bộ các dịch vụ dịch màn hình, gỡ bỏ các lớp phủ và thu hồi tài nguyên.
     */
    fun stop(context: Context) {
        if (isStopping) return
        isStopping = true
        _isServiceRunning.value = false
        try {
            // Dừng trực tiếp qua stopService để giải phóng vòng đời sạch sẽ, không khởi chạy lại service
            context.stopService(Intent(context, OverlayService::class.java))
            context.stopService(Intent(context, ScreenCaptureService::class.java))

            Timber.i("TranslatorController: Đã gửi lệnh dừng toàn bộ dịch vụ phiên dịch")
        } catch (e: SecurityException) {
            Timber.e(e, "TranslatorController: Không đủ quyền dừng dịch vụ")
        } catch (e: IllegalStateException) {
            Timber.e(e, "TranslatorController: Lỗi trạng thái khi dừng dịch vụ")
        } finally {
            isStopping = false
        }
    }
}
