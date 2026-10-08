package com.dmwnezes.sintonia.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette

/** Cores tiradas da capa do álbum, usadas no fundo e no visualizer. */
data class TrackColors(
    val deep: Color,      // fundo escuro
    val base: Color,      // tom principal
    val glow1: Color,     // brilhos do visualizer
    val glow2: Color,
    val glow3: Color,
) {
    companion object {
        val Default = TrackColors(
            deep = Color(0xFF120A24),
            base = Color(0xFF3B1E6E),
            glow1 = Color(0xFFE0457B),
            glow2 = Color(0xFF7B5CFF),
            glow3 = Color(0xFF2EC4B6),
        )

        fun from(p: Palette): TrackColors {
            fun c(sw: Palette.Swatch?) = sw?.rgb?.let { Color(it) }
            val dominant = c(p.dominantSwatch) ?: Default.base
            val vibrant = c(p.vibrantSwatch) ?: c(p.lightVibrantSwatch) ?: dominant
            val muted = c(p.mutedSwatch) ?: c(p.darkVibrantSwatch) ?: dominant
            val darkV = c(p.darkVibrantSwatch) ?: c(p.darkMutedSwatch) ?: dominant
            val lightV = c(p.lightVibrantSwatch) ?: c(p.lightMutedSwatch) ?: vibrant
            return TrackColors(
                deep = darken(darkV, 0.18f),
                base = darken(muted, 0.42f),
                glow1 = brighten(vibrant),
                glow2 = brighten(lightV),
                glow3 = brighten(lerp(dominant, vibrant, 0.5f)),
            )
        }

        /** Escurece até a luminosidade [l] (0–1), mantendo o tom. */
        private fun darken(c: Color, l: Float): Color {
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(c.toArgb(), hsl)
            hsl[2] = minOf(hsl[2], l)
            hsl[1] = minOf(hsl[1], 0.75f)
            return Color(ColorUtils.HSLToColor(hsl))
        }

        /** Garante um brilho visível sobre fundo escuro. */
        private fun brighten(c: Color): Color {
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(c.toArgb(), hsl)
            hsl[2] = hsl[2].coerceIn(0.5f, 0.72f)
            hsl[1] = maxOf(hsl[1], 0.45f)
            return Color(ColorUtils.HSLToColor(hsl))
        }
    }
}
