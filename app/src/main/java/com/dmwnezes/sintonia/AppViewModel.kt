package com.dmwnezes.sintonia

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dmwnezes.sintonia.data.NowPlaying
import com.dmwnezes.sintonia.data.ProfileData
import com.dmwnezes.sintonia.data.SpotifyAuth
import com.dmwnezes.sintonia.data.TimeRange
import com.dmwnezes.sintonia.ui.LyricsFont
import com.dmwnezes.sintonia.ui.LyricsStyle
import com.dmwnezes.sintonia.ui.ThemeMode
import com.dmwnezes.sintonia.ui.TrackColors
import com.dmwnezes.sintonia.viz.VizTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    // vindos do Playback
    val now: NowPlaying? = null,
    val firstLoadDone: Boolean = false,
    val lyrics: LyricsState = LyricsState.Idle,
    val colors: TrackColors = TrackColors.Default,
    val translation: TranslationState = TranslationState.None,
    // preferências
    val realAudioViz: Boolean = true,
    val vizTheme: VizTheme = VizTheme.BRILHOS,
    val playerOnly: Boolean = false,
    val showTranslation: Boolean = true,
    val karaoke: Boolean = true,
    val editStyle: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val lyricsStyle: LyricsStyle = LyricsStyle(),
    val liveLyrics: Boolean = false,
    val lyricsOffsetMs: Long = 0,
    val message: String? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = AppGraph.prefs
    private val spotify = AppGraph.spotify
    private val playback = AppGraph.playback

    private val _local = MutableStateFlow(baseState())
    val ui = combine(_local, playback.state) { u, p ->
        u.copy(now = p.now, firstLoadDone = p.firstLoadDone, lyrics = p.lyrics, colors = p.colors, translation = p.translation)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, _local.value)

    private val _profileRange = MutableStateFlow(TimeRange.MEDIUM)
    val profileRange = _profileRange.asStateFlow()
    private val _profile = MutableStateFlow<ProfileState>(ProfileState.Loading)
    val profile = _profile.asStateFlow()

    init {
        viewModelScope.launch { playback.messages.collect { m -> _local.update { it.copy(message = m) } } }
        viewModelScope.launch {
            playback.loggedOut.collect { m ->
                _local.update { baseState().copy(authError = m) }
                _profile.value = ProfileState.Loading
            }
        }
    }

    private fun baseState() = UiState(
        clientId = prefs.clientId,
        loggedIn = prefs.isLoggedIn,
        realAudioViz = prefs.realAudioViz,
        vizTheme = VizTheme.from(prefs.vizTheme),
        playerOnly = prefs.playerOnly,
        showTranslation = prefs.showTranslation,
        karaoke = prefs.karaoke,
        editStyle = prefs.lyricsMode == "EDIT",
        themeMode = ThemeMode.from(prefs.themeMode),
        lyricsStyle = LyricsStyle(LyricsFont.from(prefs.lyricsFont), prefs.lyricsScale),
        liveLyrics = prefs.liveLyrics,
        lyricsOffsetMs = prefs.lyricsOffsetMs,
    )

    // ---------- Login ----------

    fun saveClientId(id: String) {
        prefs.clientId = id
        _local.update { it.copy(clientId = prefs.clientId, authError = null) }
    }

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
            error != null -> _local.update {
                it.copy(authError = if (error == "access_denied") "Login cancelado." else "O Spotify recusou o login ($error).")
            }
            code == null || verifier == null -> _local.update { it.copy(authError = "Retorno de login inválido. Tente de novo.") }
            uri.getQueryParameter("state") != prefs.pendingState -> _local.update { it.copy(authError = "Retorno de login não confere. Tente de novo.") }
            else -> viewModelScope.launch {
                _local.update { it.copy(loggingIn = true, authError = null) }
                try {
                    spotify.exchangeCode(code, verifier)
                    prefs.pendingVerifier = null
                    prefs.pendingState = null
                    _local.update { it.copy(loggedIn = true, loggingIn = false) }
                    playback.onLoggedIn()
                } catch (e: Exception) {
                    _local.update {
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
        prefs.clearTokens()
        playback.reset()
        _local.update { baseState() }
        _profile.value = ProfileState.Loading
    }

    // ---------- Reprodução (delegado ao Playback) ----------

    fun startPolling() = playback.acquire("tela")
    fun stopPolling() = playback.release("tela")
    fun togglePlay() = playback.togglePlay()
    fun next() = playback.next()
    fun previous() = playback.previous()
    fun seek(ms: Long) = playback.seek(ms)
    fun retryLyrics() = playback.retryLyrics()
    fun playTrack(id: String) = playback.playTrack(id)
    fun restartAndPlay() = playback.restartAndPlay()
    fun ensurePlaying() = playback.ensurePlaying()
    fun queue(id: String, name: String) = playback.queue(id, name)

    fun clearMessage() = _local.update { it.copy(message = null) }
    fun showMessage(m: String) = _local.update { it.copy(message = m) }

    // ---------- Preferências ----------

    fun setRealAudioViz(on: Boolean) {
        prefs.realAudioViz = on
        _local.update { it.copy(realAudioViz = on) }
    }

    fun setVizTheme(t: VizTheme) {
        prefs.vizTheme = t.name
        _local.update { it.copy(vizTheme = t) }
    }

    fun setPlayerOnly(on: Boolean) {
        prefs.playerOnly = on
        _local.update { it.copy(playerOnly = on) }
    }

    fun setShowTranslation(on: Boolean) {
        playback.setTranslationEnabled(on)
        _local.update { it.copy(showTranslation = on) }
    }

    fun setThemeMode(m: ThemeMode) {
        prefs.themeMode = m.name
        _local.update { it.copy(themeMode = m) }
    }

    fun setLyricsStyle(st: LyricsStyle) {
        prefs.lyricsFont = st.font.name
        prefs.lyricsScale = st.scale
        _local.update { it.copy(lyricsStyle = st) }
    }

    fun setEditStyle(on: Boolean) {
        prefs.lyricsMode = if (on) "EDIT" else "CLASSICO"
        _local.update { it.copy(editStyle = on) }
    }

    fun setKaraoke(on: Boolean) {
        prefs.karaoke = on
        _local.update { it.copy(karaoke = on) }
    }

    fun setLiveLyrics(on: Boolean) {
        prefs.liveLyrics = on
        _local.update { it.copy(liveLyrics = on) }
    }

    fun nudgeOffset(deltaMs: Long) {
        val v = (prefs.lyricsOffsetMs + deltaMs).coerceIn(-5000, 5000)
        prefs.lyricsOffsetMs = v
        _local.update { it.copy(lyricsOffsetMs = v) }
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
                _profile.value = ProfileState.Ready(ProfileData(user.await(), artists.await(), tracks.await(), recent.await()))
            } catch (e: Exception) {
                _profile.value = ProfileState.Failed(e.message ?: "Não consegui carregar o perfil.")
            }
        }
    }
}
