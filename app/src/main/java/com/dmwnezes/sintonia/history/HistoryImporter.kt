package com.dmwnezes.sintonia.history

import android.content.ContentResolver
import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream

class HistoryImportException(message: String) : Exception(message)

/**
 * Lê o que o Spotify manda em "Baixar seus dados":
 * - Histórico estendido: Streaming_History_Audio_*.json (dentro do .zip ou soltos)
 * - Histórico básico: StreamingHistory_music_*.json / StreamingHistory_podcast_*.json
 * - Backup do próprio Sintonia (.json exportado pelo app)
 */
class HistoryImporter(private val resolver: ContentResolver) {

    fun import(uris: List<Uri>, onProgress: (Int) -> Unit = {}): HistoryStats {
        val agg = HistoryAggregator()
        var files = 0
        var count = 0
        val progress = { count++; if (count % 2000 == 0) onProgress(count) }

        for (uri in uris) {
            val name = displayName(uri).lowercase()
            resolver.openInputStream(uri)?.use { raw ->
                val input = raw.buffered()
                input.mark(4)
                val magic = ByteArray(2).also { input.read(it) }
                input.reset()
                if (magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte()) {
                    ZipInputStream(input).use { zip ->
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            val n = entry.name.substringAfterLast('/').lowercase()
                            if (!entry.isDirectory && n.endsWith(".json") && isHistoryFile(n)) {
                                readArray(NoClose(zip), agg, progress)
                                files++
                            }
                        }
                    }
                } else {
                    val backup = tryBackup(input)
                    if (backup != null) return backup
                    if (!name.endsWith(".json") && name.isNotEmpty() && !name.contains("history")) {
                        throw HistoryImportException("Esse arquivo não parece ser o histórico do Spotify.")
                    }
                    readArray(input, agg, progress)
                    files++
                }
            } ?: throw HistoryImportException("Não consegui abrir o arquivo.")
        }
        if (files == 0) throw HistoryImportException("Não encontrei arquivos de histórico (Streaming_History…json) aí dentro.")
        val stats = agg.build(files)
        if (stats.all == null) throw HistoryImportException("O arquivo foi lido, mas não tinha músicas.")
        return stats
    }

    private fun isHistoryFile(n: String) =
        n.startsWith("streaming_history") || n.startsWith("streaminghistory") || n.startsWith("endsong")

    /** Se o arquivo é um backup do Sintonia, devolve as estatísticas direto. */
    private fun tryBackup(input: InputStream): HistoryStats? {
        input.mark(64)
        val head = ByteArray(64)
        val n = input.read(head).coerceAtLeast(0)
        input.reset()
        val start = String(head, 0, n).trimStart()
        if (!start.startsWith("{")) return null
        val text = input.bufferedReader().readText()
        return runCatching { HistoryStore.fromJson(text) }.getOrNull()
            ?: throw HistoryImportException("Esse .json não é do Spotify nem um backup do Sintonia.")
    }

    private fun readArray(input: InputStream, agg: HistoryAggregator, onEntry: () -> Unit) {
        val reader = JsonReader(InputStreamReader(input, Charsets.UTF_8))
        reader.isLenient = true
        reader.beginArray()
        while (reader.hasNext()) {
            readEntry(reader)?.let { agg.add(it); onEntry() }
        }
        reader.endArray()
    }

    private fun readEntry(r: JsonReader): RawStream? {
        var ts: Long? = null
        var ms = 0L
        var track: String? = null; var artist: String? = null; var album: String? = null; var uri: String? = null
        var show: String? = null; var episode: String? = null
        var platform: String? = null; var country: String? = null
        var rStart: String? = null; var rEnd: String? = null
        var shuffle: Boolean? = null; var skipped: Boolean? = null; var offline: Boolean? = null; var incognito: Boolean? = null

        r.beginObject()
        while (r.hasNext()) {
            val key = r.nextName()
            if (r.peek() == JsonToken.NULL) { r.nextNull(); continue }
            when (key) {
                "ts" -> ts = parseIso(r.nextString())
                "endTime" -> ts = parseBasic(r.nextString())
                "ms_played", "msPlayed" -> ms = r.nextLong()
                "master_metadata_track_name", "trackName" -> track = r.nextString()
                "master_metadata_album_artist_name", "artistName" -> artist = r.nextString()
                "master_metadata_album_album_name" -> album = r.nextString()
                "spotify_track_uri" -> uri = r.nextString()
                "episode_show_name", "podcastName" -> show = r.nextString()
                "episode_name", "episodeName" -> episode = r.nextString()
                "platform" -> platform = r.nextString()
                "conn_country" -> country = r.nextString()
                "reason_start" -> rStart = r.nextString()
                "reason_end" -> rEnd = r.nextString()
                "shuffle" -> shuffle = readBool(r)
                "skipped" -> skipped = readBool(r)
                "offline" -> offline = readBool(r)
                "incognito_mode" -> incognito = readBool(r)
                else -> r.skipValue()
            }
        }
        r.endObject()
        // O histórico básico de podcast usa trackName para o episódio; separa aqui.
        if (show != null && artist == null) { episode = episode ?: track; track = null }
        return ts?.let {
            RawStream(it, ms, track, artist, album, uri, show, episode, platform, country, rStart, rEnd, shuffle, skipped, offline, incognito)
        }
    }

    private fun readBool(r: JsonReader): Boolean? = when (r.peek()) {
        JsonToken.BOOLEAN -> r.nextBoolean()
        JsonToken.NUMBER -> r.nextInt() != 0
        JsonToken.STRING -> r.nextString().equals("true", true)
        else -> { r.skipValue(); null }
    }

    private fun parseIso(s: String): Long? = runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()

    private val basicFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private fun parseBasic(s: String): Long? =
        runCatching { LocalDateTime.parse(s, basicFmt).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()

    private fun displayName(uri: Uri): String =
        runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment.orEmpty()

    /** Impede que o JsonReader feche o .zip inteiro ao terminar um arquivo. */
    private class NoClose(input: InputStream) : FilterInputStream(input) {
        override fun close() = Unit
    }
}
