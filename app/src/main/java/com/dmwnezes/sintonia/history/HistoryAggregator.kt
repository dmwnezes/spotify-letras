package com.dmwnezes.sintonia.history

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Recebe as reproduções uma a uma e calcula as estatísticas,
 * para o geral ("all") e para cada ano. Não guarda a lista inteira na memória.
 */
class HistoryAggregator(private val zone: ZoneId = ZoneId.systemDefault()) {

    private val periods = mutableMapOf<String, Acc>()
    private val artistFirst = mutableMapOf<String, Long>()
    private var total = 0

    fun add(s: RawStream) {
        if (s.msPlayed <= 0) return
        total++
        val local = Instant.ofEpochMilli(s.endEpochMs).atZone(zone)
        val year = local.year.toString()
        val isMusic = !s.track.isNullOrBlank() && !s.artist.isNullOrBlank()
        if (isMusic) {
            val prev = artistFirst[s.artist!!]
            if (prev == null || s.endEpochMs < prev) artistFirst[s.artist] = s.endEpochMs
        }
        for (key in listOf("all", year)) {
            periods.getOrPut(key) { Acc(key) }.add(s, isMusic, local.toLocalDate(), local.hour, local.dayOfWeek.value - 1, year)
        }
    }

    fun build(files: Int, importedAtMs: Long = System.currentTimeMillis()): HistoryStats {
        val firstByArtist = artistFirst.mapValues { Instant.ofEpochMilli(it.value).atZone(zone).toLocalDate() }
        val result = periods.mapValues { (_, acc) -> acc.toStats(firstByArtist) }
        val all = periods["all"]
        val timeline = all?.artistMs?.entries
            ?.sortedByDescending { it.value }?.take(40)
            ?.mapNotNull { (artist, ms) -> firstByArtist[artist]?.let { Ranked(artist, fmtDate(it), ms) } }
            ?.sortedBy { it.sub.toIsoKey() }
            .orEmpty()
        return HistoryStats(importedAtMs, total, files, result, timeline)
    }

    private class Counter { var ms = 0L; var count = 0 }

    private inner class Acc(val key: String) {
        var ms = 0L; var plays = 0; var streams = 0
        var skipped = 0; var shuffle = 0; var offline = 0; var incognito = 0
        var podcastMs = 0L
        val trackMs = HashMap<String, Counter>()
        val trackInfo = HashMap<String, Pair<String, String>>() // key -> (nome, artista)
        val trackSkips = HashMap<String, Int>()
        val trackDone = HashMap<String, Int>()
        val artistMs = HashMap<String, Long>()
        val artistPlays = HashMap<String, Int>()
        val albumMs = HashMap<String, Counter>()
        val albumArtist = HashMap<String, String>()
        val showMs = HashMap<String, Counter>()
        val hourMs = LongArray(24)
        val weekdayMs = LongArray(7)
        val bucketMs = sortedMapOf<String, Long>()
        val dayMs = HashMap<LocalDate, Long>()
        val dayTrack = HashMap<String, Int>()
        val platforms = HashMap<String, Long>()
        val countries = HashMap<String, Long>()
        val reasons = HashMap<String, Int>()

        fun add(s: RawStream, isMusic: Boolean, day: LocalDate, hour: Int, weekday: Int, year: String) {
            if (!isMusic) {
                if (!s.showName.isNullOrBlank()) {
                    podcastMs += s.msPlayed
                    showMs.getOrPut(s.showName) { Counter() }.apply { ms += s.msPlayed; count++ }
                }
                return
            }
            val artist = s.artist!!
            val trackName = s.track!!
            val tKey = s.trackUri?.takeIf { it.isNotBlank() } ?: "$artist|$trackName"
            ms += s.msPlayed
            streams++
            val counted = s.msPlayed >= 30_000
            if (counted) plays++

            trackMs.getOrPut(tKey) { Counter() }.apply { ms += s.msPlayed; if (counted) count++ }
            trackInfo.putIfAbsent(tKey, trackName to artist)
            artistMs.merge(artist, s.msPlayed, Long::plus)
            if (counted) artistPlays.merge(artist, 1, Int::plus)
            if (!s.album.isNullOrBlank()) {
                val aKey = "${s.album}|$artist"
                albumMs.getOrPut(aKey) { Counter() }.apply { ms += s.msPlayed; if (counted) count++ }
                albumArtist.putIfAbsent(aKey, artist)
            }

            val isSkip = s.skipped == true ||
                (s.reasonEnd == "fwdbtn" && s.msPlayed < 30_000) ||
                (s.reasonEnd == null && s.skipped == null && s.msPlayed < 10_000)
            if (isSkip) { skipped++; trackSkips.merge(tKey, 1, Int::plus) }
            if (s.reasonEnd == "trackdone") trackDone.merge(tKey, 1, Int::plus)
            if (s.shuffle == true) shuffle++
            if (s.offline == true) offline++
            if (s.incognito == true) incognito++

            hourMs[hour] += s.msPlayed
            weekdayMs[weekday] += s.msPlayed
            val bucket = if (key == "all") year else "%s-%02d".format(year, day.monthValue)
            bucketMs.merge(bucket, s.msPlayed, Long::plus)
            dayMs.merge(day, s.msPlayed, Long::plus)
            if (counted) dayTrack.merge("$day|$tKey", 1, Int::plus)
            s.platform?.let { platforms.merge(normalizePlatform(it), s.msPlayed, Long::plus) }
            s.country?.takeIf { it.isNotBlank() && it != "ZZ" }?.let { countries.merge(it.uppercase(), s.msPlayed, Long::plus) }
            s.reasonStart?.takeIf { it.isNotBlank() }?.let { reasons.merge(it, 1, Int::plus) }
        }

        fun toStats(firstByArtist: Map<String, LocalDate>): PeriodStats {
            val days = dayMs.keys.sorted()
            var best = 0; var bestFrom: LocalDate? = null; var bestTo: LocalDate? = null
            var run = 0; var runFrom: LocalDate? = null; var prev: LocalDate? = null
            for (d in days) {
                if (prev != null && d == prev.plusDays(1)) run++ else { run = 1; runFrom = d }
                if (run > best) { best = run; bestFrom = runFrom; bestTo = d }
                prev = d
            }
            val biggest = dayMs.maxByOrNull { it.value }
            val obs = dayTrack.maxByOrNull { it.value }?.let { (k, n) ->
                val (d, tKey) = k.split("|", limit = 2)
                val (name, artist) = trackInfo[tKey] ?: ("?" to "")
                Ranked(name, "$artist · ${fmtDate(LocalDate.parse(d))}", count = n)
            }

            val newOnes = if (key == "all") firstByArtist.keys.filter { it in artistMs }
            else firstByArtist.filter { it.value.year.toString() == key && it.key in artistMs }.keys

            return PeriodStats(
                key = key, ms = ms, plays = plays, streams = streams,
                distinctTracks = trackMs.size, distinctArtists = artistMs.size, distinctAlbums = albumMs.size,
                activeDays = dayMs.size,
                topArtists = artistMs.entries.sortedByDescending { it.value }.take(50)
                    .map { Ranked(it.key, "", it.value, artistPlays[it.key] ?: 0) },
                topTracks = trackMs.entries.sortedByDescending { it.value.ms }.take(50).map { (k, c) ->
                    val (n, a) = trackInfo.getValue(k)
                    Ranked(n, a, c.ms, c.count, k.takeIf { it.startsWith("spotify:") })
                },
                topAlbums = albumMs.entries.sortedByDescending { it.value.ms }.take(30).map { (k, c) ->
                    Ranked(k.substringBefore("|"), albumArtist[k].orEmpty(), c.ms, c.count)
                },
                hourMs = hourMs.toList(),
                weekdayMs = weekdayMs.toList(),
                monthMs = bucketMs.map { Ranked(it.key, ms = it.value) },
                skipped = skipped, shuffleStreams = shuffle, offlineStreams = offline, incognitoStreams = incognito,
                platforms = platforms.entries.sortedByDescending { it.value }.map { Ranked(it.key, ms = it.value) },
                countries = countries.entries.sortedByDescending { it.value }.take(10).map { Ranked(it.key, ms = it.value) },
                reasonsStart = reasons.entries.sortedByDescending { it.value }.take(8).map { Ranked(reasonLabel(it.key), count = it.value) },
                mostSkipped = trackSkips.entries
                    .filter { (k, n) -> n >= 3 && (trackMs[k]?.count ?: 0) + n >= 5 }
                    .sortedByDescending { it.value }.take(15).map { (k, n) ->
                        val (name, artist) = trackInfo.getValue(k)
                        val totalStreams = n + (trackMs[k]?.count ?: 0)
                        Ranked(name, "$artist · ${100 * n / totalStreams.coerceAtLeast(1)}% das vezes", count = n)
                    },
                alwaysFinished = trackDone.entries
                    .filter { (k, n) -> n >= 8 && (trackSkips[k] ?: 0) == 0 }
                    .sortedByDescending { it.value }.take(15).map { (k, n) ->
                        val (name, artist) = trackInfo.getValue(k)
                        Ranked(name, artist, count = n)
                    },
                newArtists = newOnes.size,
                topNewArtists = newOnes.sortedByDescending { artistMs[it] ?: 0 }.take(10)
                    .map { Ranked(it, fmtDate(firstByArtist.getValue(it)), artistMs[it] ?: 0) },
                podcastMs = podcastMs,
                topShows = showMs.entries.sortedByDescending { it.value.ms }.take(10).map { Ranked(it.key, ms = it.value.ms, count = it.value.count) },
                biggestDay = biggest?.let { Ranked(fmtDate(it.key), ms = it.value) },
                longestStreakDays = best,
                streakFrom = bestFrom?.let(::fmtDate),
                streakTo = bestTo?.let(::fmtDate),
                obsession = obs,
                firstDay = days.firstOrNull()?.let(::fmtDate),
                lastDay = days.lastOrNull()?.let(::fmtDate),
            )
        }
    }

    companion object {
        fun fmtDate(d: LocalDate): String = "%02d/%02d/%d".format(d.dayOfMonth, d.monthValue, d.year)
        private fun String.toIsoKey(): String = split("/").let { if (it.size == 3) "${it[2]}${it[1]}${it[0]}" else this }

        fun normalizePlatform(p: String): String {
            val s = p.lowercase()
            return when {
                "android" in s && ("tv" in s || "tablet" in s).not() -> "Celular Android"
                "ios" in s || "iphone" in s || "ipad" in s -> "iPhone/iPad"
                "windows" in s -> "Windows"
                "os x" in s || "osx" in s || "macos" in s || "mac" in s -> "Mac"
                "web" in s -> "Navegador"
                "cast" in s -> "Chromecast"
                "tv" in s || "tizen" in s || "webos" in s || "roku" in s -> "TV"
                "playstation" in s || "ps4" in s || "ps5" in s || "xbox" in s -> "Videogame"
                "car" in s || "auto" in s -> "Carro"
                "sonos" in s || "speaker" in s || "alexa" in s || "echo" in s || "google_home" in s -> "Caixa de som"
                "linux" in s -> "Linux"
                else -> "Outros"
            }
        }

        fun reasonLabel(r: String): String = when (r) {
            "trackdone" -> "Seguiu da música anterior"
            "clickrow" -> "Você escolheu na lista"
            "fwdbtn" -> "Pulou para ela"
            "backbtn" -> "Voltou para ela"
            "playbtn" -> "Apertou play"
            "appload" -> "Ao abrir o app"
            "remote" -> "Outro aparelho"
            "trackerror" -> "Erro na faixa anterior"
            else -> r
        }
    }
}
