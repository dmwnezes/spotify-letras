package com.dmwnezes.sintonia.edit

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * Desenha a letra no estilo dos "lyric edits": fundo preto, texto creme com brilho,
 * leve curvatura, rasgos de fita VHS, granulado e um movimento suave.
 * Não depende de Compose: serve para a tela ao vivo e para gerar vídeo.
 */
class EditRenderer(typeface: Typeface, private val density: Float) {

    /** Altura (0–1) onde fica o centro do bloco de letra. No modo gravar ele desce para dar lugar à capa. */
    var centerFraction = 0.42f
    /** Largura máxima do texto (0–1 da tela). */
    var maxWidthFraction = 0.84f
    /** Largura da tradução; sem valor, usa a mesma da letra. */
    var translationWidthFraction: Float? = null

    companion object {
        val BG = Color.rgb(6, 5, 5)
        val CREAM = Color.rgb(242, 226, 202)
        private const val REVEAL_MS = 280f   // tempo para uma palavra acender por completo
        private const val EXIT_MS = 180f     // o bloco some rapidinho antes do próximo
    }

    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        this.typeface = typeface
        fontVariationSettings = "'wght' 800"
        textAlign = Paint.Align.LEFT
        letterSpacing = -0.01f
    }
    private val translationPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        this.typeface = typeface
        fontVariationSettings = "'wght' 600"
    }
    private var translationLayout: StaticLayout? = null
    private var translationFor: String? = null
    private var translationWidth = 0
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val vignette = Paint()
    private var vignetteFor = 0f
    private val path = Path()

    /** Um rasgo de VHS: faixa horizontal deslocada. */
    private data class Tear(val y: Float, val height: Float, val dx: Float, val wave: Boolean)

    private var pages: List<EditPage> = emptyList()

    fun setLines(lines: List<com.dmwnezes.sintonia.lyrics.LyricLine>, translations: List<String?>? = null) {
        pages = EditLayout.pages(lines, translations)
    }

    fun draw(c: Canvas, w: Float, h: Float, posMs: Long, tSec: Float) =
        draw(c, w, h, EditLayout.pageAt(pages, posMs), posMs, tSec)

    fun draw(c: Canvas, w: Float, h: Float, page: EditPage?, posMs: Long, tSec: Float) {
        c.drawColor(BG)
        drawVignette(c, w, h)
        if (page != null) drawPage(c, w, h, page, posMs, tSec)
        drawGrain(c, w, h, tSec)
    }

    private fun drawVignette(c: Canvas, w: Float, h: Float) {
        if (vignetteFor != w * 31 + h) {
            vignette.shader = RadialGradient(w / 2, h * centerFraction, maxOf(w, h) * 0.75f,
                intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(0, 0, 0, 0), Color.argb(170, 0, 0, 0)),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            vignetteFor = w * 31 + h
        }
        c.drawRect(0f, 0f, w, h, vignette)
    }

    private fun drawPage(c: Canvas, w: Float, h: Float, page: EditPage, posMs: Long, t: Float) {
        // Tamanho: grande, mas cada pedaço cabe em ~84% da largura.
        var size = min(w * 0.136f, h * 0.12f) * (maxWidthFraction / 0.84f).coerceAtMost(1f)
        text.textSize = size
        val widest = page.chunks.maxOf { text.measureText(it.text) }
        if (widest > w * maxWidthFraction) size *= (w * maxWidthFraction) / widest
        // Bloco só de backing vocal: menor, como um eco.
        if (page.backingOnly) size *= 0.62f
        text.textSize = size
        text.setShadowLayer(size * 0.30f, 0f, 0f, Color.argb(185, 255, 204, 145))

        val lh = size * 1.06f
        val blockH = lh * page.chunks.size
        val exit = ((page.endMs - posMs) / EXIT_MS).coerceIn(0f, 1f)

        c.save()
        // Movimento suave do bloco inteiro.
        c.translate(sin(t * 0.37f) * w * 0.012f, sin(t * 0.29f + 1f) * h * 0.006f)
        val zoom = 1f + 0.012f * sin(t * 0.5f)
        c.scale(zoom, zoom, w / 2, h * centerFraction)

        val top = h * centerFraction - blockH / 2
        val tear = tearAt(t, page, top, lh, w)
        val baselines = page.chunks.indices.map { top + lh * (it + 0.78f) }

        if (tear == null) {
            drawChunks(c, w, page, baselines, size, posMs, t, exit, waveLine = -1)
        } else {
            // Tudo menos a faixa…
            c.save()
            c.clipOutRect(0f, tear.y, w, tear.y + tear.height)
            drawChunks(c, w, page, baselines, size, posMs, t, exit, waveLine = -1)
            c.restore()
            // …e a faixa deslocada para o lado (ou ondulando).
            c.save()
            c.clipRect(0f, tear.y, w, tear.y + tear.height)
            c.translate(tear.dx, 0f)
            val waveLine = if (tear.wave) baselines.indexOfFirst { abs(it - (tear.y + tear.height / 2)) < lh } else -1
            drawChunks(c, w, page, baselines, size, posMs, t, exit, waveLine)
            c.restore()
            // O risco fino da fita atravessando a tela.
            line.color = Color.argb((190 * exit).toInt(), 242, 226, 202)
            line.strokeWidth = 1.2f * density
            c.drawLine(-w, tear.y, w * 2, tear.y, line)
            line.color = Color.argb((70 * exit).toInt(), 242, 226, 202)
            line.strokeWidth = 0.8f * density
            c.drawLine(-w, tear.y + tear.height, w * 2, tear.y + tear.height, line)
        }
        val backingBottom = if (page.backing.isNotEmpty()) drawBacking(c, w, page.backing, top + blockH + size * 0.22f, size, posMs, exit) else top + blockH
        page.translation?.let { drawTranslation(c, w, h, it, backingBottom + w * 0.035f, page, posMs, exit) }
        c.restore()
        text.textSkewX = 0f
    }

    /** Tradução embaixo de tudo, com tamanho fixo: não acompanha os pedaços grandes da letra nem pisca entre os blocos da linha. */
    private fun drawTranslation(c: Canvas, w: Float, h: Float, translation: String, top: Float, page: EditPage, posMs: Long, exit: Float) {
        val width = (w * (translationWidthFraction ?: maxWidthFraction)).toInt()
        translationPaint.textSize = min(w * 0.048f, h * 0.027f)
        if (translationFor != translation || translationWidth != width) {
            translationLayout = StaticLayout.Builder.obtain(translation, 0, translation.length, translationPaint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines(3)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
            translationFor = translation
            translationWidth = width
        }
        val fadeIn = ((posMs - page.lineStartMs) / REVEAL_MS).coerceIn(0f, 1f)
        val alpha = fadeIn * if (page.lastOfLine) exit else 1f
        translationPaint.color = Color.argb((235 * alpha).toInt(), 242, 226, 202)
        c.save()
        c.translate((w - width) / 2, top)
        translationLayout?.draw(c)
        c.restore()
    }

    /** Backing vocals embaixo do bloco: menores, mais apagados, acendendo palavra por palavra (sem parênteses). Devolve onde terminam. */
    private fun drawBacking(c: Canvas, w: Float, words: List<EditWord>, top: Float, mainSize: Float, posMs: Long, exit: Float): Float {
        val bs = mainSize * 0.42f
        text.textSize = bs
        text.textSkewX = -0.12f
        text.setShadowLayer(bs * 0.25f, 0f, 0f, Color.argb(120, 255, 204, 145))
        val space = text.measureText(" ")
        val maxW = w * maxWidthFraction
        // Quebra em linhas que cabem na largura.
        val rows = mutableListOf<MutableList<EditWord>>(mutableListOf())
        var x = 0f
        for (wd in words) {
            val ww = text.measureText(wd.text)
            if (rows.last().isNotEmpty() && x + ww > maxW) { rows += mutableListOf<EditWord>(); x = 0f }
            rows.last() += wd; x += ww + space
        }
        rows.forEachIndexed { r, row ->
            val rowW = row.sumOf { text.measureText(it.text).toDouble() }.toFloat() + space * (row.size - 1)
            var xx = (w - rowW) / 2
            val y = top + bs * (1.1f + r * 1.15f)
            row.forEach { wd ->
                val p = ((posMs - wd.startMs) / REVEAL_MS).coerceIn(0f, 1f)
                val ease = 1f - (1f - p) * (1f - p)
                val bright = 0.45f + 0.55f * ease
                text.color = Color.argb((255 * (0.35f + 0.5f * ease) * exit).toInt(), (242 * bright).toInt(), (226 * bright).toInt(), (202 * bright).toInt())
                c.drawText(wd.text, xx, y, text)
                xx += text.measureText(wd.text) + space
            }
        }
        return top + bs * (1.1f + (rows.size - 1) * 1.15f + 0.35f)
    }

    private fun drawChunks(
        c: Canvas, w: Float, page: EditPage, baselines: List<Float>, size: Float,
        posMs: Long, t: Float, exit: Float, waveLine: Int,
    ) {
        page.chunks.forEachIndexed { i, chunk ->
            val full = text.measureText(chunk.text)
            val space = text.measureText(" ")
            val x0 = (w - full) / 2
            val y = baselines[i]
            // Curvatura: a linha de cima arqueia mais; respira devagar.
            val bend = size * (if (i == 0) 0.24f else 0.08f) * (0.75f + 0.25f * sin(t * 0.8f + i))
            path.reset()
            val pad = size
            if (i == waveLine) {
                // Ondulação de fita: a linha inteira vira uma onda.
                path.moveTo(x0 - pad, y)
                val steps = 24
                for (s in 1..steps) {
                    val x = x0 - pad + (full + 2 * pad) * s / steps
                    path.lineTo(x, y + sin(s * 0.9f + t * 30f) * size * 0.09f)
                }
            } else {
                path.moveTo(x0 - pad, y + bend * 0.35f)
                path.quadTo(w / 2, y - bend, x0 + full + pad, y + bend * 0.35f)
            }
            var hOffset = pad
            chunk.words.forEach { word ->
                val wWidth = text.measureText(word.text)
                val p = ((posMs - word.startMs) / REVEAL_MS).coerceIn(0f, 1f)
                if (p > 0f) {
                    val ease = 1f - (1f - p) * (1f - p)
                    // Surge apagada e clareia até o creme, subindo um pouquinho.
                    val bright = 0.42f + 0.58f * ease
                    text.color = Color.argb(
                        (255 * ease * exit).toInt(),
                        (242 * bright).toInt(), (226 * bright).toInt(), (202 * bright).toInt(),
                    )
                    c.drawTextOnPath(word.text, path, hOffset, (1f - ease) * size * 0.12f, text)
                }
                hOffset += wWidth + space
            }
        }
    }

    /** Sorteia (de forma estável no tempo) quando acontece um rasgo, onde e para que lado. */
    private fun tearAt(t: Float, page: EditPage, top: Float, lh: Float, w: Float): Tear? {
        val slotLen = 1.6f
        val slot = (t / slotLen).toInt()
        val r = rand(slot * 7919 + 13)
        if (r > 0.55f) return null                                   // nem todo intervalo tem rasgo
        val start = slot * slotLen + rand(slot * 31 + 7) * (slotLen - 0.3f)
        val dur = 0.10f + rand(slot * 17 + 3) * 0.14f
        if (t < start || t > start + dur) return null
        val line = (rand(slot * 101 + 5) * page.chunks.size).toInt().coerceIn(0, page.chunks.size - 1)
        val bandH = lh * (0.22f + rand(slot * 53 + 11) * 0.3f)
        val y = top + lh * (line + 0.15f + rand(slot * 59 + 2) * 0.55f)
        val dir = if (rand(slot * 61 + 9) > 0.5f) 1f else -1f
        val wave = rand(slot * 67 + 1) > 0.7f
        return Tear(y, bandH, dir * w * (0.025f + rand(slot * 71 + 4) * 0.07f), wave)
    }

    private fun drawGrain(c: Canvas, w: Float, h: Float, t: Float) {
        val frame = (t * 24).toInt()
        for (i in 0 until 70) {
            val k = frame * 131 + i * 7
            val a = (8 + rand(k + 3) * 60).toInt()
            dot.color = Color.argb(a, 242, 226, 202)
            c.drawCircle(rand(k) * w, rand(k + 1) * h, (0.4f + rand(k + 2) * 0.9f) * density, dot)
        }
    }

    /** Número pseudoaleatório 0–1 estável para a mesma semente. */
    private fun rand(seed: Int): Float {
        var x = seed * 374761393 + 668265263
        x = (x xor (x ushr 13)) * 1274126177
        x = x xor (x ushr 16)
        return (x and 0xFFFFFF) / 16777216f
    }

    @Suppress("unused") private val tau = (2 * PI).toFloat()
}
