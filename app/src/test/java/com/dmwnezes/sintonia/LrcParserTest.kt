package com.dmwnezes.sintonia

import com.dmwnezes.sintonia.lyrics.LrcLibClient
import com.dmwnezes.sintonia.lyrics.LrcParser
import org.junit.Assert.assertEquals
import org.junit.Test

class LrcParserTest {

    @Test
    fun parsesTimestampsAndText() {
        val lines = LrcParser.parse(
            """
            [ar:Artista]
            [00:01.50] Primeira linha
            [00:04.2]Segunda
            [01:02.345] Terceira
            [01:10.00]
            """.trimIndent()
        )
        assertEquals(listOf(1500L, 4200L, 62345L, 70000L), lines.map { it.timeMs })
        assertEquals("Primeira linha", lines[0].text)
        assertEquals("", lines[3].text)
    }

    @Test
    fun repeatedTimestampsAndOffset() {
        val lines = LrcParser.parse("[offset:+500]\n[00:10.00][00:20.00] Refrão")
        assertEquals(listOf(9500L, 19500L), lines.map { it.timeMs })
        assertEquals("Refrão", lines[1].text)
    }

    @Test
    fun indexAtFindsCurrentLine() {
        val lines = LrcParser.parse("[00:01.00]a\n[00:05.00]b\n[00:09.00]c")
        assertEquals(-1, LrcParser.indexAt(lines, 500))
        assertEquals(0, LrcParser.indexAt(lines, 1000))
        assertEquals(1, LrcParser.indexAt(lines, 8999))
        assertEquals(2, LrcParser.indexAt(lines, 600000))
    }

    @Test
    fun cleansTitles() {
        assertEquals("Oceano", LrcLibClient.cleanTitle("Oceano - Remastered 2011"))
        assertEquals("Sozinho", LrcLibClient.cleanTitle("Sozinho (feat. Fulano)"))
        assertEquals("Garota", LrcLibClient.cleanTitle("Garota"))
    }
}

class WordTimingTest {
    @org.junit.Test
    fun parsesEnhancedLrc() {
        val l = com.dmwnezes.sintonia.lyrics.LrcParser.parse("[00:10.00] <00:10.00>Olá <00:10.50>meu <00:11.20>mundo")
        org.junit.Assert.assertEquals("Olá meu mundo", l[0].text)
        org.junit.Assert.assertEquals(listOf(10_000L, 10_500L, 11_200L), l[0].words!!.map { it.startMs })
    }

    @org.junit.Test
    fun estimatesWords() {
        val line = com.dmwnezes.sintonia.lyrics.LyricLine(1000, "eu vou cantar devagar")
        val sp = com.dmwnezes.sintonia.lyrics.WordTiming.spans(line, 5000)
        org.junit.Assert.assertEquals(4, sp.size)
        org.junit.Assert.assertEquals(1000L, sp.first().startMs)
        org.junit.Assert.assertTrue(sp.last().endMs <= 1000 + (4000 * 0.85).toLong() + 4)
        org.junit.Assert.assertTrue((sp[2].endMs - sp[2].startMs) > (sp[0].endMs - sp[0].startMs)) // palavra maior, mais tempo
        val p = com.dmwnezes.sintonia.lyrics.WordTiming.progress(sp, sp[1].startMs + 1)
        org.junit.Assert.assertEquals(1f, p[0], 0f)
        org.junit.Assert.assertEquals(0f, p[3], 0f)
    }
}
