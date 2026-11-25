package com.cream.translator.service

import android.content.Context
import android.content.Intent
import com.cream.translator.core.model.LanguagePair
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

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
        val appContext = context.applicationContext ?: context
        try {
            val captureIntent =
                Intent(appContext, ScreenCaptureService::class.java).apply {
                    putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                    putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, resultData)
                }
            appContext.startForegroundService(captureIntent)

            val overlayIntent =
                Intent(appContext, OverlayService::class.java).apply {
                    putExtra(OverlayService.EXTRA_SOURCE_LANG, languagePair.sourceLanguage)
                    putExtra(OverlayService.EXTRA_TARGET_LANG, languagePair.targetLanguage)
                }
            appContext.startService(overlayIntent)

            _isServiceRunning.value = true
            Timber.i("TranslatorController: Bắt đầu toàn bộ dịch vụ phiên dịch màn hình")
        } catch (e: SecurityException) {
            Timber.e(e, "TranslatorController: Không đủ quyền khởi chạy dịch vụ")
            stop(appContext)
        } catch (e: IllegalStateException) {
            Timber.e(e, "TranslatorController: Không thể khởi chạy dịch vụ ở trạng thái hiện tại")
            stop(appContext)
        }
    }

    private val isStopping = AtomicBoolean(false)

    /**
     * Dừng toàn bộ các dịch vụ dịch màn hình, gỡ bỏ các lớp phủ và thu hồi tài nguyên.
     */
    fun stop(context: Context) {
        if (!isStopping.compareAndSet(false, true)) return
        _isServiceRunning.value = false
        val appContext = context.applicationContext ?: context
        try {
            // Dừng trực tiếp qua stopService để giải phóng vòng đời sạch sẽ, không khởi chạy lại service
            appContext.stopService(Intent(appContext, OverlayService::class.java))
            appContext.stopService(Intent(appContext, ScreenCaptureService::class.java))

            Timber.i("TranslatorController: Đã gửi lệnh dừng toàn bộ dịch vụ phiên dịch")
        } catch (e: SecurityException) {
            Timber.e(e, "TranslatorController: Không đủ quyền dừng dịch vụ")
        } catch (e: IllegalStateException) {
            Timber.e(e, "TranslatorController: Lỗi trạng thái khi dừng dịch vụ")
        } finally {
            isStopping.set(false)
        }
    }
}
