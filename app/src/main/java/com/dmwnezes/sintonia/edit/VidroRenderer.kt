package com.dmwnezes.sintonia.edit

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.ColorUtils
import com.dmwnezes.sintonia.lyrics.LyricLine
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Estilo Vidro: fundo de gradiente com as cores da capa e a letra presa num cilindro 3D.
 * A linha cantada fica nítida na frente; as outras giram para longe, menores e desfocadas.
 * As palavras clareiam conforme são cantadas.
 */
class VidroRenderer(typeface: Typeface, private val density: Float) : StyleRenderer {
    override var centerFraction = 0.46f
    override var maxWidthFraction = 0.86f

    private companion object {
        const val TRANS_MS = 520f
        const val REVEAL_MS = 220f
    }

    private var lines: List<SungLine> = emptyList()
    private var layout: ScrollLayout? = null
    private var layoutKey = ""
    private var size = 0f

    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        this.typeface = typeface
        fontVariationSettings = "'wght' 720"
        letterSpacing = -0.03f
    }
    private val rowMatrix = Matrix()
    private val matrix = Matrix()

    // Fundo: manchas de cor que passeiam devagar.
    private val blob = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shade = Paint()
    private var blobs: List<RadialGradient> = emptyList()
    private var blobsKey = ""
    private var art: StyleArt? = null
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun setLines(lines: List<LyricLine>) {
        this.lines = SungLines.from(lines)
        layoutKey = ""
    }

    override fun setArt(art: StyleArt?) {
        this.art = art
        blobsKey = ""
    }

    override fun draw(c: Canvas, w: Float, h: Float, posMs: Long, tSec: Float) {
        drawBackground(c, w, h, tSec)
        val lay = layoutFor(w, h) ?: return
        val f = lay.focus(posMs, TRANS_MS)
        val cy = h * centerFraction
        val radius = h * 0.40f
        val left = w * (1f - maxWidthFraction) / 2f
        // O cilindro oscila um pouco de lado: a ponta direita foge para o fundo.
        val yaw = Math.toRadians((-15f + 5f * sin(tSec * 0.33f)).toDouble()).toFloat()
        val motion = if (f.p < 1f) (1f - f.p) * (1f - f.p) * size * 0.18f else 0f

        for (row in lay.rows) {
            val d = row.y - f.y
            val theta = d / radius
            if (abs(theta) > 1.25f) continue
            val depth = cos(theta).coerceAtLeast(0f)
            val line = lay.lines[row.line]
            val baseY = cy + radius * sin(theta) + size * 0.36f
            val pivotY = baseY - size * 0.36f

            rowMatrix.setPerspective(theta * 0.8f, yaw, w * 1.25f, left, pivotY)

            c.save()
            c.concat(rowMatrix)
            text.textSkewX = if (rnd(line.seed) < 0.3f) -0.2f else 0f
            val isCur = row.line == f.current
            val passed = row.line < f.current
            row.words.forEachIndexed { k, wi ->
                val word = line.words[wi]
                val x = left + row.xs[k]
                // Desfoque: mais longe do centro (em cima/embaixo e para a direita) = mais desfocado.
                val far = (row.xs[k] / (w * maxWidthFraction)).coerceIn(0f, 1f)
                var blur = abs(theta) * size * 0.11f + far * size * 0.025f
                var alpha: Float
                if (isCur) {
                    val p = ((posMs - word.startMs) / REVEAL_MS).coerceIn(0f, 1f)
                    alpha = 0.5f + 0.5f * easeOut(p)
                    blur += (1f - easeOut(p)) * size * 0.05f
                } else {
                    alpha = if (passed) 0.62f else 0.48f
                }
                alpha *= depth * depth
                if (alpha < 0.02f) return@forEachIndexed
                drawSoft(c, word.text, x, baseY, blur, motion * depth, alpha, sharp = isCur && blur < size * 0.02f)
            }
            c.restore()
        }
    }

    /** Desenha o texto nítido ou "desfocado" (várias cópias levemente deslocadas, sem precisar de efeitos do sistema). */
    private fun drawSoft(c: Canvas, s: String, x: Float, y: Float, blur: Float, motionY: Float, alpha: Float, sharp: Boolean) {
        if (blur < 0.6f * density && motionY < 0.6f * density) {
            text.color = Color.argb((255 * alpha).toInt(), 255, 255, 255)
            text.setShadowLayer(size * 0.10f, 0f, size * 0.035f, Color.argb((80 * alpha).toInt(), 20, 0, 30))
            c.drawText(s, x, y, text)
            text.clearShadowLayer()
            return
        }
        val n = 10
        text.color = Color.argb((255 * alpha * 0.22f).toInt().coerceAtLeast(1), 255, 255, 255)
        for (i in 0 until n) {
            val a = i * (2 * Math.PI / n) + 0.3
            val r = if (i % 2 == 0) 1f else 0.45f
            c.drawText(s, x + (cos(a) * blur * r).toFloat(), y + (sin(a) * blur * r).toFloat() + motionY * ((i - n / 2f) / n), text)
        }
        if (sharp) {
            text.color = Color.argb((255 * alpha * 0.6f).toInt(), 255, 255, 255)
            c.drawText(s, x, y, text)
        }
    }

    private fun layoutFor(w: Float, h: Float): ScrollLayout? {
        if (lines.isEmpty()) return null
        val key = "$w|$h|$maxWidthFraction"
        if (key != layoutKey) {
            size = min(w * 0.118f, h * 0.074f) * min(1f, maxWidthFraction / 0.86f)
            text.textSize = size
            val space = text.measureText(" ")
            layout = ScrollLayout.build(lines, w * maxWidthFraction, size * 1.08f, size * 0.42f, space) { l, i -> text.measureText(l.words[i].text) }
            layoutKey = key
        }
        return layout
    }

    private fun drawBackground(c: Canvas, w: Float, h: Float, t: Float) {
        val a = art
        val deep = a?.deep ?: Color.rgb(28, 14, 44)
        val cols = if (a != null) listOf(a.glow1, a.glow2, lift(a.base), a.glow3)
        else listOf(Color.rgb(196, 60, 110), Color.rgb(96, 70, 220), Color.rgb(70, 30, 110), Color.rgb(220, 90, 70))
        val key = "$w|$h|${cols.joinToString()}"
        if (key != blobsKey) {
            val r = max(w, h) * 0.62f
            blobs = cols.map { col ->
                RadialGradient(0f, 0f, r, intArrayOf(ColorUtils.setAlphaComponent(col, 235), ColorUtils.setAlphaComponent(col, 90), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            }
            shade.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(Color.argb(40, 0, 0, 0), Color.argb(10, 0, 0, 0), Color.argb(110, 0, 0, 0)), null, Shader.TileMode.CLAMP)
            blobsKey = key
        }
        c.drawColor(ColorUtils.blendARGB(deep, cols[2], 0.35f))
        blobs.forEachIndexed { i, g ->
            val px = w * (0.5f + 0.42f * sin(t * (0.07f + i * 0.023f) + i * 1.7f))
            val py = h * (0.5f + 0.40f * cos(t * (0.05f + i * 0.019f) + i * 2.3f))
            matrix.setTranslate(px, py)
            g.setLocalMatrix(matrix)
            blob.shader = g
            c.drawRect(0f, 0f, w, h, blob)
        }
        c.drawRect(0f, 0f, w, h, shade)
        // Granulado bem fino, como num vídeo.
        val frame = (t * 24).toInt()
        for (i in 0 until 90) {
            val k = frame * 131 + i * 7
            dot.color = Color.argb((6 + rnd(k + 3) * 22).toInt(), 255, 255, 255)
            c.drawCircle(rnd(k) * w, rnd(k + 1) * h, (0.5f + rnd(k + 2)) * density, dot)
        }
    }

    private fun lift(c: Int): Int = ColorUtils.blendARGB(c, Color.WHITE, 0.12f)
}
