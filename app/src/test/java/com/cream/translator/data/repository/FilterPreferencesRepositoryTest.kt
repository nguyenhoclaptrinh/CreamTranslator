package com.cream.translator.data.repository

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FilterPreferencesRepositoryTest {
    private val context = mockk<Context>(relaxed = true)
    private val prefs = mockk<SharedPreferences>(relaxed = true)
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)

    private val inMemoryStorage = mutableMapOf<String, Any>()

    @Before
    fun setUp() {
        inMemoryStorage.clear()
        every { context.applicationContext } returns context
        every { context.getSharedPreferences("cream_filter_preferences", Context.MODE_PRIVATE) } returns prefs

        every { prefs.edit() } returns editor
        every { editor.putBoolean(any(), any()) } answers {
            inMemoryStorage[firstArg()] = secondArg<Boolean>()
            editor
        }
        every { editor.putStringSet(any(), any()) } answers {
            inMemoryStorage[firstArg()] = secondArg<Set<String>>().toMutableSet()
            editor
        }
        every { editor.remove(any()) } answers {
            inMemoryStorage.remove(firstArg())
            editor
        }
        every { editor.apply() } returns Unit

        every { prefs.getBoolean(any(), any()) } answers {
            (inMemoryStorage[firstArg()] as? Boolean) ?: secondArg<Boolean>()
        }
        every { prefs.getStringSet(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            val stored = inMemoryStorage[firstArg()] as? Set<String>
            val fallback = secondArg<Set<String>?>() ?: emptySet()
            (stored ?: fallback).toMutableSet()
        }
    }

    @Test
    fun initialLoad_returnsDefaultValues() {
        val repo = FilterPreferencesRepository(context)
        val config = repo.getConfig()

        assertTrue(config.isFilteringEnabled)
        assertTrue(config.isUiControlsEnabled)
        assertTrue(config.isSymbolsAndNumbersEnabled)
        assertTrue(config.customBlockedWords.isEmpty())
    }

    @Test
    fun setFilteringEnabled_updatesPreferencesAndState() {
        val repo = FilterPreferencesRepository(context)
        repo.setFilteringEnabled(false)

        assertFalse(repo.getConfig().isFilteringEnabled)
        verify { editor.putBoolean("is_filtering_enabled", false) }
    }

    @Test
    fun addCustomWord_addsNormalizedWordAndPreventsDuplicate() {
        val repo = FilterPreferencesRepository(context)
        val addedFirst = repo.addCustomWord("Gacha")
        assertTrue(addedFirst)
        assertTrue(repo.getConfig().customBlockedWords.contains("Gacha"))

        // Thêm từ trùng lặp (không phân biệt chữ hoa thường)
        val addedDuplicate = repo.addCustomWord("gacha")
        assertFalse(addedDuplicate)

        // Thêm từ rỗng
        val addedBlank = repo.addCustomWord("   ")
        assertFalse(addedBlank)
    }

    @Test
    fun removeCustomWord_removesCorrectly() {
        val repo = FilterPreferencesRepository(context)
        repo.addCustomWord("Gacha")
        repo.addCustomWord("Buff")
        assertEquals(2, repo.getConfig().customBlockedWords.size)

        repo.removeCustomWord("gacha")
        assertEquals(1, repo.getConfig().customBlockedWords.size)
        assertTrue(repo.getConfig().customBlockedWords.contains("Buff"))
    }

    @Test
    fun resetToDefaults_clearsCustomWordsAndResetsFlags() {
        val repo = FilterPreferencesRepository(context)
        repo.setFilteringEnabled(false)
        repo.addCustomWord("CustomWord")

        repo.resetToDefaults()
        val config = repo.getConfig()

        assertTrue(config.isFilteringEnabled)
        assertTrue(config.isUiControlsEnabled)
        assertTrue(config.isSymbolsAndNumbersEnabled)
        assertTrue(config.customBlockedWords.isEmpty())
    }
}
