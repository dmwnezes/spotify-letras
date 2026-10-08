package com.dmwnezes.sintonia

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.dmwnezes.sintonia.data.NotLoggedInException
import com.dmwnezes.sintonia.data.NowPlaying
import com.dmwnezes.sintonia.data.Prefs
import com.dmwnezes.sintonia.data.ProfileData
import com.dmwnezes.sintonia.data.SpotifyAuth
import com.dmwnezes.sintonia.data.SpotifyClient
import com.dmwnezes.sintonia.data.SpotifyException
import com.dmwnezes.sintonia.data.TimeRange
import com.dmwnezes.sintonia.data.Track
import com.dmwnezes.sintonia.lyrics.LrcLibClient
import com.dmwnezes.sintonia.lyrics.Lyrics
import com.dmwnezes.sintonia.ui.TrackColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

sealed interface LyricsState {
    data object Idle : LyricsState
    data object Loading : LyricsState
    data class Ready(val lyrics: Lyrics) : LyricsState
    data class Failed(val message: String) : LyricsState
}

sealed interface ProfileState {
    data object Loading : ProfileState
    data class Ready(val data: ProfileData) : ProfileState
    data class Failed(val message: String) : ProfileState
}

data class UiState(
    val clientId: String = "",
    val loggedIn: Boolean = false,
    val loggingIn: Boolean = false,
    val authError: String? = null,
    val now: NowPlaying? = null,
    val firstLoadDone: Boolean = false,
    val lyrics: LyricsState = LyricsState.Idle,
    val colors: TrackColors = TrackColors.Default,
    val realAudioViz: Boolean = true,
    val playerOnly: Boolean = false,
    val lyricsOffsetMs: Long = 0,
    val message: String? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val spotify = SpotifyClient(prefs, http)
    private val lrclib = LrcLibClient(http)
    private val imageLoader = ImageLoader(app)

    private val _ui = MutableStateFlow(
        UiState(
            clientId = prefs.clientId,
            loggedIn = prefs.isLoggedIn,
            realAudioViz = prefs.realAudioViz,
            playerOnly = prefs.playerOnly,
            lyricsOffsetMs = prefs.lyricsOffsetMs,
        )
    )
    val ui = _ui.asStateFlow()

    private val _profileRange = MutableStateFlow(TimeRange.MEDIUM)
    val profileRange = _profileRange.asStateFlow()
    private val _profile = MutableStateFlow<ProfileState>(ProfileState.Loading)
    val profile = _profile.asStateFlow()

    private var pollJob: Job? = null
    private var lyricsJob: Job? = null
    private var currentTrackId: String? = null

    // ---------- Login ----------

    fun saveClientId(id: String) {
        prefs.clientId = id
        _ui.update { it.copy(clientId = prefs.clientId, authError = null) }
    }

    /** Prepara o PKCE e devolve o endereço da tela de login do Spotify. */
    fun loginUri(): Uri {
        val verifier = SpotifyAuth.randomString()
        val state = SpotifyAuth.randomString(16)
        prefs.pendingVerifier = verifier
        prefs.pendingState = state
        return SpotifyAuth.authorizeUri(prefs.clientId, SpotifyAuth.challengeFor(verifier), state)
    }

    fun handleCallback(uri: Uri) {
        if (uri.scheme != "sintonia") return
        val error = uri.getQueryParameter("error")
        val code = uri.getQueryParameter("code")
        val verifier = prefs.pendingVerifier
        when {
            error != null -> _ui.update {
                it.copy(authError = if (error == "access_denied") "Login cancelado." else "O Spotify recusou o login ($error).")
            }
            code == null || verifier == null -> _ui.update { it.copy(authError = "Retorno de login inválido. Tente de novo.") }
            uri.getQueryParameter("state") != prefs.pendingState -> _ui.update { it.copy(authError = "Retorno de login não confere. Tente de novo.") }
            else -> viewModelScope.launch {
                _ui.update { it.copy(loggingIn = true, authError = null) }
                try {
                    spotify.exchangeCode(code, verifier)
                    prefs.pendingVerifier = null
                    prefs.pendingState = null
                    _ui.update { it.copy(loggedIn = true, loggingIn = false) }
                    startPolling()
                } catch (e: Exception) {
                    _ui.update {
                        it.copy(
                            loggingIn = false,
                            authError = (e.message ?: "Falha no login.") +
                                "\nConfira se o Redirect URI no painel do Spotify é exatamente ${SpotifyAuth.REDIRECT_URI}",
                        )
                    }
                }
            }
        }
    }

    fun logout() {
        stopPolling()
        prefs.clearTokens()
        currentTrackId = null
        _ui.update { UiState(clientId = prefs.clientId, realAudioViz = prefs.realAudioViz, playerOnly = prefs.playerOnly, lyricsOffsetMs = prefs.lyricsOffsetMs) }
        _profile.value = ProfileState.Loading
    }

    // ---------- Acompanhar a música ----------

    fun startPolling() {
        if (!prefs.isLoggedIn || pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                val waitMs = pollOnce()
                delay(waitMs)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    /** Consulta o Spotify uma vez e devolve quanto esperar até a próxima consulta. */
    private suspend fun pollOnce(): Long {
        return try {
            val np = spotify.nowPlaying()
            _ui.update { it.copy(now = np, firstLoadDone = true) }
            onTrack(np.track)
            val remaining = np.track?.let { it.durationMs - np.progressMs } ?: Long.MAX_VALUE
            when {
                np.track == null -> 4000L
                !np.isPlaying -> 2500L
                remaining in 0..2500 -> (remaining + 300).coerceAtLeast(400)
                else -> 2000L
            }
        } catch (e: NotLoggedInException) {
            logout()
            _ui.update { it.copy(authError = e.message) }
            60_000L
        } catch (e: SpotifyException) {
            _ui.update { it.copy(firstLoadDone = true, message = e.message) }
            if (e.code == 429) 15_000L else 5000L
        } catch (e: Exception) {
            _ui.update { it.copy(firstLoadDone = true, message = "Sem conexão com o Spotify.") }
            5000L
        }
    }

    private fun onTrack(track: Track?) {
        if (track == null || track.id == currentTrackId) return
        currentTrackId = track.id
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            _ui.update { it.copy(lyrics = LyricsState.Loading) }
            val colorsJob = async { extractColors(track.imageUrl) }
            val lyrics = runCatching { lrclib.find(track) }
            _ui.update {
                it.copy(
                    lyrics = lyrics.fold(
                        { l -> LyricsState.Ready(l) },
                        { LyricsState.Failed("Não consegui buscar a letra agora.") },
                    )
                )
            }
            colorsJob.await()?.let { c -> _ui.update { it.copy(colors = c) } }
        }
    }

    fun retryLyrics() {
        currentTrackId = null
        _ui.value.now?.track?.let(::onTrack)
    }

    private suspend fun extractColors(url: String?): TrackColors? {
        if (url == null) return null
        val req = ImageRequest.Builder(getApplication()).data(url).allowHardware(false).size(160).build()
        val result = imageLoader.execute(req) as? SuccessResult ?: return null
        val bmp = result.drawable.toBitmap()
        return TrackColors.from(Palette.from(bmp).maximumColorCount(16).generate())
    }

    // ---------- Controles ----------

    fun togglePlay() = control {
        val playing = _ui.value.now?.isPlaying == true
        _ui.update { s ->
            s.copy(now = s.now?.let { n ->
                val t = SystemClock.elapsedRealtime()
                n.copy(isPlaying = !playing, progressMs = n.positionAt(t), receivedAtMs = t)
            })
        }
        if (playing) spotify.pause() else spotify.play()
    }

    fun next() = control { spotify.next() }
    fun previous() = control { spotify.previous() }

    fun seek(ms: Long) = control {
        _ui.update { s ->
            s.copy(now = s.now?.copy(progressMs = ms, receivedAtMs = SystemClock.elapsedRealtime()))
        }
        spotify.seek(ms)
    }

    private fun control(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                delay(450)
                pollOnce()
            } catch (e: Exception) {
                _ui.update { it.copy(message = e.message ?: "Não deu para controlar o Spotify.") }
            }
        }
    }

    fun clearMessage() = _ui.update { it.copy(message = null) }

    // ---------- Preferências ----------

    fun setRealAudioViz(on: Boolean) {
        prefs.realAudioViz = on
        _ui.update { it.copy(realAudioViz = on) }
    }

    fun setPlayerOnly(on: Boolean) {
        prefs.playerOnly = on
        _ui.update { it.copy(playerOnly = on) }
    }

    fun nudgeOffset(deltaMs: Long) {
        val v = (prefs.lyricsOffsetMs + deltaMs).coerceIn(-5000, 5000)
        prefs.lyricsOffsetMs = v
        _ui.update { it.copy(lyricsOffsetMs = v) }
    }

    // ---------- Perfil ----------

    fun loadProfile(range: TimeRange = _profileRange.value, force: Boolean = false) {
        if (!force && range == _profileRange.value && _profile.value is ProfileState.Ready) return
        _profileRange.value = range
        _profile.value = ProfileState.Loading
        viewModelScope.launch {
            try {
                val user = async { runCatching { spotify.me() }.getOrNull() }
                val artists = async { spotify.topArtists(range) }
                val tracks = async { spotify.topTracks(range) }
                val recent = async { spotify.recentlyPlayed() }
                _profile.value = ProfileState.Ready(
                    ProfileData(user.await(), artists.await(), tracks.await(), recent.await())
                )
            } catch (e: Exception) {
                _profile.value = ProfileState.Failed(e.message ?: "Não consegui carregar o perfil.")
            }
        }
    }
}
