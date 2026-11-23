package com.cream.translator.core.engine.filter

import com.cream.translator.core.model.FilterConfig
import com.cream.translator.core.model.LanguagePair
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultTextFilterTest {
    private val defaultFilter = DefaultTextFilter()

    @Test
    fun shouldFilter_emptyOrBlankText_returnsTrue() {
        assertTrue(defaultFilter.shouldFilter(""))
        assertTrue(defaultFilter.shouldFilter("   "))
        assertTrue(defaultFilter.shouldFilter("\n\t"))
    }

    @Test
    fun shouldFilter_singlePunctuationOrSymbol_returnsTrue() {
        assertTrue(defaultFilter.shouldFilter("-"))
        assertTrue(defaultFilter.shouldFilter("+"))
        assertTrue(defaultFilter.shouldFilter("/"))
        assertTrue(defaultFilter.shouldFilter(">"))
        assertTrue(defaultFilter.shouldFilter("#"))
    }

    @Test
    fun shouldFilter_commonUiControls_returnsTrue() {
        assertTrue(defaultFilter.shouldFilter("OK"))
        assertTrue(defaultFilter.shouldFilter("ok"))
        assertTrue(defaultFilter.shouldFilter("Cancel"))
        assertTrue(defaultFilter.shouldFilter("CANCEL"))
        assertTrue(defaultFilter.shouldFilter("[Next]"))
        assertTrue(defaultFilter.shouldFilter("(Back)"))
        assertTrue(defaultFilter.shouldFilter("Settings!"))
        assertTrue(defaultFilter.shouldFilter("  Home  "))
        assertTrue(defaultFilter.shouldFilter("Exit"))
        assertTrue(defaultFilter.shouldFilter("Play"))
        assertTrue(defaultFilter.shouldFilter("Pause"))
        assertTrue(defaultFilter.shouldFilter("Skip"))
        assertTrue(defaultFilter.shouldFilter("Save"))
        assertTrue(defaultFilter.shouldFilter("Loading"))
    }

    @Test
    fun shouldFilter_sentencesContainingUiWords_returnsFalse() {
        assertFalse(defaultFilter.shouldFilter("OK let's proceed"))
        assertFalse(defaultFilter.shouldFilter("Please cancel my subscription"))
        assertFalse(defaultFilter.shouldFilter("Back to the future"))
        assertFalse(defaultFilter.shouldFilter("Settings for graphic resolution"))
        assertFalse(defaultFilter.shouldFilter("Are you sure you want to exit?"))
    }

    @Test
    fun shouldFilter_numbersAndCurrencyAndPercentage_returnsTrue() {
        assertTrue(defaultFilter.shouldFilter("123"))
        assertTrue(defaultFilter.shouldFilter("-45.6"))
        assertTrue(defaultFilter.shouldFilter("99%"))
        assertTrue(defaultFilter.shouldFilter("+100%"))
        assertTrue(defaultFilter.shouldFilter("$500"))
        assertTrue(defaultFilter.shouldFilter("50€"))
        assertTrue(defaultFilter.shouldFilter("100k"))
        assertTrue(defaultFilter.shouldFilter("12:00"))
        assertTrue(defaultFilter.shouldFilter("23:59:59"))
        assertTrue(defaultFilter.shouldFilter("2024-11-24"))
        assertTrue(defaultFilter.shouldFilter("#E11D48"))
    }

    @Test
    fun shouldFilter_gameStatsAndUnits_returnsTrue() {
        assertTrue(defaultFilter.shouldFilter("Lv. 99"))
        assertTrue(defaultFilter.shouldFilter("HP 500"))
        assertTrue(defaultFilter.shouldFilter("MP 100"))
        assertTrue(defaultFilter.shouldFilter("EXP +20%"))
        assertTrue(defaultFilter.shouldFilter("60fps"))
        assertTrue(defaultFilter.shouldFilter("16ms"))
        assertTrue(defaultFilter.shouldFilter("1080px"))
        assertTrue(defaultFilter.shouldFilter("100km"))
    }

    @Test
    fun shouldFilter_urlsAndWebDomains_returnsTrue() {
        assertTrue(defaultFilter.shouldFilter("https://example.com"))
        assertTrue(defaultFilter.shouldFilter("www.google.com"))
        assertTrue(defaultFilter.shouldFilter("github.com"))
    }

    @Test
    fun shouldFilter_customBlockedWords_returnsTrueWhenConfigured() {
        val config =
            FilterConfig(
                customBlockedWords = setOf("Gacha", "Buff", "Nerf"),
            )
        val filter = DefaultTextFilter(config)

        assertTrue(filter.shouldFilter("gacha"))
        assertTrue(filter.shouldFilter("[Buff]"))
        assertTrue(filter.shouldFilter("NERF!"))
        assertFalse(filter.shouldFilter("NormalWord"))
    }

    @Test
    fun shouldFilter_whenFilteringDisabled_returnsFalseForEverythingExceptBlank() {
        val config = FilterConfig(isFilteringEnabled = false)
        val filter = DefaultTextFilter(config)

        assertFalse(filter.shouldFilter("OK"))
        assertFalse(filter.shouldFilter("123"))
        assertFalse(filter.shouldFilter("Cancel"))
    }

    @Test
    fun shouldFilter_whenUiControlsDisabled_doesNotFilterUiControls() {
        val config = FilterConfig(isUiControlsEnabled = false)
        val filter = DefaultTextFilter(config)

        assertFalse(filter.shouldFilter("OK"))
        assertFalse(filter.shouldFilter("Cancel"))
        assertTrue(filter.shouldFilter("123"))
    }

    @Test
    fun shouldFilter_whenSymbolsAndNumbersDisabled_doesNotFilterNumbers() {
        val config = FilterConfig(isSymbolsAndNumbersEnabled = false)
        val filter = DefaultTextFilter(config)

        assertTrue(filter.shouldFilter("OK"))
        assertFalse(filter.shouldFilter("123"))
        assertFalse(filter.shouldFilter("99%"))
    }

    @Test
    fun shouldFilter_vietnameseTextWhenTargetIsVietnamese_returnsTrue() {
        val enViPair = LanguagePair("en", "vi")

        assertTrue(defaultFilter.shouldFilter("Cài đặt bộ lọc từ", enViPair))
        assertTrue(defaultFilter.shouldFilter("Kiểm tra bản cập nhật", enViPair))
        assertTrue(defaultFilter.shouldFilter("Trình dịch màn hình game on-device", enViPair))
        assertTrue(defaultFilter.shouldFilter("Đã cấp", enViPair))
        assertTrue(defaultFilter.shouldFilter("Hệ thống", enViPair))
    }

    @Test
    fun shouldFilter_englishTextWhenPairIsEnToVi_returnsFalse() {
        val enViPair = LanguagePair("en", "vi")

        assertFalse(defaultFilter.shouldFilter("Hello World", enViPair))
        assertFalse(defaultFilter.shouldFilter("Stop Translator Service", enViPair))
        assertFalse(defaultFilter.shouldFilter("Artificial Intelligence is fascinating", enViPair))
        assertFalse(defaultFilter.shouldFilter("Start new adventure", enViPair))
    }

    @Test
    fun shouldFilter_incompatibleScriptsForEnSource_returnsTrue() {
        val enViPair = LanguagePair("en", "vi")

        // Asian and Cyrillic scripts should be filtered when source is English
        assertTrue(defaultFilter.shouldFilter("こんにちは", enViPair))
        assertTrue(defaultFilter.shouldFilter("한국어", enViPair))
        assertTrue(defaultFilter.shouldFilter("你好世界", enViPair))
        assertTrue(defaultFilter.shouldFilter("Привет мир", enViPair))
    }

    @Test
    fun shouldFilter_japaneseTextWhenPairIsJaToVi_retainsJapaneseAndFiltersVietnamese() {
        val jaViPair = LanguagePair("ja", "vi")

        assertFalse(defaultFilter.shouldFilter("こんにちは", jaViPair))
        assertFalse(defaultFilter.shouldFilter("冒険が始まる", jaViPair))
        assertTrue(defaultFilter.shouldFilter("Cài đặt bộ lọc từ", jaViPair))
    }
}
