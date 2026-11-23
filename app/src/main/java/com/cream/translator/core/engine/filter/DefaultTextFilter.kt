package com.cream.translator.core.engine.filter

import com.cream.translator.core.model.FilterConfig
import com.cream.translator.core.model.LanguagePair
import java.util.Locale

/**
 * Bộ lọc văn bản mặc định hỗ trợ lọc theo danh mục UI, Ký hiệu & Số, và danh sách tùy biến.
 */
class DefaultTextFilter(
    private val configProvider: () -> FilterConfig = { FilterConfig.DEFAULT },
) : TextFilter {
    constructor(staticConfig: FilterConfig) : this({ staticConfig })

    override fun shouldFilter(text: String): Boolean = shouldFilter(text, null)

    override fun shouldFilter(
        text: String,
        languagePair: LanguagePair?,
    ): Boolean {
        val config = configProvider()
        if (!config.isFilteringEnabled) {
            return false
        }

        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return true
        }

        // Bỏ qua các ký tự đơn lẻ chỉ là dấu câu hoặc ký hiệu toán học
        if (trimmed.length == 1 && !trimmed[0].isLetterOrDigit()) {
            return true
        }

        // Kiểm tra tính không tương thích với ngôn ngữ hoặc đã là ngôn ngữ đích
        if (languagePair != null && isLanguageIncompatibleOrAlreadyTarget(trimmed, languagePair)) {
            return true
        }

        val normalized = normalizeForComparison(trimmed)
        if (normalized.isEmpty()) {
            return true
        }

        // 1. Kiểm tra danh sách từ tùy biến của người dùng
        if (config.customBlockedWords.isNotEmpty()) {
            val customSet = config.customBlockedWords.map { it.trim().lowercase(Locale.ROOT) }.toSet()
            if (customSet.contains(normalized)) {
                return true
            }
        }

        // 2. Kiểm tra nhóm điều hướng và nhãn UI
        if (config.isUiControlsEnabled && isUiControl(normalized)) {
            return true
        }

        // 3. Kiểm tra nhóm số thuần túy, ký hiệu, tỷ lệ, tiền tệ, chỉ số game
        if (config.isSymbolsAndNumbersEnabled && isSymbolOrNumber(trimmed, normalized)) {
            return true
        }

        return false
    }

    private fun isLanguageIncompatibleOrAlreadyTarget(
        text: String,
        languagePair: LanguagePair,
    ): Boolean {
        val target = languagePair.targetLanguage.lowercase(Locale.ROOT)
        val source = languagePair.sourceLanguage.lowercase(Locale.ROOT)

        if (isAlreadyInTargetLanguage(text, target)) {
            return true
        }

        return isIncompatibleWithSourceLanguage(text, source)
    }

    private fun isAlreadyInTargetLanguage(
        text: String,
        target: String,
    ): Boolean {
        return target == "vi" && VIETNAMESE_DIACRITICS_REGEX.containsMatchIn(text)
    }

    private fun isIncompatibleWithSourceLanguage(
        text: String,
        source: String,
    ): Boolean {
        return when (source) {
            "en" -> hasNonEnglishScript(text)
            "ja" -> isMissingJapaneseScript(text)
            "zh" -> !CJK_IDEOGRAPH_REGEX.containsMatchIn(text) && isPureLatinOrVietnamese(text)
            "ko" -> !HANGUL_REGEX.containsMatchIn(text) && isPureLatinOrVietnamese(text)
            else -> false
        }
    }

    private fun hasNonEnglishScript(text: String): Boolean {
        return VIETNAMESE_DIACRITICS_REGEX.containsMatchIn(text) ||
            CJK_IDEOGRAPH_REGEX.containsMatchIn(text) ||
            JAPANESE_KANA_REGEX.containsMatchIn(text) ||
            HANGUL_REGEX.containsMatchIn(text) ||
            CYRILLIC_REGEX.containsMatchIn(text)
    }

    private fun isMissingJapaneseScript(text: String): Boolean {
        val hasJapanese = JAPANESE_KANA_REGEX.containsMatchIn(text) || CJK_IDEOGRAPH_REGEX.containsMatchIn(text)
        return !hasJapanese && isPureLatinOrVietnamese(text)
    }

    private fun isPureLatinOrVietnamese(text: String): Boolean {
        val hasLetter = text.any { it.isLetter() }
        val hasAsianScript =
            CJK_IDEOGRAPH_REGEX.containsMatchIn(text) ||
                JAPANESE_KANA_REGEX.containsMatchIn(text) ||
                HANGUL_REGEX.containsMatchIn(text)
        return hasLetter && !hasAsianScript
    }

    private fun normalizeForComparison(raw: String): String {
        // Loại bỏ các ký tự dấu ngoặc, dấu chấm than, nháy kép bao quanh từ
        var start = 0
        var end = raw.length

        while (start < end && isSurroundingPunctuation(raw[start])) {
            start++
        }
        while (end > start && isSurroundingPunctuation(raw[end - 1])) {
            end--
        }

        if (start >= end) return ""
        return raw.substring(start, end).lowercase(Locale.ROOT).trim()
    }

    private fun isSurroundingPunctuation(c: Char): Boolean = c in "()[]{}<>\"'`!?:;.,-_*~|/\\"

    private fun isUiControl(normalized: String): Boolean = BUILT_IN_UI_CONTROLS.contains(normalized)

    private fun isSymbolOrNumber(
        originalTrimmed: String,
        normalized: String,
    ): Boolean {
        // Kiểm tra xem chuỗi có khớp với các mẫu số, tỷ lệ, tiền tệ, thời gian, thuộc tính game không
        if (REGEX_PURE_NUMBER_OR_DECIMAL.matches(originalTrimmed)) return true
        if (REGEX_PERCENTAGE.matches(originalTrimmed)) return true
        if (REGEX_CURRENCY.matches(originalTrimmed)) return true
        if (REGEX_TIME.matches(originalTrimmed)) return true
        if (REGEX_DATE.matches(originalTrimmed)) return true
        if (REGEX_HEX_COLOR.matches(originalTrimmed)) return true
        if (REGEX_GAME_STATS.matches(normalized)) return true
        if (REGEX_NUMBER_WITH_UNIT.matches(normalized)) return true
        if (REGEX_URL_OR_DOMAIN.matches(originalTrimmed)) return true

        return false
    }

    companion object {
        /**
         * Danh sách các từ điều khiển và nút bấm UI phổ biến (tiếng Anh).
         */
        val BUILT_IN_UI_CONTROLS: Set<String> =
            setOf(
                "ok",
                "cancel",
                "next",
                "back",
                "prev",
                "previous",
                "home",
                "menu",
                "settings",
                "setting",
                "close",
                "exit",
                "quit",
                "play",
                "pause",
                "resume",
                "stop",
                "save",
                "saved",
                "load",
                "loading",
                "skip",
                "start",
                "restart",
                "done",
                "yes",
                "no",
                "on",
                "off",
                "open",
                "clear",
                "delete",
                "remove",
                "edit",
                "copy",
                "cut",
                "paste",
                "select",
                "share",
                "send",
                "search",
                "login",
                "log in",
                "logout",
                "log out",
                "sign in",
                "sign out",
                "signup",
                "sign up",
                "profile",
                "account",
                "info",
                "help",
                "about",
                "more",
                "view",
                "details",
                "refresh",
                "retry",
                "apply",
                "confirm",
                "submit",
                "agree",
                "disagree",
                "accept",
                "decline",
                "reject",
                "allow",
                "deny",
                "auto",
                "manual",
                "default",
                "continue",
                "finish",
                "complete",
                "ready",
                "lock",
                "unlock",
                "mute",
                "unmute",
                "favorite",
                "like",
                "dislike",
                "download",
                "upload",
                "status",
                "mode",
                "option",
                "options",
                "filter",
                "sort",
                "item",
                "items",
                "shop",
                "store",
                "up",
                "down",
                "left",
                "right",
                "top",
                "bottom",
                "all",
                "none",
            )

        // Các mẫu Regex nhận diện định dạng số và ký hiệu
        private val REGEX_PURE_NUMBER_OR_DECIMAL =
            Regex("""^[+-]?\d+([.,]\d+)?$""")

        private val REGEX_PERCENTAGE =
            Regex("""^[+-]?\d+([.,]\d+)?\s*%$""")

        private val REGEX_CURRENCY =
            Regex(
                """^([$€£¥₫]\s*\d+([.,]\d+)?|\d+([.,]\d+)?\s*([$€£¥₫]|usd|eur|vnd|k|m|b))$""",
                RegexOption.IGNORE_CASE,
            )

        private val REGEX_TIME =
            Regex("""^\d{1,2}:\d{2}(:\d{2})?\s*(am|pm)?$""", RegexOption.IGNORE_CASE)

        private val REGEX_DATE =
            Regex("""^(\d{1,4}[-/.]\d{1,2}[-/.]\d{1,4})$""")

        private val REGEX_HEX_COLOR =
            Regex("""^#[0-9a-fA-F]{3,8}$""")

        private val REGEX_GAME_STATS =
            Regex("""^(lv|lvl|hp|mp|exp|ap|atk|def|spd|crit|cp)\.?\s*[+-]?\d+([.,]\d+)?(%|\s*pts?)?$""")

        private val REGEX_NUMBER_WITH_UNIT =
            Regex("""^[+-]?\d+([.,]\d+)?\s*(px|dp|sp|pt|fps|ms|s|m|km|cm|mm|kg|g|hz|khz|mhz|ghz|kb|mb|gb|tb)$""")

        private val REGEX_URL_OR_DOMAIN =
            Regex(
                """^(https?://|www\.)\S+$|^[a-zA-Z0-9.-]+\.(com|org|net|io|edu|gov|vn|app|dev)$""",
                RegexOption.IGNORE_CASE,
            )

        private const val VI_LOWER =
            "àáảãạăằắẳẵặâầấẩẫậđèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵ"
        private const val VI_UPPER =
            "ÀÁẢÃẠĂẰẮẲẴẶÂẦẤẨẪẬĐÈÉẺẼẸÊỀẾỂỄỆÌÍỈĨỊÒÓỎÕỌÔỒỐỔỖỘƠỜỚỞỠỢÙÚỦŨỤƯỪỨỬỮỰỲÝỶỸỴ"

        private val VIETNAMESE_DIACRITICS_REGEX =
            Regex("[$VI_LOWER$VI_UPPER]")

        private val CJK_IDEOGRAPH_REGEX =
            Regex("""[\u4e00-\u9fff]""")

        private val JAPANESE_KANA_REGEX =
            Regex("""[\u3040-\u309f\u30a0-\u30ff]""")

        private val HANGUL_REGEX =
            Regex("""[\uac00-\ud7af\u1100-\u11ff]""")

        private val CYRILLIC_REGEX =
            Regex("""[\u0400-\u04ff]""")
    }
}
