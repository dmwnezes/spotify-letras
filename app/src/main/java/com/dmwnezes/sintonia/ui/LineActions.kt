package com.dmwnezes.sintonia.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.sintonia.AppGraph
import com.dmwnezes.sintonia.data.Track
import com.dmwnezes.sintonia.notebook.DiaryEntry
import com.dmwnezes.sintonia.notebook.NotebookStore
import com.dmwnezes.sintonia.notebook.Quote
import com.dmwnezes.sintonia.notebook.QuoteImage
import com.dmwnezes.sintonia.notebook.SavedLine
import kotlinx.coroutines.launch

/** Menu que aparece ao segurar o dedo numa linha da letra. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LineActionsSheet(track: Track, line: PickedLine, onDismiss: () -> Unit, onNote: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notebook = AppGraph.notebook
    val alreadySaved = remember { notebook.hasLine(track.id, line.text) }
    val itemColors = ListItemDefaults.colors(containerColor = Color.Transparent)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                Text(line.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                line.translation?.let { Text(it, fontSize = 15.sp, color = Color.White.copy(alpha = 0.6f)) }
                Text("${track.name} · ${track.artistLine}", fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(8.dp))
            ListItem(
                headlineContent = { Text(if (alreadySaved) "Já está no Caderno" else "Salvar no Caderno") },
                leadingContent = { Icon(if (alreadySaved) Icons.Rounded.BookmarkAdded else Icons.Rounded.Bookmark, null) },
                colors = itemColors,
                modifier = Modifier.clickableRow {
                    notebook.saveLine(
                        SavedLine(NotebookStore.newId(), track.id, track.name, track.artistLine, track.imageUrl,
                            line.text, line.translation, line.timeMs, System.currentTimeMillis())
                    )
                    Toast.makeText(context, "Trecho salvo no Caderno", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
            )
            ListItem(
                headlineContent = { Text("Compartilhar como imagem") },
                leadingContent = { Icon(Icons.Rounded.Image, null) },
                colors = itemColors,
                modifier = Modifier.clickableRow {
                    scope.launch {
                        runCatching { QuoteImage.share(context, Quote(line.text, line.translation, track.name, track.artistLine, track.imageUrl)) }
                            .onFailure { Toast.makeText(context, "Não consegui gerar a imagem.", Toast.LENGTH_SHORT).show() }
                        onDismiss()
                    }
                },
            )
            ListItem(
                headlineContent = { Text("Anotar um momento nesta música") },
                leadingContent = { Icon(Icons.Rounded.EditNote, null) },
                colors = itemColors,
                modifier = Modifier.clickableRow(onNote),
            )
        }
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit) = this.clickable(onClick = onClick)

/** Caixa para escrever (ou editar) uma anotação do diário musical. */
@Composable
fun NoteDialog(
    track: Track?,
    positionMs: Long,
    onDismiss: () -> Unit,
    existing: DiaryEntry? = null,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(existing?.note.orEmpty()) }
    val title = existing?.track ?: track?.name ?: ""
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Anotar um momento" else "Editar anotação") },
        text = {
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    "Onde você estava, com quem, o que sentiu… Fica guardado no seu diário musical.",
                    fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 10.dp),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Ex.: ouvindo na estrada para a praia") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = {
                val e = existing?.copy(note = text.trim()) ?: DiaryEntry(
                    id = NotebookStore.newId(),
                    trackId = track?.id.orEmpty(),
                    track = track?.name.orEmpty(),
                    artist = track?.artistLine.orEmpty(),
                    imageUrl = track?.imageUrl,
                    note = text.trim(),
                    positionMs = positionMs,
                    createdAt = System.currentTimeMillis(),
                )
                AppGraph.notebook.saveEntry(e)
                Toast.makeText(context, "Anotado no diário", Toast.LENGTH_SHORT).show()
                onDismiss()
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
