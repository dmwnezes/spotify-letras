package com.dmwnezes.sintonia.reco

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** Uma música recomendada (vinda do Deezer), com o motivo e, quando já achada, o ID no Spotify. */
data class RecoTrack(
    val dzId: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val album: String,
    val cover: String?,
    val previewUrl: String?,
    val durationSec: Int,
    val reason: String,          // artista seu que levou a esta música
    val spotifyId: String? = null,
    val savedAt: Long = 0,
) {
    val key: String get() = Recommender.trackKey(artist, title)

    companion object {
        fun from(t: DzTrack, reason: String) = RecoTrack(
            t.id, t.title, t.artist, t.artistId, t.album, t.cover, t.previewUrl, t.durationSec, reason,
        )
    }
}

/** Tudo o que o app aprendeu e guardou sobre as recomendações. */
data class RecoData(
    val likes: List<RecoTrack> = emptyList(),
    val dislikedTracks: Set<Long> = emptySet(),
    /** Nota de cada artista (nome normalizado): sobe quando você curte, desce quando pula. */
    val artistScore: Map<String, Float> = emptyMap(),
    /** Músicas já mostradas, com o dia (epochDay) em que apareceram. */
    val seen: Map<Long, Long> = emptyMap(),
    val mixDay: Long = -1,
    val mix: List<RecoTrack> = emptyList(),
)

class RecoStore(context: Context) {
    private val file = File(context.filesDir, "recomendacoes.json")
    private val _state = MutableStateFlow(load())
    val state = _state.asStateFlow()
    val data: RecoData get() = _state.value

    fun update(f: (RecoData) -> RecoData) {
        val next = f(_state.value)
        _state.value = next
        runCatching {
            val tmp = File(file.parentFile, "recomendacoes.tmp")
            tmp.writeText(toJson(next))
            tmp.renameTo(file)
        }
    }

    private fun load(): RecoData = runCatching { if (file.exists()) fromJson(file.readText()) else RecoData() }.getOrDefault(RecoData())

    companion object {
        fun today(): Long = LocalDate.now().toEpochDay()

        /** Curtir: a música entra nas curtidas; o artista e o artista-semente ganham pontos. */
        fun like(d: RecoData, t: RecoTrack, now: Long = System.currentTimeMillis()): RecoData {
            val s = d.artistScore.toMutableMap()
            val a = DeezerClient.norm(t.artist)
            s[a] = ((s[a] ?: 0f) + 1f).coerceAtMost(4f)
            val r = DeezerClient.norm(t.reason)
            if (r != a) s[r] = ((s[r] ?: 0f) + 0.3f).coerceAtMost(4f)
            return d.copy(
                likes = listOf(t.copy(savedAt = now)) + d.likes.filterNot { it.dzId == t.dzId || it.key == t.key },
                dislikedTracks = d.dislikedTracks - t.dzId,
                artistScore = s,
            ).markSeen(t.dzId)
        }

        /** Pular: a música não volta, e o artista perde pontos. */
        fun dislike(d: RecoData, t: RecoTrack): RecoData {
            val s = d.artistScore.toMutableMap()
            val a = DeezerClient.norm(t.artist)
            s[a] = ((s[a] ?: 0f) - 0.7f).coerceAtLeast(-4f)
            return d.copy(
                dislikedTracks = d.dislikedTracks + t.dzId,
                artistScore = s,
                likes = d.likes.filterNot { it.dzId == t.dzId },
                mix = d.mix.filterNot { it.dzId == t.dzId },
            ).markSeen(t.dzId)
        }

        fun RecoData.markSeen(vararg ids: Long, day: Long = today()): RecoData =
            copy(seen = (seen + ids.associateWith { day }).filterValues { day - it <= 60 })

        fun toJson(d: RecoData): String = JSONObject().apply {
            put("likes", d.likes.toJson())
            put("disliked", JSONArray(d.dislikedTracks.toList()))
            put("artistScore", JSONObject(d.artistScore.mapValues { it.value.toDouble() }))
            put("seen", JSONObject(d.seen.mapKeys { it.key.toString() }))
            put("mixDay", d.mixDay)
            put("mix", d.mix.toJson())
        }.toString()

        fun fromJson(text: String): RecoData {
            val o = JSONObject(text)
            val scores = o.optJSONObject("artistScore") ?: JSONObject()
            val seen = o.optJSONObject("seen") ?: JSONObject()
            val dis = o.optJSONArray("disliked") ?: JSONArray()
            return RecoData(
                likes = o.optJSONArray("likes").tracks(),
                dislikedTracks = (0 until dis.length()).map { dis.getLong(it) }.toSet(),
                artistScore = scores.keys().asSequence().associateWith { scores.getDouble(it).toFloat() },
                seen = seen.keys().asSequence().associate { it.toLong() to seen.getLong(it) },
                mixDay = o.optLong("mixDay", -1),
                mix = o.optJSONArray("mix").tracks(),
            )
        }

        private fun List<RecoTrack>.toJson() = JSONArray().also { a ->
            forEach { t ->
                a.put(JSONObject().put("id", t.dzId).put("t", t.title).put("a", t.artist).put("ai", t.artistId)
                    .put("al", t.album).put("c", t.cover ?: "").put("p", t.previewUrl ?: "").put("d", t.durationSec)
                    .put("r", t.reason).put("s", t.spotifyId ?: "").put("at", t.savedAt))
            }
        }

        private fun JSONArray?.tracks(): List<RecoTrack> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }.map {
            RecoTrack(it.getLong("id"), it.optString("t"), it.optString("a"), it.optLong("ai"), it.optString("al"),
                it.optString("c").ifBlank { null }, it.optString("p").ifBlank { null }, it.optInt("d"), it.optString("r"),
                it.optString("s").ifBlank { null }, it.optLong("at"))
        }
    }
}
