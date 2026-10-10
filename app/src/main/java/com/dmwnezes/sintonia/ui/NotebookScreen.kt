package com.dmwnezes.sintonia.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
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
            Text("Caderno", color = Palette.ink, fontSize = 30.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 20.dp))
            Text("Seus trechos favoritos e memórias", color = Palette.ink.copy(alpha = 0.6f), fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp))
            SegmentedControl(
                listOf("Trechos · ${data.lines.size}", "Diário · ${data.diary.size}"), tab, { tab = it },
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            )
        }

        if (tab == 0) {
            if (data.lines.isEmpty()) item { Empty(Icons.Rounded.FormatQuote, "Nenhum trecho ainda", "Na tela de letras, segure o dedo na linha que você gostou e toque em \"Salvar no Caderno\".", accent) }
            items(data.lines, key = { it.id }) { l ->
                SavedLineCard(l, accent, onDelete = { confirmDelete = { notebook.deleteLine(l.id) } })
            }
        } else {
            item {
                if (currentTrack != null) {
                    AppButton(
                        onClick = { newNote = true },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth(),
                        containerColor = Palette.ink, contentColor = Palette.onInk,
                    ) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Anotar sobre \"${currentTrack.name}\"", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (data.diary.isEmpty()) item { Empty(Icons.Rounded.EditNote, "Diário vazio", "Anote onde você estava ou o que sentiu ouvindo uma música. Depois é só voltar aqui para relembrar.", accent) }
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
private fun Empty(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String, accent: Color) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(Brush.linearGradient(listOf(accent.copy(alpha = 0.5f), accent.copy(alpha = 0.12f)))), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Palette.ink, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, color = Palette.ink, fontSize = 19.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(desc, color = Palette.ink.copy(alpha = 0.6f), fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

/** Trecho salvo como um cartão: capa desfocada ao fundo, aspas grandes e a música embaixo. */
@Composable
private fun SavedLineCard(l: SavedLine, accent: Color, onDelete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Box(
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(26.dp), ambientColor = accent, spotColor = accent)
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF15121A))
    ) {
        AsyncImage(l.imageUrl, null, Modifier.matchParentSize().blur(36.dp).graphicsLayer { scaleX = 1.3f; scaleY = 1.3f }, contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.75f)))))
        Column(Modifier.padding(20.dp)) {
            Text("\u201C", color = accent, fontSize = 54.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black)
            Text(l.text, color = Color.White, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold)
            l.translation?.let { Text(it, color = Color.White.copy(alpha = 0.65f), fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp)) }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(l.imageUrl, null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(l.track, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(l.artist, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                GlassIconButton(Icons.Rounded.IosShare, "Compartilhar como imagem", {
                    scope.launch {
                        runCatching { QuoteImage.share(context, Quote(l.text, l.translation, l.track, l.artist, l.imageUrl)) }
                            .onFailure { Toast.makeText(context, "Não consegui gerar a imagem.", Toast.LENGTH_SHORT).show() }
                    }
                }, size = 38.dp, tint = Color.White)
                Spacer(Modifier.width(6.dp))
                GlassIconButton(Icons.Rounded.DeleteOutline, "Apagar", onDelete, size = 38.dp, tint = Color.White)
            }
        }
    }
}

@Composable
private fun DiaryRow(e: DiaryEntry, accent: Color, onEdit: () -> Unit, onDelete: () -> Unit) {
    val when_ = Instant.ofEpochMilli(e.createdAt).atZone(ZoneId.systemDefault()).format(dayFmt)
    Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 8.dp)) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(accent).border(3.dp, accent.copy(alpha = 0.3f), CircleShape))
            Box(Modifier.width(2.dp).height(120.dp).background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.5f), Color.Transparent))))
        }
        Spacer(Modifier.width(12.dp))
        GlassCard(Modifier.weight(1f), corner = 20.dp, padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(when_, color = Palette.ink.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.weight(1f))
                GlassIconButton(Icons.Rounded.Edit, "Editar", onEdit, size = 32.dp)
                Spacer(Modifier.width(6.dp))
                GlassIconButton(Icons.Rounded.DeleteOutline, "Apagar", onDelete, size = 32.dp)
            }
            Spacer(Modifier.height(6.dp))
            Text(e.note, color = Palette.ink, fontSize = 16.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).background(Palette.ink.copy(alpha = 0.06f)).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(e.imageUrl, null, Modifier.size(30.dp).clip(RoundedCornerShape(7.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.track, color = Palette.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(e.artist, color = Palette.ink.copy(alpha = 0.55f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
