package com.dmwnezes.sintonia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dmwnezes.sintonia.data.Track
import com.dmwnezes.sintonia.lyrics.LyricLine
import com.dmwnezes.sintonia.share.LyricVideoRenderer
import com.dmwnezes.sintonia.share.ShareHelper
import com.dmwnezes.sintonia.share.ShareRange
import com.dmwnezes.sintonia.share.VideoColors
import com.dmwnezes.sintonia.share.VideoFormat
import com.dmwnezes.sintonia.share.VideoSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Dim2: Color @Composable get() = Palette.ink.copy(alpha = 0.6f)

/**
 * Escolha do trecho (pela letra ou por tempo, até 30 s) e geração do vídeo para compartilhar.
 */
@Composable
fun ShareVideoDialog(
    track: Track,
    lines: List<LyricLine>,
    positionMs: Long,
    colors: TrackColors,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val synced = lines.filter { it.text.isNotBlank() }.isNotEmpty()

    var byLyrics by remember { mutableStateOf(synced) }
    var format by remember { mutableStateOf(VideoFormat.STORY) }
    // seleção pela letra
    val firstIdx = remember(lines) {
        lines.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
    }
    var selFrom by remember { mutableIntStateOf(firstIdx) }
    var selTo by remember { mutableIntStateOf((firstIdx + 2).coerceAtMost((lines.size - 1).coerceAtLeast(0))) }
    var picking by remember { mutableStateOf(false) } // true = próximo toque define o fim
    // seleção por tempo
    var timeStart by remember { mutableLongStateOf((positionMs - 2000).coerceAtLeast(0)) }
    var timeLen by remember { mutableLongStateOf(15_000L) }

    var progress by remember { mutableFloatStateOf(-1f) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    val range = if (byLyrics && synced) ShareRange.fromLines(lines, selFrom, selTo, track.durationMs)
    else ShareRange.fromTime(timeStart, timeLen, track.durationMs)

    fun generate() {
        error = null
        progress = 0f
        job = scope.launch {
            try {
                val file = withContext(Dispatchers.Default) {
                    val cover = ShareHelper.loadCover(context, track.imageUrl)
                    val spec = VideoSpec(
                        track = track, cover = cover,
                        colors = VideoColors(colors.deep.toArgb(), colors.base.toArgb(), colors.glow1.toArgb(), colors.glow2.toArgb(), colors.glow3.toArgb()),
                        lines = lines, startMs = range.startMs, endMs = range.endMs, format = format,
                    )
                    val out = ShareHelper.outputFile(context, track)
                    LyricVideoRenderer.render(spec, out) { p -> progress = p }
                    out
                }
                progress = -1f
                ShareHelper.shareVideo(context, file, track)
            } catch (e: kotlinx.coroutines.CancellationException) {
                progress = -1f
            } catch (e: Exception) {
                progress = -1f
                error = "Não consegui gerar o vídeo neste aparelho (${e.javaClass.simpleName})."
            }
        }
    }

    Dialog(
        onDismissRequest = { job?.cancel(); onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Palette.surface.copy(alpha = 0.97f))) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Compartilhar trecho", color = Palette.ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Text("${track.name} · ${track.artistLine}", color = Dim2, fontSize = 13.sp, maxLines = 1)
                    }
                    IconButton(onClick = { job?.cancel(); onDismiss() }) { Icon(Icons.Rounded.Close, "Fechar", tint = Palette.ink) }
                }

                Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Pela letra", byLyrics, enabled = synced) { byLyrics = true }
                    Chip("Pelo tempo", !byLyrics) { byLyrics = false }
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (byLyrics && synced) {
                        LinePicker(lines, selFrom, selTo, firstIdx, picking) { i ->
                            if (!picking) { selFrom = i; selTo = i; picking = true }
                            else { if (i < selFrom) { selTo = selFrom; selFrom = i } else selTo = i; picking = false }
                        }
                    } else {
                        TimePicker(track.durationMs, timeStart, timeLen, lines, { timeStart = it }, { timeLen = it })
                    }
                }

                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                    val secs = range.durationMs / 1000
                    Text(
                        "Trecho: ${fmtT(range.startMs)} → ${fmtT(range.endMs)} (${secs} s)" + if (range.clipped) " · cortado em 30 s" else "",
                        color = Palette.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    )
                    if (byLyrics && synced) {
                        Text(if (picking) "Agora toque na última linha do trecho." else "Toque na primeira linha e depois na última.", color = Dim2, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VideoFormat.entries.forEach { f -> Chip(f.label, format == f) { format = f } }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "O vídeo vai sem o som da música: o Spotify não deixa outros apps gravarem o áudio. " +
                            "No Instagram, adicione a música pelo adesivo de música. O link da faixa vai junto na mensagem.",
                        color = Palette.ink.copy(alpha = 0.45f), fontSize = 11.sp, lineHeight = 14.sp,
                    )
                    error?.let { Text(it, color = Color(0xFFFFB4B4), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
                    Spacer(Modifier.height(12.dp))
                    if (progress >= 0f) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = Palette.ink, trackColor = Palette.ink.copy(alpha = 0.2f),
                            gapSize = 0.dp, drawStopIndicator = {},
                        )
                        Text("Gerando vídeo… ${(progress * 100).toInt()}%", color = Dim2, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    } else {
                        Button(
                            onClick = ::generate,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Palette.ink, contentColor = Palette.onInk),
                        ) { Text("Gerar e compartilhar", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, enabled = enabled, label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Palette.ink.copy(alpha = 0.08f), labelColor = Palette.ink.copy(alpha = 0.85f),
            selectedContainerColor = Palette.ink, selectedLabelColor = Palette.onInk,
            disabledContainerColor = Palette.ink.copy(alpha = 0.03f), disabledLabelColor = Palette.ink.copy(alpha = 0.3f),
        ),
        border = null,
    )
}

@Composable
private fun LinePicker(lines: List<LyricLine>, from: Int, to: Int, scrollTo: Int, picking: Boolean, onTap: (Int) -> Unit) {
    val state = rememberLazyListState()
    LaunchedEffect(Unit) { state.scrollToItem((scrollTo - 2).coerceAtLeast(0)) }
    val (a, b) = minOf(from, to) to maxOf(from, to)
    LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        itemsIndexed(lines) { i, line ->
            val inRange = i in a..b
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (inRange) Palette.ink.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onTap(i) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(fmtT(line.timeMs), color = Dim2, fontSize = 12.sp, modifier = Modifier.padding(end = 12.dp))
                Text(
                    line.text.ifBlank { "•  •  •" },
                    color = if (inRange) Palette.ink else Palette.ink.copy(alpha = 0.65f),
                    fontSize = 17.sp,
                    fontWeight = if (inRange) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                if (picking && i == a) Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(Palette.ink))
            }
        }
    }
}

@Composable
private fun TimePicker(
    durationMs: Long, start: Long, length: Long, lines: List<LyricLine>,
    onStart: (Long) -> Unit, onLength: (Long) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("Começo do trecho: ${fmtT(start)}", color = Palette.ink, fontSize = 15.sp)
        Slider(
            value = start.toFloat(),
            onValueChange = { onStart(it.toLong()) },
            valueRange = 0f..(durationMs - length).coerceAtLeast(1).toFloat(),
            colors = SliderDefaults.colors(thumbColor = Palette.ink, activeTrackColor = Palette.ink, inactiveTrackColor = Palette.ink.copy(alpha = 0.2f)),
        )
        Text("Duração", color = Palette.ink, fontSize = 15.sp)
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10_000L, 15_000L, 20_000L, 30_000L).forEach { l -> Chip("${l / 1000} s", length == l) { onLength(l) } }
        }
        val inWindow = lines.filter { it.timeMs in start..(start + length) && it.text.isNotBlank() }
        if (inWindow.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Letra que aparece no vídeo:", color = Dim2, fontSize = 12.sp)
            inWindow.take(8).forEach { Text(it.text, color = Palette.ink.copy(alpha = 0.85f), fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp)) }
        }
    }
}

private fun fmtT(ms: Long): String {
    val s = ms.coerceAtLeast(0) / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
