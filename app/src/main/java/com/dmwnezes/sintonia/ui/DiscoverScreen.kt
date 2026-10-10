package com.dmwnezes.sintonia.ui

import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dmwnezes.sintonia.reco.DiscoverUi
import com.dmwnezes.sintonia.reco.DiscoverViewModel
import com.dmwnezes.sintonia.reco.PreviewState
import com.dmwnezes.sintonia.reco.RecoData
import com.dmwnezes.sintonia.reco.RecoTrack
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val Like = Color(0xFF3DDC84)
private val Nope = Color(0xFFFF6B6B)

@Composable
fun DiscoverScreen(vm: DiscoverViewModel, accent: Color, onRelogin: () -> Unit, bottomPadding: PaddingValues) {
    val ui by vm.ui.collectAsState()
    val data by vm.store.state.collectAsState()
    val pv by vm.preview.state.collectAsState()
    var section by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(section) {
        when (section) { 0 -> vm.ensureMix(); 1 -> vm.ensureDeck() }
    }
    // Sai da aba: para a prévia.
    DisposableEffect(Unit) { onDispose { vm.preview.stop() } }
    LaunchedEffect(section) { vm.preview.stop() }

    Column(Modifier.fillMaxSize().background(Palette.scrim.copy(alpha = if (Palette.dark) 0.35f else 0.25f)).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Descobrir", color = Palette.ink, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Text("Músicas novas a partir do que você ouve", color = Palette.ink.copy(alpha = 0.6f), fontSize = 13.sp)
            }
            ui.working?.let {
                CircularProgressIndicator(color = Palette.ink, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            }
        }
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Para você", "Deslizar", "Curtidas (${data.likes.size})").forEachIndexed { i, label ->
                AppChip(label, selected = section == i, onClick = { section = i })
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(bottom = bottomPadding.calculateBottomPadding())) {
            when (section) {
                0 -> ForYou(vm, ui, data, pv, accent)
                1 -> Deck(vm, ui, pv, accent)
                else -> Likes(vm, ui, data, pv, accent, onRelogin)
            }
        }
    }
}

// ---------------- Para você ----------------

@Composable
private fun ForYou(vm: DiscoverViewModel, ui: DiscoverUi, data: RecoData, pv: PreviewState, accent: Color) {
    val mix = data.mix
    when {
        ui.mixLoading && mix.isEmpty() -> Loading("Escutando o seu gosto…", "Buscando artistas parecidos com os que você mais ouve.")
        ui.mixError != null && mix.isEmpty() -> ErrorBox(ui.mixError) { vm.ensureMix(force = true) }
        else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item { MixHeader(mix, accent, ui.mixLoading, onPlayAll = { vm.playInSpotify(mix) }, onNew = { vm.ensureMix(force = true) }) }
            itemsIndexed(mix, key = { _, t -> t.dzId }) { i, t ->
                TrackRow(
                    t = t, accent = accent, pv = pv, liked = data.likes.any { it.dzId == t.dzId },
                    onPreview = { vm.togglePreview(t) },
                    onLike = { if (data.likes.any { it.dzId == t.dzId }) vm.unlike(t) else vm.like(t) },
                    onPlay = { vm.playInSpotify(mix, i) },
                    onNope = { vm.dislike(t) },
                )
            }
            item {
                Text(
                    "Curta ou pule músicas para o próximo mix ficar mais a sua cara. Um mix novo é montado todo dia.",
                    color = Palette.ink.copy(alpha = 0.5f), fontSize = 12.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
        }
    }
}

@Composable
private fun MixHeader(mix: List<RecoTrack>, accent: Color, loading: Boolean, onPlayAll: () -> Unit, onNew: () -> Unit) {
    val day = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale("pt", "BR")))
    Column(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.15f))))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverMosaic(mix.take(4).map { it.cover })
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Mix do dia", color = Palette.ink, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text(day.replaceFirstChar { it.uppercase() }, color = Palette.ink.copy(alpha = 0.7f), fontSize = 13.sp)
                Text("${mix.size} músicas que você ainda não ouve", color = Palette.ink.copy(alpha = 0.7f), fontSize = 13.sp)
            }
        }
        val reasons = mix.map { it.reason }.distinct().take(4)
        if (reasons.isNotEmpty()) {
            Text("Inspirado em ${reasons.joinToString(", ")}", color = Palette.ink.copy(alpha = 0.8f), fontSize = 13.sp,
                modifier = Modifier.padding(top = 12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppButton(onClick = onPlayAll, enabled = mix.isNotEmpty(), modifier = Modifier.weight(1.1f), contentPadding = PaddingValues(horizontal = 10.dp),
                containerColor = Palette.ink, contentColor = Palette.onInk) {
                Icon(Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(4.dp))
                Text("Tocar tudo", fontWeight = FontWeight.Bold, maxLines = 1)
            }
            AppOutlinedButton(onClick = onNew, enabled = !loading, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 10.dp)) {
                if (loading) CircularProgressIndicator(color = Palette.ink, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                else Icon(Icons.Rounded.Refresh, null, tint = Palette.ink, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text("Outro mix", color = Palette.ink, maxLines = 1)
            }
        }
    }
}

@Composable
private fun CoverMosaic(covers: List<String?>) {
    Column(Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)).background(Palette.ink.copy(alpha = 0.1f))) {
        for (r in 0..1) Row(Modifier.weight(1f)) {
            for (c in 0..1) AsyncImage(covers.getOrNull(r * 2 + c), null, Modifier.weight(1f).fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun TrackRow(
    t: RecoTrack, accent: Color, pv: PreviewState, liked: Boolean,
    onPreview: () -> Unit, onLike: () -> Unit, onPlay: () -> Unit, onNope: (() -> Unit)?,
) {
    val active = pv.dzId == t.dzId
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onPreview), contentAlignment = Alignment.Center) {
            AsyncImage(t.cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (active) 0.45f else 0.18f)))
            if (active && pv.loading) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            else Icon(if (active && pv.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Ouvir prévia", tint = Color.White)
            if (active) ProgressRing(pv.progress, accent, Modifier.fillMaxSize().padding(3.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, color = Palette.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.artist, color = Palette.ink.copy(alpha = 0.65f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("porque você ouve ${t.reason}", color = accent.copy(alpha = 0.95f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        onNope?.let { IconButton(onClick = it) { Icon(Icons.Rounded.ThumbDown, "Não gostei", tint = Palette.ink.copy(alpha = 0.45f), modifier = Modifier.size(20.dp)) } }
        IconButton(onClick = onLike) {
            Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (liked) "Descurtir" else "Curtir",
                tint = if (liked) Like else Palette.ink.copy(alpha = 0.75f))
        }
    }
}

@Composable
private fun ProgressRing(p: Float, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        drawArc(color, -90f, 360f * p, false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
    }
}

// ---------------- Deslizar ----------------

@Composable
private fun Deck(vm: DiscoverViewModel, ui: DiscoverUi, pv: PreviewState, accent: Color) {
    val deck = ui.deck
    when {
        deck.isEmpty() && ui.deckLoading -> Loading("Separando sugestões…", "Cada cartão toca 30 segundos da música.")
        deck.isEmpty() -> ErrorBox(ui.deckError ?: "Sem sugestões agora.") { vm.ensureDeck() }
        else -> {
            val top = deck.first()
            // Toca a prévia do cartão da frente sozinha.
            LaunchedEffect(top.dzId) { vm.togglePreview(top) }
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val width = constraints.maxWidth.toFloat()
                    // Cartões de trás, um pouco menores.
                    deck.drop(1).take(2).reversed().forEachIndexed { i, t ->
                        val depth = 2 - i
                        SwipeCardBody(t, accent, null, Modifier.graphicsLayer {
                            scaleX = 1f - depth * 0.05f; scaleY = 1f - depth * 0.05f; translationY = depth * 26f; alpha = 0.9f
                        })
                    }
                    key(top.dzId) {
                        SwipeCard(top, accent, pv, width, onDecide = { liked -> vm.swipe(top, liked) }, onPreview = { vm.togglePreview(top) })
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    RoundAction(Icons.Rounded.Close, "Pular", Nope, 62) { vm.swipe(top, false) }
                    RoundAction(Icons.AutoMirrored.Rounded.OpenInNew, "Tocar no Spotify", Palette.ink.copy(alpha = 0.8f), 48) { vm.playInSpotify(listOf(top)) }
                    RoundAction(Icons.Rounded.Favorite, "Curtir", Like, 62) { vm.swipe(top, true) }
                }
            }
        }
    }
}

@Composable
private fun SwipeCard(t: RecoTrack, accent: Color, pv: PreviewState, width: Float, onDecide: (Boolean) -> Unit, onPreview: () -> Unit) {
    val scope = rememberCoroutineScope()
    val dx = remember { Animatable(0f) }
    val dy = remember { Animatable(0f) }
    fun fling(liked: Boolean) = scope.launch {
        dx.animateTo(if (liked) width * 1.4f else -width * 1.4f, tween(260))
        onDecide(liked)
    }
    val drag = dx.value / width
    SwipeCardBody(
        t, accent, pv,
        Modifier
            .graphicsLayer { translationX = dx.value; translationY = dy.value; rotationZ = drag * 14f }
            .pointerInput(t.dzId) {
                detectDragGestures(
                    onDragEnd = {
                        when {
                            dx.value > width * 0.3f -> fling(true)
                            dx.value < -width * 0.3f -> fling(false)
                            else -> scope.launch { launch { dx.animateTo(0f, tween(220)) }; dy.animateTo(0f, tween(220)) }
                        }
                    },
                ) { change, amount ->
                    change.consume()
                    scope.launch { dx.snapTo(dx.value + amount.x); dy.snapTo(dy.value + amount.y * 0.3f) }
                }
            },
        onPreview = onPreview,
        likeAlpha = (drag * 3f).coerceIn(0f, 1f),
        nopeAlpha = (-drag * 3f).coerceIn(0f, 1f),
    )
}

@Composable
private fun SwipeCardBody(
    t: RecoTrack, accent: Color, pv: PreviewState?, modifier: Modifier,
    onPreview: () -> Unit = {}, likeAlpha: Float = 0f, nopeAlpha: Float = 0f,
) {
    Box(
        modifier.fillMaxWidth().aspectRatio(0.72f, matchHeightConstraintsFirst = true)
            .shadow(18.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp))
    ) {
        AsyncImage(t.cover, t.album, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.88f))))
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            Text("porque você ouve ${t.reason}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(accent.copy(alpha = 0.55f)).padding(horizontal = 10.dp, vertical = 4.dp))
            Spacer(Modifier.height(10.dp))
            Text(t.title, color = Color.White, fontSize = 28.sp, lineHeight = 31.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(t.artist, color = Color.White.copy(alpha = 0.85f), fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.album, color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (pv != null) {
                val active = pv.dzId == t.dzId
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (active && pv.loading) Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else GlassIconButton(
                        if (active && pv.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Prévia", onPreview,
                        size = 42.dp, tint = Color.White, selected = true,
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))) {
                        Box(Modifier.fillMaxWidth(if (active) pv.progress.coerceIn(0f, 1f) else 0f).height(4.dp).background(Color.White))
                    }
                    Text("  prévia", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }
        }
        Stamp("CURTI", Like, likeAlpha, Modifier.align(Alignment.TopStart).padding(24.dp).graphicsLayer { rotationZ = -14f })
        Stamp("PULAR", Nope, nopeAlpha, Modifier.align(Alignment.TopEnd).padding(24.dp).graphicsLayer { rotationZ = 14f })
    }
}

@Composable
private fun Stamp(text: String, color: Color, alpha: Float, modifier: Modifier) {
    if (alpha <= 0.01f) return
    Text(
        text, color = color, fontSize = 30.sp, fontWeight = FontWeight.Black,
        modifier = modifier.graphicsLayer { this.alpha = alpha }.border(4.dp, color, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 2.dp),
    )
}

@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, tint: Color, size: Int, onClick: () -> Unit) {
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val sc by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.88f else 1f, androidx.compose.animation.core.spring(dampingRatio = 0.4f), label = "acao")
    Box(
        Modifier.size(size.dp).graphicsLayer { scaleX = sc; scaleY = sc }
            .shadow(14.dp, CircleShape, ambientColor = tint, spotColor = tint)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(lerp(Palette.surface, Color.White, 0.08f), Palette.surface)))
            .border(1.5.dp, Brush.verticalGradient(listOf(tint.copy(alpha = 0.55f), tint.copy(alpha = 0.12f))), CircleShape)
            .clickable(source, androidx.compose.material3.ripple(color = tint), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = tint, modifier = Modifier.size((size * 0.45f).dp)) }
}

// ---------------- Curtidas ----------------

@Composable
private fun Likes(vm: DiscoverViewModel, ui: DiscoverUi, data: RecoData, pv: PreviewState, accent: Color, onRelogin: () -> Unit) {
    val likes = data.likes
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(Palette.ink.copy(alpha = 0.07f)).padding(16.dp)
            ) {
                Text("Playlist no Spotify", color = Palette.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (!ui.canEditPlaylists) {
                    Text(
                        "Para criar a playlist \"Sintonia · Descobertas\", o Spotify precisa de uma permissão nova. Entre de novo uma vez e aceite.",
                        color = Palette.ink.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp),
                    )
                    AppButton(onClick = onRelogin, containerColor = Color(0xFF1ED760), contentColor = Color.Black) {
                        Text("Entrar de novo no Spotify", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Text("Mandar cada curtida para a playlist", color = Palette.ink.copy(alpha = 0.8f), fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(checked = ui.autoPlaylist, onCheckedChange = vm::setAutoPlaylist)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                        AppOutlinedButton(onClick = vm::saveAllLikes, enabled = likes.isNotEmpty() && ui.working == null) { Text("Salvar todas", color = Palette.ink) }
                        if (ui.playlistId != null) TextButton(onClick = vm::openPlaylist) { Text("Abrir playlist", color = Palette.ink) }
                    }
                }
            }
        }
        if (likes.isEmpty()) {
            item {
                Text(
                    "Nada curtido ainda. Toque no coração no mix do dia ou deslize para a direita nos cartões.",
                    color = Palette.ink.copy(alpha = 0.6f), fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }
        } else {
            item {
                Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${likes.size} curtida(s)", color = Palette.ink, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.playInSpotify(likes) }) {
                        Icon(Icons.Rounded.PlayArrow, null, tint = Palette.ink)
                        Text("Tocar todas", color = Palette.ink)
                    }
                }
            }
            itemsIndexed(likes, key = { _, t -> t.dzId }) { i, t ->
                TrackRow(t, accent, pv, liked = true, onPreview = { vm.togglePreview(t) }, onLike = { vm.unlike(t) },
                    onPlay = { vm.playInSpotify(likes, i) }, onNope = null)
            }
        }
    }
}

// ---------------- Estados ----------------

@Composable
private fun Loading(title: String, desc: String) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Palette.ink)
        Spacer(Modifier.height(16.dp))
        Text(title, color = Palette.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(desc, color = Palette.ink.copy(alpha = 0.6f), fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ErrorBox(msg: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(msg, color = Palette.ink.copy(alpha = 0.8f), fontSize = 16.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        AppOutlinedButton(onClick = onRetry) { Text("Tentar de novo", color = Palette.ink) }
    }
}
