package com.cream.translator.core.model

/**
 * Cấu hình cho bộ lọc văn bản trước khi gửi sang dịch.
 *
 * @property isFilteringEnabled Bật/tắt toàn bộ tính năng lọc.
 * @property isUiControlsEnabled Bật/tắt lọc các nút bấm và nhãn UI phổ biến (OK, Cancel, Next, Back...).
 * @property isSymbolsAndNumbersEnabled Bật/tắt lọc chuỗi số thuần túy, ký hiệu, đơn vị đo lường, chỉ số game.
 * @property customBlockedWords Tập hợp các từ khóa do người dùng tùy biến không muốn dịch.
 */
data class FilterConfig(
    val isFilteringEnabled: Boolean = true,
    val isUiControlsEnabled: Boolean = true,
    val isSymbolsAndNumbersEnabled: Boolean = true,
    val customBlockedWords: Set<String> = emptySet(),
) {
    companion object {
        val DEFAULT = FilterConfig()
    }
}
