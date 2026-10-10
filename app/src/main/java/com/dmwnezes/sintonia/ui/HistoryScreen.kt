package com.dmwnezes.sintonia.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.sintonia.history.HistoryStats
import com.dmwnezes.sintonia.history.HistoryUi
import com.dmwnezes.sintonia.history.PeriodStats
import com.dmwnezes.sintonia.history.Ranked
import com.dmwnezes.sintonia.history.Retrospective
import com.dmwnezes.sintonia.history.fmtHours
import com.dmwnezes.sintonia.history.fmtInt
import com.dmwnezes.sintonia.history.monthLabel
import kotlin.math.roundToInt

private val Dim: Color @Composable get() = Palette.ink.copy(alpha = 0.62f)
private val Card: Color @Composable get() = Palette.ink.copy(alpha = 0.07f)

@Composable
fun HistoryScreen(
    ui: HistoryUi,
    accent: Color,
    onImport: (List<android.net.Uri>) -> Unit,
    onSelect: (String) -> Unit,
    summaryText: () -> String?,
    onSaveBackup: (android.net.Uri) -> Unit,
    onClear: () -> Unit,
    onWrapped: (String) -> Unit,
    bottomPadding: PaddingValues,
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { onImport(it) }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(onSaveBackup)
    }
    var showExport by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val openPicker = { picker.launch(arrayOf("application/zip", "application/json", "application/octet-stream", "text/plain", "*/*")) }

    LazyColumn(
        Modifier.fillMaxSize().background(Palette.scrim.copy(alpha = 0.40f)).statusBarsPadding(),
        contentPadding = PaddingValues(top = 12.dp, bottom = bottomPadding.calculateBottomPadding() + 32.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Histórico", color = Palette.ink, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    ui.stats?.all?.let { Text("${it.firstDay} a ${it.lastDay}", color = Dim, fontSize = 13.sp) }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppOutlinedButton(onClick = openPicker, enabled = !ui.importing, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.FileOpen, null, tint = Palette.ink, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ler arquivo", color = Palette.ink)
                }
                AppButton(
                    onClick = { showExport = true },
                    enabled = ui.stats != null && !ui.importing,
                    modifier = Modifier.weight(1f),
                    containerColor = Palette.ink, contentColor = Palette.onInk,
                ) {
                    Icon(Icons.Rounded.IosShare, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Exportar")
                }
            }
        }

        val stats = ui.stats
        when {
            !ui.loaded -> item { Loading() }
            ui.importing -> item { Importing(ui.importedCount) }
            stats?.all == null -> item { EmptyHistory(openPicker) }
            else -> historyContent(stats, ui.selected, accent, onSelect, onWrapped) { confirmClear = true }
        }
    }

    if (showExport) {
        AlertDialog(
            onDismissRequest = { showExport = false },
            title = { Text("Exportar histórico") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExportOption(Icons.Rounded.AutoAwesome, "Mandar resumo para o Claude", "Abre o compartilhar com um resumo em texto. Escolha o app do Claude e converse sobre seus hábitos.") {
                        showExport = false
                        summaryText()?.let { text ->
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_SUBJECT, "Meu histórico do Spotify")
                                .putExtra(Intent.EXTRA_TEXT, text)
                            context.startActivity(Intent.createChooser(send, "Mandar resumo para…"))
                        }
                    }
                    ExportOption(Icons.Rounded.Save, "Salvar backup (.json)", "Guarda as estatísticas num arquivo. Dá para abrir de novo em \"Ler arquivo\".") {
                        showExport = false
                        backup.launch("sintonia-historico.json")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showExport = false }) { Text("Fechar") } },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Apagar o histórico?") },
            text = { Text("As estatísticas saem do celular. O arquivo original do Spotify continua onde está.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; onClear() }) { Text("Apagar") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ExportOption(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Icon(icon, null, modifier = Modifier.padding(top = 2.dp).size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(desc, fontSize = 12.sp, color = Palette.ink.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Palette.ink) }
}

@Composable
private fun Importing(count: Int) {
    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Palette.ink)
        Spacer(Modifier.height(16.dp))
        Text("Lendo seu histórico…", color = Palette.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        if (count > 0) Text("${fmtInt(count)} reproduções até agora", color = Dim, fontSize = 14.sp)
    }
}

@Composable
private fun EmptyHistory(onPick: () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Panel {
            Text("Seu histórico completo, ano a ano", color = Palette.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Step("1", "Em spotify.com → Conta → Privacidade, peça o \"Histórico de streaming estendido\".")
            Step("2", "Quando o e-mail do Spotify chegar, baixe o arquivo .zip no celular.")
            Step("3", "Toque em \"Ler arquivo\" e escolha o .zip (ou os arquivos .json de dentro dele).")
            Spacer(Modifier.height(8.dp))
            Text(
                "Tudo é calculado aqui no celular. Nada do seu histórico é enviado para lugar nenhum.",
                color = Dim, fontSize = 13.sp,
            )
            Spacer(Modifier.height(14.dp))
            AppButton(
                onClick = onPick,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                containerColor = Palette.ink, contentColor = Palette.onInk,
            ) { Text("Ler arquivo do Spotify", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun Step(n: String, text: String) {
    Row(Modifier.padding(vertical = 5.dp)) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(Palette.ink.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Text(n, color = Palette.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, color = Palette.ink.copy(alpha = 0.88f), fontSize = 15.sp)
    }
}

// ---------------- Conteúdo ----------------

private fun androidx.compose.foundation.lazy.LazyListScope.historyContent(
    stats: HistoryStats,
    selected: String,
    accent: Color,
    onSelect: (String) -> Unit,
    onWrapped: (String) -> Unit,
    onClear: () -> Unit,
) {
    val p = stats.periods[selected] ?: stats.all!!
    val isAll = p.key == "all"

    item {
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("all") + stats.years) { key ->
                AppChip(if (key == "all") "Tudo" else key, selected = key == p.key, onClick = { onSelect(key) })
            }
        }
    }

    item { Hero(p, accent) }

    item {
        Section("Retrospectiva")
        Panel(Modifier.padding(horizontal = 20.dp)) {
            Text(Retrospective.forPeriod(stats, p.key), color = Palette.ink.copy(alpha = 0.92f), fontSize = 15.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(14.dp))
            AppButton(
                onClick = { onWrapped(p.key) },
                modifier = Modifier.fillMaxWidth(),
                containerColor = accent, contentColor = Color.Black,
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isAll) "Ver retrospectiva animada" else "Ver retrospectiva de ${p.key}", fontWeight = FontWeight.Bold)
            }
        }
    }

    item {
        Section(if (isAll) "Horas por ano" else "Horas por mês")
        Panel(Modifier.padding(horizontal = 20.dp)) {
            val bars = p.monthMs
            BarChart(
                values = bars.map { it.ms.toFloat() },
                labels = bars.map { if (isAll) "'" + it.name.takeLast(2) else monthLabel(it.name).take(3) },
                accent = accent,
            )
            bars.maxByOrNull { it.ms }?.let {
                Text(
                    "Pico: ${if (isAll) it.name else monthLabel(it.name)}, ${fmtHours(it.ms)}",
                    color = Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

    rankedSection("Artistas mais ouvidos", p.topArtists, accent) { "${fmtHours(it.ms)} · ${fmtInt(it.count)} plays" }
    rankedSection("Músicas mais ouvidas", p.topTracks, accent) { "${it.sub} · ${fmtInt(it.count)}x" }
    rankedSection("Álbuns mais ouvidos", p.topAlbums, accent, initial = 5) { "${it.sub} · ${fmtHours(it.ms)}" }

    item {
        Section("Quando você ouve")
        Panel(Modifier.padding(horizontal = 20.dp)) {
            Text("Por hora do dia", color = Dim, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            BarChart(p.hourMs.map { it.toFloat() }, (0..23).map { if (it % 3 == 0) "${it}h" else "" }, accent, height = 90)
            Spacer(Modifier.height(18.dp))
            Text("Por dia da semana", color = Dim, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            BarChart(p.weekdayMs.map { it.toFloat() }, listOf("seg", "ter", "qua", "qui", "sex", "sáb", "dom"), accent, height = 90)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                MiniStat(pct(p.shuffleStreams, p.streams), "no aleatório")
                MiniStat("${(p.skipRate * 100).roundToInt()}%", "puladas")
                MiniStat(pct(p.offlineStreams, p.streams), "offline")
            }
        }
    }

    item {
        Section("Recordes")
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            p.biggestDay?.let { Record("Dia que você mais ouviu", it.name, fmtHours(it.ms)) }
            if (p.longestStreakDays > 1) Record("Maior sequência de dias seguidos", "${p.longestStreakDays} dias", "${p.streakFrom} a ${p.streakTo}")
            p.obsession?.let { Record("Obsessão de um dia só", "\"${it.name}\" ${it.count}x", it.sub) }
            Record("Dias com música", fmtInt(p.activeDays), "${fmtInt(p.distinctTracks)} músicas · ${fmtInt(p.distinctAlbums)} álbuns")
        }
    }

    item {
        Section(if (isAll) "Quando cada artista chegou" else "Descobertas de ${p.key}")
        Panel(Modifier.padding(horizontal = 20.dp)) {
            if (isAll) {
                Text("Primeira vez que você ouviu seus artistas principais", color = Dim, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                stats.artistTimeline.take(20).forEach { TimelineRow(it.sub, it.name, accent) }
            } else {
                Text("${fmtInt(p.newArtists)} artistas novos", color = Palette.ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                p.topNewArtists.forEach { TimelineRow(it.sub, "${it.name} · ${fmtHours(it.ms)}", accent) }
            }
        }
    }

    if (p.mostSkipped.isNotEmpty()) {
        rankedSection("As que você mais pula", p.mostSkipped, accent, numbered = false) { "${fmtInt(it.count)} vezes · ${it.sub}" }
    }
    if (p.alwaysFinished.isNotEmpty()) {
        rankedSection("Sempre ouve até o fim", p.alwaysFinished, accent, numbered = false) { "${it.sub} · ${fmtInt(it.count)}x sem pular" }
    }

    if (p.reasonsStart.isNotEmpty()) {
        item {
            Section("Como as músicas começam")
            Panel(Modifier.padding(horizontal = 20.dp)) {
                val total = p.reasonsStart.sumOf { it.count }.coerceAtLeast(1)
                p.reasonsStart.forEach { ShareBar(it.name, it.count.toFloat() / total, accent) }
            }
        }
    }

    item {
        Section("Onde você ouve")
        Panel(Modifier.padding(horizontal = 20.dp)) {
            p.platforms.take(6).forEach { ShareBar(it.name, it.ms.toFloat() / p.ms.coerceAtLeast(1), accent) }
            if (p.countries.size > 1) {
                Spacer(Modifier.height(12.dp))
                Text("Países: " + p.countries.joinToString { "${it.name} ${pctMs(it.ms, p.ms)}" }, color = Dim, fontSize = 13.sp)
            }
        }
    }

    if (p.podcastMs > 0) {
        rankedSection("Podcasts", p.topShows, accent, initial = 5) { "${fmtHours(it.ms)} · ${fmtInt(it.count)} episódios" }
    }

    item {
        Text(
            "${fmtInt(stats.totalEntries)} reproduções lidas de ${stats.files} arquivo(s).",
            color = Palette.ink.copy(alpha = 0.4f), fontSize = 12.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
        )
        TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.DeleteOutline, null, tint = Palette.ink.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Apagar histórico do celular", color = Palette.ink.copy(alpha = 0.5f))
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.rankedSection(
    title: String,
    list: List<Ranked>,
    accent: Color,
    initial: Int = 10,
    numbered: Boolean = true,
    detail: (Ranked) -> String,
) {
    if (list.isEmpty()) return
    item(key = "sec-$title") {
        var expanded by rememberSaveable(title) { mutableStateOf(false) }
        Section(title)
        Panel(Modifier.padding(horizontal = 20.dp)) {
            val maxValue = list.maxOf { maxOf(it.ms, it.count.toLong()) }.coerceAtLeast(1)
            (if (expanded) list else list.take(initial)).forEachIndexed { i, r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (numbered) {
                        Text("${i + 1}", color = if (i < 3) accent else Dim, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(r.name, color = Palette.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(detail(r), color = Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        val v = maxOf(r.ms, r.count.toLong()).toFloat() / maxValue
                        Box(Modifier.fillMaxWidth(v.coerceIn(0.02f, 1f)).height(3.dp).clip(CircleShape).background(accent.copy(alpha = 0.7f)))
                    }
                }
            }
            if (list.size > initial) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Mostrar menos" else "Ver todos (${list.size})", color = Palette.ink)
                }
            }
        }
    }
}

@Composable
private fun Hero(p: PeriodStats, accent: Color) {
    Column(
        Modifier.padding(horizontal = 20.dp, vertical = 14.dp).fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)).background(accent.copy(alpha = 0.24f)).padding(20.dp)
    ) {
        Text(if (p.key == "all") "Tempo total ouvindo música" else "Em ${p.key} você ouviu", color = Dim, fontSize = 13.sp)
        Text(fmtHours(p.ms), color = Palette.ink, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
        val minutes = p.ms / 60_000
        Text("${fmtInt(minutes.toInt())} minutos", color = Dim, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            MiniStat(fmtInt(p.plays), "reproduções")
            MiniStat(fmtInt(p.distinctArtists), "artistas")
            MiniStat(fmtInt(p.distinctTracks), "músicas")
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, color = Palette.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 10.dp))
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card).padding(16.dp), content = content)
}

@Composable
private fun MiniStat(value: String, label: String) {
    Column {
        Text(value, color = Palette.ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = Dim, fontSize = 12.sp)
    }
}

@Composable
private fun Record(title: String, value: String, detail: String) {
    Panel {
        Text(title, color = Dim, fontSize = 12.sp)
        Text(value, color = Palette.ink, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(detail, color = Dim, fontSize = 12.sp)
    }
}

@Composable
private fun TimelineRow(date: String, text: String, accent: Color) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(10.dp))
        Text(date, color = Dim, fontSize = 12.sp, modifier = Modifier.width(78.dp))
        Text(text, color = Palette.ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ShareBar(label: String, share: Float, accent: Color) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Palette.ink, fontSize = 14.sp)
            Text("${(share * 100).roundToInt()}%", color = Dim, fontSize = 13.sp)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Palette.ink.copy(alpha = 0.1f))) {
            Box(Modifier.fillMaxWidth(share.coerceIn(0.01f, 1f)).height(6.dp).clip(CircleShape).background(accent))
        }
    }
}

@Composable
private fun BarChart(values: List<Float>, labels: List<String>, accent: Color, height: Int = 130) {
    if (values.isEmpty()) return
    val max = values.max().coerceAtLeast(1f)
    val peak = values.indexOf(values.max())
    Canvas(Modifier.fillMaxWidth().height(height.dp)) {
        val gap = size.width / values.size
        val w = (gap * 0.62f).coerceAtMost(28.dp.toPx())
        values.forEachIndexed { i, v ->
            val h = (v / max) * size.height
            drawRoundRect(
                color = if (i == peak) accent else accent.copy(alpha = 0.45f),
                topLeft = Offset(i * gap + (gap - w) / 2, size.height - h),
                size = Size(w, h.coerceAtLeast(2f)),
                cornerRadius = CornerRadius(w / 3),
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        labels.forEach { Text(it, color = Dim, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f)) }
    }
}

private fun pct(a: Int, b: Int) = if (b == 0) "0%" else "${(100.0 * a / b).roundToInt()}%"
private fun pctMs(a: Long, b: Long) = if (b == 0L) "0%" else "${(100.0 * a / b).roundToInt()}%"
