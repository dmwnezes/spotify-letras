package com.dmwnezes.sintonia.reco

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PreviewState(val dzId: Long? = null, val loading: Boolean = false, val playing: Boolean = false, val progress: Float = 0f)

/**
 * Toca as prévias de 30 s. Pede o foco de áudio "temporário": o Spotify pausa sozinho
 * enquanto a prévia toca e volta quando ela termina.
 */
class PreviewPlayer(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attrs)
        .setOnAudioFocusChangeListener { if (it == AudioManager.AUDIOFOCUS_LOSS || it == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stop() }
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: MediaPlayer? = null
    private var ticker: Job? = null

    private val _state = MutableStateFlow(PreviewState())
    val state = _state.asStateFlow()

    var onFinished: ((Long) -> Unit)? = null

    fun play(dzId: Long, url: String) {
        stop(releaseFocus = false)
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        _state.value = PreviewState(dzId, loading = true)
        val mp = MediaPlayer()
        player = mp
        runCatching {
            mp.setAudioAttributes(attrs)
            mp.setDataSource(url)
            mp.setOnPreparedListener {
                if (player !== it) return@setOnPreparedListener
                it.start()
                _state.value = PreviewState(dzId, playing = true)
                startTicker(dzId)
            }
            mp.setOnCompletionListener {
                stop()
                onFinished?.invoke(dzId)
            }
            mp.setOnErrorListener { _, _, _ -> stop(); true }
            mp.prepareAsync()
        }.onFailure { stop() }
    }

    fun toggle() {
        val mp = player ?: return
        val s = _state.value
        if (s.playing) { mp.pause(); _state.value = s.copy(playing = false) }
        else if (!s.loading) { mp.start(); _state.value = s.copy(playing = true) }
    }

    fun stop(releaseFocus: Boolean = true) {
        ticker?.cancel()
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
        _state.value = PreviewState()
        if (releaseFocus) audio.abandonAudioFocusRequest(focus)
    }

    private fun startTicker(dzId: Long) {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val mp = player ?: break
                val d = runCatching { mp.duration }.getOrDefault(30_000).coerceAtLeast(1)
                val p = runCatching { mp.currentPosition }.getOrDefault(0)
                if (_state.value.dzId == dzId) _state.value = _state.value.copy(progress = p.toFloat() / d)
                delay(120)
            }
        }
    }
}
