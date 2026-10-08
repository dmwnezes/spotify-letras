package com.dmwnezes.sintonia.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.dmwnezes.sintonia.LyricsState
import com.dmwnezes.sintonia.UiState
import com.dmwnezes.sintonia.data.NowPlaying
import com.dmwnezes.sintonia.lyrics.LrcParser
import com.dmwnezes.sintonia.lyrics.LyricLine
import com.dmwnezes.sintonia.lyrics.Lyrics
import com.dmwnezes.sintonia.viz.AudioSpectrum
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max

/** Antecipação para a linha acender um instante antes de ser cantada, como nos apps de música. */
private const val ANTICIPATION_MS = 250L

@Composable
fun LyricsScreen(
    state: UiState,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onSetRealViz: (Boolean) -> Unit,
    onNudgeOffset: (Long) -> Unit,
    onRetryLyrics: () -> Unit,
    bottomPadding: PaddingValues,
) {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // ----- Visualizer: áudio real ou animação -----
    var hasMic by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var showMicDialog by rememberSaveable { mutableStateOf(false) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMic = granted
        if (!granted) onSetRealViz(false)
    }
    LaunchedEffect(state.realAudioViz, hasMic) {
        if (state.realAudioViz && !hasMic) showMicDialog = true
    }

    val playing = state.now?.isPlaying == true
    val spectrum = remember { AudioSpectrum() }
    val useReal = state.realAudioViz && hasMic
    var vizUnavailable by remember { mutableStateOf(false) }
    DisposableEffect(useReal) {
        vizUnavailable = useReal && !spectrum.start()
        onDispose { spectrum.stop() }
    }
    LaunchedEffect(useReal, playing) {
        while (useReal && playing) {
            delay(1000)
            val quietFor = System.currentTimeMillis() - max(spectrum.lastSoundAtMs, spectrum.startedAtMs)
            vizUnavailable = quietFor > 6000
        }
    }

    Box(Modifier.fillMaxSize()) {
        VisualizerCanvas(
            colors = state.colors,
            playing = playing,
            source = { if (useReal && !vizUnavailable) spectrum.levels else null },
        )
        // Véu escuro para a letra ficar legível sobre os brilhos.
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))

        Column(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = bottomPadding.calculateBottomPadding())) {
            val now = state.now
            when {
                !state.firstLoadDone -> Centered { CircularProgressIndicator(color = Color.White) }
                now?.track == null -> NothingPlaying()
                else -> {
                    var showTune by rememberSaveable { mutableStateOf(false) }
                    TrackHeader(
                        now = now,
                        realViz = useReal,
                        vizUnavailable = vizUnavailable && useReal,
                        onToggleViz = {
                            if (state.realAudioViz) onSetRealViz(false)
                            else {
                                onSetRealViz(true)
                                if (!hasMic) showMicDialog = true
                            }
                        },
                        onTune = { showTune = !showTune },
                    )
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        LyricsBody(state.lyrics, now, state.lyricsOffsetMs, onSeek, onRetryLyrics)
                    }
                    AnimatedVisibility(showTune) {
                        OffsetTuner(state.lyricsOffsetMs, onNudgeOffset)
                    }
                    PlayerControls(now, onTogglePlay, onNext, onPrevious)
                }
            }
        }
    }

    if (showMicDialog) {
        AlertDialog(
            onDismissRequest = { showMicDialog = false },
            title = { Text("Visualizer com o som real") },
            text = {
                Text(
                    "Para o visualizer reagir à música, o Android pede a permissão de \"gravar áudio\". " +
                        "O Sintonia só lê a intensidade do som que sai do celular para animar as cores. " +
                        "Nada é gravado nem enviado."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showMicDialog = false
                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text("Permitir") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showMicDialog = false
                    onSetRealViz(false)
                }) { Text("Usar modo animado") }
            },
        )
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun NothingPlaying() {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Nada tocando agora", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(10.dp))
        Text(
            "Dê o play em uma música no Spotify e a letra aparece aqui, acompanhando o tempo da música.",
            color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = {
            context.packageManager.getLaunchIntentForPackage("com.spotify.music")?.let(context::startActivity)
        }) { Text("Abrir o Spotify", color = Color.White) }
    }
}

@Composable
private fun TrackHeader(
    now: NowPlaying,
    realViz: Boolean,
    vizUnavailable: Boolean,
    onToggleViz: () -> Unit,
    onTune: () -> Unit,
) {
    val track = now.track ?: return
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = track.imageUrl,
                contentDescription = "Capa",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artistLine, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onTune) {
                Icon(Icons.Rounded.Tune, contentDescription = "Ajustar sincronia", tint = Color.White.copy(alpha = 0.8f))
            }
            IconButton(onClick = onToggleViz) {
                Icon(
                    if (realViz) Icons.Rounded.GraphicEq else Icons.Rounded.AutoAwesome,
                    contentDescription = if (realViz) "Visualizer: som real" else "Visualizer: animado",
                    tint = Color.White,
                )
            }
        }
        if (vizUnavailable) {
            Text(
                "Sem leitura do som agora (volume zerado ou bloqueio do aparelho). Usando a animação.",
                color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp, end = 12.dp),
            )
        }
    }
}

@Composable
private fun LyricsBody(
    lyrics: LyricsState,
    now: NowPlaying,
    offsetMs: Long,
    onSeek: (Long) -> Unit,
    onRetry: () -> Unit,
) {
    when (lyrics) {
        LyricsState.Idle, LyricsState.Loading -> Centered {
            Text("Buscando a letra…", color = Color.White.copy(alpha = 0.6f), fontSize = 18.sp)
        }
        is LyricsState.Failed -> Centered {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(lyrics.message, color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
                TextButton(onClick = onRetry) { Text("Tentar de novo", color = Color.White) }
            }
        }
        is LyricsState.Ready -> when (val l = lyrics.lyrics) {
            is Lyrics.Synced -> SyncedLyrics(l.lines, now, offsetMs, onSeek)
            is Lyrics.Plain -> PlainLyrics(l.text)
            Lyrics.Instrumental -> Centered { Text("♪  Instrumental", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
            Lyrics.NotFound -> Centered {
                Text(
                    "Não encontrei a letra desta música.",
                    color = Color.White.copy(alpha = 0.7f), fontSize = 17.sp,
                    modifier = Modifier.padding(32.dp),
                )
            }
        }
    }
}

@Composable
private fun SyncedLyrics(lines: List<LyricLine>, now: NowPlaying, offsetMs: Long, onSeek: (Long) -> Unit) {
    // Posição da música atualizada ~12x por segundo, sem recompor a tela inteira.
    var posMs by remember { mutableLongStateOf(now.positionAt(SystemClock.elapsedRealtime())) }
    val currentNow by rememberUpdatedState(now)
    LaunchedEffect(Unit) {
        while (true) {
            posMs = currentNow.positionAt(SystemClock.elapsedRealtime())
            delay(80)
        }
    }
    val currentOffset by rememberUpdatedState(offsetMs)
    val index by remember(lines) {
        derivedStateOf { LrcParser.indexAt(lines, posMs + currentOffset + ANTICIPATION_MS) }
    }

    val listState = rememberLazyListState()
    var userScrollAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect {
            if (it is DragInteraction.Start || it is DragInteraction.Stop) userScrollAt = SystemClock.elapsedRealtime()
        }
    }
    val idx by rememberUpdatedState(index)
    LaunchedEffect(index, userScrollAt) {
        val sinceUser = SystemClock.elapsedRealtime() - userScrollAt
        if (sinceUser < 3500) delay(3500 - sinceUser)
        listState.animateScrollToItem(max(idx, 0))
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = maxHeight * 0.28f, bottom = maxHeight * 0.65f),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(lines, key = { i, l -> "$i-${l.timeMs}" }) { i, line ->
                LyricLineView(
                    text = line.text,
                    distance = if (index < 0) i + 1 else i - index,
                    onClick = { onSeek(line.timeMs) },
                )
            }
        }
    }
}

@Composable
private fun LyricLineView(text: String, distance: Int, onClick: () -> Unit) {
    val active = distance == 0
    val alpha by animateFloatAsState(
        targetValue = when {
            active -> 1f
            distance < 0 -> 0.30f
            else -> 0.45f
        },
        animationSpec = tween(350), label = "alpha",
    )
    val scale by animateFloatAsState(if (active) 1f else 0.94f, tween(350), label = "scale")
    val blurDp = if (abs(distance) >= 2) 1.5.dp else 0.dp
    val isBreak = text.isBlank()
    Text(
        text = if (isBreak) "•  •  •" else text,
        color = Color.White,
        fontSize = if (isBreak) 22.sp else 30.sp,
        lineHeight = 37.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 11.dp)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .blur(blurDp),
    )
}

@Composable
private fun PlainLyrics(text: String) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 26.dp, vertical = 20.dp)
    ) {
        Text(
            "Letra sem marcação de tempo para esta música.",
            color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp,
        )
        Spacer(Modifier.height(14.dp))
        Text(text, color = Color.White.copy(alpha = 0.9f), fontSize = 22.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun OffsetTuner(offsetMs: Long, onNudge: (Long) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = { onNudge(-250) }) { Text("Atrasar", color = Color.White) }
        val s = "%+.2f s".format(offsetMs / 1000.0).replace('.', ',')
        Text("Sincronia: $s", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
        TextButton(onClick = { onNudge(250) }) { Text("Adiantar", color = Color.White) }
    }
}

@Composable
private fun PlayerControls(now: NowPlaying, onTogglePlay: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit) {
    val duration = now.track?.durationMs ?: 1L
    var pos by remember { mutableLongStateOf(now.positionAt(SystemClock.elapsedRealtime())) }
    val currentNow by rememberUpdatedState(now)
    LaunchedEffect(Unit) {
        while (true) {
            pos = currentNow.positionAt(SystemClock.elapsedRealtime())
            delay(250)
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        LinearProgressIndicator(
            progress = { (pos.toFloat() / duration).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.22f),
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt(pos), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            Text("-" + fmt(duration - pos), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipPrevious, "Anterior", tint = Color.White, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.width(18.dp))
            FilledIconButton(
                onClick = onTogglePlay,
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black),
            ) {
                Icon(if (now.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (now.isPlaying) "Pausar" else "Tocar", modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.width(18.dp))
            IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipNext, "Próxima", tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }
    }
}

private fun fmt(ms: Long): String {
    val s = (ms.coerceAtLeast(0) / 1000)
    return "%d:%02d".format(s / 60, s % 60)
}
