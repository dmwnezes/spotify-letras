package com.dmwnezes.sintonia.translate

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import java.util.Locale

/**
 * Detecta o idioma e traduz a letra para português, tudo no celular (ML Kit, gratuito).
 * O pacote de cada idioma (~30 MB) é baixado uma única vez, na primeira tradução.
 */
class LyricsTranslator {

    private val identifier by lazy {
        LanguageIdentification.getClient(LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.45f).build())
    }
    private val cache = HashMap<String, List<String?>>()

    /** Código do idioma (ex.: "en") ou "und" se não reconhecer. */
    suspend fun detect(text: String): String = identifier.identifyLanguage(text).await().substringBefore('-')

    fun supports(code: String): Boolean = code != "und" && TranslateLanguage.fromLanguageTag(code) != null

    fun displayName(code: String): String =
        Locale(code).getDisplayLanguage(Locale("pt", "BR")).ifBlank { code }

    suspend fun isModelReady(code: String): Boolean {
        val tag = TranslateLanguage.fromLanguageTag(code) ?: return false
        val manager = RemoteModelManager.getInstance()
        val model = TranslateRemoteModel.Builder(tag).build()
        val pt = TranslateRemoteModel.Builder(TranslateLanguage.PORTUGUESE).build()
        return manager.isModelDownloaded(model).await() && manager.isModelDownloaded(pt).await()
    }

    /** Traduz cada linha; linhas vazias ficam null. Resultado guardado por música. */
    suspend fun translate(trackId: String, code: String, lines: List<String>): List<String?> {
        cache[trackId]?.let { return it }
        val source = TranslateLanguage.fromLanguageTag(code) ?: error("idioma não suportado")
        val client = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(TranslateLanguage.PORTUGUESE).build()
        )
        try {
            client.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            val unique = HashMap<String, String>()
            val out = lines.map { line ->
                val t = line.trim()
                if (t.isEmpty()) null
                else unique.getOrPut(t) { client.translate(t).await() }.takeIf { !it.equals(t, ignoreCase = true) }
            }
            cache[trackId] = out
            return out
        } finally {
            client.close()
        }
    }
}
