package com.cream.translator.core.model

/**
 * Độ lệch tọa độ điểm ảnh (pixel offset) thuần Kotlin, không phụ thuộc Android framework.
 * Dùng để ánh xạ tọa độ bounding box từ không gian Bitmap đã crop
 * về không gian màn hình tuyệt đối khi chạy pipeline dịch vùng chọn.
 */
data class PixelOffset(val x: Int = 0, val y: Int = 0) {
    val isZero: Boolean get() = x == 0 && y == 0

    companion object {
        val NONE = PixelOffset(0, 0)
    }
}
