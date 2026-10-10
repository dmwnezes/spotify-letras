package com.dmwnezes.sintonia.edit

import com.dmwnezes.sintonia.lyrics.LyricLine
import com.dmwnezes.sintonia.lyrics.WordTiming

/** Uma palavra do estilo Edit, com quando começa a acender. */
data class EditWord(val text: String, val startMs: Long, val endMs: Long)

/** Um pedaço de 2–3 palavras, que vira uma linha na tela. */
data class EditChunk(val words: List<EditWord>) {
    val text: String get() = words.joinToString(" ") { it.text }
}

/** Um bloco de até 3 pedaços empilhados; a tela limpa quando muda de bloco. */
data class EditPage(val chunks: List<EditChunk>, val startMs: Long, val endMs: Long)

/**
 * Transforma a letra sincronizada no formato dos "lyric edits":
 * cada linha é quebrada em pedaços curtos (2–3 palavras) e os pedaços são agrupados em blocos de até 3.
 */
object EditLayout {
    private const val MAX_CHARS = 13        // pedaço curto: cabe grande na tela
    private const val MAX_WORDS = 3
    private const val MAX_CHUNKS = 3
    private const val HOLD_MS = 2600L       // quanto o último bloco fica na tela se a próxima linha demora

    /** Palavras pequenas que ficam grudadas na seguinte ("o amor", "it's not"). */
    private val glue = setOf(
        "a", "o", "as", "os", "um", "uma", "de", "do", "da", "dos", "das", "em", "no", "na", "nos", "nas",
        "e", "que", "se", "me", "te", "eu", "tu", "meu", "minha", "teu", "tua", "pra", "pro", "por", "com", "sem", "é",
        "the", "a", "an", "to", "of", "in", "on", "my", "your", "i", "i'm", "it's", "you", "we", "and", "but", "for",
        "el", "la", "los", "las", "y", "mi", "tu", "en", "con",
    )

    fun pages(lines: List<LyricLine>): List<EditPage> {
        val out = mutableListOf<EditPage>()
        lines.forEachIndexed { i, line ->
            if (line.text.isBlank()) return@forEachIndexed
            val next = lines.drop(i + 1).firstOrNull()?.timeMs
            val words = WordTiming.spans(line, next).map { EditWord(it.text.trim(), it.startMs, it.endMs) }.filter { it.text.isNotEmpty() }
            val chunks = chunk(words)
            val groups = chunks.chunked(MAX_CHUNKS)
            groups.forEachIndexed { g, group ->
                val start = group.first().words.first().startMs
                val endOfGroup = if (g + 1 < groups.size) groups[g + 1].first().words.first().startMs
                else minOf(next ?: Long.MAX_VALUE, group.last().words.last().endMs + HOLD_MS)
                out += EditPage(group, start, endOfGroup)
            }
        }
        // Garante que um bloco nunca passa por cima do seguinte.
        return out.mapIndexed { k, p -> if (k + 1 < out.size && p.endMs > out[k + 1].startMs) p.copy(endMs = out[k + 1].startMs) else p }
    }

    /** Quebra as palavras de uma linha em pedaços curtos, sem deixar artigo/preposição sozinho no fim. */
    fun chunk(words: List<EditWord>): List<EditChunk> {
        val out = mutableListOf<MutableList<EditWord>>()
        var cur = mutableListOf<EditWord>()
        fun len(l: List<EditWord>) = l.sumOf { it.text.length } + (l.size - 1).coerceAtLeast(0)
        fun isGlue(w: EditWord) = w.text.lowercase().trim(',', '.', '!', '?', '"') in glue
        for (w in words) {
            val wouldBe = len(cur + w)
            if (cur.isNotEmpty() && (cur.size >= MAX_WORDS || wouldBe > MAX_CHARS)) {
                // Artigo/preposição no fim do pedaço passa para o próximo ("você sabe | que não é").
                val carry = mutableListOf<EditWord>()
                while (cur.size > 1 && isGlue(cur.last())) carry.add(0, cur.removeAt(cur.size - 1))
                out += cur
                cur = carry
                // Se só sobrou "glue" e o pedaço já está cheio, fecha mesmo assim.
                if (cur.size >= MAX_WORDS) { out += cur; cur = mutableListOf() }
            }
            cur += w
        }
        if (cur.isNotEmpty()) out += cur
        // Uma palavra sozinha no final vai para o pedaço anterior, se couber.
        if (out.size >= 2 && out.last().size == 1 && len(out[out.size - 2] + out.last()) <= MAX_CHARS + 4 && out[out.size - 2].size < MAX_WORDS) {
            out[out.size - 2].addAll(out.removeAt(out.size - 1))
        }
        return out.map { EditChunk(it) }
    }

    /** Bloco visível no tempo [posMs] (ou null entre frases). */
    fun pageAt(pages: List<EditPage>, posMs: Long): EditPage? {
        var lo = 0; var hi = pages.size - 1; var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (pages[mid].startMs <= posMs) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        if (ans < 0) return null
        return pages[ans].takeIf { posMs < it.endMs }
    }
}
