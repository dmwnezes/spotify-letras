package com.dmwnezes.sintonia

import android.app.Application
import com.dmwnezes.sintonia.data.Prefs
import com.dmwnezes.sintonia.data.SpotifyClient
import com.dmwnezes.sintonia.lyrics.LrcLibClient
import com.dmwnezes.sintonia.notebook.NotebookStore
import com.dmwnezes.sintonia.translate.LyricsTranslator
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Peças compartilhadas pelo app inteiro (telas, serviço da tela de bloqueio e widget).
 * Um único SpotifyClient evita que duas partes renovem o login ao mesmo tempo.
 */
object AppGraph {
    lateinit var app: Application
        private set

    fun init(application: Application) {
        app = application
    }

    val prefs by lazy { Prefs(app) }
    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
    val spotify by lazy { SpotifyClient(prefs, http) }
    val lrclib by lazy { LrcLibClient(http) }
    val translator by lazy { LyricsTranslator() }
    val playback by lazy { Playback(app, prefs, spotify, lrclib, translator) }
    val notebook by lazy { NotebookStore(app) }
}

class SintoniaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}
