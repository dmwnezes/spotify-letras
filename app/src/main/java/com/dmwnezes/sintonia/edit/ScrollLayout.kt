package com.dmwnezes.sintonia.edit

import com.dmwnezes.sintonia.lyrics.LyricLine
import com.dmwnezes.sintonia.lyrics.WordTiming

/** Uma linha cantada, com o tempo de cada palavra (texto já sem espaços nas pontas). */
internal class SungLine(val startMs: Long, val words: List<WordTiming.Span>, val seed: Int)

internal object SungLines {
    fun from(lines: List<LyricLine>): List<SungLine> {
        val out = mutableListOf<SungLine>()
        lines.forEachIndexed { i, l ->
            if (l.text.isBlank()) return@forEachIndexed
            val next = lines.drop(i + 1).firstOrNull()?.timeMs
            val ws = WordTiming.spans(l, next).map { it.copy(text = it.text.trim()) }.filter { it.text.isNotEmpty() }
            if (ws.isNotEmpty()) out += SungLine(l.timeMs, ws, l.text.hashCode() xor (i * 7919))
        }
        return out
    }
}

/** Uma linha de texto na tela (uma linha da letra pode virar 1–3 destas). */
internal class Row(val line: Int, val words: IntArray, val xs: FloatArray, val width: Float, val y: Float)

/** Letra quebrada em linhas que cabem na largura, empilhadas como uma lista que rola. */
internal class ScrollLayout(val lines: List<SungLine>, val rows: List<Row>, val centers: FloatArray) {

    /** Onde a lista está (em px de conteúdo), qual linha está sendo cantada e quanto da transição já passou (0–1). */
    data class Focus(val y: Float, val current: Int, val p: Float)

    fun focus(posMs: Long, transMs: Float): Focus {
        if (lines.isEmpty()) return Focus(0f, -1, 1f)
        var lo = 0; var hi = lines.size - 1; var cur = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].startMs <= posMs) { cur = mid; lo = mid + 1 } else hi = mid - 1
        }
        if (cur < 0) {
            // Antes da primeira linha: ela espera logo abaixo do centro.
            val gap = centers.getOrElse(1) { centers[0] + 100f } - centers[0]
            return Focus(centers[0] - gap, -1, 1f)
        }
        val p = ((posMs - lines[cur].startMs) / transMs).coerceIn(0f, 1f)
        val from = if (cur == 0) centers[0] - (centers.getOrElse(1) { centers[0] + 100f } - centers[0]) else centers[cur - 1]
        return Focus(from + (centers[cur] - from) * easeOut(p), cur, p)
    }

    companion object {
        /**
         * Quebra cada linha em pedaços que cabem em [maxW].
         * [measure] devolve a largura de uma palavra; [lh] é a altura de cada linha de texto e [gap] o espaço extra entre frases.
         */
        fun build(lines: List<SungLine>, maxW: Float, lh: Float, gap: Float, space: Float, measure: (SungLine, Int) -> Float): ScrollLayout {
            val rows = mutableListOf<Row>()
            val centers = FloatArray(lines.size)
            var y = 0f
            lines.forEachIndexed { li, line ->
                val first = rows.size
                var cur = mutableListOf<Int>(); var xs = mutableListOf<Float>(); var x = 0f
                fun flush() {
                    if (cur.isEmpty()) return
                    rows += Row(li, cur.toIntArray(), xs.toFloatArray(), x - space, y)
                    y += lh; cur = mutableListOf(); xs = mutableListOf(); x = 0f
                }
                line.words.indices.forEach { wi ->
                    val ww = measure(line, wi)
                    if (cur.isNotEmpty() && x + ww > maxW) flush()
                    cur += wi; xs += x; x += ww + space
                }
                flush()
                centers[li] = (rows[first].y + rows.last().y) / 2
                y += gap
            }
            return ScrollLayout(lines, rows, centers)
        }
    }
}
