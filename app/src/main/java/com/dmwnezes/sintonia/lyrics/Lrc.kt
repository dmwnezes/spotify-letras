package com.dmwnezes.sintonia.lyrics

data class LyricLine(val timeMs: Long, val text: String)

sealed interface Lyrics {
    data class Synced(val lines: List<LyricLine>) : Lyrics
    data class Plain(val text: String) : Lyrics
    data object Instrumental : Lyrics
    data object NotFound : Lyrics
}

/** Lê letras no formato LRC: "[01:23.45] texto da linha". */
object LrcParser {
    private val stamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val offsetTag = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)

    fun parse(lrc: String): List<LyricLine> {
        val offset = offsetTag.find(lrc)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val out = mutableListOf<LyricLine>()
        for (raw in lrc.lineSequence()) {
            val stamps = mutableListOf<Long>()
            var rest = raw.trim()
            while (true) {
                val m = stamp.matchAt(rest, 0) ?: break
                val (min, sec, frac) = m.destructured
                val fracMs = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.toLong()
                }
                stamps += min.toLong() * 60_000 + sec.toLong() * 1_000 + fracMs
                rest = rest.substring(m.range.last + 1).trimStart()
            }
            for (t in stamps) out += LyricLine((t - offset).coerceAtLeast(0), rest.trim())
        }
        return out.sortedBy { it.timeMs }
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
