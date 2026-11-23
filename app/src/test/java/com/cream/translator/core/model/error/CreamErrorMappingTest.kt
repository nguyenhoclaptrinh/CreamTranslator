package com.cream.translator.core.model.error

import com.cream.translator.core.model.TranslationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreamErrorMappingTest {
    @Test
    fun captureErrors_haveCorrectProperties() {
        val permError = CreamError.CaptureError.PermissionDenied()
        assertTrue(permError.isRecoverable)
        assertTrue(permError.userFriendlyMessage.contains("Quyền chụp màn hình"))

        val timeoutError = CreamError.CaptureError.FrameAcquireTimeout()
        assertTrue(timeoutError.isRecoverable)

        val virtualDisplayError = CreamError.CaptureError.VirtualDisplayFailed()
        assertFalse(virtualDisplayError.isRecoverable)
    }

    @Test
    fun ocrErrors_haveCorrectProperties() {
        val zeroError = CreamError.OcrError.ZeroRegionsDetected()
        assertTrue(zeroError.isRecoverable)
        assertEquals("Không tìm thấy chữ trên màn hình", zeroError.userFriendlyMessage)

        val modelError = CreamError.OcrError.ModelUnavailable()
        assertTrue(modelError.isRecoverable)
    }

    @Test
    fun translationErrors_haveCorrectProperties() {
        val netDownloadError = CreamError.TranslationError.ModelDownloadFailed("vi", isNetworkError = true)
        assertTrue(netDownloadError.isRecoverable)
        assertTrue(netDownloadError.userFriendlyMessage.contains("kết nối mạng"))

        val nonNetDownloadError = CreamError.TranslationError.ModelDownloadFailed("vi", isNetworkError = false)
        assertTrue(nonNetDownloadError.isRecoverable)
        assertTrue(nonNetDownloadError.userFriendlyMessage.contains("Không thể tải mô hình"))
    }

    @Test
    fun updateErrors_haveCorrectProperties() {
        val rateLimitError = CreamError.UpdateError.RateLimited(12345L)
        assertTrue(rateLimitError.isRecoverable)
        assertTrue(rateLimitError.userFriendlyMessage.contains("GitHub API"))

        val corruptError = CreamError.UpdateError.PayloadCorrupted(1000L, 500L)
        assertTrue(corruptError.isRecoverable)
        assertTrue(corruptError.userFriendlyMessage.contains("500/1000"))

        val installError = CreamError.UpdateError.InstallationFailed("No activity found")
        assertFalse(installError.isRecoverable)
    }

    @Test
    fun translationState_errorEncapsulation() {
        val error = CreamError.OcrError.ZeroRegionsDetected()
        val state = TranslationState.Error(error)

        assertEquals(error, state.error)
        assertEquals(error.userFriendlyMessage, state.message)
        assertEquals(error.isRecoverable, state.isRecoverable)
    }
}
