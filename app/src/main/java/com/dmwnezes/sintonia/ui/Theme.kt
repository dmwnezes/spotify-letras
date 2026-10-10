package com.dmwnezes.sintonia.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.dmwnezes.sintonia.R

enum class ThemeMode(val label: String) { AUTO("Automático"), DARK("Escuro"), LIGHT("Claro");
    companion object { fun from(s: String) = entries.firstOrNull { it.name == s } ?: AUTO }
}

/** Cores de texto e superfícies do tema atual. */
data class AppPalette(
    val dark: Boolean,
    val ink: Color,     // texto principal
    val onInk: Color,   // texto sobre botões preenchidos com [ink]
    val surface: Color, // fundo de telas cheias (busca, quiz…)
    val scrim: Color,   // véu sobre o fundo colorido
)

val DarkPalette = AppPalette(true, Color.White, Color.Black, Color(0xFF0B0712), Color.Black)
val LightPalette = AppPalette(false, Color(0xFF14101C), Color.White, Color(0xFFF6F2FA), Color.White)

val LocalAppPalette = staticCompositionLocalOf { DarkPalette }

/** Atalhos: Palette.ink, Palette.surface… dentro de qualquer tela. */
object Palette {
    val ink: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.ink
    val onInk: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.onInk
    val surface: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.surface
    val scrim: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.scrim
    val dark: Boolean @Composable @ReadOnlyComposable get() = LocalAppPalette.current.dark
}

/** Fonte usada na letra. */
enum class LyricsFont(val label: String) {
    PADRAO("Padrão"), SERIFA("Serifa"), ARREDONDADA("Arredondada"), MANUSCRITA("Manuscrita");

    companion object { fun from(s: String) = entries.firstOrNull { it.name == s } ?: PADRAO }
}

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, vararg weights: Int) = FontFamily(
    weights.map { w -> Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))) }
)

private val Playfair by lazy { variable(R.font.playfair, 400, 500, 600, 700, 800, 900) }
private val Nunito by lazy { variable(R.font.nunito, 400, 500, 600, 700, 800, 900) }
private val Caveat by lazy { variable(R.font.caveat, 400, 500, 600, 700) }

/** Fonte do estilo Edit (a mesma do texto da letra). */
val Montserrat by lazy { variable(R.font.montserrat, 500, 600, 700, 800) }

val LyricsFont.family: FontFamily
    get() = when (this) {
        LyricsFont.PADRAO -> FontFamily.Default
        LyricsFont.SERIFA -> Playfair
        LyricsFont.ARREDONDADA -> Nunito
        LyricsFont.MANUSCRITA -> Caveat
    }

/** A manuscrita é mais miúda: compensa no tamanho. */
val LyricsFont.sizeBoost: Float get() = if (this == LyricsFont.MANUSCRITA) 1.18f else 1f

/** Estilo da letra escolhido pela pessoa. */
data class LyricsStyle(val font: LyricsFont = LyricsFont.PADRAO, val scale: Float = 1f) {
    val family get() = font.family
    val k get() = scale * font.sizeBoost
}

val LocalLyricsStyle = staticCompositionLocalOf { LyricsStyle() }
