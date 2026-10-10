package com.dmwnezes.sintonia.edit

import android.graphics.Bitmap
import android.graphics.Canvas
import com.dmwnezes.sintonia.lyrics.LyricLine

/** Os jeitos de mostrar a letra. Todos menos o Clássico são animações em tela cheia. */
enum class LyricsMode(val label: String, val hint: String) {
    EDIT("Edit", "Fundo preto, creme com brilho e VHS"),
    VIDRO("Vidro", "Profundidade 3D nas cores da capa"),
    CILINDRO("Cilindro", "Letra ondulada sobre a capa desfocada"),
    COLAGEM("Colagem", "Palavras gigantes, recortes e grão"),
    CLASSICO("Clássico", "Lista com o visualizer");

    val animated: Boolean get() = this != CLASSICO

    companion object {
        fun from(s: String?) = entries.firstOrNull { it.name == s } ?: EDIT
    }
}

/** Arte da música para os estilos que usam a capa ou as cores dela. Cores em ARGB. */
data class StyleArt(val cover: Bitmap?, val deep: Int, val base: Int, val glow1: Int, val glow2: Int, val glow3: Int)

/** Um estilo animado de letra. Desenha num Canvas comum: serve para a tela e para os testes. */
interface StyleRenderer {
    /** Altura (0–1) do centro do texto. */
    var centerFraction: Float
    /** Largura máxima do texto (0–1). */
    var maxWidthFraction: Float
    fun setLines(lines: List<LyricLine>)
    fun setArt(art: StyleArt?) {}
    fun draw(c: Canvas, w: Float, h: Float, posMs: Long, tSec: Float)
}

/** Número pseudoaleatório 0–1 estável para a mesma semente. */
internal fun rnd(seed: Int): Float {
    var x = seed * 374761393 + 668265263
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return (x and 0xFFFFFF) / 16777216f
}

internal fun easeOut(p: Float): Float { val q = 1f - p.coerceIn(0f, 1f); return 1f - q * q * q }
internal fun easeInOut(p: Float): Float { val x = p.coerceIn(0f, 1f); return x * x * (3 - 2 * x) }

/**
 * Perspectiva 3D simples (sem android.graphics.Camera): gira o plano em torno do ponto ([px], [py])
 * — [pitch] para trás/para a frente, [yaw] para os lados, em radianos — visto de uma distância [dist] em px.
 * Valores positivos de Z ficam mais longe e menores.
 */
internal fun android.graphics.Matrix.setPerspective(pitch: Float, yaw: Float, dist: Float, px: Float, py: Float) {
    val ca = kotlin.math.cos(pitch); val sa = kotlin.math.sin(pitch)
    val cb = kotlin.math.cos(yaw); val sb = kotlin.math.sin(yaw)
    // R = Ry(yaw) · Rx(pitch), aplicado a (x, y, 0); projeção x' = X·d/(d+Z).
    setValues(floatArrayOf(cb, sa * sb, 0f, 0f, ca, 0f, -sb / dist, sa * cb / dist, 1f))
    preTranslate(-px, -py)
    postTranslate(px, py)
}
