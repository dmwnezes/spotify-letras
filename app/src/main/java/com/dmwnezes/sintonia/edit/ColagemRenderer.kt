package com.dmwnezes.sintonia.edit

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.dmwnezes.sintonia.lyrics.LyricLine
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Estilo Colagem: tipografia cinética. Uma ou duas palavras por vez, gigantes, em caixa alta,
 * e cada trecho ganha um tratamento diferente — bloco com aberração cromática, pincel, letras recortadas,
 * contorno feito à mão, palavra deitada no chão, letra riscada. Entra com zoom e borrão de movimento,
 * com granulado de filme, vinheta e vazamentos de luz.
 */
class ColagemRenderer(
    archivo: Typeface,
    private val playfair: Typeface,
    private val montserrat: Typeface,
    private val density: Float,
) : StyleRenderer {
    override var centerFraction = 0.46f
    override var maxWidthFraction = 0.86f

    private enum class Look { BLOCO, PINCEL, RECORTE, CONTORNO, CHAO, RISCADO }

    private class Beat(val words: List<EditWord>, val startMs: Long, val endMs: Long, val look: Look, val seed: Int)

    private companion object {
        const val ENTER_MS = 230f
        const val EXIT_MS = 110f
        const val POP_MS = 170f
        const val HOLD_MS = 1500L
        val CREAM = Color.rgb(243, 233, 218)
        val RED = Color.rgb(226, 70, 43)
        val ORANGE = Color.rgb(242, 122, 36)
        val INK = Color.rgb(43, 37, 34)
        val MAROON = Color.rgb(36, 12, 10)
    }

    private var beats: List<Beat> = emptyList()

    /** Só para testes: força um tratamento (índice de Look). */
    internal var forcedLook: Int = -1

    private fun archivoPaint(tf: Typeface, wdth: Int, wght: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = tf
        fontVariationSettings = "'wdth' $wdth, 'wght' $wght"
        textAlign = Paint.Align.CENTER
    }

    private val bloco = archivoPaint(archivo, 72, 800)
    private val pincel = archivoPaint(archivo, 62, 900)
    private val contorno = archivoPaint(archivo, 82, 800).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val chao = archivoPaint(archivo, 100, 900)
    private val chaoTop = archivoPaint(archivo, 85, 800)
    private val riscado = archivoPaint(archivo, 66, 900)
    private val cutFonts = listOf(
        archivoPaint(archivo, 110, 900),
        Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = playfair; fontVariationSettings = "'wght' 900"; textAlign = Paint.Align.CENTER },
        Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = montserrat; fontVariationSettings = "'wght' 800"; textAlign = Paint.Align.CENTER },
        archivoPaint(archivo, 62, 800),
    )
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val matrix = Matrix()

    override fun setLines(lines: List<LyricLine>) {
        val sung = SungLines.from(lines)
        val out = mutableListOf<Beat>()
        var prev: Look? = null
        sung.forEachIndexed { li, line ->
            val words = line.words.map { EditWord(it.text, it.startMs, it.endMs) }
            val chunks = EditLayout.chunk(words, maxChars = 11, maxWords = 2)
            val nextLine = sung.getOrNull(li + 1)?.startMs
            chunks.forEachIndexed { ci, ch ->
                val start = ch.words.first().startMs
                val end = chunks.getOrNull(ci + 1)?.words?.first()?.startMs
                    ?: min(nextLine ?: Long.MAX_VALUE, ch.words.last().endMs + HOLD_MS)
                val seed = line.seed * 31 + ci * 977
                val letters = ch.text.count { it.isLetterOrDigit() }
                var look: Look
                var k = 0
                do {
                    look = Look.entries[(rnd(seed + k * 13) * Look.entries.size).toInt().coerceAtMost(Look.entries.size - 1)]
                    k++
                } while ((look == prev || (look == Look.RECORTE && letters > 9)) && k < 12)
                if (forcedLook >= 0) look = Look.entries[forcedLook % Look.entries.size]
                prev = look
                out += Beat(ch.words, start, end, look, seed)
            }
        }
        beats = out.mapIndexed { i, b -> if (i + 1 < out.size && b.endMs > out[i + 1].startMs) Beat(b.words, b.startMs, out[i + 1].startMs, b.look, b.seed) else b }
    }

    private fun beatAt(pos: Long): Beat? {
        var lo = 0; var hi = beats.size - 1; var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (beats[mid].startMs <= pos) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        return beats.getOrNull(ans)?.takeIf { pos < it.endMs }
    }

    override fun draw(c: Canvas, w: Float, h: Float, posMs: Long, tSec: Float) {
        val beat = beatAt(posMs)
        val look = beat?.look ?: Look.BLOCO
        drawBackground(c, w, h, look, beat?.seed ?: 0, tSec, empty = beat == null)
        if (beat != null) {
            val lt = (posMs - beat.startMs).toFloat()
            val e = easeOut(lt / ENTER_MS)
            val exit = ((beat.endMs - posMs) / EXIT_MS).coerceIn(0f, 1f)
            // Câmera na mão: tremidinha constante e um tranco na entrada.
            val frame = (tSec * 24).toInt()
            val shake = w * (0.0016f + 0.008f * (1 - e))
            c.save()
            c.translate((rnd(frame * 7 + 1) - 0.5f) * 2 * shake, (rnd(frame * 7 + 2) - 0.5f) * 2 * shake)
            val slip = lt < 90f && rnd(beat.seed + 5) < 0.6f
            if (slip) {
                // Quadro "escorregando": uma faixa da imagem deslocada no corte.
                val y0 = h * (0.3f + rnd(beat.seed + 6) * 0.35f); val bh = h * 0.07f
                c.save(); c.clipOutRect(0f, y0, w, y0 + bh); drawBeat(c, w, h, beat, posMs, lt, e, exit); c.restore()
                c.save(); c.clipRect(0f, y0, w, y0 + bh); c.translate(w * 0.05f, 0f); drawBeat(c, w, h, beat, posMs, lt, e, exit); c.restore()
            } else drawBeat(c, w, h, beat, posMs, lt, e, exit)
            c.restore()
        }
        drawFilm(c, w, h, tSec, look)
    }

    /** Desenha o trecho com entrada em zoom e borrão de movimento. */
    private fun drawBeat(c: Canvas, w: Float, h: Float, b: Beat, pos: Long, lt: Float, e: Float, exit: Float) {
        val cx = w / 2; val cy = h * centerFraction
        val push = 1f + 0.06f * min(1f, lt / 1800f)
        val alpha = exit
        if (b.look == Look.CHAO) {
            drawChao(c, w, h, b, pos, e, alpha); return
        }
        // Rastro do zoom: cópias maiores e transparentes.
        if (e < 1f) {
            for (k in 3 downTo 1) {
                val s = (1.6f - 0.6f * e) * (1f + 0.09f * k)
                c.save(); c.scale(s * push, s * push, cx, cy)
                drawLook(c, w, h, b, pos, alpha * 0.16f * (1 - e), ghost = true)
                c.restore()
            }
        }
        val s = (1.6f - 0.6f * e) * push * (1f + 0.06f * (1 - exit))
        c.save(); c.scale(s, s, cx, cy)
        if (b.look == Look.PINCEL) c.rotate(-4f + 2f * rnd(b.seed), cx, cy)
        drawLook(c, w, h, b, pos, alpha, ghost = false, aberr = (1 - e))
        c.restore()
    }

    private fun upper(s: String) = s.uppercase()

    /** Tamanho para as palavras empilhadas caberem na largura e numa altura razoável. */
    private fun fit(p: Paint, words: List<String>, w: Float, h: Float, lineK: Float = 0.9f): Float {
        p.textSize = 100f
        val widest = words.maxOf { p.measureText(it) }.coerceAtLeast(1f)
        val byW = 100f * w * maxWidthFraction / widest
        val byH = h * 0.40f / (words.size * lineK)
        return min(byW, byH)
    }

    /** Palavras visíveis do trecho e quanto cada uma "estourou" na tela (0–1). */
    private fun pops(b: Beat, pos: Long) = b.words.mapIndexed { i, wd -> if (i == 0) 1f else ((pos - wd.startMs) / POP_MS).coerceIn(0f, 1f) }

    private fun drawLook(c: Canvas, w: Float, h: Float, b: Beat, pos: Long, alpha: Float, ghost: Boolean, aberr: Float = 0f) {
        val words = b.words.map { upper(it.text) }
        val pop = pops(b, pos)
        val cx = w / 2; val cy = h * centerFraction
        when (b.look) {
            Look.RECORTE -> { drawRecorte(c, w, h, b, words, pop, alpha, ghost); return }
            else -> {}
        }
        val p = when (b.look) {
            Look.BLOCO -> bloco; Look.PINCEL -> pincel; Look.CONTORNO -> contorno; Look.RISCADO -> riscado; else -> bloco
        }
        val size = fit(p, words, w, h)
        p.textSize = size
        val lh = size * 0.88f
        val top = cy - lh * words.size / 2
        words.forEachIndexed { i, word ->
            val pp = pop[i]
            if (pp <= 0f) return@forEachIndexed
            val y = top + lh * i + size * 0.80f
            c.save()
            val sc = 1f + 0.35f * (1 - easeOut(pp))
            c.scale(sc, sc, cx, y - size * 0.35f)
            val a = alpha * easeOut(pp)
            when (b.look) {
                Look.BLOCO -> {
                    val ca = size * (0.035f + 0.05f * aberr)
                    text(c, word, cx + ca, y + ca * 0.5f, p, RED, a * 0.9f)
                    text(c, word, cx - ca * 0.7f, y, p, Color.rgb(60, 90, 255), a * 0.45f)
                    if (!ghost) p.setShadowLayer(size * 0.12f, 0f, size * 0.05f, Color.argb((150 * a).toInt(), 0, 0, 0))
                    text(c, word, cx, y, p, CREAM, a)
                    p.clearShadowLayer()
                }
                Look.PINCEL -> {
                    // Contorno tremido feito em três passadas, depois o laranja "pincelado".
                    p.style = Paint.Style.STROKE; p.strokeWidth = size * 0.07f; p.strokeJoin = Paint.Join.ROUND
                    for (k in 0 until 3) text(c, word, cx + (rnd(b.seed + k * 3 + i) - 0.5f) * size * 0.04f, y + (rnd(b.seed + k * 5 + i) - 0.5f) * size * 0.04f, p, Color.rgb(42, 14, 10), a)
                    p.style = Paint.Style.FILL
                    text(c, word, cx, y, p, ORANGE, a)
                    text(c, word, cx - size * 0.012f, y - size * 0.014f, p, Color.rgb(255, 170, 80), a * 0.55f)
                    // Riscos de cerdas atravessando a letra.
                    if (!ghost) brushStreaks(c, cx, y, p.measureText(word), size, b.seed + i, a)
                }
                Look.CONTORNO -> {
                    p.strokeWidth = size * 0.034f
                    for (k in 0 until 2) text(c, word, cx + (rnd(b.seed + k * 7 + i) - 0.5f) * size * 0.03f, y + (rnd(b.seed + k * 11 + i) - 0.5f) * size * 0.03f, p, INK, a * (if (k == 0) 1f else 0.7f))
                }
                Look.RISCADO -> {
                    p.style = Paint.Style.FILL
                    text(c, word, cx + size * 0.05f, y + size * 0.05f, p, Color.rgb(90, 20, 12), a)
                    text(c, word, cx, y, p, RED, a)
                    p.style = Paint.Style.STROKE; p.strokeWidth = size * 0.014f
                    text(c, word, cx - size * 0.018f, y - size * 0.018f, p, CREAM, a * 0.9f)
                    p.strokeWidth = size * 0.028f
                    text(c, word, cx, y, p, Color.rgb(58, 15, 10), a)
                    p.style = Paint.Style.FILL
                }
                else -> {}
            }
            c.restore()
        }
        if (b.look == Look.CONTORNO && !ghost) contornoExtras(c, words, pop, p, size, lh, top, cx, b.seed, alpha)
    }

    private fun text(c: Canvas, s: String, x: Float, y: Float, p: Paint, color: Int, a: Float) {
        p.color = color
        p.alpha = (255 * a).toInt().coerceIn(0, 255)
        c.drawText(s, x, y, p)
    }

    private fun brushStreaks(c: Canvas, cx: Float, y: Float, tw: Float, size: Float, seed: Int, a: Float) {
        stroke.strokeWidth = size * 0.012f
        for (k in 0 until 6) {
            val yy = y - size * (0.1f + rnd(seed * 3 + k) * 0.6f)
            val x0 = cx - tw / 2 + rnd(seed * 5 + k) * tw * 0.6f
            stroke.color = Color.argb((70 * a).toInt(), 40, 10, 6)
            c.drawLine(x0, yy, x0 + tw * (0.15f + rnd(seed * 7 + k) * 0.3f), yy + size * 0.01f, stroke)
        }
    }

    /** Bilhete amarelo com fita e um retângulo rabiscado em volta da última palavra. */
    private fun contornoExtras(c: Canvas, words: List<String>, pop: List<Float>, p: Paint, size: Float, lh: Float, top: Float, cx: Float, seed: Int, a: Float) {
        val tw = p.measureText(words[0])
        val nx = cx - tw / 2 - size * 0.25f; val ny = top - size * 0.05f
        c.save(); c.rotate(-8f + 6f * rnd(seed + 2), nx, ny)
        fill.color = Color.argb((60 * a).toInt(), 0, 0, 0); c.drawRect(nx - size * 0.2f + size * 0.04f, ny - size * 0.2f + size * 0.05f, nx + size * 0.2f + size * 0.04f, ny + size * 0.2f + size * 0.05f, fill)
        fill.color = Color.argb((255 * a).toInt(), 246, 210, 74); c.drawRect(nx - size * 0.2f, ny - size * 0.2f, nx + size * 0.2f, ny + size * 0.2f, fill)
        fill.color = Color.argb((140 * a).toInt(), 235, 225, 200); c.drawRect(nx - size * 0.12f, ny - size * 0.26f, nx + size * 0.12f, ny - size * 0.14f, fill)
        c.restore()
        val last = words.lastIndex
        if (pop[last] <= 0f) return
        val lw = p.measureText(words[last])
        val y = top + lh * last + size * 0.80f
        rect.set(cx - lw / 2 - size * 0.14f, y - size * 0.86f, cx + lw / 2 + size * 0.14f, y + size * 0.14f)
        stroke.strokeWidth = size * 0.018f
        stroke.color = Color.argb((220 * a * pop[last]).toInt(), 43, 37, 34)
        for (k in 0 until 2) {
            val j = size * 0.04f
            path.reset()
            path.moveTo(rect.left + (rnd(seed + k) - 0.5f) * j, rect.top)
            path.lineTo(rect.right + (rnd(seed + k + 9) - 0.5f) * j, rect.top + (rnd(seed + k + 3) - 0.5f) * j)
            path.lineTo(rect.right + (rnd(seed + k + 4) - 0.5f) * j, rect.bottom)
            path.lineTo(rect.left + (rnd(seed + k + 5) - 0.5f) * j, rect.bottom + (rnd(seed + k + 6) - 0.5f) * j)
            path.lineTo(rect.left + (rnd(seed + k + 7) - 0.5f) * j, rect.top - j)
            c.drawPath(path, stroke)
        }
    }

    /** Letras recortadas de revista, cada uma num papel de cor e inclinação diferentes. */
    private fun drawRecorte(c: Canvas, w: Float, h: Float, b: Beat, words: List<String>, pop: List<Float>, alpha: Float, ghost: Boolean) {
        val cx = w / 2; val cy = h * centerFraction
        val cards = intArrayOf(CREAM, Color.rgb(242, 161, 196), Color.rgb(20, 18, 18), Color.WHITE, Color.rgb(244, 211, 94), Color.rgb(226, 70, 43))
        // Mede com tamanho 100 e escala para caber.
        val rowsW = words.map { word -> word.indices.sumOf { i -> (cardW(word[i].toString(), b.seed + i, 100f)).toDouble() }.toFloat() }
        val size = min(100f * w * maxWidthFraction / rowsW.max().coerceAtLeast(1f), h * 0.36f / (words.size * 1.1f))
        val lh = size * 1.18f
        val top = cy - lh * words.size / 2
        words.forEachIndexed { r, word ->
            if (pop[r] <= 0f) return@forEachIndexed
            var x = cx - rowsW[r] * size / 100f / 2
            val y = top + lh * r + size * 0.85f
            word.forEachIndexed { i, ch ->
                val s = ch.toString()
                val seed = b.seed + r * 101 + i
                val cw = cardW(s, seed, size)
                val p = cutFonts[(rnd(seed) * cutFonts.size).toInt().coerceAtMost(cutFonts.size - 1)]
                p.textSize = size * (0.92f + rnd(seed + 1) * 0.2f)
                val card = cards[(rnd(seed + 2) * cards.size).toInt().coerceAtMost(cards.size - 1)]
                val ink = if (Color.luminance(card) > 0.45f) Color.rgb(20, 16, 16) else CREAM
                val ccx = x + cw / 2; val ccy = y - size * 0.33f + (rnd(seed + 3) - 0.5f) * size * 0.14f
                val a = alpha * easeOut(pop[r])
                c.save(); c.rotate((rnd(seed + 4) - 0.5f) * 16f, ccx, ccy)
                if (ch != ' ') {
                    if (!ghost) { fill.color = Color.argb((90 * a).toInt(), 0, 0, 0); c.drawRect(ccx - cw / 2 + size * 0.03f, ccy - size * 0.55f + size * 0.04f, ccx + cw / 2 + size * 0.03f, ccy + size * 0.5f + size * 0.04f, fill) }
                    fill.color = card; fill.alpha = (255 * a).toInt(); c.drawRect(ccx - cw / 2, ccy - size * 0.55f, ccx + cw / 2, ccy + size * 0.5f, fill)
                    p.color = ink; p.alpha = (255 * a).toInt()
                    c.drawText(s, ccx, ccy + size * 0.33f, p)
                }
                c.restore()
                x += cw
            }
        }
        if (!ghost) {
            // Setinha desenhada à mão apontando para a palavra.
            val ax = cx + w * maxWidthFraction * 0.36f; val ay = top + lh * words.size + size * 0.25f
            stroke.color = Color.argb((230 * alpha).toInt(), 243, 233, 218); stroke.strokeWidth = size * 0.035f
            path.reset(); path.moveTo(ax + size * 0.35f, ay + size * 0.5f)
            path.cubicTo(ax + size * 0.9f, ay + size * 0.1f, ax + size * 0.1f, ay - size * 0.3f, ax - size * 0.2f, ay + size * 0.05f)
            path.cubicTo(ax - size * 0.4f, ay + size * 0.3f, ax - size * 0.1f, ay + size * 0.45f, ax - size * 0.05f, ay - size * 0.25f)
            c.drawPath(path, stroke)
            path.reset(); path.moveTo(ax - size * 0.2f, ay - size * 0.12f); path.lineTo(ax - size * 0.05f, ay - size * 0.28f); path.lineTo(ax + size * 0.12f, ay - size * 0.1f)
            c.drawPath(path, stroke)
        }
    }

    private fun cardW(s: String, seed: Int, size: Float): Float {
        if (s == " ") return size * 0.35f
        val p = cutFonts[(rnd(seed) * cutFonts.size).toInt().coerceAtMost(cutFonts.size - 1)]
        p.textSize = size * (0.92f + rnd(seed + 1) * 0.2f)
        return p.measureText(s) + size * 0.24f
    }

    /** A última palavra deitada no chão, em perspectiva; as anteriores pequenas, em pé, acima dela. */
    private fun drawChao(c: Canvas, w: Float, h: Float, b: Beat, pos: Long, e: Float, alpha: Float) {
        val words = b.words.map { upper(it.text) }
        val pop = pops(b, pos)
        val cx = w / 2; val cy = h * centerFraction
        val floorWord = words.last()
        chao.textSize = 100f
        val size = min(100f * w * maxWidthFraction * 1.08f / chao.measureText(floorWord).coerceAtLeast(1f), h * 0.42f)
        chao.textSize = size
        val baseY = cy + size * 0.45f
        val lastPop = pop.last()
        if (lastPop > 0f) {
            matrix.setPerspective(Math.toRadians(-48.0).toFloat(), 0f, w * 1.1f, cx, baseY)
            matrix.postTranslate(0f, (1 - easeOut(if (words.size == 1) e else lastPop)) * h * 0.25f)
            c.save(); c.concat(matrix)
            val a = alpha * easeOut(lastPop)
            text(c, floorWord, cx, baseY + size * 0.09f, chao, Color.rgb(60, 80, 255), a * 0.55f)
            text(c, floorWord, cx, baseY + size * 0.05f, chao, RED, a)
            text(c, floorWord, cx, baseY, chao, Color.rgb(250, 244, 236), a)
            c.restore()
        }
        if (words.size > 1) {
            val upright = words.dropLast(1).joinToString(" ")
            chaoTop.textSize = min(size * 0.5f, 100f * w * maxWidthFraction / max(1f, run { chaoTop.textSize = 100f; chaoTop.measureText(upright) }))
            val sc = 1.5f - 0.5f * e
            c.save(); c.scale(sc, sc, cx, baseY - size * 0.45f)
            chaoTop.setShadowLayer(chaoTop.textSize * 0.1f, 0f, chaoTop.textSize * 0.06f, Color.argb((160 * alpha).toInt(), 0, 0, 0))
            text(c, upright, cx, baseY - size * 0.38f, chaoTop, Color.rgb(250, 244, 236), alpha * e)
            chaoTop.clearShadowLayer()
            c.restore()
        }
    }

    private fun drawBackground(c: Canvas, w: Float, h: Float, look: Look, seed: Int, t: Float, empty: Boolean) {
        val (inner, outer) = when {
            empty -> Color.rgb(30, 14, 12) to Color.rgb(12, 8, 8)
            look == Look.BLOCO -> Color.rgb(84, 28, 22) to MAROON
            look == Look.PINCEL -> Color.rgb(34, 22, 20) to Color.rgb(12, 9, 9)
            look == Look.RECORTE -> Color.rgb(170, 48, 36) to Color.rgb(96, 22, 16)
            look == Look.CONTORNO -> Color.rgb(242, 234, 220) to Color.rgb(214, 200, 182)
            look == Look.CHAO -> Color.rgb(40, 16, 14) to Color.rgb(10, 8, 8)
            else -> Color.rgb(244, 224, 208) to Color.rgb(222, 178, 156)
        }
        c.drawColor(outer)
        bg.shader = RadialGradient(w * (0.45f + 0.1f * rnd(seed)), h * centerFraction, max(w, h) * 0.75f, inner, outer, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, bg)
        bg.shader = null
        if (look == Look.PINCEL || look == Look.CHAO) {
            // Vazamento de luz laranja num canto.
            val lx = if (rnd(seed + 1) < 0.5f) 0f else w
            bg.shader = RadialGradient(lx, h * (0.2f + 0.6f * rnd(seed + 2)), w * 0.7f, Color.argb(90, 255, 120, 40), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, bg); bg.shader = null
        }
        if (look == Look.CONTORNO || look == Look.RISCADO || look == Look.RECORTE) {
            // Fibras do papel.
            for (i in 0 until 40) {
                val k = seed * 3 + i * 17
                stroke.strokeWidth = (0.6f + rnd(k) * 1.2f) * density
                stroke.color = if (look == Look.RECORTE) Color.argb(26, 255, 220, 200) else Color.argb(18, 80, 50, 30)
                val x = rnd(k + 1) * w; val y = rnd(k + 2) * h
                c.drawLine(x, y, x + (rnd(k + 3) - 0.5f) * w * 0.3f, y + (rnd(k + 4) - 0.5f) * h * 0.02f, stroke)
            }
        }
    }

    /** Granulado de filme, vinheta e um vazamento de luz que passeia de vez em quando. */
    private fun drawFilm(c: Canvas, w: Float, h: Float, t: Float, look: Look) {
        val light = look == Look.CONTORNO || look == Look.RISCADO
        val frame = (t * 24).toInt()
        for (i in 0 until 160) {
            val k = frame * 131 + i * 7
            val a = (10 + rnd(k + 3) * 50).toInt()
            dot.color = if (i % 2 == 0) Color.argb(a, 255, 240, 225) else Color.argb(a, 0, 0, 0)
            c.drawCircle(rnd(k) * w, rnd(k + 1) * h, (0.5f + rnd(k + 2) * 1.1f) * density, dot)
        }
        bg.shader = RadialGradient(w / 2, h / 2, max(w, h) * 0.72f, intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(if (light) 90 else 160, 0, 0, 0)),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, bg)
        val leak = sin(t * 0.45f)
        if (leak > 0.6f) {
            val a = ((leak - 0.6f) / 0.4f * 70).toInt()
            bg.shader = RadialGradient(w * (1.1f - (t * 0.07f % 1.2f)), h * 0.25f, w * 0.6f, Color.argb(a, 255, 140, 60), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, bg)
        }
        bg.shader = null
    }
}
