package com.dmwnezes.sintonia

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import com.dmwnezes.sintonia.edit.EditLayout
import com.dmwnezes.sintonia.edit.EditRenderer
import com.dmwnezes.sintonia.edit.LyricsMode
import com.dmwnezes.sintonia.lyrics.TranslatedLyrics
import com.dmwnezes.sintonia.lyrics.Word
import com.dmwnezes.sintonia.lyrics.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class EditStyleTest {
    // Letra inventada para o teste.
    private val lines = listOf(
        LyricLine(1_000, "você sabe que não é o mesmo de antes"),
        LyricLine(6_000, "como era"),
        LyricLine(9_000, "e eu fiquei esperando a noite inteira por você"),
    )

    @Test
    fun chunksAndPages() {
        val pages = EditLayout.pages(lines)
        val texts = pages.map { p -> p.chunks.map { it.text } }
        println(texts)
        pages.flatMap { it.chunks }.forEach { ch ->
            assertTrue(ch.text, ch.words.size <= 3)
            // nenhum pedaço termina com artigo/preposição solta
            assertTrue(ch.text, ch.words.last().text.lowercase() !in setOf("o", "a", "de", "que", "por"))
        }
        assertTrue(pages.all { it.chunks.size <= 3 })
        assertEquals(listOf("como era"), texts.first { "como era" in it.joinToString(" ") })
        // blocos não se sobrepõem e somem quando a próxima linha demora
        pages.zipWithNext().forEach { (a, b) -> assertTrue(a.endMs <= b.startMs) }
        assertNull(EditLayout.pageAt(pages, 500))
        assertTrue(EditLayout.pageAt(pages, 1_200)!!.chunks.first().text.startsWith("você"))
    }

    @Test
    fun translationStaysWithItsLineAcrossPages() {
        val tr = listOf("You know it's not the same as before", null, "And I waited all night for you")
        val pages = EditLayout.pages(lines, tr)
        val first = pages.filter { it.translation == tr[0] }
        assertTrue(first.size >= 2)
        assertTrue(first.all { it.lineStartMs == first.first().startMs })
        assertEquals(listOf(first.last()), first.filter { it.lastOfLine })
        assertNull(pages.first { "como era" in it.chunks.first().text }.translation)
        assertNull(EditLayout.pages(lines).first().translation)
    }

    @Test
    fun translationOnlyRevealsTranslatedWordsInTheOriginalTime() {
        val real = LyricLine(1000, "you know", words = listOf(Word(1000, "you "), Word(1500, "know ")), backing = "oh")
        val shown = TranslatedLyrics.only(listOf(real, LyricLine(9000, "same"), LyricLine(12000, "again")), listOf("você sabe bem", "", null))
        assertEquals("você sabe bem", shown[0].text)
        assertEquals(listOf("você ", "sabe ", "bem"), shown[0].words!!.map { it.text })
        assertEquals(1000L, shown[0].words!!.first().startMs)
        assertTrue(shown[0].words!!.zipWithNext().all { (a, b) -> a.startMs < b.startMs })
        assertNull(shown[0].backing)
        // Sem tradução, a linha fica como está.
        assertEquals("same", shown[1].text)
        assertEquals("again", shown[2].text)
        val page = EditLayout.pages(shown).first()
        assertTrue(page.chunks.joinToString(" ") { it.text } == "você sabe bem")
    }

    private fun fixedPage(chunks: List<String>) = com.dmwnezes.sintonia.edit.EditPage(
        chunks.map { com.dmwnezes.sintonia.edit.EditChunk(listOf(com.dmwnezes.sintonia.edit.EditWord(it, 0, 100))) },
        0, 100_000, translation = "Eu quero te levar praquele lugar na Roche",
    )

    private fun fixedFrame(page: com.dmwnezes.sintonia.edit.EditPage, t: Float): Bitmap {
        val r = EditRenderer(Typeface.createFromFile(File("src/main/res/font/montserrat.ttf")), 2f)
        return Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888).also { r.draw(Canvas(it), 720f, 1280f, page, 5_000, t) }
    }

    /** Altura da primeira linha de pixels bem claros: o grão é apagado demais para contar. */
    private fun inkTop(b: Bitmap) = (0 until b.height).first { y ->
        (0 until b.width step 2).count { x -> Color.red(b.getPixel(x, y)) > 200 } > 3
    }

    @Test
    fun translationStaysInPlaceWhateverTheNumberOfRows() {
        fun zone(b: Bitmap) = Bitmap.createBitmap(b, 0, 730, b.width, b.height - 730)
        val one = zone(fixedFrame(fixedPage(listOf("eu")), 2.7f))
        val three = zone(fixedFrame(fixedPage(listOf("I wanna take", "you to that", "place")), 2.7f))
        assertTrue(one.sameAs(three))
    }

    @Test
    fun lyricsDoNotDriftOverTimeOrWithRows() {
        val page = fixedPage(listOf("I wanna take", "you to that", "place"))
        val y = inkTop(fixedFrame(page, 0.05f))
        assertEquals(y, inkTop(fixedFrame(page, 3.1f)))
        assertEquals(y, inkTop(fixedFrame(fixedPage(listOf("I wanna take")), 0.05f)))
    }

    @Test
    fun removedStyleFallsBackToEdit() {
        assertEquals(LyricsMode.EDIT, LyricsMode.from("COLAGEM"))
        assertEquals(LyricsMode.CLASSICO, LyricsMode.from("CLASSICO"))
    }

    @Test
    fun translationIsDrawnOnlyWhenPresent() {
        val tf = Typeface.createFromFile(File("src/main/res/font/montserrat.ttf"))
        val out = File("build/frames/edit").apply { mkdirs() }
        fun frame(tr: List<String?>?): Bitmap {
            val r = EditRenderer(tf, 2f)
            r.setLines(lines, tr)
            return Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888).also { r.draw(Canvas(it), 720f, 1280f, 2_700L, 2.7f) }
        }
        val plain = frame(null)
        val translated = frame(listOf("Você sabe que isso não é mais como antes, e nunca mais vai ser como era", null, null))
        File(out, "traducao.png").outputStream().use { translated.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(!plain.sameAs(translated))
    }

    @Test
    fun renderFrames() {
        val out = File("build/frames/edit").apply { mkdirs() }
        val tf = Typeface.createFromFile(File("src/main/res/font/montserrat.ttf"))
        val r = EditRenderer(tf, 2f)
        val pages = EditLayout.pages(lines)
        // quadros ao longo da primeira frase, e um com rasgo de VHS forçado pelo tempo
        val times = listOf(1_250L, 1_900L, 2_700L, 3_600L, 6_400L, 9_900L)
        times.forEachIndexed { i, pos ->
            val bmp = Bitmap.createBitmap(720, 720, Bitmap.Config.ARGB_8888)
            r.draw(Canvas(bmp), 720f, 720f, EditLayout.pageAt(pages, pos), pos, pos / 1000f)
            File(out, "q$i.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        // procura um instante com rasgo para conferir o efeito
        var found = 0
        var t = 1.3f
        while (t < 6f && found < 3) {
            val bmp = Bitmap.createBitmap(720, 720, Bitmap.Config.ARGB_8888)
            val pos = 3_600L
            r.draw(Canvas(bmp), 720f, 720f, EditLayout.pageAt(pages, pos), pos, t)
            // linha de rasgo = muitos pixels claros numa mesma linha de 1px na largura toda
            val hasTear = (0 until 720).any { y -> (0 until 720 step 6).count { x -> Color.red(bmp.getPixel(x, y)) > 120 } > 100 }
            if (hasTear) { File(out, "rasgo$found.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }; found++; t += 0.4f }
            t += 0.02f
        }
        println("rasgos encontrados: $found")
        assertTrue(found > 0)
    }
}
private typealias Color = android.graphics.Color
