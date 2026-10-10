package com.dmwnezes.sintonia.ui

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Repeat
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
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Histórico", color = Palette.ink, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    ui.stats?.all?.let { Text("${it.firstDay} a ${it.lastDay}", color = Dim, fontSize = 13.sp) }
                }
                GlassIconButton(Icons.Rounded.FileOpen, "Ler arquivo", openPicker, size = 42.dp, enabled = !ui.importing)
                Spacer(Modifier.width(8.dp))
                GlassIconButton(Icons.Rounded.IosShare, "Exportar", { showExport = true }, size = 42.dp, enabled = ui.stats != null && !ui.importing)
            }
            Spacer(Modifier.height(14.dp))
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

    item(key = "hero-${p.key}") { Hero(p, accent) }
    item { WrappedBanner(p.key, accent) { onWrapped(p.key) } }

    item {
        SectionHeader("Destaques")
        Highlights(stats, p, accent)
    }

    item(key = "tops-${p.key}") {
        SectionHeader("Seus mais ouvidos")
        TopTabs(p, accent)
    }

    item(key = "when-${p.key}") {
        SectionHeader("Quando você ouve")
        WhenCard(p, isAll, accent)
    }

    item(key = "more-${p.key}") { MoreStats(stats, p, isAll, accent) }

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

/** Número principal grande e três blocos logo abaixo. */
@Composable
private fun Hero(p: PeriodStats, accent: Color) {
    Column(
        Modifier.padding(horizontal = 20.dp, vertical = 14.dp).fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.18f))))
            .padding(22.dp)
    ) {
        Text(if (p.key == "all") "Você já ouviu" else "Em ${p.key} você ouviu", color = Palette.ink.copy(alpha = 0.75f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.Bottom) {
            CountUpText(p.ms / 3_600_000, { fmtInt(it.toInt()) }, 58.sp)
            Text(" horas", color = Palette.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
        }
        val days = p.ms / 86_400_000.0
        Text(
            if (days >= 1) "≈ ${"%.0f".format(days)} dias inteiros sem parar" else "${fmtInt((p.ms / 60_000).toInt())} minutos",
            color = Palette.ink.copy(alpha = 0.75f), fontSize = 14.sp,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(fmtCompact(p.plays), "plays", Modifier.weight(1f), Icons.Rounded.PlayCircle, Palette.ink)
            StatTile(fmtCompact(p.distinctArtists), "artistas", Modifier.weight(1f), Icons.Rounded.Mic, Palette.ink)
            StatTile(fmtCompact(p.distinctTracks), "músicas", Modifier.weight(1f), Icons.Rounded.MusicNote, Palette.ink)
        }
    }
}

/** Atalho grande para a retrospectiva animada. */
@Composable
private fun WrappedBanner(key: String, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(24.dp), ambientColor = accent, spotColor = accent)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.horizontalGradient(listOf(accent, lerp(accent, Color(0xFFFF5FA2), 0.45f))))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(if (key == "all") "Sua retrospectiva" else "Retrospectiva $key", color = Color.Black, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text("Assista em telas animadas, como Stories", color = Color.Black.copy(alpha = 0.7f), fontSize = 13.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Color.Black.copy(alpha = 0.7f))
    }
}

private data class Fact(val icon: ImageVector, val label: String, val value: String, val detail: String)

/** Os fatos mais legais do período em cartões que deslizam para o lado. */
@Composable
private fun Highlights(stats: HistoryStats, p: PeriodStats, accent: Color) {
    val facts = buildList {
        p.topArtists.firstOrNull()?.let { add(Fact(Icons.Rounded.Mic, if (p.key == "all") "Artista da sua vida" else "Artista do ano", it.name, "${fmtHours(it.ms)} ouvindo")) }
        p.topTracks.firstOrNull()?.let { add(Fact(Icons.Rounded.MusicNote, "Música que mais tocou", it.name, "${it.sub} · ${fmtInt(it.count)} vezes")) }
        if (p.key == "all") stats.years.mapNotNull { y -> stats.periods[y] }.maxByOrNull { it.ms }?.let {
            add(Fact(Icons.Rounded.CalendarMonth, "Ano em que mais ouviu", it.key, fmtHours(it.ms)))
        } else p.monthMs.maxByOrNull { it.ms }?.let { add(Fact(Icons.Rounded.CalendarMonth, "Mês mais intenso", monthLabel(it.name), fmtHours(it.ms))) }
        if (p.longestStreakDays > 1) add(Fact(Icons.Rounded.LocalFireDepartment, "Maior sequência", "${p.longestStreakDays} dias", "${p.streakFrom} a ${p.streakTo}"))
        p.biggestDay?.let { add(Fact(Icons.Rounded.EmojiEvents, "Dia recorde", fmtHours(it.ms), it.name)) }
        p.obsession?.takeIf { it.count >= 5 }?.let { add(Fact(Icons.Rounded.Repeat, "Obsessão de um dia", "${it.count}x", "${it.name} · ${it.sub.substringAfter(" · ")}")) }
        if (p.newArtists > 0) add(Fact(Icons.Rounded.AutoAwesome, "Artistas novos", fmtInt(p.newArtists), p.topNewArtists.firstOrNull()?.let { "o maior achado: ${it.name}" } ?: "descobertos no período"))
    }
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(facts) { f ->
            Column(
                Modifier.width(220.dp).height(160.dp).clip(RoundedCornerShape(24.dp))
                    .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.26f), Palette.ink.copy(alpha = 0.05f))))
                    .border(1.dp, Palette.ink.copy(alpha = 0.10f), RoundedCornerShape(24.dp))
                    .padding(16.dp),
            ) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                    Icon(f.icon, null, tint = Color.Black, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(f.label, color = Dim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(f.value, color = Palette.ink, fontSize = 22.sp, lineHeight = 25.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(f.detail, color = Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Artistas / músicas / álbuns num só cartão, com abas; 5 primeiros e "ver mais". */
@Composable
private fun TopTabs(p: PeriodStats, accent: Color) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var expanded by rememberSaveable(tab, p.key) { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 20.dp)) {
        SegmentedControl(listOf("Artistas", "Músicas", "Álbuns"), tab, { tab = it })
        Spacer(Modifier.height(12.dp))
        val list = when (tab) { 0 -> p.topArtists; 1 -> p.topTracks; else -> p.topAlbums }
        val detail: (Ranked) -> String = when (tab) {
            0 -> { r -> "${fmtHours(r.ms)} · ${fmtInt(r.count)} plays" }
            1 -> { r -> "${r.sub} · ${fmtInt(r.count)}x" }
            else -> { r -> "${r.sub} · ${fmtHours(r.ms)}" }
        }
        GlassCard(padding = 10.dp) {
            val max = list.maxOfOrNull { maxOf(it.ms, it.count.toLong()) }?.coerceAtLeast(1) ?: 1
            (if (expanded) list.take(30) else list.take(5)).forEachIndexed { i, r -> RankRow(i, r, detail(r), maxOf(r.ms, r.count.toLong()).toFloat() / max, accent) }
            if (list.size > 5) {
                Text(
                    if (expanded) "Mostrar menos" else "Ver mais",
                    color = Palette.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).clickable { expanded = !expanded }.padding(10.dp),
                )
            }
        }
    }
}

@Composable
private fun RankRow(i: Int, r: Ranked, detail: String, share: Float, accent: Color) {
    val top = i == 0
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (top) accent.copy(alpha = 0.18f) else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = if (top) 12.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(if (top) 36.dp else 28.dp).clip(CircleShape).background(if (i < 3) accent else Palette.ink.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) { Text("${i + 1}", color = if (i < 3) Color.Black else Palette.ink, fontSize = if (top) 16.sp else 13.sp, fontWeight = FontWeight.ExtraBold) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.name, color = Palette.ink, fontSize = if (top) 17.sp else 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Palette.ink.copy(alpha = 0.08f))) {
                Box(Modifier.fillMaxWidth(share.coerceIn(0.02f, 1f)).height(4.dp).clip(CircleShape).background(accent.copy(alpha = 0.85f)))
            }
        }
    }
}

/** Um gráfico por vez: por ano/mês, por hora, por dia da semana. */
@Composable
private fun WhenCard(p: PeriodStats, isAll: Boolean, accent: Color) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.padding(horizontal = 20.dp)) {
        SegmentedControl(listOf(if (isAll) "Anos" else "Meses", "Horas", "Dias"), tab, { tab = it })
        Spacer(Modifier.height(12.dp))
        GlassCard {
            when (tab) {
                0 -> {
                    val bars = p.monthMs
                    val peak = bars.maxByOrNull { it.ms }
                    peak?.let { Insight("Pico", if (isAll) it.name else monthLabel(it.name), fmtHours(it.ms)) }
                    BarChart(bars.map { it.ms.toFloat() }, bars.map { if (isAll) "'" + it.name.takeLast(2) else monthLabel(it.name).take(3) }, accent)
                }
                1 -> {
                    val h = p.hourMs.indices.maxByOrNull { p.hourMs[it] } ?: 0
                    Insight("Horário favorito", "por volta das ${h}h", partOfDay(h))
                    BarChart(p.hourMs.map { it.toFloat() }, (0..23).map { if (it % 3 == 0) "${it}h" else "" }, accent)
                }
                else -> {
                    val names = listOf("segunda", "terça", "quarta", "quinta", "sexta", "sábado", "domingo")
                    val d = p.weekdayMs.indices.maxByOrNull { p.weekdayMs[it] } ?: 0
                    Insight("Dia favorito", names[d], "")
                    BarChart(p.weekdayMs.map { it.toFloat() }, listOf("seg", "ter", "qua", "qui", "sex", "sáb", "dom"), accent)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(pct(p.shuffleStreams, p.streams), "no aleatório", Modifier.weight(1f))
                StatTile("${(p.skipRate * 100).roundToInt()}%", "puladas", Modifier.weight(1f))
                StatTile(pct(p.offlineStreams, p.streams), "offline", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Insight(label: String, value: String, detail: String) {
    Text(label, color = Dim, fontSize = 12.sp)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, color = Palette.ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        if (detail.isNotEmpty()) Text("  $detail", color = Dim, fontSize = 13.sp, modifier = Modifier.padding(bottom = 3.dp))
    }
    Spacer(Modifier.height(12.dp))
}

private fun partOfDay(h: Int) = when (h) { in 5..11 -> "de manhã"; in 12..17 -> "à tarde"; in 18..23 -> "à noite"; else -> "de madrugada" }

/** O resto (descobertas, puladas, onde ouve, podcasts) fica guardado atrás de um toque. */
@Composable
private fun MoreStats(stats: HistoryStats, p: PeriodStats, isAll: Boolean, accent: Color) {
    var open by rememberSaveable(p.key) { mutableStateOf(false) }
    val rot by animateFloatAsState(if (open) 180f else 0f, label = "seta")
    Column(Modifier.padding(horizontal = 20.dp).padding(top = 22.dp)) {
        GlassCard(onClick = { open = !open }, padding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mais curiosidades", color = Palette.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("Descobertas, as que você pula, onde e como ouve", color = Dim, fontSize = 12.sp)
                }
                Icon(Icons.Rounded.ExpandMore, null, tint = Palette.ink, modifier = Modifier.graphicsLayer { rotationZ = rot })
            }
            if (open) {
                Spacer(Modifier.height(10.dp))
                SubTitle(if (isAll) "Quando cada artista chegou" else "Descobertas de ${p.key}")
                if (isAll) stats.artistTimeline.take(10).forEach { TimelineRow(it.sub, it.name, accent) }
                else p.topNewArtists.take(8).forEach { TimelineRow(it.sub, it.name, accent) }
                if (p.mostSkipped.isNotEmpty()) {
                    SubTitle("As que você mais pula")
                    p.mostSkipped.take(5).forEach { SmallRow(it.name, "${fmtInt(it.count)}x") }
                }
                if (p.alwaysFinished.isNotEmpty()) {
                    SubTitle("Sempre ouve até o fim")
                    p.alwaysFinished.take(5).forEach { SmallRow(it.name, "${fmtInt(it.count)}x") }
                }
                if (p.platforms.isNotEmpty()) {
                    SubTitle("Onde você ouve")
                    p.platforms.take(4).forEach { ShareBar(it.name, it.ms.toFloat() / p.ms.coerceAtLeast(1), accent) }
                }
                if (p.reasonsStart.isNotEmpty()) {
                    SubTitle("Como as músicas começam")
                    val total = p.reasonsStart.sumOf { it.count }.coerceAtLeast(1)
                    p.reasonsStart.take(4).forEach { ShareBar(it.name, it.count.toFloat() / total, accent) }
                }
                if (p.podcastMs > 0) {
                    SubTitle("Podcasts · ${fmtHours(p.podcastMs)}")
                    p.topShows.take(3).forEach { SmallRow(it.name, fmtHours(it.ms)) }
                }
            }
        }
    }
}

@Composable
private fun SubTitle(t: String) {
    Text(t, color = Palette.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
}

@Composable
private fun SmallRow(name: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(name, color = Palette.ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(value, color = Dim, fontSize = 13.sp)
    }
}

private fun fmtCompact(n: Int): String = when {
    n >= 1_000_000 -> "%.1f mi".format(n / 1_000_000.0).replace('.', ',')
    n >= 10_000 -> "%.0f mil".format(n / 1_000.0)
    n >= 1_000 -> "%.1f mil".format(n / 1_000.0).replace('.', ',')
    else -> "$n"
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card).padding(16.dp), content = content)
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
