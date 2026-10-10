package com.dmwnezes.sintonia.edit

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

/** A letra no estilo "lyric edit", ao vivo, acompanhando a música. */
@Composable
fun EditLyricsView(lines: List<LyricLine>, positionMs: () -> Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val renderer = remember(density) {
        val tf = ResourcesCompat.getFont(context, R.font.montserrat) ?: android.graphics.Typeface.DEFAULT_BOLD
        EditRenderer(tf, density)
    }
    val pages = remember(lines) { EditLayout.pages(lines) }
    val pos by rememberUpdatedState(positionMs)
    var t by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val start = System.nanoTime()
        while (true) withFrameNanos { t = (it - start) / 1e9f }
    }

    Canvas(modifier.fillMaxSize()) {
        val now = pos()
        // lê t para redesenhar a cada quadro (granulado, movimento e rasgos)
        val time = t
        drawIntoCanvas { c ->
            renderer.draw(c.nativeCanvas, size.width, size.height, EditLayout.pageAt(pages, now), now, time)
        }
    }
}
