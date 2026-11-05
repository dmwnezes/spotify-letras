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

/** A letra no estilo Edit, ao vivo, acompanhando a música. [translations] tem uma tradução por linha. */
@Composable
fun EditLyricsView(
    lines: List<LyricLine>,
    positionMs: () -> Long,
    modifier: Modifier = Modifier,
    translations: List<String?>? = null,
    centerFraction: Float? = null,
    maxWidthFraction: Float? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val renderer = remember(density) {
        EditRenderer(ResourcesCompat.getFont(context, R.font.montserrat) ?: Typeface.DEFAULT_BOLD, density)
    }
    val defaults = remember(renderer) { renderer.centerFraction to renderer.maxWidthFraction }
    renderer.centerFraction = centerFraction ?: defaults.first
    renderer.maxWidthFraction = maxWidthFraction ?: defaults.second
    remember(renderer, lines, translations) { renderer.setLines(lines, translations); 0 }

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
