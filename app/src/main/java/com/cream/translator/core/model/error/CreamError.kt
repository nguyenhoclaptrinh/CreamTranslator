package com.cream.translator.core.model.error

sealed interface CreamError {
    val cause: Throwable?
    val isRecoverable: Boolean
    val userFriendlyMessage: String

    sealed interface CaptureError : CreamError {
        data class PermissionDenied(
            override val cause: Throwable? = null,
        ) : CaptureError {
            override val isRecoverable: Boolean = true
            override val userFriendlyMessage: String =
                "Quyền chụp màn hình bị từ chối hoặc bị thu hồi"
        }

        data class ProjectionTerminated(
            override val cause: Throwable? = null,
        ) : CaptureError {
            override val isRecoverable: Boolean = true
            override val userFriendlyMessage: String =
                "Phiên chụp màn hình đã kết thúc"
        }

        data class FrameAcquireTimeout(
            override val cause: Throwable? = null,
        ) : CaptureError {
            override val isRecoverable: Boolean = true
            override val userFriendlyMessage: String =
                "Không nhận được khung hình từ màn hình"
        }

        data class VirtualDisplayFailed(
            override val cause: Throwable? = null,
        ) : CaptureError {
            override val isRecoverable: Boolean = false
            override val userFriendlyMessage: String =
                "Không thể khởi tạo VirtualDisplay"
        }
    }

    sealed interface OcrError : CreamError {
        data class ModelUnavailable(
            override val cause: Throwable? = null,
        ) : OcrError {
            override val isRecoverable: Boolean = true
            override val userFriendlyMessage: String =
                "Mô hình nhận dạng chữ chưa sẵn sàng"
        }

        data class RecognitionFailed(
            override val cause: Throwable? = null,
        ) : OcrError {
            override val isRecoverable: Boolean = true
            override val userFriendlyMessage: String =
                "Lỗi trong quá trình nhận dạng chữ trên màn hình"
        }

        data class ZeroRegionsDetected(
            override val cause: Throwable? = null,
        ) : OcrError {
            override val isRecoverable: Boolean = true
            override val userFriendlyMessage: String =
                "Không tìm thấy chữ trên màn hình"
        }
    }

    sealed interface TranslationError : CreamError {
        data class ModelNotReady(
            val languageCode: String,
            override val cause: Throwable? = null,
        ) : TranslationError {
            override val isRecoverable: Boolean = true
            override val userFriendlyMessage: String =
                "Mô hình dịch ngôn ngữ $languageCode đang được tải về"
        }

        data class ModelDownloadFailed(
            val languageCode: String,
            val isNetworkError: Boolean,
            override val cause: Throwable? = null,
        ) : TranslationError {
            override val isRecoverable: Boolean = true
            override val userFriendlyMessage: String =
                if (isNetworkError) {
                    "Tải mô hình dịch thất bại: Vui lòng kiểm tra kết nối mạng"
                } else {
                    "Không thể tải mô hình dịch cho $languageCode"
                }
        }

        data class InferenceFailed(
            override val cause: Throwable? = null,
        ) : TranslationError {
            override val isRecoverable: Boolean = true
            override val userFriendlyMessage: String =
                "Lỗi khi thực hiện dịch văn bản"
        }
    }

    sealed interface OverlayError : CreamError {
        data class PermissionNotGranted(
            override val cause: Throwable? = null,
        ) : OverlayError {
            override val isRecoverable: Boolean = true
            override val userFriendlyMessage: String =
                "Chưa được cấp quyền hiển thị trên các ứng dụng khác"
        }

        data class WindowAttachmentFailed(
            override val cause: Throwable? = null,
        ) : OverlayError {
            override val isRecoverable: Boolean = false
            override val userFriendlyMessage: String =
                "Không thể gắn lớp phủ dịch đè lên màn hình"
        }
    }

    sealed interface UpdateError : CreamError {
        data class RateLimited(
            val resetTimeSeconds: Long? = null,
            override val cause: Throwable? = null,
        ) : UpdateError {
            override val isRecoverable: Boolean = true
            override val userFriendlyMessage: String =
                "Đã vượt quá giới hạn truy vấn GitHub API. Vui lòng thử lại sau."
        }

        data class NetworkUnavailable(
            override val cause: Throwable? = null,
        ) : UpdateError {
            override val isRecoverable: Boolean = true
            override val userFriendlyMessage: String =
                "Không có kết nối mạng để kiểm tra bản cập nhật"
        }

        data class PayloadCorrupted(
            val expectedBytes: Long,
            val actualBytes: Long,
            override val cause: Throwable? = null,
        ) : UpdateError {
            override val isRecoverable: Boolean = true
            override val userFriendlyMessage: String =
                "Tệp cài đặt bị hỏng hoặc không đầy đủ ($actualBytes/$expectedBytes bytes)"
        }

        data class InstallationFailed(
            val reason: String,
            override val cause: Throwable? = null,
        ) : UpdateError {
            override val isRecoverable: Boolean = false
            override val userFriendlyMessage: String =
                "Không thể khởi động trình cài đặt: $reason"
        }
    }
}
