package com.dmwnezes.sintonia.edit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.dmwnezes.sintonia.lyrics.LyricLine
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Estilo Cilindro: fundo escuro com a capa bem desfocada e apagada no meio,
 * e a letra enrolada num cilindro — as linhas sobem rolando, ondulam e se curvam nas pontas.
 * Palavras ainda não cantadas ficam cinza; as cantadas, brancas.
 */
class CilindroRenderer(typeface: Typeface, private val density: Float) : StyleRenderer {
    override var centerFraction = 0.46f
    override var maxWidthFraction = 0.88f

    private companion object {
        const val TRANS_MS = 600f
        const val REVEAL_MS = 160f
        val BG = Color.rgb(20, 20, 20)
    }

    private var lines: List<SungLine> = emptyList()
    private var layout: ScrollLayout? = null
    private var layoutKey = ""
    private var size = 0f

    // Dois pesos misturados, como nos edits: umas palavras mais fortes que outras.
    private val bold = textPaint(typeface, 800)
    private val semi = textPaint(typeface, 600)
    private val widths = FloatArray(64)

    private var blurred: Bitmap? = null
    private val coverPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val fade = Paint(Paint.ANTI_ALIAS_FLAG)
    private var fadeKey = ""
    private val rect = RectF()
    private var art: StyleArt? = null

    private fun textPaint(tf: Typeface, wght: Int) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = tf
        fontVariationSettings = "'wght' $wght"
        letterSpacing = 0.005f
        fontFeatureSettings = "'liga' 0, 'clig' 0" // letra por letra: sem ligaduras (fi, fl)
    }

    override fun setLines(lines: List<LyricLine>) {
        this.lines = SungLines.from(lines)
        layoutKey = ""
    }

    override fun setArt(art: StyleArt?) {
        this.art = art
        // Desfoque barato e bonito: reduz a capa a poucos pixels e amplia de novo, duas vezes.
        blurred = art?.cover?.let { src ->
            val tiny = Bitmap.createScaledBitmap(src, 10, 10, true)
            val mid = Bitmap.createScaledBitmap(tiny, 40, 40, true)
            Bitmap.createScaledBitmap(mid, 160, 160, true)
        }
    }

    private fun paintFor(line: SungLine, wi: Int) = if (rnd(line.seed + wi * 31) < 0.6f) bold else semi

    override fun draw(c: Canvas, w: Float, h: Float, posMs: Long, tSec: Float) {
        c.drawColor(BG)
        drawCover(c, w, h, tSec)
        val lay = layoutFor(w, h) ?: return
        val f = lay.focus(posMs, TRANS_MS)
        val cy = h * centerFraction
        val radius = h * 0.24f
        val left = w * (1f - maxWidthFraction) / 2f
        // Na troca de linha o texto incha e ondula mais, depois assenta.
        val pulse = if (f.p < 1f) sin(PI.toFloat() * f.p) else 0f

        for (row in lay.rows) {
            val theta = (row.y - f.y) / radius
            if (abs(theta) > 1.45f) continue
            val depth = cos(theta).coerceAtLeast(0f)
            val line = lay.lines[row.line]
            val isCur = row.line == f.current
            val baseY = cy + radius * sin(theta) + size * 0.35f
            val squash = (0.35f + 0.65f * depth)
            val grow = if (isCur) 1f + 0.07f * pulse else 1f
            val amp = size * (0.05f + 0.13f * pulse + 0.10f * abs(sin(theta)))
            val bend = -sin(theta) * size * 0.9f            // em cima as pontas descem, embaixo sobem
            val phase = tSec * 1.6f + row.line * 0.9f
            val cx = left + row.width / 2

            row.words.forEachIndexed { k, wi ->
                val word = line.words[wi]
                val p = paintFor(line, wi)
                p.textSize = size * grow
                val sung = ((posMs - word.startMs) / REVEAL_MS).coerceIn(0f, 1f)
                val grey = when {
                    isCur -> 150 + (105 * easeOut(sung)).toInt()
                    row.line < f.current -> 150
                    else -> 125
                }
                val alpha = (255 * depth * depth * (if (isCur) 1f else 0.85f)).toInt()
                if (alpha < 4) return@forEachIndexed
                p.color = Color.argb(alpha, grey, grey, grey)
                p.setShadowLayer(size * 0.08f, 0f, size * 0.03f, Color.argb(alpha / 2, 0, 0, 0))

                // Letra por letra, cada uma seguindo a curva.
                val s = word.text
                val n = min(s.length, widths.size)
                for (i in 0 until n) widths[i] = p.measureText(s, i, i + 1)
                var gx = left + (cx - left) * (1 - grow) + row.xs[k] * grow
                for (i in 0 until n) {
                    val cw = widths[i]
                    val mid = gx + cw / 2
                    val u = (mid - cx) / (w * 0.5f)
                    val dy = bend * u * u + amp * sin(u * 5.2f + phase)
                    val slope = bend * 2 * u / (w * 0.5f) + amp * 5.2f / (w * 0.5f) * cos(u * 5.2f + phase)
                    c.save()
                    c.translate(mid, baseY + dy)
                    c.rotate(Math.toDegrees(atan(slope).toDouble()).toFloat())
                    c.scale(1f, squash)
                    c.drawText(s, i, i + 1, -cw / 2, 0f, p)
                    c.restore()
                    gx += cw
                }
            }
        }
    }

    private fun drawCover(c: Canvas, w: Float, h: Float, t: Float) {
        val side = min(w * 0.86f, h * 0.55f) * (1f + 0.025f * sin(t * 0.6f))
        val cx = w / 2; val cy = h * (centerFraction - 0.02f)
        rect.set(cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2)
        val b = blurred
        if (b != null) {
            coverPaint.alpha = 120
            c.drawBitmap(b, null, rect, coverPaint)
        } else {
            val a = art
            coverPaint.shader = null
            coverPaint.color = a?.base ?: Color.rgb(60, 56, 52)
            coverPaint.alpha = 90
            c.drawRoundRect(rect, side * 0.1f, side * 0.1f, coverPaint)
        }
        // Bordas da capa somem no fundo.
        val key = "$w|$h|$side|$centerFraction"
        if (key != fadeKey) {
            fade.shader = RadialGradient(cx, cy, side * 0.64f, intArrayOf(Color.argb(0, 20, 20, 20), Color.argb(60, 20, 20, 20), BG),
                floatArrayOf(0f, 0.45f, 0.9f), Shader.TileMode.CLAMP)
            fadeKey = key
        }
        c.drawRect(rect.left - side * 0.1f, rect.top - side * 0.1f, rect.right + side * 0.1f, rect.bottom + side * 0.1f, fade)
    }

    private fun layoutFor(w: Float, h: Float): ScrollLayout? {
        if (lines.isEmpty()) return null
        val key = "$w|$h|$maxWidthFraction"
        if (key != layoutKey) {
            size = min(w * 0.094f, h * 0.06f) * min(1f, maxWidthFraction / 0.88f)
            bold.textSize = size; semi.textSize = size
            val space = bold.measureText(" ")
            layout = ScrollLayout.build(lines, w * maxWidthFraction, size * 1.05f, size * 0.55f, space) { l, i -> paintFor(l, i).measureText(l.words[i].text) }
            layoutKey = key
        }
        return layout
    }
}
