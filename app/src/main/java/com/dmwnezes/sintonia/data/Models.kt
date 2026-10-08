package com.dmwnezes.sintonia.data

data class Track(
    val id: String,
    val name: String,
    val artists: List<String>,
    val album: String,
    val imageUrl: String?,
    val durationMs: Long,
) {
    val artistLine: String get() = artists.joinToString(", ")
}

/** Estado de reprodução recebido do Spotify, com o instante (relógio do celular) em que chegou. */
data class NowPlaying(
    val track: Track?,
    val isPlaying: Boolean,
    val progressMs: Long,
    val receivedAtMs: Long,
) {
    /** Posição estimada agora, avançando o relógio entre uma consulta e outra. */
    fun positionAt(nowMs: Long): Long {
        val dur = track?.durationMs ?: Long.MAX_VALUE
        val p = if (isPlaying) progressMs + (nowMs - receivedAtMs) else progressMs
        return p.coerceIn(0, dur)
    }
}

data class Artist(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val genres: List<String>,
)

data class RecentPlay(val track: Track, val playedAtMs: Long)

data class UserProfile(val name: String, val imageUrl: String?)

enum class TimeRange(val apiValue: String, val label: String) {
    SHORT("short_term", "4 semanas"),
    MEDIUM("medium_term", "6 meses"),
    LONG("long_term", "Desde sempre"),
}

data class ProfileData(
    val user: UserProfile?,
    val topArtists: List<Artist>,
    val topTracks: List<Track>,
    val recent: List<RecentPlay>,
)
