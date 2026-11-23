package com.cream.translator.core.engine

import com.cream.translator.core.model.LanguagePair
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitTranslationEngine : TranslationEngine {
    private val translators = ConcurrentHashMap<String, Translator>()

    private fun getOrCreateTranslator(languagePair: LanguagePair): Translator {
        val key = "${languagePair.sourceLanguage}_${languagePair.targetLanguage}"
        return translators.getOrPut(key) {
            val source =
                TranslateLanguage.fromLanguageTag(languagePair.sourceLanguage)
                    ?: TranslateLanguage.ENGLISH
            val target =
                TranslateLanguage.fromLanguageTag(languagePair.targetLanguage)
                    ?: TranslateLanguage.VIETNAMESE

            val options =
                TranslatorOptions.Builder()
                    .setSourceLanguage(source)
                    .setTargetLanguage(target)
                    .build()

            Translation.getClient(options)
        }
    }

    override suspend fun ensureModelDownloaded(languagePair: LanguagePair): Boolean =
        suspendCancellableCoroutine { continuation ->
            val translator = getOrCreateTranslator(languagePair)
            val conditions = DownloadConditions.Builder().build()

            translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener {
                    Timber.d("Offline model ready for %s", languagePair.displayName)
                    continuation.resume(true)
                }
                .addOnFailureListener { exception ->
                    Timber.e(exception, "Failed to download model for %s", languagePair.displayName)
                    continuation.resume(false)
                }
        }

    override suspend fun translate(
        text: String,
        languagePair: LanguagePair,
    ): String =
        suspendCancellableCoroutine { continuation ->
            if (text.isBlank()) {
                continuation.resume("")
                return@suspendCancellableCoroutine
            }

            val translator = getOrCreateTranslator(languagePair)
            translator.translate(text)
                .addOnSuccessListener { translatedText ->
                    continuation.resume(translatedText)
                }
                .addOnFailureListener { exception ->
                    Timber.e(exception, "Translation failed for text: %s", text)
                    continuation.resumeWithException(exception)
                }
        }

    override suspend fun translateBatch(
        texts: List<String>,
        languagePair: LanguagePair,
    ): List<String> =
        coroutineScope {
            texts.map { text ->
                async {
                    try {
                        translate(text, languagePair)
                    } catch (e: Exception) {
                        Timber.w(e, "Fallback to original text due to translation error")
                        text
                    }
                }
            }.awaitAll()
        }

    override fun close() {
        translators.values.forEach { translator ->
            try {
                translator.close()
            } catch (e: Exception) {
                Timber.e(e, "Error closing translator")
            }
        }
        translators.clear()
    }
}
