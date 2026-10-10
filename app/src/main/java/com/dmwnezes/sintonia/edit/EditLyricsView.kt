package com.dmwnezes.sintonia.edit

import android.content.Context
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.res.ResourcesCompat
import com.dmwnezes.sintonia.R
import com.dmwnezes.sintonia.lyrics.LyricLine
import com.dmwnezes.sintonia.share.ShareHelper
import com.dmwnezes.sintonia.ui.TrackColors

/** Cria o desenhista de cada estilo animado. */
fun createStyleRenderer(context: Context, mode: LyricsMode, density: Float): StyleRenderer {
    fun font(id: Int) = ResourcesCompat.getFont(context, id) ?: Typeface.DEFAULT_BOLD
    return when (mode) {
        LyricsMode.VIDRO -> VidroRenderer(font(R.font.inter), density)
        LyricsMode.CILINDRO -> CilindroRenderer(font(R.font.montserrat), density)
        LyricsMode.COLAGEM -> ColagemRenderer(font(R.font.archivo), font(R.font.playfair), font(R.font.montserrat), density)
        else -> EditRenderer(font(R.font.montserrat), density)
    }
}

/** A letra num dos estilos animados, ao vivo, acompanhando a música. */
@Composable
fun StyledLyricsView(
    mode: LyricsMode,
    lines: List<LyricLine>,
    positionMs: () -> Long,
    modifier: Modifier = Modifier,
    coverUrl: String? = null,
    colors: TrackColors? = null,
    centerFraction: Float? = null,
    maxWidthFraction: Float? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val renderer = remember(mode, density) { createStyleRenderer(context, mode, density) }
    val defaults = remember(renderer) { renderer.centerFraction to renderer.maxWidthFraction }
    renderer.centerFraction = centerFraction ?: defaults.first
    renderer.maxWidthFraction = maxWidthFraction ?: defaults.second
    remember(renderer, lines) { renderer.setLines(lines); 0 }

    // Capa (para o Cilindro) e cores (para o Vidro).
    var cover by remember(coverUrl) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(coverUrl, mode) {
        if (mode == LyricsMode.CILINDRO && coverUrl != null) cover = runCatching { ShareHelper.loadCover(context, coverUrl) }.getOrNull()
    }
    remember(renderer, cover, colors) {
        val tc = colors ?: TrackColors.Default
        renderer.setArt(StyleArt(cover, tc.deep.toArgb(), tc.base.toArgb(), tc.glow1.toArgb(), tc.glow2.toArgb(), tc.glow3.toArgb()))
        0
    }

    val pos by rememberUpdatedState(positionMs)
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = System.nanoTime()
        while (true) withFrameNanos { t = (it - start) / 1e9f }
    }

    Canvas(modifier.fillMaxSize()) {
        val now = pos()
        val time = t // lido aqui para redesenhar a cada quadro
        drawIntoCanvas { c -> renderer.draw(c.nativeCanvas, size.width, size.height, now, time) }
    }
}
