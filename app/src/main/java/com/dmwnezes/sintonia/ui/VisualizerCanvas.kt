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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import com.dmwnezes.sintonia.viz.AudioSpectrum
import com.dmwnezes.sintonia.viz.VizTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Fundo com as cores da capa, em transição suave quando a música muda. */
@Composable
fun AppBackground(colors: TrackColors, modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    val dark = Palette.dark
    val a by animateColorAsState(if (dark) colors.base else colors.soft2, tween(1200), label = "a")
    val b by animateColorAsState(if (dark) colors.deep else colors.soft1, tween(1200), label = "b")
    val end = if (dark) Color(0xFF050308) else Color(0xFFFBF8FD)
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(a, b, end)))
    ) { content() }
}

/** Partículas do tema "Partículas" (estado mutável, atualizado a cada quadro). */
private class Particles(n: Int) {
    val x = FloatArray(n); val y = FloatArray(n)
    val vx = FloatArray(n); val vy = FloatArray(n)
    val life = FloatArray(n); val size = FloatArray(n); val hue = IntArray(n)
    var cursor = 0
}

/**
 * Visualizer com quatro temas. [source] devolve os níveis reais do áudio (0–1 por faixa)
 * ou null para usar a animação de reserva.
 */
@Composable
fun VisualizerCanvas(
    colors: TrackColors,
    playing: Boolean,
    source: () -> FloatArray?,
    modifier: Modifier = Modifier,
    theme: VizTheme = VizTheme.BRILHOS,
) {
    val bands = AudioSpectrum.BANDS
    val shown = remember { FloatArray(bands) }
    val peaks = remember { FloatArray(bands) }
    val particles = remember { Particles(160) }
    var time by remember { mutableFloatStateOf(0f) }
    val isPlaying by rememberUpdatedState(playing)
    val currentSource by rememberUpdatedState(source)
    val currentTheme by rememberUpdatedState(theme)

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
                    shown[i] += (target[i] - shown[i]) * min(1f, dt * if (up) 22f else 7f)
                    // picos que caem devagar (tema Retrô)
                    peaks[i] = if (shown[i] > peaks[i]) shown[i] else (peaks[i] - dt * 0.35f).coerceAtLeast(0f)
                }
                if (currentTheme == VizTheme.PARTICULAS) stepParticles(particles, shown, dt, isPlaying)
                time += dt
            }
        }
    }

    Canvas(modifier.fillMaxSize()) {
        val t = time
        val bass = avg(shown, 0, 6)
        val mid = avg(shown, 6, 26)
        val treble = avg(shown, 26, bands)
        when (theme) {
            VizTheme.BRILHOS -> {
                drawGlow(g1, t * 0.13f, 0.30f, 0.30f, 0.55f + bass * 0.55f, 0.50f + bass * 0.35f)
                drawGlow(g2, t * 0.09f + 2.1f, 0.72f, 0.42f, 0.45f + mid * 0.6f, 0.38f + mid * 0.35f)
                drawGlow(g3, t * 0.11f + 4.2f, 0.45f, 0.78f, 0.40f + treble * 0.7f, 0.30f + treble * 0.4f)
                drawSpectrum(shown, g1, g2)
            }
            VizTheme.ONDAS -> {
                drawGlow(g2, t * 0.07f, 0.5f, 0.25f, 0.6f, 0.22f)
                drawWave(g3, t, 0.62f, 0.05f + treble * 0.10f, 1.6f, 0.9f, 0.30f)
                drawWave(g2, t, 0.68f, 0.06f + mid * 0.13f, 1.1f, -0.7f, 0.34f)
                drawWave(g1, t, 0.75f, 0.07f + bass * 0.17f, 0.7f, 0.5f, 0.42f)
            }
            VizTheme.PARTICULAS -> {
                drawGlow(g1, t * 0.1f, 0.5f, 0.55f, 0.35f + bass * 0.5f, 0.25f + bass * 0.4f)
                drawParticles(particles, listOf(g1, g2, g3))
            }
            VizTheme.RETRO -> {
                drawGlow(g2, t * 0.05f, 0.5f, 0.85f, 0.7f, 0.18f)
                drawRetro(shown, peaks, g1, g2, g3)
            }
        }
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
        brush = Brush.radialGradient(listOf(color.copy(alpha = alpha.coerceIn(0f, 0.85f)), color.copy(alpha = 0f)), center, r),
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

/** Onda suave preenchida até o rodapé. */
private fun DrawScope.drawWave(color: Color, t: Float, baseY: Float, amp: Float, freq: Float, speed: Float, alpha: Float) {
    val path = Path()
    val steps = 48
    val y0 = size.height * baseY
    val a = size.height * amp
    path.moveTo(0f, size.height)
    for (i in 0..steps) {
        val x = size.width * i / steps
        val u = i / steps.toFloat()
        val y = y0 + a * sin(u * 2 * PI.toFloat() * freq + t * speed) * (0.6f + 0.4f * sin(u * 5f + t * 0.8f))
        path.lineTo(x, y)
    }
    path.lineTo(size.width, size.height)
    path.close()
    drawPath(path, Brush.verticalGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = 0.02f)), startY = y0 - a, endY = size.height))
}

private fun stepParticles(p: Particles, levels: FloatArray, dt: Float, playing: Boolean) {
    val n = p.x.size
    val bass = avg(levels, 0, 6)
    val treble = avg(levels, 26, levels.size)
    // Mais partículas quando o grave bate forte.
    val spawn = if (playing) (bass * 9f + 0.6f).toInt() else 0
    repeat(spawn) {
        val i = p.cursor
        p.cursor = (p.cursor + 1) % n
        val ang = Random.nextFloat() * 2f * PI.toFloat()
        val speed = 0.05f + bass * 0.35f + Random.nextFloat() * 0.08f
        p.x[i] = 0.5f; p.y[i] = 0.55f
        p.vx[i] = cos(ang) * speed; p.vy[i] = sin(ang) * speed * 0.8f - 0.04f
        p.life[i] = 1f
        p.size[i] = 2.5f + treble * 6f + Random.nextFloat() * 3f
        p.hue[i] = Random.nextInt(3)
    }
    for (i in 0 until n) {
        if (p.life[i] <= 0f) continue
        p.x[i] += p.vx[i] * dt
        p.y[i] += p.vy[i] * dt
        p.vy[i] -= 0.02f * dt // sobe devagar
        p.life[i] -= dt * 0.45f
    }
}

private fun DrawScope.drawParticles(p: Particles, palette: List<Color>) {
    for (i in p.x.indices) {
        val life = p.life[i]
        if (life <= 0f) continue
        val c = palette[p.hue[i]]
        val center = Offset(p.x[i] * size.width, p.y[i] * size.height)
        val r = p.size[i] * density * (0.6f + life * 0.6f)
        drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.9f * life), c.copy(alpha = 0f)), center, r * 2.2f), r * 2.2f, center)
        drawCircle(Color.White.copy(alpha = 0.55f * life), r * 0.45f, center)
    }
}

/** Equalizador clássico em blocos, com picos que caem e reflexo. */
private fun DrawScope.drawRetro(levels: FloatArray, peaks: FloatArray, c1: Color, c2: Color, c3: Color) {
    val cols = 24
    val step = levels.size / cols
    val left = size.width * 0.06f
    val usable = size.width * 0.88f
    val gap = usable / cols
    val bw = gap * 0.72f
    val baseY = size.height * 0.80f
    val maxH = size.height * 0.30f
    val blocks = 14
    val blockH = maxH / blocks
    for (c in 0 until cols) {
        var v = 0f; var pk = 0f
        for (k in 0 until step) { v = maxOf(v, levels[c * step + k]); pk = maxOf(pk, peaks[c * step + k]) }
        val lit = (v * blocks).toInt().coerceIn(1, blocks)
        val x = left + c * gap
        for (b in 0 until lit) {
            val f = b / (blocks - 1f)
            val col = if (f < 0.6f) lerp(c3, c1, f / 0.6f) else lerp(c1, c2, (f - 0.6f) / 0.4f)
            val y = baseY - (b + 1) * blockH
            drawRoundRect(col.copy(alpha = 0.85f), Offset(x, y + blockH * 0.15f), Size(bw, blockH * 0.7f), CornerRadius(3f))
            // reflexo
            drawRoundRect(col.copy(alpha = 0.12f * (1f - f)), Offset(x, baseY + b * blockH + blockH * 0.15f), Size(bw, blockH * 0.7f), CornerRadius(3f))
        }
        val py = baseY - (pk * blocks).coerceIn(1f, blocks.toFloat()) * blockH - blockH * 0.4f
        drawRoundRect(Color.White.copy(alpha = 0.9f), Offset(x, py), Size(bw, blockH * 0.28f), CornerRadius(2f))
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
