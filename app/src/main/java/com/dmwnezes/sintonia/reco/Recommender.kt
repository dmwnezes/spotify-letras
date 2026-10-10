package com.dmwnezes.sintonia.reco

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.ln
import kotlin.random.Random

/** Um artista que representa o seu gosto, com o peso dele. */
data class Seed(val name: String, val weight: Float)

/** O que o app sabe do seu gosto antes de recomendar. */
data class Taste(
    val seeds: List<Seed>,
    /** Músicas que você já conhece (artista|título normalizado): não aparecem como novidade. */
    val knownTracks: Set<String>,
    /** Artistas que você já ouve muito: podem aparecer, mas valem menos que descobertas. */
    val knownArtists: Set<String>,
)

/** Uma candidata com a pontuação acumulada e o artista seu que a trouxe. */
data class Candidate(val track: DzTrack, val score: Float, val reason: String)

/**
 * Monta recomendações: para cada artista que você ouve, busca no Deezer os artistas parecidos
 * e a "rádio" do artista; junta tudo, dá pontos e aplica o que aprendeu com suas curtidas e pulos.
 */
class Recommender(private val dz: DeezerClient) {

    suspend fun gather(taste: Taste, data: RecoData, maxSeeds: Int = 10): List<Candidate> = coroutineScope {
        // Artistas que você curtiu aqui também viram sementes.
        val learned = data.artistScore.filter { it.value >= 1f }
            .map { (name, s) -> Seed(data.likes.firstOrNull { DeezerClient.norm(it.artist) == name }?.artist ?: name, 0.35f * s) }
        val seeds = (taste.seeds + learned)
            .groupBy { DeezerClient.norm(it.name) }.map { (_, g) -> Seed(g.first().name, g.sumOf { it.weight.toDouble() }.toFloat()) }
            .sortedByDescending { it.weight }
            .let { top -> top.take(maxSeeds - 2) + top.drop(maxSeeds - 2).shuffled().take(2) } // um pouco de variedade

        val resolved = seeds.map { s -> async { dz.findArtist(s.name)?.let { s to it } } }.awaitAll().filterNotNull()

        val out = mutableListOf<Candidate>()
        val relatedScore = HashMap<Long, Pair<DzArtist, Float>>()
        val relatedReason = HashMap<Long, Pair<String, Float>>() // semente que mais contribuiu

        val perSeed = resolved.map { (seed, artist) ->
            async { Triple(seed, dz.related(artist.id, 15), dz.radio(artist.id, 25)) }
        }.awaitAll()

        for ((seed, related, radio) in perSeed) {
            radio.forEachIndexed { i, t -> out += Candidate(t, seed.weight * 0.8f * (1f - i / 40f), seed.name) }
            related.forEachIndexed { i, a ->
                val add = seed.weight * (1f - i / 20f)
                val prev = relatedScore[a.id]?.second ?: 0f
                relatedScore[a.id] = a to (prev + add)
                if (add > (relatedReason[a.id]?.second ?: 0f)) relatedReason[a.id] = seed.name to add
            }
        }
        // Artistas parecidos com vários dos seus somam pontos; os melhores trazem suas músicas principais.
        val topRelated = relatedScore.values.sortedByDescending { it.second }.take(22)
        val tops = topRelated.map { (a, s) -> async { Triple(a, s, dz.top(a.id, 4)) } }.awaitAll()
        for ((a, s, tracks) in tops) {
            tracks.forEachIndexed { i, t -> out += Candidate(t, s * (1f - i * 0.18f), relatedReason[a.id]?.first ?: a.name) }
        }
        out
    }

    companion object {
        fun trackKey(artist: String, title: String): String {
            val clean = title.replace(Regex("\\s*[(\\[].*?[)\\]]"), "").replace(Regex("\\s+-\\s+.*$"), "")
            return DeezerClient.norm(artist) + "|" + DeezerClient.norm(clean)
        }

        /**
         * Pontua e escolhe [n] músicas. Regras:
         * fora o que você já conhece, já pulou ou viu há pouco; nota do artista pelo que você curtiu/pulou;
         * no máximo 2 por artista e 5 pelo mesmo motivo; artistas que você já ouve valem metade.
         */
        fun rank(
            candidates: List<Candidate>,
            taste: Taste,
            data: RecoData,
            n: Int,
            today: Long = RecoStore.today(),
            freshDays: Int = 14,
            exclude: Set<Long> = emptySet(),
            random: Random = Random.Default,
        ): List<RecoTrack> {
            val merged = LinkedHashMap<String, Candidate>()
            for (c in candidates) {
                val t = c.track
                if (t.title.isBlank() || t.artist.isBlank()) continue
                if (t.id in data.dislikedTracks || t.id in exclude) continue
                val key = trackKey(t.artist, t.title)
                if (key in taste.knownTracks) continue
                if (data.likes.any { it.dzId == t.id || it.key == key }) continue
                val seenDay = data.seen[t.id]
                if (seenDay != null && today - seenDay < freshDays) continue
                val artistNote = data.artistScore[DeezerClient.norm(t.artist)] ?: 0f
                if (artistNote <= -1.3f) continue // pulado várias vezes: some
                val prev = merged[key]
                merged[key] = if (prev == null) c else prev.copy(score = prev.score + c.score * 0.6f,
                    reason = if (c.score > prev.score) c.reason else prev.reason)
            }

            val scored = merged.values.map { c ->
                val a = DeezerClient.norm(c.track.artist)
                var s = c.score
                if (a in taste.knownArtists) s *= 0.5f
                s += 0.45f * (data.artistScore[a] ?: 0f)
                s += 0.08f * ln(1f + c.track.rank / 100_000f)       // um pouco de popularidade
                s *= 0.85f + 0.3f * random.nextFloat()                // variedade a cada geração
                c to s
            }.sortedByDescending { it.second }

            val perArtist = HashMap<String, Int>()
            val perReason = HashMap<String, Int>()
            val picked = mutableListOf<RecoTrack>()
            // 1ª passada respeita o limite por motivo; a 2ª completa a lista se faltar.
            for (reasonCap in listOf(5, Int.MAX_VALUE)) {
                for ((c, _) in scored) {
                    if (picked.size >= n) break
                    if (picked.any { it.dzId == c.track.id }) continue
                    val a = DeezerClient.norm(c.track.artist)
                    if ((perArtist[a] ?: 0) >= 2 || (perReason[c.reason] ?: 0) >= reasonCap) continue
                    perArtist[a] = (perArtist[a] ?: 0) + 1
                    perReason[c.reason] = (perReason[c.reason] ?: 0) + 1
                    picked += RecoTrack.from(c.track, c.reason)
                }
            }
            return spread(picked)
        }

        /** Evita duas músicas do mesmo artista em seguida. */
        fun spread(list: List<RecoTrack>): List<RecoTrack> {
            val pool = list.toMutableList()
            val out = mutableListOf<RecoTrack>()
            while (pool.isNotEmpty()) {
                val last = out.lastOrNull()?.artist
                val i = pool.indexOfFirst { it.artist != last }.takeIf { it >= 0 } ?: 0
                out += pool.removeAt(i)
            }
            return out
        }
    }
}
