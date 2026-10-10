package com.dmwnezes.sintonia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.dmwnezes.sintonia.AppGraph
import com.dmwnezes.sintonia.data.Track
import kotlinx.coroutines.delay

/** Aba Buscar: procure qualquer música e toque no Spotify na hora (ou coloque na fila). */
@Composable
fun SearchScreen(
    onPlay: (Track) -> Unit,
    onQueue: (Track) -> Unit,
    accent: Color,
    bottomPadding: PaddingValues = PaddingValues(),
    autoFocus: Boolean = false,
    onBack: (() -> Unit)? = null,
    searcher: suspend (String) -> List<Track> = { AppGraph.spotify.search(it) },
) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var recents by remember { mutableStateOf(runCatching { AppGraph.prefs.recentSearches }.getOrDefault(emptyList())) }
    var playingId by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) { if (autoFocus) runCatching { focus.requestFocus() } }
    // Busca enquanto digita, com uma pequena espera para não consultar a cada letra.
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { results = emptyList(); error = null; return@LaunchedEffect }
        delay(380)
        loading = true
        val r = runCatching { searcher(q) }
        loading = false
        r.onSuccess { results = it; error = null }.onFailure { error = it.message ?: "Não consegui buscar agora." }
    }
    fun remember(q: String) {
        val list = (listOf(q.trim()) + recents.filter { !it.equals(q.trim(), ignoreCase = true) }).take(8)
        recents = list
        runCatching { AppGraph.prefs.recentSearches = list }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = if (onBack != null) 8.dp else 20.dp, end = 20.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", onBack, size = 42.dp)
                Spacer(Modifier.width(10.dp))
            } else {
                Text("Buscar", color = Palette.ink, fontSize = 30.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            }
        }
        // Campo de busca em pílula.
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 14.dp).fillMaxWidth().height(54.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.verticalGradient(listOf(Palette.ink.copy(alpha = 0.14f), Palette.ink.copy(alpha = 0.07f))))
                .border(1.dp, Palette.ink.copy(alpha = 0.16f), RoundedCornerShape(50))
                .padding(start = 18.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = Palette.ink.copy(alpha = 0.7f))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text("Música, artista ou álbum", color = Palette.ink.copy(alpha = 0.45f), fontSize = 16.sp)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Palette.ink, fontSize = 16.sp),
                    cursorBrush = SolidColor(Palette.ink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) remember(query); focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (loading) CircularProgressIndicator(color = Palette.ink, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            if (query.isNotEmpty()) GlassIconButton(Icons.Rounded.Close, "Limpar", { query = "" }, size = 36.dp)
        }

        when {
            error != null -> Text(error!!, color = Palette.ink.copy(alpha = 0.7f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
            query.trim().length < 2 -> LazyColumn(contentPadding = PaddingValues(bottom = bottomPadding.calculateBottomPadding() + 24.dp)) {
                item {
                    GlassCard(Modifier.padding(horizontal = 20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(42.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.PlayArrow, null, tint = Color.Black)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Toque para tocar na hora", color = Palette.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("A música começa no seu Spotify. O botão de fila põe ela para tocar em seguida.", color = Palette.ink.copy(alpha = 0.6f), fontSize = 13.sp)
                            }
                        }
                    }
                }
                if (recents.isNotEmpty()) {
                    item { SectionHeader("Buscas recentes", action = "Limpar") { recents = emptyList(); runCatching { AppGraph.prefs.recentSearches = emptyList() } } }
                    items(recents) { r ->
                        Row(
                            Modifier.fillMaxWidth().clickable { query = r }.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.History, null, tint = Palette.ink.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(14.dp))
                            Text(r, color = Palette.ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            results.isEmpty() && !loading -> Text("Nada encontrado.", color = Palette.ink.copy(alpha = 0.6f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding.calculateBottomPadding() + 24.dp)) {
                results.firstOrNull()?.let { top ->
                    item(key = "top-${top.id}") {
                        TopResult(top, accent, playing = playingId == top.id, onPlay = { remember(query); playingId = top.id; onPlay(top) }, onQueue = { onQueue(top) })
                    }
                }
                if (results.size > 1) item { SectionHeader("Músicas") }
                items(results.drop(1), key = { it.id }) { t ->
                    ResultRow(t, accent, playing = playingId == t.id, onPlay = { remember(query); playingId = t.id; onPlay(t) }, onQueue = { onQueue(t) })
                }
            }
        }
    }
}

/** Primeiro resultado em destaque, com capa grande e botão de tocar. */
@Composable
private fun TopResult(t: Track, accent: Color, playing: Boolean, onPlay: () -> Unit, onQueue: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.30f), Palette.ink.copy(alpha = 0.06f))))
            .border(1.dp, Palette.ink.copy(alpha = 0.10f), RoundedCornerShape(24.dp))
            .clickable(onClick = onPlay).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(t.imageUrl, null, Modifier.size(88.dp).clip(RoundedCornerShape(14.dp)).background(Palette.ink.copy(alpha = 0.08f)), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Melhor resultado", color = Palette.ink.copy(alpha = 0.6f), fontSize = 12.sp)
            Text(t.name, color = Palette.ink, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(t.artistLine, color = Palette.ink.copy(alpha = 0.7f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppButton(onClick = onPlay, containerColor = accent, contentColor = Color.Black, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), modifier = Modifier.height(38.dp)) {
                    Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (playing) "Tocando" else "Tocar", fontSize = 14.sp)
                }
                Spacer(Modifier.width(8.dp))
                GlassIconButton(Icons.AutoMirrored.Rounded.PlaylistAdd, "Tocar em seguida", onQueue, size = 38.dp)
            }
        }
    }
}

@Composable
private fun ResultRow(t: Track, accent: Color, playing: Boolean, onPlay: () -> Unit, onQueue: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (playing) accent.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onPlay).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(t.imageUrl, null, Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(Palette.ink.copy(alpha = 0.08f)), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.name, color = if (playing) accent else Palette.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${t.artistLine} · ${fmtTime(t.durationMs)}", color = Palette.ink.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        GlassIconButton(Icons.AutoMirrored.Rounded.PlaylistAdd, "Tocar em seguida", onQueue, size = 38.dp)
    }
}

/** A mesma busca em tela cheia por cima de tudo (atalhos e botão da tela de letras). */
@Composable
fun SearchDialog(onPlay: (Track) -> Unit, onQueue: (Track) -> Unit, onDismiss: () -> Unit, accent: Color = Color.White) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Palette.surface).navigationBarsPadding()) {
            SearchScreen(onPlay = { onPlay(it); onDismiss() }, onQueue = onQueue, accent = accent, autoFocus = true, onBack = onDismiss)
        }
    }
}

