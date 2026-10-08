package com.dmwnezes.sintonia.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dmwnezes.sintonia.AppGraph
import com.dmwnezes.sintonia.data.Track
import com.dmwnezes.sintonia.notebook.DiaryEntry
import com.dmwnezes.sintonia.notebook.Quote
import com.dmwnezes.sintonia.notebook.QuoteImage
import com.dmwnezes.sintonia.notebook.SavedLine
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ptBR = Locale("pt", "BR")
private val monthFmt = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ptBR)
private val dayFmt = DateTimeFormatter.ofPattern("d MMM · HH:mm", ptBR)

/** Aba Caderno: trechos salvos e diário musical. */
@Composable
fun NotebookScreen(currentTrack: Track?, positionMs: () -> Long, accent: Color, bottomPadding: PaddingValues) {
    val notebook = AppGraph.notebook
    val data by notebook.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }
    var newNote by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<DiaryEntry?>(null) }
    var confirmDelete by remember { mutableStateOf<(() -> Unit)?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().background(Palette.scrim.copy(alpha = 0.40f)).statusBarsPadding(),
        contentPadding = PaddingValues(top = 12.dp, bottom = bottomPadding.calculateBottomPadding() + 32.dp),
    ) {
        item {
            Text("Caderno", color = Palette.ink, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 20.dp))
            Text(
                "Segure o dedo numa linha da letra para salvar um trecho ou anotar um momento.",
                color = Palette.ink.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Trechos (${data.lines.size})", "Diário (${data.diary.size})").forEachIndexed { i, label ->
                    FilterChip(
                        selected = tab == i, onClick = { tab = i }, label = { Text(label) }, border = null,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Palette.ink.copy(alpha = 0.08f), labelColor = Palette.ink.copy(alpha = 0.8f),
                            selectedContainerColor = Palette.ink, selectedLabelColor = Palette.onInk,
                        ),
                    )
                }
            }
        }

        if (tab == 0) {
            if (data.lines.isEmpty()) item { Empty("Nenhum trecho salvo ainda.", "Na tela de letras, segure o dedo na linha que você gostou e toque em \"Salvar no Caderno\".") }
            items(data.lines, key = { it.id }) { l ->
                SavedLineCard(l, accent, onDelete = { confirmDelete = { notebook.deleteLine(l.id) } })
            }
        } else {
            item {
                if (currentTrack != null) {
                    Button(
                        onClick = { newNote = true },
                        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.ink, contentColor = Palette.onInk),
                    ) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Anotar sobre \"${currentTrack.name}\"", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (data.diary.isEmpty()) item { Empty("Seu diário musical está vazio.", "Anote onde você estava ou o que sentiu ouvindo uma música. Depois é só voltar aqui para relembrar.") }
            val zone = ZoneId.systemDefault()
            val byMonth = data.diary.groupBy { Instant.ofEpochMilli(it.createdAt).atZone(zone).format(monthFmt) }
            byMonth.forEach { (month, entries) ->
                item(key = "m-$month") {
                    Text(
                        month.replaceFirstChar { it.uppercase() }, color = Palette.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
                    )
                }
                items(entries, key = { it.id }) { e ->
                    DiaryRow(e, accent, onEdit = { editing = e }, onDelete = { confirmDelete = { notebook.deleteEntry(e.id) } })
                }
            }
        }
    }

    if (newNote && currentTrack != null) NoteDialog(track = currentTrack, positionMs = positionMs(), onDismiss = { newNote = false })
    editing?.let { e -> NoteDialog(track = null, positionMs = e.positionMs, existing = e, onDismiss = { editing = null }) }
    confirmDelete?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Apagar?") },
            text = { Text("Isso não pode ser desfeito.") },
            confirmButton = { TextButton(onClick = { action(); confirmDelete = null }) { Text("Apagar") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Empty(title: String, desc: String) {
    Column(Modifier.fillMaxWidth().padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Palette.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(desc, color = Palette.ink.copy(alpha = 0.6f), fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SavedLineCard(l: SavedLine, accent: Color, onDelete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(
        Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth()
            .clip(RoundedCornerShape(22.dp)).background(Palette.ink.copy(alpha = 0.08f)).padding(18.dp)
    ) {
        Row {
            Box(Modifier.width(4.dp).height(26.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(l.text, color = Palette.ink, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.ExtraBold)
                l.translation?.let { Text(it, color = Palette.ink.copy(alpha = 0.6f), fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp)) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(l.imageUrl, null, Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(l.track, color = Palette.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(l.artist, color = Palette.ink.copy(alpha = 0.55f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = {
                scope.launch {
                    runCatching { QuoteImage.share(context, Quote(l.text, l.translation, l.track, l.artist, l.imageUrl)) }
                        .onFailure { Toast.makeText(context, "Não consegui gerar a imagem.", Toast.LENGTH_SHORT).show() }
                }
            }) { Icon(Icons.Rounded.Image, "Compartilhar como imagem", tint = Palette.ink) }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "Apagar", tint = Palette.ink.copy(alpha = 0.6f)) }
        }
    }
}

@Composable
private fun DiaryRow(e: DiaryEntry, accent: Color, onEdit: () -> Unit, onDelete: () -> Unit) {
    val when_ = Instant.ofEpochMilli(e.createdAt).atZone(ZoneId.systemDefault()).format(dayFmt)
    Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth()) {
        // linha do tempo
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 6.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
            Box(Modifier.width(2.dp).height(110.dp).background(Palette.ink.copy(alpha = 0.12f)))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(Palette.ink.copy(alpha = 0.07f)).padding(14.dp)) {
            Text(when_, color = Palette.ink.copy(alpha = 0.55f), fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Text(e.note, color = Palette.ink, fontSize = 16.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(e.imageUrl, null, Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.track, color = Palette.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(e.artist, color = Palette.ink.copy(alpha = 0.55f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, "Editar", tint = Palette.ink.copy(alpha = 0.7f)) }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "Apagar", tint = Palette.ink.copy(alpha = 0.6f)) }
            }
        }
    }
}
