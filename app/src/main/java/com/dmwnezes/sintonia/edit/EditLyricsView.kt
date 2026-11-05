package com.dmwnezes.sintonia.edit

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.res.ResourcesCompat
import com.dmwnezes.sintonia.R
import com.dmwnezes.sintonia.lyrics.LyricLine
import com.dmwnezes.sintonia.lyrics.TranslatedLyrics

/**
 * A letra no estilo Edit, ao vivo, acompanhando a música. [translations] tem uma tradução por linha;
 * com [translationOnly], a tradução toma o lugar da letra e herda o efeito das palavras acendendo.
 */
@Composable
fun EditLyricsView(
    lines: List<LyricLine>,
    positionMs: () -> Long,
    modifier: Modifier = Modifier,
    translations: List<String?>? = null,
    translationOnly: Boolean = false,
    centerFraction: Float? = null,
    maxWidthFraction: Float? = null,
    translationWidthFraction: Float? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val renderer = remember(density) {
        EditRenderer(ResourcesCompat.getFont(context, R.font.montserrat) ?: Typeface.DEFAULT_BOLD, density)
    }
    val defaults = remember(renderer) { renderer.centerFraction to renderer.maxWidthFraction }
    renderer.centerFraction = centerFraction ?: defaults.first
    renderer.maxWidthFraction = maxWidthFraction ?: defaults.second
    renderer.translationWidthFraction = translationWidthFraction
    remember(renderer, lines, translations, translationOnly) {
        if (translationOnly && translations != null) renderer.setLines(TranslatedLyrics.only(lines, translations))
        else renderer.setLines(lines, translations)
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
