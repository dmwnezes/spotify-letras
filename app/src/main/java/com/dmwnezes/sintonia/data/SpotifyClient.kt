package com.dmwnezes.sintonia.data

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

class NotLoggedInException : Exception("Sessão do Spotify expirou. Entre novamente.")
class SpotifyException(val code: Int, message: String) : Exception(message)

class SpotifyClient(private val prefs: Prefs, private val http: OkHttpClient) {

    companion object {
        const val NEEDS_RELOGIN = "Para salvar playlists, entre de novo no Spotify e aceite a nova permissão."
    }

    private val tokenLock = Mutex()

    // ---------- Login ----------

    suspend fun exchangeCode(code: String, verifier: String) = requestToken(
        FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", SpotifyAuth.REDIRECT_URI)
            .add("client_id", prefs.clientId)
            .add("code_verifier", verifier)
            .build()
    )

    private suspend fun refresh() {
        val rt = prefs.refreshToken ?: throw NotLoggedInException()
        requestToken(
            FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", rt)
                .add("client_id", prefs.clientId)
                .build()
        )
    }

    private suspend fun requestToken(form: FormBody) = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("https://accounts.spotify.com/api/token").post(form).build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                if (resp.code == 400) prefs.clearTokens()
                val desc = runCatching { JSONObject(body).optString("error_description") }.getOrNull()
                throw if (resp.code == 400) NotLoggedInException()
                else SpotifyException(resp.code, desc?.ifBlank { null } ?: "Falha no login (${resp.code})")
            }
            val json = JSONObject(body)
            prefs.accessToken = json.getString("access_token")
            json.optString("refresh_token").takeIf { it.isNotBlank() }?.let { prefs.refreshToken = it }
            prefs.expiresAtMs = System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000
            json.optString("scope").takeIf { it.isNotBlank() }?.let { prefs.grantedScopes = it }
        }
    }

    private suspend fun token(forceRefresh: Boolean = false): String = tokenLock.withLock {
        val current = prefs.accessToken
        if (forceRefresh || current == null || System.currentTimeMillis() > prefs.expiresAtMs - 60_000) {
            refresh()
        }
        prefs.accessToken ?: throw NotLoggedInException()
    }

    // ---------- Chamadas ----------

    private suspend fun call(method: String, path: String, body: RequestBody? = null): String? {
        var retried = false
        while (true) {
            val tk = token(forceRefresh = retried)
            val result = withContext(Dispatchers.IO) {
                val req = Request.Builder()
                    .url("https://api.spotify.com/v1$path")
                    .header("Authorization", "Bearer $tk")
                    .method(method, body ?: if (method == "GET") null else ByteArray(0).toRequestBody())
                    .build()
                http.newCall(req).execute().use { resp -> resp.code to resp.body?.string() }
            }
            val (code, text) = result
            when {
                code == 401 && !retried -> retried = true
                code == 204 -> return null
                code in 200..299 -> return text?.ifBlank { null }
                else -> throw SpotifyException(code, friendlyError(code, text))
            }
        }
    }

    private fun friendlyError(code: Int, text: String?): String {
        val reason = runCatching { JSONObject(text ?: "").getJSONObject("error").optString("reason") }.getOrNull()
        val msg = runCatching { JSONObject(text ?: "").getJSONObject("error").optString("message") }.getOrNull().orEmpty()
        return when {
            code == 403 && msg.contains("scope", ignoreCase = true) -> NEEDS_RELOGIN
            reason == "NO_ACTIVE_DEVICE" || code == 404 -> "Abra o Spotify e toque algo primeiro."
            reason == "PREMIUM_REQUIRED" -> "Esse controle exige Spotify Premium."
            code == 403 -> "O Spotify recusou o acesso. Confira se sua conta está em \"User Management\" no painel do app."
            code == 429 -> "Muitas consultas seguidas. Aguarde alguns segundos."
            code == 400 -> "O Spotify não aceitou o pedido (400)." + (msg.takeIf { it.isNotBlank() }?.let { " $it" } ?: "")
            else -> "Erro do Spotify ($code)"
        }
    }

    // ---------- Reprodução ----------

    suspend fun nowPlaying(): NowPlaying {
        val text = call("GET", "/me/player/currently-playing?additional_types=track")
        val now = SystemClock.elapsedRealtime()
        if (text == null) return NowPlaying(null, false, 0, now)
        val json = JSONObject(text)
        val item = json.optJSONObject("item")
        val track = if (item != null && json.optString("currently_playing_type") == "track") parseTrack(item) else null
        return NowPlaying(
            track = track,
            isPlaying = json.optBoolean("is_playing"),
            progressMs = json.optLong("progress_ms"),
            receivedAtMs = now,
        )
    }

    suspend fun play() { call("PUT", "/me/player/play") }
    suspend fun pause() { call("PUT", "/me/player/pause") }
    suspend fun next() { call("POST", "/me/player/next") }
    suspend fun previous() { call("POST", "/me/player/previous") }
    suspend fun seek(ms: Long) { call("PUT", "/me/player/seek?position_ms=$ms") }

    /** Toca uma faixa específica no aparelho ativo do Spotify. */
    suspend fun playTrack(id: String) {
        val body = JSONObject().put("uris", JSONArray().put("spotify:track:$id")).toString()
        try {
            call("PUT", "/me/player/play", body.toRequestBody("application/json".toMediaType()))
        } catch (e: SpotifyException) {
            // Sem aparelho ativo: tenta tocar no celular (ou no primeiro aparelho que o Spotify conhece).
            if (e.code != 404) throw e
            val device = devices().let { list -> list.firstOrNull { it.second == "Smartphone" } ?: list.firstOrNull() } ?: throw e
            call("PUT", "/me/player/play?device_id=${device.first}", body.toRequestBody("application/json".toMediaType()))
        }
    }

    /** Aparelhos onde o Spotify está aberto: (id, tipo). */
    private suspend fun devices(): List<Pair<String, String>> {
        val json = JSONObject(call("GET", "/me/player/devices") ?: "{}")
        return json.optJSONArray("devices").objects().mapNotNull { d ->
            d.optString("id").takeIf { it.isNotBlank() && !d.optBoolean("is_restricted") }?.let { it to d.optString("type") }
        }
    }

    /** Toca uma lista de faixas, em ordem, no aparelho ativo. */
    suspend fun playTracks(ids: List<String>, offset: Int = 0) {
        val body = JSONObject()
            .put("uris", JSONArray(ids.take(100).map { "spotify:track:$it" }))
            .put("offset", JSONObject().put("position", offset.coerceIn(0, (ids.size - 1).coerceAtLeast(0))))
        call("PUT", "/me/player/play", body.toString().toRequestBody("application/json".toMediaType()))
    }

    /**
     * Acha no Spotify a mesma música vista no Deezer: primeiro pelo ISRC (código único da gravação),
     * depois por título + artista, conferindo se o artista bate.
     */
    suspend fun findTrack(title: String, artist: String, isrc: String?): String? {
        fun norm(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()
        if (isrc != null) {
            val r = runCatching { searchRaw("isrc:$isrc", 3) }.getOrNull().orEmpty()
            r.firstOrNull()?.let { return it.id }
        }
        val mainArtist = artist.split(Regex(" e | & |, | feat\\.? | ft\\.? ", RegexOption.IGNORE_CASE)).first()
        val cleanTitle = title.replace(Regex("\\s*[(\\[].*?[)\\]]"), "").trim()
        for (q in listOf("track:\"$cleanTitle\" artist:\"$mainArtist\"", "$cleanTitle $mainArtist")) {
            val r = runCatching { searchRaw(q, 8) }.getOrNull().orEmpty()
            val a = norm(mainArtist)
            val t = norm(cleanTitle)
            r.firstOrNull { tr -> tr.artists.any { norm(it).contains(a) || a.contains(norm(it)) } && norm(tr.name).startsWith(t.take(12)) }
                ?.let { return it.id }
            r.firstOrNull { tr -> tr.artists.any { norm(it) == a } }?.let { return it.id }
        }
        return null
    }

    private suspend fun searchRaw(q: String, limit: Int): List<Track> {
        val enc = java.net.URLEncoder.encode(q, "UTF-8")
        val json = JSONObject(call("GET", "/search?type=track&limit=$limit&q=$enc") ?: "{}")
        return json.optJSONObject("tracks")?.optJSONArray("items").objects().map(::parseTrack)
    }

    suspend fun myId(): String = JSONObject(call("GET", "/me") ?: "{}").optString("id")

    /** A playlist ainda existe e é sua? (pode ter sido apagada no Spotify) */
    suspend fun playlistExists(id: String): Boolean = try {
        val o = JSONObject(call("GET", "/playlists/$id?fields=id,owner(id)") ?: "{}")
        o.optString("id") == id
    } catch (e: SpotifyException) {
        if (e.code == 404) false else throw e
    }

    suspend fun createPlaylist(name: String, description: String): String {
        val uid = myId()
        val body = JSONObject().put("name", name).put("description", description).put("public", false)
        val o = JSONObject(call("POST", "/users/$uid/playlists", body.toString().toRequestBody("application/json".toMediaType())) ?: "{}")
        return o.getString("id")
    }

    suspend fun addToPlaylist(playlistId: String, ids: List<String>) {
        ids.chunked(100).forEach { chunk ->
            val body = JSONObject().put("uris", JSONArray(chunk.map { "spotify:track:$it" })).put("position", 0)
            call("POST", "/playlists/$playlistId/tracks", body.toString().toRequestBody("application/json".toMediaType()))
        }
    }

    /** Coloca uma faixa na fila do Spotify. */
    suspend fun queue(id: String) { call("POST", "/me/player/queue?uri=spotify:track:$id") }

    /** Busca músicas pelo nome, artista ou trecho. */
    suspend fun search(query: String): List<Track> {
        val q = java.net.URLEncoder.encode(query.trim(), "UTF-8")
        // Apps em modo de desenvolvimento só podem pedir até 10 resultados por busca (acima disso o Spotify devolve 400).
        // Se ainda assim recusar, tenta de novo de forma mais simples.
        val attempts = listOf("limit=10&market=from_token", "limit=10", "limit=5")
        var last: Exception? = null
        for (params in attempts) {
            try {
                val json = JSONObject(call("GET", "/search?type=track&$params&q=$q") ?: "{}")
                return json.optJSONObject("tracks")?.optJSONArray("items").objects().map(::parseTrack)
            } catch (e: SpotifyException) {
                if (e.code != 400) throw e
                last = e
            }
        }
        throw last ?: SpotifyException(400, "Não consegui buscar agora.")
    }

    // ---------- Perfil ----------

    suspend fun me(): UserProfile {
        val json = JSONObject(call("GET", "/me") ?: "{}")
        return UserProfile(
            name = json.optString("display_name").ifBlank { json.optString("id") },
            imageUrl = json.optJSONArray("images")?.firstImage(),
        )
    }

    suspend fun topArtists(range: TimeRange): List<Artist> {
        val json = JSONObject(call("GET", "/me/top/artists?limit=30&time_range=${range.apiValue}") ?: "{}")
        return json.optJSONArray("items").objects().map { a ->
            Artist(
                id = a.optString("id"),
                name = a.optString("name"),
                imageUrl = a.optJSONArray("images")?.firstImage(),
                genres = a.optJSONArray("genres")?.strings().orEmpty(),
            )
        }
    }

    suspend fun topTracks(range: TimeRange): List<Track> {
        val json = JSONObject(call("GET", "/me/top/tracks?limit=30&time_range=${range.apiValue}") ?: "{}")
        return json.optJSONArray("items").objects().map(::parseTrack)
    }

    suspend fun recentlyPlayed(): List<RecentPlay> {
        val json = JSONObject(call("GET", "/me/player/recently-played?limit=50") ?: "{}")
        return json.optJSONArray("items").objects().mapNotNull { item ->
            val t = item.optJSONObject("track") ?: return@mapNotNull null
            val at = runCatching { Instant.parse(item.optString("played_at")).toEpochMilli() }.getOrDefault(0L)
            RecentPlay(parseTrack(t), at)
        }
    }

    // ---------- JSON ----------

    private fun parseTrack(o: JSONObject): Track {
        val album = o.optJSONObject("album")
        return Track(
            id = o.optString("id"),
            name = o.optString("name"),
            artists = o.optJSONArray("artists").objects().map { it.optString("name") },
            album = album?.optString("name").orEmpty(),
            imageUrl = album?.optJSONArray("images")?.firstImage(),
            durationMs = o.optLong("duration_ms"),
        )
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONArray.strings(): List<String> = (0 until length()).map { optString(it) }

    private fun JSONArray.firstImage(): String? = optJSONObject(0)?.optString("url")?.ifBlank { null }
}
