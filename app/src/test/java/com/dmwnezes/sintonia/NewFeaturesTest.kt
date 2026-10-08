package com.dmwnezes.sintonia

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.dmwnezes.sintonia.history.HistoryAggregator
import com.dmwnezes.sintonia.history.RawStream
import com.dmwnezes.sintonia.notebook.DiaryEntry
import com.dmwnezes.sintonia.notebook.Notebook
import com.dmwnezes.sintonia.notebook.NotebookStore
import com.dmwnezes.sintonia.notebook.Quote
import com.dmwnezes.sintonia.notebook.QuoteImage
import com.dmwnezes.sintonia.notebook.SavedLine
import com.dmwnezes.sintonia.quiz.QuizEngine
import com.dmwnezes.sintonia.wrapped.Slide
import com.dmwnezes.sintonia.wrapped.WrappedBuilder
import com.dmwnezes.sintonia.wrapped.WrappedCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class NewFeaturesTest {
    private val out = File("build/frames").apply { mkdirs() }

    @Test
    fun quizExcerptAvoidsTitleAndFirstLine() {
        val lines = listOf("Primeira linha da canção", "Oceano onde eu vou mergulhar", "Linha neutra número um", "Linha neutra número dois", "Oceano de novo aqui")
        repeat(30) { seed ->
            val ex = QuizEngine.pickExcerpt(lines, "Oceano", Random(seed))
            assertEquals(2, ex.size)
            assertFalse(ex.any { it.contains("Oceano") })
            assertFalse(ex.contains("Primeira linha da canção"))
        }
        assertEquals("ola mundo", QuizEngine.norm("Olá Mundo (Ao Vivo)"))
    }

    @Test
    fun notebookRoundTrip() {
        val n = Notebook(
            lines = listOf(SavedLine("1", "t1", "Música", "Artista", null, "Uma linha", "A line", 1234, 99)),
            diary = listOf(DiaryEntry("2", "t1", "Música", "Artista", "http://x", "Na praia", 5000, 100)),
        )
        assertEquals(n, NotebookStore.fromJson(NotebookStore.toJson(n)))
    }

    private fun stats(): com.dmwnezes.sintonia.history.HistoryStats {
        val zone = ZoneId.of("America/Fortaleza")
        val agg = HistoryAggregator(zone)
        fun add(at: String, t: String, a: String, n: Int) = repeat(n) { i ->
            agg.add(RawStream(LocalDateTime.parse(at).plusDays(i.toLong()).atZone(zone).toInstant().toEpochMilli(), 210_000, t, a, "Álbum", "spotify:track:$t",
                null, null, "android", "BR", "clickrow", "trackdone", false, false, false, false))
        }
        add("2024-03-01T22:00:00", "Oceano", "Djavan", 30)
        add("2024-04-01T21:00:00", "Ainda Bem", "Marisa Monte", 20)
        add("2024-05-01T20:00:00", "Trem-Bala", "Ana Vilela", 12)
        add("2024-06-01T23:00:00", "Coisas Pequenas", "Banda Exemplo", 8)
        add("2023-06-01T10:00:00", "Antiga", "Djavan", 10)
        return agg.build(1)
    }

    @Test
    fun wrappedSlidesAndCard() {
        val slides = WrappedBuilder.build(stats(), "2024")
        assertTrue(slides.first() is Slide.Intro)
        assertTrue(slides.last() is Slide.Summary)
        assertTrue(slides.any { it is Slide.Spotlight && it.name == "Djavan" })
        assertTrue(slides.size >= 7)
        val summary = slides.last() as Slide.Summary
        File(out, "retrospectiva.png").outputStream().use { WrappedCard.render(summary).compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun quoteImage() {
        val cover = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawRect(0f, 0f, 300f, 300f, Paint().apply { shader = LinearGradient(0f, 0f, 300f, 300f, Color.rgb(20, 120, 200), Color.rgb(240, 160, 40), Shader.TileMode.CLAMP) })
        }
        val q = Quote("And I said hey, what's going on, a line long enough to wrap a couple of times", "E eu disse ei, o que está acontecendo", "Canção de Teste", "Artista Exemplo", null)
        File(out, "trecho.png").outputStream().use { QuoteImage.render(q, cover).compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
