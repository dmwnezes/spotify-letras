package com.dmwnezes.sintonia

import com.dmwnezes.sintonia.data.Track
import com.dmwnezes.sintonia.translate.GeminiFailure
import com.dmwnezes.sintonia.translate.GeminiTranslator
import com.dmwnezes.sintonia.translate.TranslationStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GeminiTranslatorTest {
    @get:Rule val tmp = TemporaryFolder()

    private val track = Track("id1", "Song", listOf("Artist"), "Album", null, 1000)
    private val lines = listOf("Hello there", "", "Oh", "I'm over the moon")

    private fun response(json: String) = JSONObject().put(
        "candidates", JSONArray().put(JSONObject().put("content", JSONObject().put(
            "parts", JSONArray().put(JSONObject().put("text", json))
        )))
    ).toString()

    @Test fun promptNumbersLinesWithSongContext() {
        val req = GeminiTranslator.buildRequest(track, "en", lines)
        val prompt = req.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text")
        assertTrue(prompt.contains("Música: Song\nArtista: Artist"))
        assertTrue(prompt.contains("1| Hello there\n3| Oh\n4| I'm over the moon"))
        // A análise tem de vir antes das linhas, senão a tradução não usa o contexto.
        assertEquals("analise", req.getJSONObject("generationConfig").getJSONObject("responseSchema").getJSONArray("propertyOrdering").getString(0))
    }

    @Test fun mapsTranslationsBackToTheirLines() {
        val out = GeminiTranslator.parseResponse(
            response("""{"analise":"x","linhas":[{"n":4,"t":"Tô nas nuvens"},{"n":1,"t":"Oi, você"},{"n":3,"t":"oh"}]}"""), lines,
        )
        assertEquals(listOf("Oi, você", null, null, "Tô nas nuvens"), out)
    }

    @Test(expected = JSONException::class) fun rejectsMissingLine() {
        GeminiTranslator.parseResponse(response("""{"analise":"x","linhas":[{"n":1,"t":"Oi"}]}"""), lines)
    }

    @Test(expected = JSONException::class) fun rejectsBlockedAnswer() {
        GeminiTranslator.parseResponse("""{"promptFeedback":{"blockReason":"OTHER"}}""", lines)
    }

    private val done = listOf<String?>("ok")

    @Test fun promptAsksForLowThinking() {
        val config = GeminiTranslator.buildRequest(track, "en", lines).getJSONObject("generationConfig")
        assertEquals("low", config.getJSONObject("thinkingConfig").getString("thinkingLevel"))
    }

    @Test fun slowModelIsBeatenAndCancelled() = runBlocking {
        var cancelled = false
        val out = GeminiTranslator.race(listOf("trava", "rapido"), 30) { model ->
            if (model == "trava") try { delay(60_000); done } catch (e: CancellationException) { cancelled = true; throw e }
            else listOf("do rapido")
        }
        assertEquals(listOf<String?>("do rapido"), out)
        assertTrue(cancelled)
    }

    @Test fun fastModelDoesNotCallTheNext() = runBlocking {
        val called = mutableListOf<String>()
        assertEquals(done, GeminiTranslator.race(listOf("a", "b"), 5_000) { called += it; done })
        assertEquals(listOf("a"), called)
    }

    @Test fun failureGoesStraightToTheNext() = runBlocking {
        val out = GeminiTranslator.race(listOf("a", "b"), 60_000) { if (it == "a") throw GeminiFailure("503") else done }
        assertEquals(done, out)
    }

    @Test fun fatalFailureStopsAndLastFailureIsReported() = runBlocking {
        val called = mutableListOf<String>()
        val fatal = runCatching { GeminiTranslator.race(listOf("a", "b"), 60_000) { called += it; throw GeminiFailure("chave", fatal = true) } }
        assertEquals("chave", fatal.exceptionOrNull()?.message)
        assertEquals(listOf("a"), called)
        val all = runCatching { GeminiTranslator.race(listOf("a", "b"), 60_000) { throw GeminiFailure(it) } }
        assertEquals("b", all.exceptionOrNull()?.message)
    }

    @Test fun storeKeepsTranslationUntilLyricsChange() {
        val store = TranslationStore(tmp.newFolder())
        assertNull(store.get("id:1", lines))
        val translated = listOf("Oi", null, null, "Nas nuvens")
        store.put("id:1", lines, translated)
        assertEquals(translated, store.get("id:1", lines))
        assertNull(store.get("id:1", lines + "nova linha"))
    }
}
