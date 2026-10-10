package com.dmwnezes.sintonia

import com.dmwnezes.sintonia.reco.Candidate
import com.dmwnezes.sintonia.reco.DeezerClient
import com.dmwnezes.sintonia.reco.DzTrack
import com.dmwnezes.sintonia.reco.RecoData
import com.dmwnezes.sintonia.reco.RecoStore
import com.dmwnezes.sintonia.reco.RecoTrack
import com.dmwnezes.sintonia.reco.Recommender
import com.dmwnezes.sintonia.reco.Seed
import com.dmwnezes.sintonia.reco.Taste
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.random.Random

class RecommenderTest {
    private fun t(id: Long, title: String, artist: String, rank: Int = 500_000) =
        DzTrack(id, title, id * 10, artist, "Álbum", null, null, 200, rank)

    private val taste = Taste(listOf(Seed("Djavan", 1f)), setOf(Recommender.trackKey("Djavan", "Oceano")), setOf("djavan"))

    @Test
    fun rankRules() {
        val cands = listOf(
            Candidate(t(1, "Oceano", "Djavan"), 5f, "Djavan"),                // já conhece
            Candidate(t(2, "Ainda Bem", "Marisa Monte"), 3f, "Djavan"),
            Candidate(t(3, "Amor I Love You", "Marisa Monte"), 2.9f, "Djavan"),
            Candidate(t(4, "Velha Infância", "Marisa Monte"), 2.8f, "Djavan"), // 3ª da mesma artista: fora
            Candidate(t(5, "Sozinho", "Caetano Veloso"), 2f, "Djavan"),
            Candidate(t(6, "Chata", "Banda Pulada"), 9f, "Djavan"),
            Candidate(t(7, "Ainda Bem (Ao Vivo)", "Marisa Monte"), 1f, "Djavan"), // mesma música
        )
        var data = RecoData()
        data = RecoStore.dislike(data, RecoTrack.from(t(60, "X", "Banda Pulada"), "Djavan"))
        data = RecoStore.dislike(data, RecoTrack.from(t(61, "Y", "Banda Pulada"), "Djavan"))
        val r = Recommender.rank(cands, taste, data, 10, random = Random(1))
        val titles = r.map { it.title }
        assertFalse("Oceano" in titles)
        assertFalse("Chata" in titles)               // artista pulado 2x
        assertEquals(2, r.count { it.artist == "Marisa Monte" })
        assertTrue("Sozinho" in titles)
        // nunca duas seguidas do mesmo artista quando dá para evitar
        assertTrue(r.zipWithNext().none { (a, b) -> a.artist == b.artist })
    }

    @Test
    fun learningAndStore() {
        var d = RecoData()
        val song = RecoTrack.from(t(9, "Trem-Bala", "Ana Vilela"), "Djavan")
        d = RecoStore.like(d, song)
        assertEquals(1f, d.artistScore["ana vilela"]!!, 0f)
        assertEquals(0.3f, d.artistScore["djavan"]!!, 0.001f)
        assertEquals(1, d.likes.size)
        assertEquals(d, RecoStore.fromJson(RecoStore.toJson(d)))
        // curtido não volta como novidade
        val r = Recommender.rank(listOf(Candidate(t(9, "Trem-Bala", "Ana Vilela"), 3f, "Djavan")), taste, d, 5)
        assertTrue(r.isEmpty())
    }

    /** Usa a internet: rode com LIVE_TESTS=1. */
    @Test
    fun liveDeezer() = runBlocking {
        assumeTrue(System.getenv("LIVE_TESTS") == "1")
        val rec = Recommender(DeezerClient(OkHttpClient()))
        val liveTaste = Taste(
            listOf(Seed("Djavan", 1f), Seed("Marisa Monte", 0.9f), Seed("Ana Vilela", 0.8f), Seed("Tim Maia", 0.6f)),
            setOf(Recommender.trackKey("Djavan", "Oceano")), setOf("djavan", "marisa monte", "ana vilela", "tim maia"),
        )
        val c = rec.gather(liveTaste, RecoData(), maxSeeds = 4)
        val mix = Recommender.rank(c, liveTaste, RecoData(), 30)
        println("candidatas: ${c.size}")
        mix.forEach { println("${it.title} — ${it.artist}   [porque: ${it.reason}]  prévia=${it.previewUrl != null}") }
        assertTrue(mix.size >= 20)
    }
}
