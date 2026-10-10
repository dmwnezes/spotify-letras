package com.dmwnezes.sintonia

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import com.dmwnezes.sintonia.edit.CilindroRenderer
import com.dmwnezes.sintonia.edit.ColagemRenderer
import com.dmwnezes.sintonia.edit.StyleArt
import com.dmwnezes.sintonia.edit.StyleRenderer
import com.dmwnezes.sintonia.edit.VidroRenderer
import com.dmwnezes.sintonia.lyrics.LyricLine
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Gera quadros dos estilos Vidro, Cilindro e Colagem para conferir o visual. Letra e capa inventadas. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LyricStylesTest {
    private val lines = listOf(
        LyricLine(1_000, "você sabe que não é o mesmo de antes"),
        LyricLine(4_600, "a cidade acorda devagar"),
        LyricLine(7_800, "e eu fiquei esperando a noite inteira"),
        LyricLine(11_400, "guarda esse momento"),
        LyricLine(14_000, "porque amanhã tudo muda"),
    )

    private fun font(name: String) = Typeface.createFromFile(File("src/main/res/font/$name.ttf"))

    /** Capa abstrata (pôr do sol em gradiente) só para o teste. */
    private fun fakeCover(): Bitmap {
        val b = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, 300f, Color.rgb(236, 120, 90), Color.rgb(70, 40, 120), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 300f, 300f, p); p.shader = null
        p.color = Color.rgb(255, 222, 170); c.drawCircle(190f, 130f, 46f, p)
        p.color = Color.rgb(36, 30, 70); c.drawRect(0f, 210f, 300f, 300f, p)
        return b
    }

    private val art = StyleArt(fakeCover(), Color.rgb(30, 12, 50), Color.rgb(90, 40, 110), Color.rgb(220, 70, 120), Color.rgb(120, 90, 240), Color.rgb(240, 120, 80))

    private fun render(name: String, r: StyleRenderer, times: List<Long>, w: Int = 540, h: Int = 960) {
        val out = File("build/frames/styles").apply { mkdirs() }
        r.setLines(lines); r.setArt(art)
        times.forEachIndexed { i, pos ->
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            r.draw(Canvas(bmp), w.toFloat(), h.toFloat(), pos, pos / 1000f)
            File(out, "$name-$i.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            // Algo foi desenhado além do fundo.
            val distinct = HashSet<Int>(); for (y in 0 until h step 24) for (x in 0 until w step 24) distinct += bmp.getPixel(x, y)
            assertTrue("$name-$i vazio", distinct.size > 20)
        }
    }

    private val times = listOf(1_150L, 2_200L, 3_400L, 4_750L, 5_300L, 8_400L, 9_600L, 11_550L, 12_300L, 14_400L)

    @Test fun vidro() = render("vidro", VidroRenderer(font("inter"), 1.5f), times)
    @Test fun cilindro() = render("cilindro", CilindroRenderer(font("montserrat"), 1.5f), times)
    @Test fun colagemLooks() {
        for (k in 0 until 6) {
            val r = ColagemRenderer(font("archivo"), font("playfair"), font("montserrat"), 1.5f).apply { forcedLook = k }
            render("look$k", r, listOf(1_100L, 1_900L, 4_900L))
        }
    }

    @Test fun colagem() = render("colagem", ColagemRenderer(font("archivo"), font("playfair"), font("montserrat"), 1.5f),
        listOf(1_080L, 1_300L, 1_900L, 2_600L, 3_300L, 4_700L, 5_600L, 7_900L, 8_700L, 9_800L, 11_500L, 12_500L, 14_100L, 15_000L))
}
