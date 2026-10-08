package com.dmwnezes.sintonia

import com.dmwnezes.sintonia.history.HistoryAggregator
import com.dmwnezes.sintonia.history.HistoryStore
import com.dmwnezes.sintonia.history.HistorySummary
import com.dmwnezes.sintonia.history.RawStream
import com.dmwnezes.sintonia.history.Retrospective
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class HistoryAggregatorTest {
    private val zone = ZoneId.of("America/Fortaleza")

    private fun s(
        at: String, track: String, artist: String, ms: Long = 200_000,
        skipped: Boolean = false, end: String = "trackdone",
    ) = RawStream(
        endEpochMs = LocalDateTime.parse(at).atZone(zone).toInstant().toEpochMilli(),
        msPlayed = ms, track = track, artist = artist, album = "Álbum de $artist",
        trackUri = "spotify:track:${track.hashCode()}", showName = null, episodeName = null,
        platform = "Android OS 14 API 34 (samsung, SM-A546E)", country = "BR",
        reasonStart = "clickrow", reasonEnd = end, shuffle = true, skipped = skipped,
        offline = false, incognito = false,
    )

    private fun sample(): HistoryAggregator {
        val agg = HistoryAggregator(zone)
        // 2023: Djavan domina
        repeat(10) { agg.add(s("2023-05-0${(it % 3) + 1}T22:10:00", "Oceano", "Djavan")) }
        repeat(3) { agg.add(s("2023-05-04T09:00:00", "Faixa B", "Banda X")) }
        // 2024: artista novo + pulos
        repeat(6) { agg.add(s("2024-01-1${it}T20:00:00", "Nova", "Artista Novo")) }
        repeat(5) { agg.add(s("2024-01-20T21:00:00", "Chata", "Banda X", ms = 5_000, skipped = true, end = "fwdbtn")) }
        agg.add(s("2024-01-21T21:00:00", "Chata", "Banda X"))
        // podcast
        agg.add(RawStream(LocalDateTime.parse("2024-02-01T08:00:00").atZone(zone).toInstant().toEpochMilli(), 1_800_000,
            null, null, null, null, "Podcast Y", "Ep 1", "android", "BR", null, null, null, null, null, null))
        return agg
    }

    @Test
    fun aggregatesPeriods() {
        val stats = sample().build(files = 1)
        val all = stats.all!!
        assertEquals(listOf("2024", "2023"), stats.years)
        assertEquals("Djavan", all.topArtists.first().name)
        assertEquals("Oceano", all.topTracks.first().name)
        assertEquals(10, all.topTracks.first().count)
        assertEquals(5, all.skipped)
        assertEquals("Chata", all.mostSkipped.first().name)
        assertEquals(1_800_000, all.podcastMs)
        assertEquals("Celular Android", all.platforms.first().name)
        assertEquals(22, Retrospective.peakHour(stats.periods["2023"]!!))

        val y24 = stats.periods["2024"]!!
        assertEquals(1, y24.newArtists)               // Banda X já existia em 2023
        assertEquals("Artista Novo", y24.topNewArtists.first().name)
        assertEquals(6, y24.longestStreakDays)        // 10/01 a 15/01
        assertEquals("10/01/2024", y24.streakFrom)
        assertNotNull(stats.periods["2023"]!!.obsession)
    }

    @Test
    fun textsAndBackupRoundTrip() {
        val stats = sample().build(files = 1, importedAtMs = 123)
        val retro = Retrospective.forPeriod(stats, "2024")
        assertTrue(retro, retro.contains("Em 2024 você ouviu"))
        assertTrue(Retrospective.forPeriod(stats, "all").contains("Djavan"))
        assertTrue(HistorySummary.build(stats).contains("## 2023"))

        val back = HistoryStore.fromJson(HistoryStore.toJson(stats))
        assertEquals(stats, back)
    }
}
