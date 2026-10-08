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
