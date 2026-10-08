package com.dmwnezes.sintonia.lyrics

import com.dmwnezes.sintonia.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** Busca letras no LRCLIB (lrclib.net), base aberta e gratuita de letras sincronizadas. */
class LrcLibClient(private val http: OkHttpClient) {

    private val cache = mutableMapOf<String, Lyrics>()

    suspend fun find(track: Track): Lyrics {
        cache[track.id]?.let { return it }
        val result = lookup(track)
        cache[track.id] = result
        return result
    }

    private suspend fun lookup(track: Track): Lyrics {
        val artist = track.artists.firstOrNull().orEmpty()
        val durationSec = track.durationMs / 1000.0

        // 1) Busca exata (nome + artista + álbum + duração)
        val exact = get(
            "https://lrclib.net/api/get".toHttpUrl().newBuilder()
                .addQueryParameter("track_name", track.name)
                .addQueryParameter("artist_name", artist)
                .addQueryParameter("album_name", track.album)
                .addQueryParameter("duration", (track.durationMs / 1000).toString())
                .build().toString()
        )?.let { toLyrics(JSONObject(it)) }
        if (exact != null && exact !is Lyrics.Plain) return exact

        // 2) Pesquisa mais solta, com o nome limpo ("- Remastered", "(feat. ...)" etc.)
        val clean = cleanTitle(track.name)
        val candidates = mutableListOf<JSONObject>()
        for (name in listOf(track.name, clean).distinct()) {
            val body = get(
                "https://lrclib.net/api/search".toHttpUrl().newBuilder()
                    .addQueryParameter("track_name", name)
                    .addQueryParameter("artist_name", artist)
                    .build().toString()
            ) ?: continue
            val arr = JSONArray(body)
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let(candidates::add)
            if (candidates.any { it.hasSynced() && it.closeTo(durationSec) }) break
        }

        val best = candidates.filter { it.hasSynced() && it.closeTo(durationSec) }
            .minByOrNull { abs(it.optDouble("duration", 0.0) - durationSec) }
            ?: candidates.firstOrNull { it.hasSynced() && it.closeTo(durationSec, 8.0) }
            ?: candidates.firstOrNull { it.optBoolean("instrumental") && it.closeTo(durationSec) }
            ?: candidates.firstOrNull { !it.optString("plainLyrics").isNullOrBlank() && it.closeTo(durationSec, 8.0) }

        val found = best?.let { toLyrics(it) }
        return when {
            found is Lyrics.Synced || found is Lyrics.Instrumental -> found
            exact != null -> exact
            else -> found ?: Lyrics.NotFound
        }
    }

    private fun JSONObject.hasSynced() = !optString("syncedLyrics").isNullOrBlank() && optString("syncedLyrics") != "null"
    private fun JSONObject.closeTo(sec: Double, tol: Double = 4.0) = abs(optDouble("duration", sec) - sec) <= tol

    private fun toLyrics(o: JSONObject): Lyrics? {
        if (o.optBoolean("instrumental")) return Lyrics.Instrumental
        val synced = o.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
        if (synced != null) {
            val lines = LrcParser.parse(synced)
            if (lines.isNotEmpty()) return Lyrics.Synced(lines)
        }
        val plain = o.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
        return plain?.let { Lyrics.Plain(it) }
    }

    private suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url)
            .header("User-Agent", "Sintonia/1.0 (https://github.com/dmwnezes/spotify-letras)")
            .build()
        http.newCall(req).execute().use { resp ->
            when {
                resp.isSuccessful -> resp.body?.string()
                resp.code == 404 -> null
                else -> throw IllegalStateException("LRCLIB respondeu ${resp.code}")
            }
        }
    }

    companion object {
        private val feat = Regex("""\s*[(\[](feat\.?|ft\.?|with|part\.?|participação)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE)
        private val dashSuffix = Regex("""\s+-\s+.*$""")

        fun cleanTitle(name: String): String =
            name.replace(feat, "").replace(dashSuffix, "").trim().ifBlank { name }
    }
}
