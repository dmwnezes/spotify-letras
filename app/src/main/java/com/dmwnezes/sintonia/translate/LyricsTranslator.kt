package com.dmwnezes.sintonia.translate

import com.dmwnezes.sintonia.data.Prefs
import com.dmwnezes.sintonia.data.Track
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import java.io.File
import java.util.Locale

/** [notice] avisa quando a tradução com IA falhou e foi usada a básica. */
data class TranslationResult(val lines: List<String?>, val notice: String? = null)

/**
 * Detecta o idioma e traduz a letra para português: com o Gemini, se houver chave, ou no próprio celular (ML Kit).
 * O pacote de cada idioma do ML Kit (~30 MB) é baixado uma única vez, na primeira tradução.
 */
class LyricsTranslator(private val prefs: Prefs, http: OkHttpClient, filesDir: File) {
    private val gemini = GeminiTranslator(http)
    // Trocar o "v1" ao mudar as instruções da IA, para refazer as traduções salvas.
    private val store = TranslationStore(File(filesDir, "traducoes-v1"))

    val hasAiKey: Boolean get() = prefs.geminiKey.isNotBlank()


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

    /** Usa a tradução salva, senão a IA, e o ML Kit como reserva. */
    suspend fun translate(track: Track, code: String, lines: List<String>): TranslationResult {
        store.get(track.id, lines)?.let { return TranslationResult(it) }
        var notice: String? = null
        if (hasAiKey) {
            try {
                val out = gemini.translate(prefs.geminiKey, track, code, lines)
                store.put(track.id, lines, out)
                return TranslationResult(out)
            } catch (e: GeminiFailure) {
                notice = "${e.message} Usei a tradução básica."
            }
        }
        return TranslationResult(translateOnDevice(track.id, code, lines), notice)
    }

    /** Traduz cada linha; linhas vazias ficam null. Resultado guardado por música. */
    private suspend fun translateOnDevice(trackId: String, code: String, lines: List<String>): List<String?> {
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
