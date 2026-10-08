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
