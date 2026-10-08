package com.dmwnezes.sintonia

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.dmwnezes.sintonia.data.NotLoggedInException
import com.dmwnezes.sintonia.data.NowPlaying
import com.dmwnezes.sintonia.data.Prefs
import com.dmwnezes.sintonia.data.SpotifyClient
import com.dmwnezes.sintonia.data.SpotifyException
import com.dmwnezes.sintonia.data.Track
import com.dmwnezes.sintonia.lyrics.LrcLibClient
import com.dmwnezes.sintonia.lyrics.Lyrics
import com.dmwnezes.sintonia.translate.LyricsTranslator
import com.dmwnezes.sintonia.ui.TrackColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface LyricsState {
    data object Idle : LyricsState
    data object Loading : LyricsState
    data class Ready(val lyrics: Lyrics) : LyricsState
    data class Failed(val message: String) : LyricsState
}

sealed interface TranslationState {
    /** Letra já em português, sem letra, ou idioma não reconhecido. */
    data object None : TranslationState
    /** Letra em outro idioma; tradução desligada. [language] = nome do idioma. */
    data class Available(val language: String) : TranslationState
    data class Loading(val language: String, val downloading: Boolean) : TranslationState
    /** Uma tradução por linha da letra (mesma ordem); null quando a linha não precisa. */
    data class Ready(val language: String, val lines: List<String?>) : TranslationState
    data class Failed(val language: String, val message: String) : TranslationState
}

data class PlaybackState(
    val now: NowPlaying? = null,
    val firstLoadDone: Boolean = false,
    val lyrics: LyricsState = LyricsState.Idle,
    val colors: TrackColors = TrackColors.Default,
    val cover: Bitmap? = null,          // capa pequena (notificação e widget)
    val translation: TranslationState = TranslationState.None,
)

/**
 * Acompanha o que está tocando no Spotify, busca letra, cores e tradução.
 * Só consulta enquanto alguém está usando ([acquire]): as telas abertas ou o serviço da tela de bloqueio.
 */
class Playback(
    private val context: Context,
    private val prefs: Prefs,
    private val spotify: SpotifyClient,
    private val lrclib: LrcLibClient,
    private val translator: LyricsTranslator,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val imageLoader by lazy { ImageLoader(context) }

    private val _state = MutableStateFlow(PlaybackState())
    val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private val _loggedOut = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val loggedOut = _loggedOut.asSharedFlow()

    private val users = mutableSetOf<String>()
    private var pollJob: Job? = null
    private var trackJob: Job? = null
    private var translateJob: Job? = null
    private var currentTrackId: String? = null
    private var detectedLang: String? = null

    // ---------- Quem está usando ----------

    fun acquire(who: String) {
        users += who
        start()
    }

    fun release(who: String) {
        users -= who
        if (users.isEmpty()) stop()
    }

    private fun start() {
        if (!prefs.isLoggedIn || pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) delay(pollOnce())
        }
    }

    private fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    fun onLoggedIn() { if (users.isNotEmpty()) start() }

    fun reset() {
        stop()
        trackJob?.cancel()
        translateJob?.cancel()
        currentTrackId = null
        _state.value = PlaybackState()
    }

    // ---------- Consulta ----------

    private suspend fun pollOnce(): Long = try {
        val np = spotify.nowPlaying()
        _state.update { it.copy(now = np, firstLoadDone = true) }
        onTrack(np.track)
        val remaining = np.track?.let { it.durationMs - np.progressMs } ?: Long.MAX_VALUE
        when {
            np.track == null -> 4000L
            !np.isPlaying -> 3000L
            remaining in 0..2500 -> (remaining + 300).coerceAtLeast(400)
            else -> 2000L
        }
    } catch (e: NotLoggedInException) {
        reset()
        _loggedOut.tryEmit(e.message ?: "Sessão expirou.")
        60_000L
    } catch (e: SpotifyException) {
        _state.update { it.copy(firstLoadDone = true) }
        _messages.tryEmit(e.message ?: "Erro do Spotify")
        if (e.code == 429) 15_000L else 5000L
    } catch (e: Exception) {
        _state.update { it.copy(firstLoadDone = true) }
        _messages.tryEmit("Sem conexão com o Spotify.")
        5000L
    }

    private fun onTrack(track: Track?) {
        if (track == null || track.id == currentTrackId) return
        currentTrackId = track.id
        detectedLang = null
        trackJob?.cancel()
        translateJob?.cancel()
        trackJob = scope.launch {
            _state.update { it.copy(lyrics = LyricsState.Loading, translation = TranslationState.None) }
            val art = async { loadArt(track.imageUrl) }
            val lyrics = runCatching { lrclib.find(track) }
            _state.update {
                it.copy(lyrics = lyrics.fold({ l -> LyricsState.Ready(l) }, { LyricsState.Failed("Não consegui buscar a letra agora.") }))
            }
            lyrics.getOrNull()?.let { checkLanguage(track, it) }
            art.await()?.let { (colors, cover) -> _state.update { it.copy(colors = colors, cover = cover) } }
        }
    }

    fun retryLyrics() {
        currentTrackId = null
        _state.value.now?.track?.let(::onTrack)
    }

    private suspend fun loadArt(url: String?): Pair<TrackColors, Bitmap>? {
        if (url == null) return null
        val req = ImageRequest.Builder(context).data(url).allowHardware(false).size(300).build()
        val result = imageLoader.execute(req) as? SuccessResult ?: return null
        val bmp = result.drawable.toBitmap()
        return TrackColors.from(Palette.from(bmp).maximumColorCount(16).generate()) to bmp
    }

    // ---------- Tradução ----------

    private fun lyricLines(l: Lyrics): List<String> = when (l) {
        is Lyrics.Synced -> l.lines.map { it.text }
        is Lyrics.Plain -> l.text.lines()
        else -> emptyList()
    }

    private suspend fun checkLanguage(track: Track, lyrics: Lyrics) {
        val lines = lyricLines(lyrics)
        if (lines.none { it.isNotBlank() }) return
        val code = runCatching { translator.detect(lines.filter { it.isNotBlank() }.take(40).joinToString("\n")) }.getOrNull()
        if (code == null || code == "pt" || !translator.supports(code) || track.id != currentTrackId) return
        detectedLang = code
        val name = translator.displayName(code)
        _state.update { it.copy(translation = TranslationState.Available(name)) }
        if (prefs.showTranslation) translate(track.id, code, lines)
    }

    /** Liga/desliga a tradução (chamado pelo botão na tela de letras). */
    fun setTranslationEnabled(on: Boolean) {
        prefs.showTranslation = on
        val code = detectedLang ?: return
        val lyrics = (_state.value.lyrics as? LyricsState.Ready)?.lyrics ?: return
        val name = translator.displayName(code)
        if (on) {
            if (_state.value.translation !is TranslationState.Ready) translate(currentTrackId ?: return, code, lyricLines(lyrics))
        } else {
            translateJob?.cancel()
            _state.update { it.copy(translation = TranslationState.Available(name)) }
        }
    }

    private fun translate(trackId: String, code: String, lines: List<String>) {
        val name = translator.displayName(code)
        translateJob?.cancel()
        translateJob = scope.launch {
            val needsDownload = runCatching { !translator.isModelReady(code) }.getOrDefault(false)
            _state.update { it.copy(translation = TranslationState.Loading(name, needsDownload)) }
            val result = runCatching { translator.translate(trackId, code, lines) }
            if (trackId != currentTrackId) return@launch
            _state.update {
                it.copy(translation = result.fold(
                    { t -> TranslationState.Ready(name, t) },
                    { TranslationState.Failed(name, "Não consegui traduzir agora. Confira a internet (o tradutor é baixado uma vez).") },
                ))
            }
        }
    }

    // ---------- Controles ----------

    fun togglePlay() = control {
        val playing = _state.value.now?.isPlaying == true
        _state.update { s ->
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
        _state.update { s -> s.copy(now = s.now?.copy(progressMs = ms, receivedAtMs = SystemClock.elapsedRealtime())) }
        spotify.seek(ms)
    }

    fun playTrack(id: String) = control { spotify.playTrack(id) }

    fun queue(id: String, name: String) {
        scope.launch {
            runCatching { spotify.queue(id) }
                .onSuccess { _messages.tryEmit("\"$name\" vai tocar em seguida.") }
                .onFailure { _messages.tryEmit(it.message ?: "Não deu para colocar na fila.") }
        }
    }

    private fun control(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                delay(450)
                pollOnce()
            } catch (e: Exception) {
                _messages.tryEmit(e.message ?: "Não deu para controlar o Spotify.")
            }
        }
    }
}
