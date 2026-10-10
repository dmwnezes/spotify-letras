package com.dmwnezes.sintonia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dmwnezes.sintonia.ProfileState
import com.dmwnezes.sintonia.data.ProfileData
import com.dmwnezes.sintonia.data.RecentPlay
import com.dmwnezes.sintonia.data.TimeRange
import com.dmwnezes.sintonia.data.Track
import java.time.Instant
import java.time.ZoneId

@Composable
fun ProfileScreen(
    state: ProfileState,
    range: TimeRange,
    accent: Color,
    onRange: (TimeRange) -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenQuiz: () -> Unit,
    bottomPadding: PaddingValues,
    onPlayTrack: (Track) -> Unit = {},
) {
    LaunchedEffect(Unit) { onRange(range) }
    var showAllTracks by rememberSaveable { mutableStateOf(false) }
    var showAllRecent by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().background(Palette.scrim.copy(alpha = 0.35f)).statusBarsPadding(),
        contentPadding = PaddingValues(top = 12.dp, bottom = bottomPadding.calculateBottomPadding() + 24.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                val user = (state as? ProfileState.Ready)?.data?.user
                Box(
                    Modifier.size(52.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.4f)))).padding(2.5.dp)
                ) {
                    AsyncImage(user?.imageUrl, null, Modifier.fillMaxSize().clip(CircleShape).background(Palette.surface), contentScale = ContentScale.Crop)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Olá, ${user?.name ?: ""}".trimEnd(',', ' '), color = Palette.ink.copy(alpha = 0.65f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Seu perfil", color = Palette.ink, fontSize = 28.sp, fontWeight = FontWeight.Black)
                }
                GlassIconButton(Icons.Rounded.Settings, "Ajustes", onOpenSettings, size = 42.dp)
                Spacer(Modifier.width(8.dp))
                GlassIconButton(Icons.AutoMirrored.Rounded.Logout, "Sair", onLogout, size = 42.dp)
            }
        }
        item {
            SegmentedControl(
                TimeRange.entries.map { shortLabel(it) }, TimeRange.entries.indexOf(range), { onRange(TimeRange.entries[it]) },
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp),
            )
        }

        when (state) {
            ProfileState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Palette.ink) }
            }
            is ProfileState.Failed -> item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.message, color = Palette.ink.copy(alpha = 0.75f))
                    Spacer(Modifier.height(12.dp))
                    AppButton(onClick = onRefresh) { Text("Tentar de novo") }
                }
            }
            is ProfileState.Ready -> {
                val d = state.data
                item { TopArtistHero(d, range, accent) }
                item { QuickStats(d, accent) }
                item { QuizCard(accent, onOpenQuiz) }
                if (d.topArtists.size > 1) {
                    item { SectionHeader("Artistas no topo") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            itemsIndexed(d.topArtists.take(20)) { i, a ->
                                Column(Modifier.width(92.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box {
                                        AsyncImage(a.imageUrl, a.name, Modifier.size(92.dp).clip(CircleShape).background(Palette.ink.copy(alpha = 0.08f)), contentScale = ContentScale.Crop)
                                        Box(
                                            Modifier.align(Alignment.BottomStart).size(30.dp).clip(CircleShape)
                                                .background(if (i < 3) accent else Palette.surface).border(2.dp, Palette.surface, CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) { Text("${i + 1}", color = if (i < 3) Color.Black else Palette.ink, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold) }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(a.name, color = Palette.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
                val genres = topGenres(d)
                if (genres.isNotEmpty()) {
                    item { SectionHeader("Seus gêneros") }
                    item { GenreCloud(genres, accent) }
                }
                if (d.topTracks.isNotEmpty()) {
                    item { SectionHeader("Músicas no topo", action = if (d.topTracks.size > 5) (if (showAllTracks) "Menos" else "Ver todas") else null) { showAllTracks = !showAllTracks } }
                    item { HintLine("Toque para tocar no Spotify") }
                    itemsIndexed(d.topTracks.take(if (showAllTracks) 30 else 5)) { i, t -> TrackRow(t, leading = "${i + 1}", accent = accent, onClick = { onPlayTrack(t) }) }
                }
                if (d.recent.isNotEmpty()) {
                    item { SectionHeader("Tocadas agora há pouco", action = if (d.recent.size > 5) (if (showAllRecent) "Menos" else "Ver todas") else null) { showAllRecent = !showAllRecent } }
                    itemsIndexed(d.recent.take(if (showAllRecent) 30 else 5)) { _, r -> TrackRow(r.track, trailing = ago(r.playedAtMs), accent = accent, onClick = { onPlayTrack(r.track) }) }
                }
            }
        }
    }
}

private fun shortLabel(r: TimeRange) = when (r) {
    TimeRange.SHORT -> "4 semanas"
    TimeRange.MEDIUM -> "6 meses"
    TimeRange.LONG -> "Sempre"
}

@Composable
private fun HintLine(t: String) {
    Text(t, color = Palette.ink.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.padding(start = 20.dp, bottom = 4.dp))
}

/** O artista nº 1 em destaque, com a foto dele de fundo. */
@Composable
private fun TopArtistHero(d: ProfileData, range: TimeRange, accent: Color) {
    val a = d.topArtists.firstOrNull() ?: return
    val topTrack = d.topTracks.firstOrNull()
    val period = when (range) {
        TimeRange.SHORT -> "das últimas 4 semanas"
        TimeRange.MEDIUM -> "dos últimos 6 meses"
        TimeRange.LONG -> "de todos os tempos"
    }
    Box(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(250.dp)
            .shadow(20.dp, RoundedCornerShape(28.dp), ambientColor = accent, spotColor = accent)
            .clip(RoundedCornerShape(28.dp))
    ) {
        AsyncImage(a.imageUrl, a.name, Modifier.fillMaxSize().background(accent.copy(alpha = 0.3f)), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.88f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            Text(
                "Nº 1 $period".uppercase(), color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(accent).padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(a.name, color = Color.White, fontSize = 34.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (topTrack != null) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(topTrack.imageUrl, null, Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Música mais ouvida", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                        Text("${topTrack.name} · ${topTrack.artists.firstOrNull().orEmpty()}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickStats(d: ProfileData, accent: Color) {
    val distinct = d.recent.flatMap { it.track.artists.take(1) }.distinct().size
    val peak = peakHour(d.recent)
    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("$distinct", "artistas nas últimas ${d.recent.size} músicas", Modifier.weight(1f), Icons.Rounded.Groups, accent)
        StatTile(peak?.let { "${it}h" } ?: "–", "seu horário de ouvir música", Modifier.weight(1f), Icons.Rounded.Schedule, accent)
    }
}

@Composable
private fun QuizCard(accent: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(start = 20.dp, end = 20.dp, top = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.35f), Palette.ink.copy(alpha = 0.06f))))
            .border(1.dp, Palette.ink.copy(alpha = 0.10f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Quiz, null, tint = Color.Black)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Quiz da letra", color = Palette.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("Adivinhe a música pelo trecho", color = Palette.ink.copy(alpha = 0.65f), fontSize = 13.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Palette.ink.copy(alpha = 0.6f))
    }
}

/** Gêneros como pílulas: quanto mais você ouve, maior e mais forte. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun GenreCloud(genres: List<Pair<String, Float>>, accent: Color) {
    val max = genres.maxOf { it.second }
    androidx.compose.foundation.layout.FlowRow(
        Modifier.padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        genres.forEach { (name, share) ->
            val k = share / max
            Text(
                name.replaceFirstChar { it.uppercase() },
                color = if (k > 0.6f) Color.Black else Palette.ink,
                fontSize = (13 + 7 * k).sp, fontWeight = if (k > 0.6f) FontWeight.ExtraBold else FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(if (k > 0.6f) accent else accent.copy(alpha = 0.12f + 0.3f * k))
                    .padding(horizontal = (12 + 6 * k).dp, vertical = (7 + 3 * k).dp),
            )
        }
    }
}

@Composable
private fun TrackRow(t: Track, accent: Color, leading: String? = null, trailing: String? = null, onClick: () -> Unit = {}) {
    Row(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Text(leading, color = if (leading == "1") accent else Palette.ink.copy(alpha = 0.55f), fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
        }
        AsyncImage(t.imageUrl, null, Modifier.size(50.dp).clip(RoundedCornerShape(10.dp)).background(Palette.ink.copy(alpha = 0.08f)), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.name, color = Palette.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.artistLine, color = Palette.ink.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, color = Palette.ink.copy(alpha = 0.5f), fontSize = 12.sp)
        }
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Rounded.PlayArrow, "Tocar", tint = Palette.ink.copy(alpha = 0.5f), modifier = Modifier.size(22.dp))
    }
}

/** Gêneros mais frequentes entre os artistas do topo (o nº 1 pesa mais que o nº 30). */
private fun topGenres(d: ProfileData): List<Pair<String, Float>> {
    val score = mutableMapOf<String, Float>()
    d.topArtists.forEachIndexed { i, a ->
        val w = 1f / (1f + i * 0.15f)
        a.genres.forEach { g -> score[g] = (score[g] ?: 0f) + w }
    }
    val total = score.values.sum().takeIf { it > 0 } ?: return emptyList()
    return score.entries.sortedByDescending { it.value }.take(6).map { it.key to it.value / total }
}

private fun peakHour(recent: List<RecentPlay>): Int? {
    if (recent.size < 5) return null
    val zone = ZoneId.systemDefault()
    return recent.filter { it.playedAtMs > 0 }
        .groupingBy { Instant.ofEpochMilli(it.playedAtMs).atZone(zone).hour }
        .eachCount()
        .maxByOrNull { it.value }?.key
}

private fun ago(ms: Long): String {
    if (ms <= 0) return ""
    val min = (System.currentTimeMillis() - ms) / 60_000
    return when {
        min < 1 -> "agora"
        min < 60 -> "há $min min"
        min < 60 * 24 -> "há ${min / 60} h"
        else -> "há ${min / (60 * 24)} d"
    }
}
