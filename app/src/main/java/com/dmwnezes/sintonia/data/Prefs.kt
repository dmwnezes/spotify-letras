package com.dmwnezes.sintonia.data

import android.content.Context
import com.dmwnezes.sintonia.BuildConfig

/** Guarda Client ID, tokens e preferências no próprio celular (armazenamento privado do app). */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("sintonia", Context.MODE_PRIVATE)

    var clientId: String
        get() = sp.getString("client_id", null)?.takeIf { it.isNotBlank() } ?: BuildConfig.SPOTIFY_CLIENT_ID
        set(v) = sp.edit().putString("client_id", v.trim()).apply()

    var accessToken: String?
        get() = sp.getString("access_token", null)
        set(v) = sp.edit().putString("access_token", v).apply()

    var refreshToken: String?
        get() = sp.getString("refresh_token", null)
        set(v) = sp.edit().putString("refresh_token", v).apply()

    var expiresAtMs: Long
        get() = sp.getLong("expires_at", 0L)
        set(v) = sp.edit().putLong("expires_at", v).apply()

    var pendingVerifier: String?
        get() = sp.getString("pkce_verifier", null)
        set(v) = sp.edit().putString("pkce_verifier", v).apply()

    var pendingState: String?
        get() = sp.getString("pkce_state", null)
        set(v) = sp.edit().putString("pkce_state", v).apply()

    var realAudioViz: Boolean
        get() = sp.getBoolean("viz_real", true)
        set(v) = sp.edit().putBoolean("viz_real", v).apply()

    /** Mostrar só o player (capa grande), sem a letra. */
    var playerOnly: Boolean
        get() = sp.getBoolean("player_only", false)
        set(v) = sp.edit().putBoolean("player_only", v).apply()

    /** Mostrar a tradução abaixo de cada linha quando a letra está em outro idioma. */
    var showTranslation: Boolean
        get() = sp.getBoolean("show_translation", true)
        set(v) = sp.edit().putBoolean("show_translation", v).apply()

    /** Letra ao vivo na tela de bloqueio e no widget (serviço em primeiro plano). */
    var liveLyrics: Boolean
        get() = sp.getBoolean("live_lyrics", false)
        set(v) = sp.edit().putBoolean("live_lyrics", v).apply()

    /** Últimas buscas de música (mais recente primeiro). */
    var recentSearches: List<String>
        get() = (sp.getString("recent_searches", "") ?: "").split('\n').filter { it.isNotBlank() }
        set(v) = sp.edit().putString("recent_searches", v.take(8).joinToString("\n")).apply()

    /** Acender a linha atual palavra por palavra (karaokê). */
    var karaoke: Boolean
        get() = sp.getBoolean("karaoke", true)
        set(v) = sp.edit().putBoolean("karaoke", v).apply()

    /** Estilo da letra: EDIT, VIDRO, CILINDRO, COLAGEM ou CLASSICO. */
    var lyricsMode: String
        get() = sp.getString("lyrics_mode", "EDIT") ?: "EDIT"
        set(v) = sp.edit().putString("lyrics_mode", v).apply()

    var themeMode: String
        get() = sp.getString("theme_mode", "AUTO") ?: "AUTO"
        set(v) = sp.edit().putString("theme_mode", v).apply()

    var lyricsFont: String
        get() = sp.getString("lyrics_font", "PADRAO") ?: "PADRAO"
        set(v) = sp.edit().putString("lyrics_font", v).apply()

    var lyricsScale: Float
        get() = sp.getFloat("lyrics_scale", 1f)
        set(v) = sp.edit().putFloat("lyrics_scale", v).apply()

    var vizTheme: String
        get() = sp.getString("viz_theme", "BRILHOS") ?: "BRILHOS"
        set(v) = sp.edit().putString("viz_theme", v).apply()

    /** Versão que a pessoa pediu para não lembrar de novo ("Agora não"). */
    var skippedUpdate: String?
        get() = sp.getString("skipped_update", null)
        set(v) = sp.edit().putString("skipped_update", v).apply()

    /** Permissões que o Spotify concedeu no último login. */
    var grantedScopes: String?
        get() = sp.getString("granted_scopes", null)
        set(v) = sp.edit().putString("granted_scopes", v).apply()

    /** Playlist "Sintonia" no Spotify, onde vão as curtidas. */
    var recoPlaylistId: String?
        get() = sp.getString("reco_playlist", null)
        set(v) = sp.edit().putString("reco_playlist", v).apply()

    /** Mandar automaticamente cada curtida para a playlist. */
    var autoPlaylist: Boolean
        get() = sp.getBoolean("auto_playlist", true)
        set(v) = sp.edit().putBoolean("auto_playlist", v).apply()

    var quizBest: Int
        get() = sp.getInt("quiz_best", 0)
        set(v) = sp.edit().putInt("quiz_best", v).apply()

    /** Ajuste fino da sincronia da letra, em milissegundos. */
    var lyricsOffsetMs: Long
        get() = sp.getLong("lyrics_offset", 0L)
        set(v) = sp.edit().putLong("lyrics_offset", v).apply()

    val isLoggedIn: Boolean get() = refreshToken != null

    fun clearTokens() {
        sp.edit()
            .remove("access_token").remove("refresh_token").remove("expires_at")
            .remove("pkce_verifier").remove("pkce_state")
            .apply()
    }
}
