package com.dmwnezes.sintonia.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.dmwnezes.sintonia.viz.AudioSpectrum
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/** Fundo com as cores da capa, em transição suave quando a música muda. */
@Composable
fun AppBackground(colors: TrackColors, modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    val deep by animateColorAsState(colors.deep, tween(1200), label = "deep")
    val base by animateColorAsState(colors.base, tween(1200), label = "base")
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(base, deep, Color(0xFF050308))))
    ) { content() }
}

/**
 * Visualizer: brilhos que respiram com graves/médios/agudos e barras de espectro no rodapé.
 * [source] devolve os níveis reais do áudio (ou null para usar a animação de reserva).
 */
@Composable
fun VisualizerCanvas(
    colors: TrackColors,
    playing: Boolean,
    source: () -> FloatArray?,
    modifier: Modifier = Modifier,
) {
    val bands = AudioSpectrum.BANDS
    val shown = remember { FloatArray(bands) }
    var time by remember { mutableFloatStateOf(0f) }
    val isPlaying by rememberUpdatedState(playing)
    val currentSource by rememberUpdatedState(source)

    val g1 by animateColorAsState(colors.glow1, tween(1200), label = "g1")
    val g2 by animateColorAsState(colors.glow2, tween(1200), label = "g2")
    val g3 by animateColorAsState(colors.glow3, tween(1200), label = "g3")

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceAtMost(0.1f)
                last = now
                val target = currentSource() ?: fallbackLevels(time, isPlaying, bands)
                for (i in 0 until bands) {
                    val up = target[i] > shown[i]
                    val speed = if (up) 22f else 7f // sobe rápido, desce devagar
                    shown[i] += (target[i] - shown[i]) * min(1f, dt * speed)
                }
                time += dt
            }
        }
    }

    Canvas(modifier.fillMaxSize()) {
        val t = time
        val bass = avg(shown, 0, 6)
        val mid = avg(shown, 6, 26)
        val treble = avg(shown, 26, bands)
        drawGlow(g1, t * 0.13f, 0.30f, 0.30f, 0.55f + bass * 0.55f, 0.50f + bass * 0.35f)
        drawGlow(g2, t * 0.09f + 2.1f, 0.72f, 0.42f, 0.45f + mid * 0.6f, 0.38f + mid * 0.35f)
        drawGlow(g3, t * 0.11f + 4.2f, 0.45f, 0.78f, 0.40f + treble * 0.7f, 0.30f + treble * 0.4f)
        drawSpectrum(shown, g1, g2)
    }
}

private fun avg(a: FloatArray, from: Int, to: Int): Float {
    var s = 0f
    for (i in from until to) s += a[i]
    return s / (to - from)
}

private fun DrawScope.drawGlow(color: Color, phase: Float, cx: Float, cy: Float, radiusK: Float, alpha: Float) {
    val r = size.minDimension * radiusK
    val center = Offset(
        x = size.width * (cx + 0.10f * cos(phase * 2 * PI.toFloat())),
        y = size.height * (cy + 0.07f * sin(phase * 2 * PI.toFloat() * 1.3f)),
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha.coerceIn(0f, 0.85f)), color.copy(alpha = 0f)),
            center = center,
            radius = r,
        ),
        radius = r,
        center = center,
    )
}

private fun DrawScope.drawSpectrum(levels: FloatArray, c1: Color, c2: Color) {
    val n = levels.size
    val maxH = size.height * 0.16f
    val gap = size.width / n
    val barW = gap * 0.55f
    val brush = Brush.verticalGradient(listOf(c2.copy(alpha = 0.55f), c1.copy(alpha = 0.12f)))
    for (i in 0 until n) {
        val h = (levels[i] * maxH).coerceAtLeast(barW)
        drawRoundRect(
            brush = brush,
            topLeft = Offset(i * gap + (gap - barW) / 2f, size.height - h - size.height * 0.015f),
            size = Size(barW, h),
            cornerRadius = CornerRadius(barW / 2f),
        )
    }
}

/** Animação de reserva quando não há leitura de áudio: ondas suaves num ritmo de ~100 BPM. */
private fun fallbackLevels(t: Float, playing: Boolean, bands: Int): FloatArray {
    val beat = (sin(t * 2f * PI.toFloat() * (100f / 60f)) + 1f) / 2f
    val pulse = 0.65f + 0.35f * beat * beat
    val amp = if (playing) 1f else 0.12f
    return FloatArray(bands) { i ->
        val x = i / bands.toFloat()
        val envelope = 0.18f + 0.62f * exp(-x * 2.2f)
        val wave = 0.55f + 0.45f * sin(t * (1.7f + i * 0.11f) + i * 0.8f)
        (envelope * wave * (if (i < 8) pulse else 1f) * amp).coerceIn(0f, 1f)
    }
}
