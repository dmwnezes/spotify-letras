package com.dmwnezes.sintonia.data

import android.net.Uri
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Login no Spotify com PKCE: não precisa de Client Secret dentro do app,
 * só do Client ID (que não é segredo).
 */
object SpotifyAuth {
    const val REDIRECT_URI = "sintonia://callback"

    private val SCOPES = listOf(
        "user-read-currently-playing",
        "user-read-playback-state",
        "user-modify-playback-state",
        "user-top-read",
        "user-read-recently-played",
        "user-read-private",
    )

    fun randomString(bytes: Int = 64): String {
        val b = ByteArray(bytes)
        SecureRandom().nextBytes(b)
        return b64url(b)
    }

    fun challengeFor(verifier: String): String =
        b64url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    fun authorizeUri(clientId: String, challenge: String, state: String): Uri =
        Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("state", state)
            .appendQueryParameter("scope", SCOPES.joinToString(" "))
            .build()

    private fun b64url(b: ByteArray): String =
        Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
