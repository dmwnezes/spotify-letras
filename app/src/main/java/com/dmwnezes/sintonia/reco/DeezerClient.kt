package com.dmwnezes.sintonia.reco

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

data class DzArtist(val id: Long, val name: String, val fans: Int, val picture: String?)

data class DzTrack(
    val id: Long,
    val title: String,
    val artistId: Long,
    val artist: String,
    val album: String,
    val cover: String?,
    val previewUrl: String?,
    val durationSec: Int,
    val rank: Int,
)

/**
 * API pública do Deezer (sem chave): artistas parecidos, rádio de artista, músicas principais e prévias de 30 s.
 * Usada só para descobrir músicas; quem toca é o Spotify.
 */
class DeezerClient(private val http: OkHttpClient) {

    // O Deezer aceita ~50 consultas a cada 5 s; segura em 6 ao mesmo tempo.
    private val gate = Semaphore(6)
    private val artistCache = HashMap<String, DzArtist?>()

    suspend fun findArtist(name: String): DzArtist? {
        val key = norm(name)
        synchronized(artistCache) { if (artistCache.containsKey(key)) return artistCache[key] }
        val url = "https://api.deezer.com/search/artist".toHttpUrl().newBuilder()
            .addQueryParameter("q", name).addQueryParameter("limit", "10").build().toString()
        val list = get(url)?.let { artists(it) }.orEmpty()
        // Mesmo nome (ignorando acentos/maiúsculas) com mais fãs; senão o primeiro com muitos fãs.
        val best = list.filter { norm(it.name) == key }.maxByOrNull { it.fans }
            ?: list.firstOrNull()?.takeIf { it.fans > 5000 }
        synchronized(artistCache) { artistCache[key] = best }
        return best
    }

    suspend fun related(artistId: Long, limit: Int = 20): List<DzArtist> =
        get("https://api.deezer.com/artist/$artistId/related?limit=$limit")?.let { artists(it) }.orEmpty()

    suspend fun radio(artistId: Long, limit: Int = 25): List<DzTrack> =
        get("https://api.deezer.com/artist/$artistId/radio?limit=$limit")?.let { tracks(it) }.orEmpty()

    suspend fun top(artistId: Long, limit: Int = 5): List<DzTrack> =
        get("https://api.deezer.com/artist/$artistId/top?limit=$limit")?.let { tracks(it) }.orEmpty()

    /** Faixa completa (com ISRC e prévia nova; os links de prévia expiram). */
    suspend fun track(id: Long): Pair<DzTrack, String?>? {
        val o = get("https://api.deezer.com/track/$id") ?: return null
        if (o.has("error")) return null
        return parseTrack(o) to o.optString("isrc").ifBlank { null }
    }

    private suspend fun get(url: String): JSONObject? = gate.withPermit {
        withContext(Dispatchers.IO) {
            repeat(3) { attempt ->
                val req = Request.Builder().url(url).header("User-Agent", "Sintonia-app").build()
                val body = runCatching { http.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null } }.getOrNull()
                    ?: return@withContext null
                val o = runCatching { JSONObject(body) }.getOrNull() ?: return@withContext null
                // Código 4 = limite de consultas: espera um pouco e tenta de novo.
                if (o.optJSONObject("error")?.optInt("code") == 4) { delay(1200L * (attempt + 1)); return@repeat }
                return@withContext o
            }
            null
        }
    }

    private fun artists(o: JSONObject): List<DzArtist> = o.optJSONArray("data").objs().map {
        DzArtist(it.optLong("id"), it.optString("name"), it.optInt("nb_fan"), it.optString("picture_medium").ifBlank { null })
    }

    private fun tracks(o: JSONObject): List<DzTrack> = o.optJSONArray("data").objs().filter { it.optString("type", "track") == "track" }.map(::parseTrack)

    private fun parseTrack(t: JSONObject): DzTrack {
        val ar = t.optJSONObject("artist")
        val al = t.optJSONObject("album")
        return DzTrack(
            id = t.optLong("id"),
            title = t.optString("title_short").ifBlank { t.optString("title") },
            artistId = ar?.optLong("id") ?: 0,
            artist = ar?.optString("name").orEmpty(),
            album = al?.optString("title").orEmpty(),
            cover = al?.optString("cover_xl")?.ifBlank { null } ?: al?.optString("cover_big")?.ifBlank { null },
            previewUrl = t.optString("preview").ifBlank { null },
            durationSec = t.optInt("duration"),
            rank = t.optInt("rank"),
        )
    }

    private fun JSONArray?.objs(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    companion object {
        fun norm(s: String): String = Normalizer.normalize(s.lowercase().trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()
    }
}
