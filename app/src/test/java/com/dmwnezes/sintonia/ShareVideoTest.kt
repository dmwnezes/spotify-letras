package com.dmwnezes.sintonia

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.dmwnezes.sintonia.data.Track
import com.dmwnezes.sintonia.lyrics.LyricLine
import com.dmwnezes.sintonia.share.FramePreview
import com.dmwnezes.sintonia.share.ShareRange
import com.dmwnezes.sintonia.share.VideoColors
import com.dmwnezes.sintonia.share.VideoFormat
import com.dmwnezes.sintonia.share.VideoSpec
import org.junit.Assert.assertEquals
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
class ShareVideoTest {

    private val lines = listOf(
        LyricLine(10_000, "Primeira linha de exemplo"),
        LyricLine(14_000, "Uma segunda linha um pouco mais comprida para quebrar em duas"),
        LyricLine(19_000, ""),
        LyricLine(22_000, "Terceira linha"),
        LyricLine(60_000, "Bem depois"),
    )

    @Test
    fun rangeRules() {
        val r = ShareRange.fromLines(lines, 0, 1, 200_000)
        assertEquals(9_600, r.startMs)
        assertEquals(19_000, r.endMs)
        val long = ShareRange.fromLines(lines, 0, 4, 200_000)
        assertTrue(long.clipped)
        assertEquals(30_000, long.durationMs)
        val t = ShareRange.fromTime(195_000, 15_000, 200_000)
        assertEquals(185_000, t.startMs)
        assertEquals(200_000, t.endMs)
    }

    private fun fakeCover(): Bitmap {
        val b = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint().apply { shader = LinearGradient(0f, 0f, 640f, 640f, Color.rgb(230, 90, 60), Color.rgb(40, 60, 160), Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, 640f, 640f, p)
        p.shader = null; p.color = Color.argb(200, 255, 230, 120)
        c.drawCircle(420f, 230f, 120f, p)
        return b
    }

    @Test
    fun rendersFrames() {
        val out = File("build/frames").apply { mkdirs() }
        val track = Track("abc", "Canção de Teste", listOf("Artista Exemplo", "Convidada"), "Álbum", null, 200_000)
        val colors = VideoColors(Color.rgb(20, 12, 30), Color.rgb(70, 30, 60), Color.rgb(230, 90, 60), Color.rgb(120, 140, 255), Color.rgb(250, 200, 90))
        for (f in VideoFormat.entries) {
            for ((name, lyr) in listOf("letra" to lines, "semletra" to emptyList())) {
                val spec = VideoSpec(track, fakeCover(), colors, lyr, 13_000, 25_000, f)
                val bmp = FramePreview.frame(spec, 2_500)
                File(out, "${f.name.lowercase()}-$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
}
