package com.cream.translator.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.cream.translator.core.model.FilterConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Repository quản lý cấu hình bộ lọc từ vựng và lưu trữ bền vững qua SharedPreferences.
 */
class FilterPreferencesRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _configFlow = MutableStateFlow(loadConfig())
    val configFlow: StateFlow<FilterConfig> = _configFlow.asStateFlow()

    fun getConfig(): FilterConfig = _configFlow.value

    fun setFilteringEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IS_FILTERING_ENABLED, enabled).apply()
        _configFlow.value = _configFlow.value.copy(isFilteringEnabled = enabled)
    }

    fun setUiControlsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IS_UI_CONTROLS_ENABLED, enabled).apply()
        _configFlow.value = _configFlow.value.copy(isUiControlsEnabled = enabled)
    }

    fun setSymbolsAndNumbersEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IS_SYMBOLS_AND_NUMBERS_ENABLED, enabled).apply()
        _configFlow.value = _configFlow.value.copy(isSymbolsAndNumbersEnabled = enabled)
    }

    fun addCustomWord(word: String): Boolean {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return false

        val currentWords = _configFlow.value.customBlockedWords.toMutableSet()
        val normalized = trimmed.lowercase(Locale.ROOT)
        // Kiểm tra xem đã tồn tại (không phân biệt chữ hoa/thường) chưa
        val alreadyExists = currentWords.any { it.equals(normalized, ignoreCase = true) }
        if (alreadyExists) return false

        currentWords.add(trimmed)
        prefs.edit().putStringSet(KEY_CUSTOM_BLOCKED_WORDS, currentWords).apply()
        _configFlow.value = _configFlow.value.copy(customBlockedWords = currentWords)
        return true
    }

    fun removeCustomWord(word: String) {
        val currentWords = _configFlow.value.customBlockedWords.toMutableSet()
        val changed = currentWords.removeAll { it.equals(word.trim(), ignoreCase = true) }
        if (changed) {
            prefs.edit().putStringSet(KEY_CUSTOM_BLOCKED_WORDS, currentWords).apply()
            _configFlow.value = _configFlow.value.copy(customBlockedWords = currentWords)
        }
    }

    fun resetToDefaults() {
        prefs.edit()
            .putBoolean(KEY_IS_FILTERING_ENABLED, true)
            .putBoolean(KEY_IS_UI_CONTROLS_ENABLED, true)
            .putBoolean(KEY_IS_SYMBOLS_AND_NUMBERS_ENABLED, true)
            .remove(KEY_CUSTOM_BLOCKED_WORDS)
            .apply()

        _configFlow.value = FilterConfig.DEFAULT
    }

    private fun loadConfig(): FilterConfig {
        val isFilteringEnabled = prefs.getBoolean(KEY_IS_FILTERING_ENABLED, true)
        val isUiControlsEnabled = prefs.getBoolean(KEY_IS_UI_CONTROLS_ENABLED, true)
        val isSymbolsAndNumbersEnabled = prefs.getBoolean(KEY_IS_SYMBOLS_AND_NUMBERS_ENABLED, true)
        val customWords = prefs.getStringSet(KEY_CUSTOM_BLOCKED_WORDS, emptySet())?.toSet() ?: emptySet()

        return FilterConfig(
            isFilteringEnabled = isFilteringEnabled,
            isUiControlsEnabled = isUiControlsEnabled,
            isSymbolsAndNumbersEnabled = isSymbolsAndNumbersEnabled,
            customBlockedWords = customWords,
        )
    }

    companion object {
        private const val PREFS_NAME = "cream_filter_preferences"
        private const val KEY_IS_FILTERING_ENABLED = "is_filtering_enabled"
        private const val KEY_IS_UI_CONTROLS_ENABLED = "is_ui_controls_enabled"
        private const val KEY_IS_SYMBOLS_AND_NUMBERS_ENABLED = "is_symbols_and_numbers_enabled"
        private const val KEY_CUSTOM_BLOCKED_WORDS = "custom_blocked_words"

        @Volatile
        private var instance: FilterPreferencesRepository? = null

        fun getInstance(context: Context): FilterPreferencesRepository =
            instance ?: synchronized(this) {
                instance ?: FilterPreferencesRepository(context).also { instance = it }
            }
    }
}
