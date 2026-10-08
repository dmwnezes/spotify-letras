package com.dmwnezes.sintonia.share

import com.dmwnezes.sintonia.lyrics.LyricLine

/** Regras do trecho a compartilhar: no máximo 30 s. */
object ShareRange {
    const val MAX_MS = 30_000L
    const val MIN_MS = 3_000L

    data class Range(val startMs: Long, val endMs: Long, val clipped: Boolean) {
        val durationMs get() = endMs - startMs
    }

    /** Trecho a partir das linhas [from]..[to] da letra (índices na lista). */
    fun fromLines(lines: List<LyricLine>, from: Int, to: Int, trackDurationMs: Long): Range {
        val a = minOf(from, to).coerceIn(lines.indices)
        val b = maxOf(from, to).coerceIn(lines.indices)
        val start = (lines[a].timeMs - 400).coerceAtLeast(0)
        val naturalEnd = if (b + 1 < lines.size) lines[b + 1].timeMs else minOf(lines[b].timeMs + 5_000, trackDurationMs)
        val end = maxOf(naturalEnd, start + MIN_MS)
        val clipped = end - start > MAX_MS
        return Range(start, if (clipped) start + MAX_MS else end, clipped)
    }

    /** Trecho por tempo: começa em [startMs] e dura [lengthMs] (até 30 s), sem passar do fim da faixa. */
    fun fromTime(startMs: Long, lengthMs: Long, trackDurationMs: Long): Range {
        val len = lengthMs.coerceIn(MIN_MS, MAX_MS)
        val start = startMs.coerceIn(0, (trackDurationMs - len).coerceAtLeast(0))
        return Range(start, minOf(start + len, trackDurationMs.coerceAtLeast(start + MIN_MS)), false)
    }
}
