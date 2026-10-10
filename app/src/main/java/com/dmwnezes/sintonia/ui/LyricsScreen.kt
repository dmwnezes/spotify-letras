package com.dmwnezes.sintonia.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.dmwnezes.sintonia.share.NowPlayingCard
import com.dmwnezes.sintonia.share.NowPlayingInfo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.shadow
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
import com.dmwnezes.sintonia.TranslationState
import com.dmwnezes.sintonia.UiState
import com.dmwnezes.sintonia.data.NowPlaying
import com.dmwnezes.sintonia.lyrics.LrcParser
import com.dmwnezes.sintonia.lyrics.LyricLine
import com.dmwnezes.sintonia.lyrics.Lyrics
import com.dmwnezes.sintonia.edit.EditLyricsView
import androidx.compose.runtime.CompositionLocalProvider
import com.dmwnezes.sintonia.lyrics.WordTiming
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.lerp
import com.dmwnezes.sintonia.viz.AudioSpectrum
import com.dmwnezes.sintonia.viz.VizTheme
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max

/** Antecipação para a linha acender um instante antes de ser cantada, como nos apps de música. */
private const val ANTICIPATION_MS = 250L

/** Linha escolhida ao segurar o dedo, com a tradução quando houver. */
data class PickedLine(val text: String, val translation: String?, val timeMs: Long)

@Composable
fun LyricsScreen(
    state: UiState,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onSetRealViz: (Boolean) -> Unit,
    onSetVizTheme: (VizTheme) -> Unit,
    onNudgeOffset: (Long) -> Unit,
    onRetryLyrics: () -> Unit,
    onSetPlayerOnly: (Boolean) -> Unit,
    onSetShowTranslation: (Boolean) -> Unit,
    bottomPadding: PaddingValues,
    onSetKaraoke: (Boolean) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onSetEditStyle: (Boolean) -> Unit = {},
    onRecordMode: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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

    val translations: List<String?>? =
        (state.translation as? TranslationState.Ready)?.lines?.takeIf { state.showTranslation }

    // Estilo "Edit": a letra sincronizada vira o vídeo de lyric edit, em tela cheia e sempre escura.
    val editLines = ((state.lyrics as? LyricsState.Ready)?.lyrics as? Lyrics.Synced)?.lines
        ?.takeIf { state.editStyle && !state.playerOnly && state.now?.track != null }
    val nowForEdit by rememberUpdatedState(state.now)
    val offsetForEdit by rememberUpdatedState(state.lyricsOffsetMs)

    CompositionLocalProvider(LocalAppPalette provides if (editLines != null) DarkPalette else LocalAppPalette.current) {
    Box(Modifier.fillMaxSize()) {
        if (editLines != null) {
            EditLyricsView(
                lines = editLines,
                positionMs = { (nowForEdit?.positionAt(SystemClock.elapsedRealtime()) ?: 0L) + offsetForEdit + 120 },
            )
        } else {
            VisualizerCanvas(
                colors = state.colors,
                playing = playing,
                theme = state.vizTheme,
                source = { if (useReal && !vizUnavailable) spectrum.levels else null },
            )
            // Véu escuro para a letra ficar legível sobre os brilhos.
            Box(Modifier.fillMaxSize().background(Palette.scrim.copy(alpha = if (Palette.dark) 0.28f else 0.42f)))
        }

        Column(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = bottomPadding.calculateBottomPadding())) {
            val now = state.now
            when {
                !state.firstLoadDone -> Centered { CircularProgressIndicator(color = Palette.ink) }
                now?.track == null -> NothingPlaying(onOpenSearch)
                else -> {
                    val track = now.track
                    var showTune by rememberSaveable { mutableStateOf(false) }
                    var picked by remember { mutableStateOf<PickedLine?>(null) }
                    var noteFor by remember { mutableStateOf<Long?>(null) }

                    picked?.let { p ->
                        LineActionsSheet(
                            track = track,
                            line = p,
                            onDismiss = { picked = null },
                            onNote = { picked = null; noteFor = p.timeMs },
                        )
                    }
                    noteFor?.let { pos ->
                        NoteDialog(track = track, positionMs = pos, onDismiss = { noteFor = null })
                    }

                    TrackHeader(
                        now = now,
                        playerOnly = state.playerOnly,
                        translation = state.translation,
                        showTranslation = state.showTranslation,
                        onToggleTranslation = { onSetShowTranslation(!state.showTranslation) },
                        realViz = state.realAudioViz,
                        theme = state.vizTheme,
                        vizUnavailable = vizUnavailable && useReal,
                        onSetRealViz = { on ->
                            onSetRealViz(on)
                            if (on && !hasMic) showMicDialog = true
                        },
                        onSetTheme = onSetVizTheme,
                        karaoke = state.karaoke,
                        onSetKaraoke = onSetKaraoke,
                        editStyle = state.editStyle,
                        onSetEditStyle = onSetEditStyle,
                        onTune = { showTune = !showTune },
                        onNote = { noteFor = now.positionAt(SystemClock.elapsedRealtime()) },
                        onSearch = onOpenSearch,
                        onRecordMode = onRecordMode,
                    )
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (editLines == null) Crossfade(state.playerOnly, animationSpec = tween(450), label = "modo") { onlyPlayer ->
                            if (onlyPlayer) BigCover(now)
                            else LyricsBody(state.lyrics, translations, now, state.lyricsOffsetMs, state.karaoke, onSeek, onRetryLyrics) { picked = it }
                        }
                    }
                    AnimatedVisibility(showTune && !state.playerOnly) {
                        OffsetTuner(state.lyricsOffsetMs, onNudgeOffset)
                    }
                    PlayerControls(
                        now = now,
                        playerOnly = state.playerOnly,
                        onTogglePlayerOnly = { onSetPlayerOnly(!state.playerOnly) },
                        onRecordMode = onRecordMode,
                        onShareCard = {
                            val pos = now.positionAt(SystemClock.elapsedRealtime())
                            val synced = ((state.lyrics as? LyricsState.Ready)?.lyrics as? Lyrics.Synced)?.lines.orEmpty()
                            val idx = LrcParser.indexAt(synced, pos + state.lyricsOffsetMs + ANTICIPATION_MS)
                            val line = synced.getOrNull(idx)?.text?.takeIf { it.isNotBlank() }
                            val tr = translations?.getOrNull(idx)
                            scope.launch {
                                runCatching { NowPlayingCard.share(context, NowPlayingInfo(track, pos, line, tr)) }
                                    .onFailure { android.widget.Toast.makeText(context, "Não consegui gerar o cartão.", android.widget.Toast.LENGTH_SHORT).show() }
                            }
                        },
                        onTogglePlay = onTogglePlay,
                        onNext = onNext,
                        onPrevious = onPrevious,
                    )
                }
            }
        }
    }

    } // CompositionLocalProvider

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
private fun NothingPlaying(onOpenSearch: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Nada tocando agora", color = Palette.ink, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(10.dp))
        Text(
            "Dê o play em uma música no Spotify e a letra aparece aqui, acompanhando o tempo da música.",
            color = Palette.ink.copy(alpha = 0.7f), fontSize = 16.sp,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onOpenSearch) {
            Icon(Icons.Rounded.Search, null, tint = Palette.ink)
            Spacer(Modifier.width(6.dp))
            Text("Buscar uma música", color = Palette.ink)
        }
        TextButton(onClick = {
            context.packageManager.getLaunchIntentForPackage("com.spotify.music")?.let(context::startActivity)
        }) { Text("Abrir o Spotify", color = Palette.ink.copy(alpha = 0.8f)) }
    }
}

@Composable
private fun TrackHeader(
    now: NowPlaying,
    playerOnly: Boolean,
    translation: TranslationState,
    showTranslation: Boolean,
    onToggleTranslation: () -> Unit,
    realViz: Boolean,
    theme: VizTheme,
    vizUnavailable: Boolean,
    onSetRealViz: (Boolean) -> Unit,
    onSetTheme: (VizTheme) -> Unit,
    karaoke: Boolean,
    onSetKaraoke: (Boolean) -> Unit,
    editStyle: Boolean,
    onSetEditStyle: (Boolean) -> Unit,
    onTune: () -> Unit,
    onNote: () -> Unit,
    onSearch: () -> Unit,
    onRecordMode: () -> Unit,
) {
    val track = now.track ?: return
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 12.dp)) {
        Row(Modifier.height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!playerOnly) {
                AsyncImage(
                    model = track.imageUrl,
                    contentDescription = "Capa",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.name, color = Palette.ink, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artistLine, color = Palette.ink.copy(alpha = 0.7f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (translation != TranslationState.None) {
                    IconButton(onClick = onToggleTranslation) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape)
                                .background(if (showTranslation) Palette.ink else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Translate,
                                contentDescription = if (showTranslation) "Esconder tradução" else "Mostrar tradução",
                                tint = if (showTranslation) Palette.onInk else Palette.ink.copy(alpha = 0.8f),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Buscar música", tint = Palette.ink) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Mais opções", tint = Palette.ink) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Modo gravar para Stories") },
                        leadingIcon = { Icon(Icons.Rounded.Videocam, null) },
                        onClick = { menu = false; onRecordMode() },
                    )
                    DropdownMenuItem(
                        text = { Text("Anotar um momento") },
                        leadingIcon = { Icon(Icons.Rounded.EditNote, null) },
                        onClick = { menu = false; onNote() },
                    )
                    if (!playerOnly) {
                        DropdownMenuItem(text = { Text("Ajustar sincronia da letra") }, onClick = { menu = false; onTune() })
                        MenuCheck("Acender palavra por palavra", karaoke) { onSetKaraoke(!karaoke); menu = false }
                        HorizontalDivider()
                        Text("Estilo da letra", fontSize = 12.sp, color = Palette.ink.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                        MenuCheck("Edit (estilo reel)", editStyle) { onSetEditStyle(true); menu = false }
                        MenuCheck("Clássico", !editStyle) { onSetEditStyle(false); menu = false }
                    }
                    HorizontalDivider()
                    Text("Visualizer", fontSize = 12.sp, color = Palette.ink.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    MenuCheck("Reagir ao som real", realViz) { onSetRealViz(true); menu = false }
                    MenuCheck("Animação", !realViz) { onSetRealViz(false); menu = false }
                    HorizontalDivider()
                    Text("Tema", fontSize = 12.sp, color = Palette.ink.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    VizTheme.entries.forEach { t -> MenuCheck(t.label, t == theme) { onSetTheme(t); menu = false } }
                }
            }
        }
        val status = when (translation) {
            is TranslationState.Loading ->
                if (translation.downloading) "Baixando o tradutor de ${translation.language} (só na primeira vez)…"
                else "Traduzindo do ${translation.language}…"
            is TranslationState.Failed -> translation.message
            else -> null
        }
        if (showTranslation && status != null && !playerOnly) {
            Text(status, color = Palette.ink.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, end = 12.dp))
        }
        if (vizUnavailable) {
            Text(
                "Sem leitura do som agora (volume zerado ou bloqueio do aparelho). Usando a animação.",
                color = Palette.ink.copy(alpha = 0.55f), fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp, end = 12.dp),
            )
        }
    }
}

@Composable
private fun MenuCheck(label: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        trailingIcon = { if (checked) Icon(Icons.Rounded.Check, null) },
        onClick = onClick,
    )
}

/** Modo só player: capa grande que encolhe um pouco quando a música pausa. */
@Composable
private fun BigCover(now: NowPlaying) {
    val track = now.track ?: return
    val scale by animateFloatAsState(if (now.isPlaying) 1f else 0.86f, tween(500), label = "capa")
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = track.imageUrl,
            contentDescription = "Capa do álbum ${track.album}",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .weight(1f, fill = false)
                .aspectRatio(1f)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .shadow(28.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp)),
        )
        Spacer(Modifier.height(28.dp))
        Text(track.name, color = Palette.ink, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
        Text(track.artistLine, color = Palette.ink.copy(alpha = 0.72f), fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
        Text(track.album, color = Palette.ink.copy(alpha = 0.5f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LyricsBody(
    lyrics: LyricsState,
    translations: List<String?>?,
    now: NowPlaying,
    offsetMs: Long,
    karaoke: Boolean,
    onSeek: (Long) -> Unit,
    onRetry: () -> Unit,
    onPick: (PickedLine) -> Unit,
) {
    when (lyrics) {
        LyricsState.Idle, LyricsState.Loading -> Centered {
            Text("Buscando a letra…", color = Palette.ink.copy(alpha = 0.6f), fontSize = 18.sp)
        }
        is LyricsState.Failed -> Centered {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(lyrics.message, color = Palette.ink.copy(alpha = 0.7f), fontSize = 16.sp)
                TextButton(onClick = onRetry) { Text("Tentar de novo", color = Palette.ink) }
            }
        }
        is LyricsState.Ready -> when (val l = lyrics.lyrics) {
            is Lyrics.Synced -> SyncedLyrics(l.lines, translations, now, offsetMs, karaoke, onSeek, onPick)
            is Lyrics.Plain -> PlainLyrics(l.text, translations, onPick)
            Lyrics.Instrumental -> Centered { Text("♪  Instrumental", color = Palette.ink, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
            Lyrics.NotFound -> Centered {
                Text("Não encontrei a letra desta música.", color = Palette.ink.copy(alpha = 0.7f), fontSize = 17.sp, modifier = Modifier.padding(32.dp))
            }
        }
    }
}

@Composable
private fun SyncedLyrics(
    lines: List<LyricLine>,
    translations: List<String?>?,
    now: NowPlaying,
    offsetMs: Long,
    karaoke: Boolean,
    onSeek: (Long) -> Unit,
    onPick: (PickedLine) -> Unit,
) {
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
                val tr = translations?.getOrNull(i)
                val spans = if (karaoke && i == index && line.text.isNotBlank()) {
                    remember(line) { WordTiming.spans(line, lines.getOrNull(i + 1)?.timeMs) }
                } else null
                LyricLineView(
                    text = line.text,
                    translation = tr,
                    spans = spans,
                    positionMs = { posMs + currentOffset + 120 },
                    distance = if (index < 0) i + 1 else i - index,
                    onClick = { onSeek(line.timeMs) },
                    onLongClick = { if (line.text.isNotBlank()) onPick(PickedLine(line.text, tr, line.timeMs)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LyricLineView(
    text: String,
    translation: String?,
    distance: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    spans: List<WordTiming.Span>? = null,
    positionMs: () -> Long = { 0L },
) {
    val active = distance == 0
    val st = LocalLyricsStyle.current
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
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 26.dp, vertical = 11.dp)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .blur(blurDp),
    ) {
        if (spans != null) {
            // Karaokê: cada palavra vai acendendo conforme é cantada.
            val prog = WordTiming.progress(spans, positionMs())
            val dim = Palette.ink.copy(alpha = 0.42f)
            Text(
                text = buildAnnotatedString {
                    spans.forEachIndexed { k, sp -> withStyle(SpanStyle(color = lerp(dim, Palette.ink, prog[k]))) { append(sp.text) } }
                },
                fontSize = (30 * st.k).sp,
                lineHeight = (37 * st.k).sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = st.family,
            )
        } else Text(
            text = if (isBreak) "•  •  •" else text,
            color = Palette.ink,
            fontSize = if (isBreak) 22.sp else (30 * st.k).sp,
            lineHeight = (37 * st.k).sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = st.family,
        )
        // Tradução logo abaixo, menor e mais clara, como no Spotify.
        if (!isBreak && translation != null) {
            Text(
                text = translation,
                color = Palette.ink.copy(alpha = 0.62f),
                fontSize = (18 * st.scale).sp,
                lineHeight = (23 * st.scale).sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlainLyrics(text: String, translations: List<String?>?, onPick: (PickedLine) -> Unit) {
    val lines = remember(text) { text.lines() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 26.dp, vertical = 20.dp)) {
        Text("Letra sem marcação de tempo para esta música.", color = Palette.ink.copy(alpha = 0.55f), fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        lines.forEachIndexed { i, line ->
            if (line.isBlank()) Spacer(Modifier.height(16.dp))
            else Column(
                Modifier.fillMaxWidth()
                    .combinedClickable(onClick = {}, onLongClick = { onPick(PickedLine(line, translations?.getOrNull(i), 0)) })
                    .padding(vertical = 3.dp)
            ) {
                val st = LocalLyricsStyle.current
                Text(line, color = Palette.ink.copy(alpha = 0.9f), fontSize = (22 * st.k).sp, lineHeight = (30 * st.k).sp, fontWeight = FontWeight.SemiBold, fontFamily = st.family)
                translations?.getOrNull(i)?.let { Text(it, color = Palette.ink.copy(alpha = 0.6f), fontSize = 16.sp, lineHeight = 21.sp) }
            }
        }
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
        TextButton(onClick = { onNudge(-250) }) { Text("Atrasar", color = Palette.ink) }
        val s = "%+.2f s".format(offsetMs / 1000.0).replace('.', ',')
        Text("Sincronia: $s", color = Palette.ink.copy(alpha = 0.85f), fontSize = 14.sp)
        TextButton(onClick = { onNudge(250) }) { Text("Adiantar", color = Palette.ink) }
    }
}

@Composable
private fun PlayerControls(
    now: NowPlaying,
    playerOnly: Boolean,
    onTogglePlayerOnly: () -> Unit,
    onRecordMode: () -> Unit,
    onShareCard: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
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
            color = Palette.ink,
            trackColor = Palette.ink.copy(alpha = 0.22f),
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt(pos), color = Palette.ink.copy(alpha = 0.6f), fontSize = 12.sp)
            Text("-" + fmt(duration - pos), color = Palette.ink.copy(alpha = 0.6f), fontSize = 12.sp)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onTogglePlayerOnly) {
                Icon(
                    if (playerOnly) Icons.Rounded.Lyrics else Icons.Rounded.Album,
                    contentDescription = if (playerOnly) "Mostrar letra" else "Só o player",
                    tint = Palette.ink.copy(alpha = 0.85f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Anterior", tint = Palette.ink, modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.width(12.dp))
                FilledIconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Palette.ink, contentColor = Palette.onInk),
                ) {
                    Icon(if (now.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (now.isPlaying) "Pausar" else "Tocar", modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.width(12.dp))
                IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Próxima", tint = Palette.ink, modifier = Modifier.size(36.dp))
                }
            }
            Box {
                var shareMenu by remember { mutableStateOf(false) }
                IconButton(onClick = { shareMenu = true }) {
                    Icon(Icons.Rounded.IosShare, contentDescription = "Compartilhar", tint = Palette.ink.copy(alpha = 0.85f))
                }
                DropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Cartão para Stories") },
                        leadingIcon = { Icon(Icons.Rounded.Image, null) },
                        onClick = { shareMenu = false; onShareCard() },
                    )
                    DropdownMenuItem(
                        text = { Text("Modo gravar para Stories") },
                        leadingIcon = { Icon(Icons.Rounded.Videocam, null) },
                        onClick = { shareMenu = false; onRecordMode() },
                    )
                }
            }
        }
    }
}

private fun fmt(ms: Long): String {
    val s = (ms.coerceAtLeast(0) / 1000)
    return "%d:%02d".format(s / 60, s % 60)
}
