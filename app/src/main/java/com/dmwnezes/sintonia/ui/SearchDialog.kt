package com.dmwnezes.sintonia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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

/** Buscar qualquer música e mandar tocar no Spotify (ou colocar na fila). */
@Composable
fun SearchDialog(onPlay: (Track) -> Unit, onQueue: (Track) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }
    // Busca enquanto digita, com uma pequena espera para não consultar a cada letra.
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { results = emptyList(); error = null; return@LaunchedEffect }
        delay(380)
        loading = true
        val r = runCatching { AppGraph.spotify.search(q) }
        loading = false
        r.onSuccess { results = it; error = null }.onFailure { error = it.message ?: "Não consegui buscar agora." }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Palette.surface)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", tint = Palette.ink) }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text("Música, artista ou álbum") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Limpar") } },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {}),
                        shape = RoundedCornerShape(28.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Palette.ink, unfocusedTextColor = Palette.ink,
                            focusedBorderColor = Palette.ink.copy(alpha = 0.6f), unfocusedBorderColor = Palette.ink.copy(alpha = 0.25f),
                            cursorColor = Palette.ink, focusedLeadingIconColor = Palette.ink, unfocusedLeadingIconColor = Palette.ink.copy(alpha = 0.6f),
                            focusedTrailingIconColor = Palette.ink, unfocusedTrailingIconColor = Palette.ink,
                            focusedPlaceholderColor = Palette.ink.copy(alpha = 0.5f), unfocusedPlaceholderColor = Palette.ink.copy(alpha = 0.5f),
                        ),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                    )
                }
                when {
                    loading && results.isEmpty() -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Palette.ink)
                    }
                    error != null -> Text(error!!, color = Palette.ink.copy(alpha = 0.7f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
                    query.trim().length < 2 -> Text(
                        "Toque numa música para ela tocar no Spotify agora, ou use o botão de fila para ela tocar em seguida.",
                        color = Palette.ink.copy(alpha = 0.6f), fontSize = 14.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                    results.isEmpty() -> Text("Nada encontrado.", color = Palette.ink.copy(alpha = 0.6f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(results, key = { it.id }) { t ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onPlay(t); onDismiss() }.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AsyncImage(t.imageUrl, null, Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(Palette.ink.copy(alpha = 0.08f)), contentScale = ContentScale.Crop)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(t.name, color = Palette.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${t.artistLine} · ${t.album}", color = Palette.ink.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(fmtDur(t.durationMs), color = Palette.ink.copy(alpha = 0.5f), fontSize = 12.sp)
                                IconButton(onClick = { onQueue(t) }) {
                                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Tocar em seguida", tint = Palette.ink.copy(alpha = 0.8f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun fmtDur(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
