package com.dmwnezes.sintonia.lyrics

/** Uma palavra (com o espaço que vem depois dela) e quando começa a ser cantada. */
data class Word(val startMs: Long, val text: String)

/** [words] só existe quando a letra tem marcação palavra por palavra. */
data class LyricLine(val timeMs: Long, val text: String, val words: List<Word>? = null)

sealed interface Lyrics {
    data class Synced(val lines: List<LyricLine>) : Lyrics
    data class Plain(val text: String) : Lyrics
    data object Instrumental : Lyrics
    data object NotFound : Lyrics
}

/**
 * Lê letras no formato LRC: "[01:23.45] texto da linha".
 * Também entende o LRC "melhorado", com o tempo de cada palavra: "[00:12.00] <00:12.00>Olá <00:12.40>mundo".
 */
object LrcParser {
    private val stamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val wordStamp = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")
    private val offsetTag = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)

    private fun toMs(min: String, sec: String, frac: String): Long {
        val fracMs = when (frac.length) {
            0 -> 0L
            1 -> frac.toLong() * 100
            2 -> frac.toLong() * 10
            else -> frac.take(3).toLong()
        }
        return min.toLong() * 60_000 + sec.toLong() * 1_000 + fracMs
    }

    fun parse(lrc: String): List<LyricLine> {
        val offset = offsetTag.find(lrc)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val out = mutableListOf<LyricLine>()
        for (raw in lrc.lineSequence()) {
            val stamps = mutableListOf<Long>()
            var rest = raw.trim()
            while (true) {
                val m = stamp.matchAt(rest, 0) ?: break
                val (min, sec, frac) = m.destructured
                stamps += toMs(min, sec, frac)
                rest = rest.substring(m.range.last + 1).trimStart()
            }
            if (stamps.isEmpty()) continue
            val (text, words) = parseWords(rest, offset)
            for (t in stamps) out += LyricLine((t - offset).coerceAtLeast(0), text, words)
        }
        return out.sortedBy { it.timeMs }
    }

    /** Separa as marcações <mm:ss.xx> de cada palavra; devolve o texto limpo e as palavras (ou null). */
    private fun parseWords(s: String, offset: Long): Pair<String, List<Word>?> {
        val marks = wordStamp.findAll(s).toList()
        if (marks.isEmpty()) return s.trim() to null
        val words = mutableListOf<Word>()
        marks.forEachIndexed { i, m ->
            val (min, sec, frac) = m.destructured
            val end = if (i + 1 < marks.size) marks[i + 1].range.first else s.length
            val piece = s.substring(m.range.last + 1, end)
            if (piece.isNotBlank()) words += Word((toMs(min, sec, frac) - offset).coerceAtLeast(0), piece)
        }
        val text = wordStamp.replace(s, "").replace(Regex("\\s+"), " ").trim()
        return text to words.ifEmpty { null }
    }

    /** Índice da linha que deve estar em destaque no tempo [posMs], ou -1 antes da primeira. */
    fun indexAt(lines: List<LyricLine>, posMs: Long): Int {
        var lo = 0
        var hi = lines.size - 1
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= posMs) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        return ans
    }
}

/** Tempo de cada palavra de uma linha: o real quando existe, senão uma estimativa pelo tamanho das palavras. */
object WordTiming {

    data class Span(val text: String, val startMs: Long, val endMs: Long)

    fun spans(line: LyricLine, nextLineMs: Long?): List<Span> {
        val gap = ((nextLineMs ?: (line.timeMs + 5000)) - line.timeMs).coerceAtLeast(400)
        line.words?.let { ws ->
            return ws.mapIndexed { i, w ->
                val end = if (i + 1 < ws.size) ws[i + 1].startMs else minOf(line.timeMs + gap, w.startMs + 1200)
                Span(w.text, w.startMs, end.coerceAtLeast(w.startMs + 80))
            }
        }
        // Estimativa: a linha é cantada em ~85% do intervalo até a próxima (no máximo ~180 ms por letra),
        // e cada palavra leva um tempo proporcional ao seu tamanho.
        val pieces = Regex("""\S+\s*""").findAll(line.text).map { it.value }.toList()
        if (pieces.isEmpty()) return emptyList()
        val letters = pieces.sumOf { p -> p.count { it.isLetterOrDigit() } + 1 }
        val singMs = minOf((gap * 0.85).toLong(), letters * 180L).coerceAtLeast(300)
        var t = line.timeMs
        return pieces.map { p ->
            val share = (p.count { it.isLetterOrDigit() } + 1).toDouble() / letters
            val d = (singMs * share).toLong().coerceAtLeast(60)
            Span(p, t, t + d).also { t += d }
        }
    }

    /** Quanto de cada palavra já foi cantado (0–1) no tempo [posMs]. */
    fun progress(spans: List<Span>, posMs: Long): FloatArray = FloatArray(spans.size) { i ->
        val s = spans[i]
        ((posMs - s.startMs).toFloat() / (s.endMs - s.startMs)).coerceIn(0f, 1f)
    }
}
