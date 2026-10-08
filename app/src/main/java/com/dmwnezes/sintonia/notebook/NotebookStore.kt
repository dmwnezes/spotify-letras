package com.dmwnezes.sintonia.notebook

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Trecho de letra guardado no Caderno. */
data class SavedLine(
    val id: String,
    val trackId: String,
    val track: String,
    val artist: String,
    val imageUrl: String?,
    val text: String,
    val translation: String?,
    val timeMs: Long,
    val savedAt: Long,
)

/** Anotação do diário musical, ligada a uma música. */
data class DiaryEntry(
    val id: String,
    val trackId: String,
    val track: String,
    val artist: String,
    val imageUrl: String?,
    val note: String,
    val positionMs: Long,
    val createdAt: Long,
)

data class Notebook(val lines: List<SavedLine> = emptyList(), val diary: List<DiaryEntry> = emptyList())

/** Caderno salvo no armazenamento privado do app (um arquivo JSON). */
class NotebookStore(context: Context) {
    private val file = File(context.filesDir, "caderno.json")
    private val _state = MutableStateFlow(load())
    val state = _state.asStateFlow()

    fun saveLine(l: SavedLine) = mutate { it.copy(lines = listOf(l) + it.lines.filterNot { x -> x.trackId == l.trackId && x.text == l.text }) }
    fun deleteLine(id: String) = mutate { it.copy(lines = it.lines.filterNot { l -> l.id == id }) }
    fun hasLine(trackId: String, text: String) = _state.value.lines.any { it.trackId == trackId && it.text == text }

    fun saveEntry(e: DiaryEntry) = mutate { n ->
        val others = n.diary.filterNot { it.id == e.id }
        n.copy(diary = (others + e).sortedByDescending { it.createdAt })
    }
    fun deleteEntry(id: String) = mutate { it.copy(diary = it.diary.filterNot { e -> e.id == id }) }

    private fun mutate(f: (Notebook) -> Notebook) {
        val next = f(_state.value)
        _state.value = next
        runCatching {
            val tmp = File(file.parentFile, "caderno.tmp")
            tmp.writeText(toJson(next))
            tmp.renameTo(file)
        }
    }

    private fun load(): Notebook = runCatching { if (file.exists()) fromJson(file.readText()) else Notebook() }.getOrDefault(Notebook())

    companion object {
        fun newId(): String = UUID.randomUUID().toString()

        fun toJson(n: Notebook): String = JSONObject().apply {
            put("lines", JSONArray().also { a ->
                n.lines.forEach { l ->
                    a.put(JSONObject().put("id", l.id).put("trackId", l.trackId).put("track", l.track).put("artist", l.artist)
                        .put("imageUrl", l.imageUrl ?: "").put("text", l.text).put("translation", l.translation ?: "")
                        .put("timeMs", l.timeMs).put("savedAt", l.savedAt))
                }
            })
            put("diary", JSONArray().also { a ->
                n.diary.forEach { e ->
                    a.put(JSONObject().put("id", e.id).put("trackId", e.trackId).put("track", e.track).put("artist", e.artist)
                        .put("imageUrl", e.imageUrl ?: "").put("note", e.note).put("positionMs", e.positionMs).put("createdAt", e.createdAt))
                }
            })
        }.toString()

        fun fromJson(text: String): Notebook {
            val o = JSONObject(text)
            val lines = o.optJSONArray("lines") ?: JSONArray()
            val diary = o.optJSONArray("diary") ?: JSONArray()
            return Notebook(
                lines = (0 until lines.length()).map { lines.getJSONObject(it) }.map {
                    SavedLine(it.getString("id"), it.optString("trackId"), it.optString("track"), it.optString("artist"),
                        it.optString("imageUrl").ifBlank { null }, it.optString("text"), it.optString("translation").ifBlank { null },
                        it.optLong("timeMs"), it.optLong("savedAt"))
                },
                diary = (0 until diary.length()).map { diary.getJSONObject(it) }.map {
                    DiaryEntry(it.getString("id"), it.optString("trackId"), it.optString("track"), it.optString("artist"),
                        it.optString("imageUrl").ifBlank { null }, it.optString("note"), it.optLong("positionMs"), it.optLong("createdAt"))
                },
            )
        }
    }
}
