package com.dmwnezes.sintonia.history

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dmwnezes.sintonia.AppGraph
import com.dmwnezes.sintonia.data.RecentPlay
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Reproduções que o próprio app coletou do Spotify: uma por linha, só no celular. */
class PlayLog(private val file: File) {

    @Synchronized
    fun read(): List<RawStream> =
        if (!file.exists()) emptyList()
        else file.readLines().mapNotNull { line -> runCatching { parse(JSONObject(line)) }.getOrNull() }

    /** Guarda só o que é mais novo que a última reprodução registrada. Devolve quantas entraram. */
    @Synchronized
    fun add(plays: List<RecentPlay>): Int {
        val last = read().maxOfOrNull { it.endEpochMs } ?: 0L
        val fresh = plays.filter { it.playedAtMs > last && it.track.artists.isNotEmpty() }.sortedBy { it.playedAtMs }
        if (fresh.isNotEmpty()) file.appendText(fresh.joinToString("") { line(it) })
        return fresh.size
    }

    /** Apaga tudo, mas lembra o momento para o Spotify não devolver as mesmas músicas na próxima coleta. */
    @Synchronized
    fun clear(nowMs: Long = System.currentTimeMillis()) {
        file.writeText(JSONObject().put("t", nowMs).put("m", 0).toString() + "\n")
    }

    private fun line(p: RecentPlay) = JSONObject().apply {
        put("t", p.playedAtMs)
        // shortcut: o Spotify não informa quanto foi ouvido e só lista o que tocou por 30 s ou mais; usa a duração da faixa, sem API melhor não há como refinar
        put("m", p.track.durationMs)
        put("n", p.track.name)
        put("a", p.track.artists.first())
        put("l", p.track.album)
        if (p.track.id.isNotBlank()) put("u", "spotify:track:${p.track.id}")
    }.toString() + "\n"

    private fun parse(o: JSONObject) = RawStream(
        endEpochMs = o.getLong("t"),
        msPlayed = o.optLong("m"),
        track = o.optString("n").ifBlank { null },
        artist = o.optString("a").ifBlank { null },
        album = o.optString("l").ifBlank { null },
        trackUri = o.optString("u").ifBlank { null },
        showName = null, episodeName = null, platform = null, country = null,
        reasonStart = null, reasonEnd = null, shuffle = null, skipped = null, offline = null, incognito = null,
    )
}

/** Coleta com o app fechado: o Spotify só devolve as últimas 50 músicas, então não dá para esperar a pessoa abrir o app. */
class CollectWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (AppGraph.prefs.isLoggedIn) runCatching { AppGraph.playLog.add(AppGraph.spotify.recentlyPlayed()) }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CollectWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("coletar-reproducoes", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
