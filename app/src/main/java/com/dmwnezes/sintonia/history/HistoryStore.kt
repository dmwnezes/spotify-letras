package com.dmwnezes.sintonia.history

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Salva as estatísticas no armazenamento privado do app e converte de/para o backup .json. */
class HistoryStore(context: Context) {
    private val file = File(context.filesDir, "historico.json")

    fun load(): HistoryStats? = runCatching { if (file.exists()) fromJson(file.readText()) else null }.getOrNull()

    fun save(stats: HistoryStats) {
        val tmp = File(file.parentFile, "historico.tmp")
        tmp.writeText(toJson(stats))
        tmp.renameTo(file)
    }

    fun clear() { file.delete() }

    companion object {
        private const val FORMAT = "sintonia-historico"
        private const val VERSION = 1

        fun toJson(s: HistoryStats): String = JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("importedAt", s.importedAtMs)
            put("totalEntries", s.totalEntries)
            put("files", s.files)
            put("artistTimeline", s.artistTimeline.toJson())
            put("periods", JSONObject().apply { s.periods.forEach { (k, p) -> put(k, p.toJson()) } })
        }.toString()

        fun fromJson(text: String): HistoryStats {
            val o = JSONObject(text)
            require(o.optString("format") == FORMAT) { "formato desconhecido" }
            val periods = o.getJSONObject("periods")
            return HistoryStats(
                importedAtMs = o.optLong("importedAt"),
                totalEntries = o.optInt("totalEntries"),
                files = o.optInt("files"),
                periods = periods.keys().asSequence().associateWith { periodFrom(periods.getJSONObject(it)) },
                artistTimeline = o.optJSONArray("artistTimeline").rankedList(),
            )
        }

        private fun Ranked.toJson() = JSONObject().apply {
            put("n", name)
            if (sub.isNotEmpty()) put("s", sub)
            if (ms != 0L) put("m", ms)
            if (count != 0) put("c", count)
            uri?.let { put("u", it) }
        }

        private fun List<Ranked>.toJson() = JSONArray().also { a -> forEach { a.put(it.toJson()) } }
        private fun List<Long>.longs() = JSONArray().also { a -> forEach { a.put(it) } }

        private fun rankedFrom(o: JSONObject) = Ranked(
            name = o.optString("n"),
            sub = o.optString("s"),
            ms = o.optLong("m"),
            count = o.optInt("c"),
            uri = o.optString("u").ifBlank { null },
        )

        private fun JSONArray?.rankedList(): List<Ranked> =
            if (this == null) emptyList() else (0 until length()).map { rankedFrom(getJSONObject(it)) }

        private fun JSONArray?.longList(size: Int): List<Long> =
            if (this == null) List(size) { 0L } else (0 until length()).map { optLong(it) }

        private fun PeriodStats.toJson() = JSONObject().apply {
            put("key", key); put("ms", ms); put("plays", plays); put("streams", streams)
            put("distinctTracks", distinctTracks); put("distinctArtists", distinctArtists); put("distinctAlbums", distinctAlbums)
            put("activeDays", activeDays)
            put("topArtists", topArtists.toJson()); put("topTracks", topTracks.toJson()); put("topAlbums", topAlbums.toJson())
            put("hourMs", hourMs.longs()); put("weekdayMs", weekdayMs.longs()); put("monthMs", monthMs.toJson())
            put("skipped", skipped); put("shuffle", shuffleStreams); put("offline", offlineStreams); put("incognito", incognitoStreams)
            put("platforms", platforms.toJson()); put("countries", countries.toJson()); put("reasonsStart", reasonsStart.toJson())
            put("mostSkipped", mostSkipped.toJson()); put("alwaysFinished", alwaysFinished.toJson())
            put("newArtists", newArtists); put("topNewArtists", topNewArtists.toJson())
            put("podcastMs", podcastMs); put("topShows", topShows.toJson())
            biggestDay?.let { put("biggestDay", it.toJson()) }
            put("streak", longestStreakDays); put("streakFrom", streakFrom ?: ""); put("streakTo", streakTo ?: "")
            obsession?.let { put("obsession", it.toJson()) }
            put("firstDay", firstDay ?: ""); put("lastDay", lastDay ?: "")
        }

        private fun periodFrom(o: JSONObject) = PeriodStats(
            key = o.getString("key"), ms = o.optLong("ms"), plays = o.optInt("plays"), streams = o.optInt("streams"),
            distinctTracks = o.optInt("distinctTracks"), distinctArtists = o.optInt("distinctArtists"),
            distinctAlbums = o.optInt("distinctAlbums"), activeDays = o.optInt("activeDays"),
            topArtists = o.optJSONArray("topArtists").rankedList(),
            topTracks = o.optJSONArray("topTracks").rankedList(),
            topAlbums = o.optJSONArray("topAlbums").rankedList(),
            hourMs = o.optJSONArray("hourMs").longList(24),
            weekdayMs = o.optJSONArray("weekdayMs").longList(7),
            monthMs = o.optJSONArray("monthMs").rankedList(),
            skipped = o.optInt("skipped"), shuffleStreams = o.optInt("shuffle"),
            offlineStreams = o.optInt("offline"), incognitoStreams = o.optInt("incognito"),
            platforms = o.optJSONArray("platforms").rankedList(),
            countries = o.optJSONArray("countries").rankedList(),
            reasonsStart = o.optJSONArray("reasonsStart").rankedList(),
            mostSkipped = o.optJSONArray("mostSkipped").rankedList(),
            alwaysFinished = o.optJSONArray("alwaysFinished").rankedList(),
            newArtists = o.optInt("newArtists"),
            topNewArtists = o.optJSONArray("topNewArtists").rankedList(),
            podcastMs = o.optLong("podcastMs"),
            topShows = o.optJSONArray("topShows").rankedList(),
            biggestDay = o.optJSONObject("biggestDay")?.let(::rankedFrom),
            longestStreakDays = o.optInt("streak"),
            streakFrom = o.optString("streakFrom").ifBlank { null },
            streakTo = o.optString("streakTo").ifBlank { null },
            obsession = o.optJSONObject("obsession")?.let(::rankedFrom),
            firstDay = o.optString("firstDay").ifBlank { null },
            lastDay = o.optString("lastDay").ifBlank { null },
        )
    }
}
