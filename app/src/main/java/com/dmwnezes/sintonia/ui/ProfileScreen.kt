package com.dmwnezes.sintonia.ui

import androidx.compose.foundation.background
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
) {
    LaunchedEffect(Unit) { onRange(range) }

    LazyColumn(
        Modifier.fillMaxSize().background(Palette.scrim.copy(alpha = 0.35f)).statusBarsPadding(),
        contentPadding = PaddingValues(
            top = 12.dp,
            bottom = bottomPadding.calculateBottomPadding() + 24.dp,
        ),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val user = (state as? ProfileState.Ready)?.data?.user
                if (user?.imageUrl != null) {
                    AsyncImage(user.imageUrl, null, Modifier.size(44.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Perfil Musical", color = Palette.ink, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    user?.name?.let { Text(it, color = Palette.ink.copy(alpha = 0.65f), fontSize = 14.sp) }
                }
                IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, "Atualizar", tint = Palette.ink) }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, "Ajustes", tint = Palette.ink) }
                IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Rounded.Logout, "Sair", tint = Palette.ink.copy(alpha = 0.7f)) }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeRange.entries.forEach { r ->
                    AppChip(r.label, selected = r == range, onClick = { onRange(r) })
                }
            }
        }

        when (state) {
            ProfileState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Palette.ink)
                }
            }
            is ProfileState.Failed -> item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.message, color = Palette.ink.copy(alpha = 0.75f))
                    TextButton(onClick = onRefresh) { Text("Tentar de novo", color = Palette.ink) }
                }
            }
            is ProfileState.Ready -> {
                val d = state.data
                item { Highlights(d, range, accent) }
                item { QuizCard(accent, onOpenQuiz) }
                if (d.topArtists.isNotEmpty()) {
                    item { SectionTitle("Artistas no topo") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            itemsIndexed(d.topArtists) { i, a ->
                                Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    AsyncImage(a.imageUrl, a.name, Modifier.size(96.dp).clip(CircleShape).background(Palette.ink.copy(alpha = 0.08f)), contentScale = ContentScale.Crop)
                                    Spacer(Modifier.height(6.dp))
                                    Text("${i + 1}. ${a.name}", color = Palette.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                val genres = topGenres(d)
                if (genres.isNotEmpty()) {
                    item { SectionTitle("Seus gêneros") }
                    item { GenreBars(genres, accent) }
                }
                if (d.topTracks.isNotEmpty()) {
                    item { SectionTitle("Músicas no topo") }
                    itemsIndexed(d.topTracks.take(20)) { i, t -> TrackRow(t, leading = "${i + 1}") }
                }
                if (d.recent.isNotEmpty()) {
                    item { SectionTitle("Tocadas recentemente") }
                    itemsIndexed(d.recent.take(25)) { _, r -> TrackRow(r.track, trailing = ago(r.playedAtMs)) }
                }
            }
        }
    }
}

@Composable
private fun QuizCard(accent: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(start = 20.dp, end = 20.dp, top = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Palette.ink.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Quiz, null, tint = Color.Black)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Quiz: adivinhe a música", color = Palette.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("Trechos das letras das músicas que você mais ouve", color = Palette.ink.copy(alpha = 0.65f), fontSize = 13.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Palette.ink.copy(alpha = 0.6f))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, color = Palette.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp),
    )
}

@Composable
private fun Highlights(d: ProfileData, range: TimeRange, accent: Color) {
    val topArtist = d.topArtists.firstOrNull()?.name
    val topTrack = d.topTracks.firstOrNull()
    val distinct = d.recent.flatMap { it.track.artists.take(1) }.distinct().size
    val peak = peakHour(d.recent)
    val period = when (range) {
        TimeRange.SHORT -> "nas últimas 4 semanas"
        TimeRange.MEDIUM -> "nos últimos 6 meses"
        TimeRange.LONG -> "desde sempre"
    }
    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(accent.copy(alpha = 0.22f))
            .padding(18.dp)
    ) {
        if (topArtist != null) {
            Text("Seu artista nº 1 $period", color = Palette.ink.copy(alpha = 0.7f), fontSize = 13.sp)
            Text(topArtist, color = Palette.ink, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        }
        if (topTrack != null) {
            Spacer(Modifier.height(10.dp))
            Text("Música mais ouvida", color = Palette.ink.copy(alpha = 0.7f), fontSize = 13.sp)
            Text("${topTrack.name} · ${topTrack.artists.firstOrNull().orEmpty()}", color = Palette.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Stat("$distinct", "artistas nas\núltimas ${d.recent.size} tocadas")
            if (peak != null) Stat("${peak}h", "horário em que\nvocê mais ouve")
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column {
        Text(value, color = Palette.ink, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = Palette.ink.copy(alpha = 0.65f), fontSize = 12.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun GenreBars(genres: List<Pair<String, Float>>, accent: Color) {
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        genres.forEach { (name, share) ->
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name.replaceFirstChar { it.uppercase() }, color = Palette.ink, fontSize = 14.sp)
                    Text("${(share * 100).toInt()}%", color = Palette.ink.copy(alpha = 0.6f), fontSize = 13.sp)
                }
                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Palette.ink.copy(alpha = 0.12f))) {
                    Box(Modifier.fillMaxWidth(share.coerceIn(0.02f, 1f)).height(6.dp).clip(CircleShape).background(accent))
                }
            }
        }
    }
}

@Composable
private fun TrackRow(t: Track, leading: String? = null, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) {
            Text(leading, color = Palette.ink.copy(alpha = 0.55f), fontSize = 14.sp, modifier = Modifier.width(28.dp))
        }
        AsyncImage(t.imageUrl, null, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Palette.ink.copy(alpha = 0.08f)), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.name, color = Palette.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.artistLine, color = Palette.ink.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, color = Palette.ink.copy(alpha = 0.5f), fontSize = 12.sp)
        }
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
