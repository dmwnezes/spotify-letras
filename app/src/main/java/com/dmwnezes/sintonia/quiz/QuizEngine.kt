package com.dmwnezes.sintonia.quiz

import com.dmwnezes.sintonia.data.SpotifyClient
import com.dmwnezes.sintonia.data.TimeRange
import com.dmwnezes.sintonia.data.Track
import com.dmwnezes.sintonia.lyrics.LrcLibClient
import com.dmwnezes.sintonia.lyrics.Lyrics
import java.text.Normalizer
import kotlin.random.Random

data class QuizQuestion(val answer: Track, val options: List<Track>, val excerpt: List<String>)

/** Monta perguntas com trechos das letras das músicas que a pessoa mais ouve. */
class QuizEngine(private val spotify: SpotifyClient, private val lrclib: LrcLibClient, private val random: Random = Random.Default) {
    private var pool: List<Track> = emptyList()
    private val used = mutableSetOf<String>()

    /** Junta as músicas mais ouvidas dos três períodos. Devolve quantas há. */
    suspend fun prepare(): Int {
        if (pool.isEmpty()) {
            val all = mutableListOf<Track>()
            for (r in listOf(TimeRange.LONG, TimeRange.MEDIUM, TimeRange.SHORT)) {
                runCatching { spotify.topTracks(r) }.getOrNull()?.let(all::addAll)
            }
            pool = all.distinctBy { it.id }.distinctBy { norm(it.name) + "|" + it.artists.firstOrNull() }
        }
        used.clear()
        return pool.size
    }

    suspend fun next(): QuizQuestion? {
        repeat(15) {
            val cand = pool.filter { it.id !in used }.randomOrNull(random) ?: return null
            used += cand.id
            val lyrics = runCatching { lrclib.find(cand) }.getOrNull()
            val texts = when (lyrics) {
                is Lyrics.Synced -> lyrics.lines.map { it.text }
                is Lyrics.Plain -> lyrics.text.lines()
                else -> null
            }?.map { it.trim() }?.filter { it.length >= 12 }
            if (texts == null || texts.size < 4) return@repeat
            val excerpt = pickExcerpt(texts, cand.name, random)
            val others = pool.filter { it.id != cand.id && norm(it.name) != norm(cand.name) }
                .shuffled(random).distinctBy { norm(it.name) }.take(3)
            if (others.size < 3) return null
            return QuizQuestion(cand, (others + cand).shuffled(random), excerpt)
        }
        return null
    }

    companion object {
        fun norm(s: String): String =
            Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
                .replace(Regex("\\(.*?\\)|-.*$"), "").replace(Regex("[^a-z0-9 ]"), "").trim()

        /**
         * Escolhe duas linhas seguidas, evitando a primeira linha da música
         * e linhas que contêm o próprio nome da música (fácil demais).
         */
        fun pickExcerpt(lines: List<String>, title: String, random: Random = Random.Default): List<String> {
            val t = norm(title)
            fun hasTitle(i: Int) = t.length >= 3 && norm(lines[i]).contains(t)
            val starts = (1 until lines.size - 1).filter { !hasTitle(it) && !hasTitle(it + 1) && lines[it] != lines[it + 1] }
            val s = starts.randomOrNull(random) ?: (0 until lines.size - 1).random(random)
            return listOf(lines[s], lines[s + 1])
        }
    }
}
