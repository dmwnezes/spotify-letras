package com.dmwnezes.sintonia.history

/** Uma reprodução lida do arquivo do Spotify (já normalizada). */
data class RawStream(
    val endEpochMs: Long,
    val msPlayed: Long,
    val track: String?,
    val artist: String?,
    val album: String?,
    val trackUri: String?,
    val showName: String?,      // podcast
    val episodeName: String?,
    val platform: String?,
    val country: String?,
    val reasonStart: String?,
    val reasonEnd: String?,
    val shuffle: Boolean?,
    val skipped: Boolean?,
    val offline: Boolean?,
    val incognito: Boolean?,
)

/** Item de ranking: [name] principal, [sub] linha secundária (artista, data…). */
data class Ranked(
    val name: String,
    val sub: String = "",
    val ms: Long = 0,
    val count: Int = 0,
    val uri: String? = null,
)

/** Estatísticas de um período: "all" (todo o histórico) ou um ano ("2024"). */
data class PeriodStats(
    val key: String,
    val ms: Long,
    val plays: Int,                 // reproduções de 30 s ou mais (critério do Spotify)
    val streams: Int,               // todas as entradas de música
    val distinctTracks: Int,
    val distinctArtists: Int,
    val distinctAlbums: Int,
    val activeDays: Int,
    val topArtists: List<Ranked>,
    val topTracks: List<Ranked>,
    val topAlbums: List<Ranked>,
    val hourMs: List<Long>,         // 24 posições, hora local
    val weekdayMs: List<Long>,      // 7 posições, segunda = 0
    val monthMs: List<Ranked>,      // name = "2024-03" (ou ano em "all"), ms
    val skipped: Int,
    val shuffleStreams: Int,
    val offlineStreams: Int,
    val incognitoStreams: Int,
    val platforms: List<Ranked>,
    val countries: List<Ranked>,
    val reasonsStart: List<Ranked>,
    val mostSkipped: List<Ranked>,  // count = vezes pulada, sub = "% das vezes"
    val alwaysFinished: List<Ranked>,
    val newArtists: Int,
    val topNewArtists: List<Ranked>, // descobertos neste período, sub = data da 1ª vez
    val podcastMs: Long,
    val topShows: List<Ranked>,
    val biggestDay: Ranked?,        // name = data, ms
    val longestStreakDays: Int,
    val streakFrom: String?,
    val streakTo: String?,
    val obsession: Ranked?,         // mais vezes a mesma música num só dia; sub = data
    val firstDay: String?,
    val lastDay: String?,
) {
    val hours: Double get() = ms / 3_600_000.0
    val skipRate: Double get() = if (streams == 0) 0.0 else skipped.toDouble() / streams
}

data class HistoryStats(
    val importedAtMs: Long,
    val totalEntries: Int,
    val files: Int,
    val periods: Map<String, PeriodStats>,
    /** Quando cada um dos principais artistas apareceu pela primeira vez. */
    val artistTimeline: List<Ranked>,
) {
    val all: PeriodStats? get() = periods["all"]
    val years: List<String> get() = periods.keys.filter { it != "all" }.sortedDescending()
}
