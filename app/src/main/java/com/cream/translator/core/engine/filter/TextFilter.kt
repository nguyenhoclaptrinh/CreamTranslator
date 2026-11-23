package com.cream.translator.core.engine.filter

import com.cream.translator.core.model.LanguagePair

/**
 * Giao diện cho bộ lọc tiền xử lý văn bản OCR.
 */
interface TextFilter {
    /**
     * Kiểm tra xem khối văn bản có nên bị bỏ qua (không gửi dịch và không vẽ đè lớp phủ) hay không.
     *
     * @param text Nội dung văn bản nhận diện được từ OCR.
     * @return true nếu khối chữ này nên được bỏ qua; false nếu cần được dịch bình thường.
     */
    fun shouldFilter(text: String): Boolean

    /**
     * Kiểm tra xem khối văn bản có nên bị bỏ qua dựa trên ngữ cảnh cặp ngôn ngữ hay không.
     *
     * @param text Nội dung văn bản nhận diện được từ OCR.
     * @param languagePair Cặp ngôn ngữ nguồn và đích hiện tại.
     * @return true nếu khối chữ này nên được bỏ qua; false nếu cần được dịch bình thường.
     */
    fun shouldFilter(
        text: String,
        languagePair: LanguagePair?,
    ): Boolean = shouldFilter(text)
}
