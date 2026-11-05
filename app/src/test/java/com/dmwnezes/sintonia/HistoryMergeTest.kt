package com.dmwnezes.sintonia

import com.dmwnezes.sintonia.data.RecentPlay
import com.dmwnezes.sintonia.data.Track
import com.dmwnezes.sintonia.history.HistoryAggregator
import com.dmwnezes.sintonia.history.HistoryMerge
import com.dmwnezes.sintonia.history.HistoryStore
import com.dmwnezes.sintonia.history.PlayLog
import com.dmwnezes.sintonia.history.RawStream
import com.dmwnezes.sintonia.wrapped.Slide
import com.dmwnezes.sintonia.wrapped.WrappedBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

class HistoryMergeTest {
    private val zone = ZoneId.of("America/Fortaleza")
    private fun at(s: String) = LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli()

    private fun raw(when_: String, track: String, artist: String, ms: Long = 200_000) = RawStream(
        at(when_), ms, track, artist, "Álbum de $artist", null, null, null, null, null, null, null, null, null, null, null,
    )

    private fun base() = HistoryAggregator(zone).apply {
        repeat(3) { add(raw("2026-10-0${it + 1}T20:00:00", "Oceano", "Djavan")) }
        add(raw("2026-10-10T20:00:00", "Faixa B", "Banda X"))
    }.build(files = 1)

    @Test
    fun collectedPlaysFeedTheRetrospective() {
        val merged = HistoryMerge.combine(
            base(),
            listOf(
                raw("2026-10-10T23:00:00", "Faixa B", "Banda X"), // mesmo dia do último importado: já pode estar nele
                raw("2026-10-11T09:00:00", "Oceano", "Djavan"),
                raw("2026-10-12T09:00:00", "Nova", "Artista Novo"),
            ),
            zone,
        )!!
        val p = merged.periods["2026"]!!
        assertEquals(6 * 200_000L, p.ms)
        assertEquals(6, p.activeDays)
        assertEquals(4, p.topTracks.first { it.name == "Oceano" }.count)
        assertEquals("12/10/2026", p.lastDay)
        assertEquals(3, merged.all!!.distinctArtists)
        assertEquals(3, p.newArtists) // Djavan e Banda X no importado, Artista Novo coletado
        assertEquals(800_000L, p.topArtists.first { it.name == "Djavan" }.ms)
        assertEquals(p.ms, p.monthMs.sumOf { it.ms })

        val minutes = WrappedBuilder.build(merged, "2026").filterIsInstance<Slide.BigNumber>().first().value
        assertEquals(p.ms / 60_000, minutes)
        assertEquals(merged, HistoryStore.fromJson(HistoryStore.toJson(merged)))
    }

    @Test
    fun newYearAndNoImportWork() {
        val merged = HistoryMerge.combine(base(), listOf(raw("2027-01-02T09:00:00", "Oceano", "Djavan")), zone)!!
        assertEquals(0, merged.periods["2027"]!!.newArtists)
        assertEquals(200_000L, merged.periods["2027"]!!.ms)

        val alone = HistoryMerge.combine(null, listOf(raw("2026-10-12T09:00:00", "Nova", "Artista")), zone)!!
        assertEquals("Nova", alone.all!!.topTracks.first().name)
        assertNull(HistoryMerge.combine(null, emptyList(), zone))
    }

    @Test
    fun logKeepsOnlyNewPlaysAndClearDoesNotBringThemBack() {
        val log = PlayLog(File.createTempFile("coletadas", ".jsonl").apply { delete(); deleteOnExit() })
        val track = Track("id1", "Oceano", listOf("Djavan"), "Álbum", null, 200_000)
        val plays = listOf(RecentPlay(track, at("2026-10-11T09:00:00")), RecentPlay(track, at("2026-10-11T08:00:00")))

        assertEquals(2, log.add(plays))
        assertEquals(0, log.add(plays))
        assertEquals(1, log.add(plays + RecentPlay(track, at("2026-10-11T10:00:00"))))
        assertEquals("spotify:track:id1", log.read().first().trackUri)
        assertEquals(listOf(at("2026-10-11T08:00:00"), at("2026-10-11T09:00:00"), at("2026-10-11T10:00:00")), log.read().map { it.endEpochMs })

        log.clear(nowMs = at("2026-10-12T00:00:00"))
        assertEquals(0, log.add(plays))
        assertNull(HistoryMerge.combine(null, log.read(), zone))
        assertTrue(log.add(listOf(RecentPlay(track, at("2026-10-13T00:00:00")))) == 1)
    }
}
