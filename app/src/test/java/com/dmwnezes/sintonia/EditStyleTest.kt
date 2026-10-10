package com.dmwnezes.sintonia

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import com.dmwnezes.sintonia.edit.EditLayout
import com.dmwnezes.sintonia.edit.EditRenderer
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
