package com.dmwnezes.sintonia.wrapped

import com.dmwnezes.sintonia.ui.AppButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dmwnezes.sintonia.history.HistoryStats
import kotlinx.coroutines.delay
import java.util.Locale

private const val SLIDE_MS = 6500

/** Pares de cores vivas, uma por tela. */
private val GRADIENTS = listOf(
    Color(0xFFFF5C8A) to Color(0xFF4A1E9E),
    Color(0xFFFFB347) to Color(0xFFD7263D),
    Color(0xFF1EC8B5) to Color(0xFF1B3A8C),
    Color(0xFFB5F44A) to Color(0xFF14804A),
    Color(0xFF9B5CFF) to Color(0xFF16133A),
    Color(0xFFFF7A59) to Color(0xFF6A1B4D),
    Color(0xFF4AC6FF) to Color(0xFF3B1F7A),
    Color(0xFFFFD84A) to Color(0xFFB2361F),
)

/** Retrospectiva em telas animadas, estilo Stories: toque à direita avança, à esquerda volta, segurar pausa. */
@Composable
fun WrappedDialog(stats: HistoryStats, key: String, onDismiss: () -> Unit) {
    val slides = remember(stats, key) { WrappedBuilder.build(stats, key) }
    if (slides.isEmpty()) { LaunchedEffect(Unit) { onDismiss() }; return }
    var index by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }

    // Avança em passos curtos para poder pausar enquanto o dedo está na tela.
    LaunchedEffect(index) {
        progress.snapTo(0f)
        while (progress.value < 1f) {
            if (paused) { delay(50); continue }
            val step = 0.02f
            progress.animateTo((progress.value + step).coerceAtMost(1f), tween((step * SLIDE_MS).toInt(), easing = LinearEasing))
        }
        if (index < slides.lastIndex) index++
    }

    val (c1, c2) = GRADIENTS[index % GRADIENTS.size]
    val top by animateColorAsState(c1, tween(600), label = "c1")
    val bottom by animateColorAsState(c2, tween(600), label = "c2")

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BoxWithConstraints(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(top, bottom)))
                .pointerInput(slides.size) {
                    detectTapGestures(
                        onPress = {
                            paused = true
                            tryAwaitRelease()
                            paused = false
                        },
                        onTap = { pos ->
                            if (pos.x < size.width * 0.3f) {
                                if (index > 0) index--
                            } else if (index < slides.lastIndex) index++
                        },
                    )
                }
        ) {
            FloatingBlobs(top, bottom)
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                // barras de progresso
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    slides.indices.forEach { i ->
                        val f = when { i < index -> 1f; i == index -> progress.value; else -> 0f }
                        Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f))) {
                            Box(Modifier.fillMaxWidth(f).height(3.dp).background(Color.White))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Sintonia", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp).weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Fechar", tint = Color.White) }
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp), contentAlignment = Alignment.CenterStart) {
                    key(index) { SlideView(slides[index]) }
                }
            }
        }
    }
}

@Composable
private fun FloatingBlobs(a: Color, b: Color) {
    val t = rememberInfiniteTransition(label = "blobs")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "p")
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), Offset(size.width * (0.2f + 0.6f * p), size.height * 0.2f), size.width * 0.7f),
            size.width * 0.7f, Offset(size.width * (0.2f + 0.6f * p), size.height * 0.2f))
        drawCircle(Brush.radialGradient(listOf(a.copy(alpha = 0.5f), Color.Transparent), Offset(size.width * (0.9f - 0.5f * p), size.height * 0.85f), size.width * 0.8f),
            size.width * 0.8f, Offset(size.width * (0.9f - 0.5f * p), size.height * 0.85f))
    }
}

/** Entra com atraso e deslizando para cima. */
@Composable
private fun Reveal(delayMs: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(delayMs.toLong()); visible = true }
    AnimatedVisibility(visible, enter = fadeIn(tween(500)) + slideInVertically(tween(600, easing = FastOutSlowInEasing)) { it / 3 }) { content() }
}

@Composable
private fun SlideView(s: Slide) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        when (s) {
            is Slide.Intro -> {
                Reveal(100) { Text(s.title, color = Color.White, fontSize = 52.sp, lineHeight = 56.sp, fontWeight = FontWeight.Black) }
                Spacer(Modifier.height(16.dp))
                Reveal(700) { Text(s.subtitle, color = Color.White.copy(alpha = 0.8f), fontSize = 18.sp) }
            }
            is Slide.BigNumber -> {
                Reveal(100) { Kicker(s.kicker) }
                val count = remember { Animatable(0f) }
                LaunchedEffect(Unit) { delay(400); count.animateTo(1f, tween(1800, easing = FastOutSlowInEasing)) }
                Text(
                    String.format(Locale("pt", "BR"), "%,d", (s.value * count.value).toLong()),
                    color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Black,
                )
                Text(s.unit, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                Reveal(2200) { Text(s.footnote, color = Color.White.copy(alpha = 0.85f), fontSize = 18.sp) }
            }
            is Slide.Spotlight -> {
                Reveal(100) { Kicker(s.kicker) }
                Reveal(600) { Text(s.name, color = Color.White, fontSize = 46.sp, lineHeight = 50.sp, fontWeight = FontWeight.Black) }
                Spacer(Modifier.height(12.dp))
                Reveal(1200) { Text(s.detail, color = Color.White.copy(alpha = 0.9f), fontSize = 19.sp) }
                s.extra?.let { Spacer(Modifier.height(6.dp)); Reveal(1700) { Text(it, color = Color.White.copy(alpha = 0.75f), fontSize = 16.sp) } }
            }
            is Slide.TopList -> {
                Reveal(100) { Kicker(s.kicker) }
                Spacer(Modifier.height(12.dp))
                s.items.forEachIndexed { i, (name, detail) ->
                    Reveal(400 + i * 380) {
                        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", color = Color.White.copy(alpha = 0.6f), fontSize = 30.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(44.dp))
                            Column {
                                Text(name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(detail, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            is Slide.Habits -> {
                Reveal(100) { Kicker(s.kicker) }
                Reveal(600) { Text(s.persona, color = Color.White, fontSize = 42.sp, lineHeight = 46.sp, fontWeight = FontWeight.Black) }
                Spacer(Modifier.height(20.dp))
                Reveal(1100) { HourBars(s.hours) }
                Spacer(Modifier.height(14.dp))
                Reveal(1600) { Text(s.detail, color = Color.White.copy(alpha = 0.85f), fontSize = 17.sp) }
            }
            is Slide.Summary -> {
                Reveal(100) { Kicker("Resumo ${s.period}") }
                Reveal(400) {
                    Text(String.format(Locale("pt", "BR"), "%,d minutos", s.minutes), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(16.dp))
                Reveal(800) {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text("Artistas", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                            s.topArtists.forEachIndexed { i, n -> Text("${i + 1}  $n", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Músicas", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                            s.topTracks.forEachIndexed { i, n -> Text("${i + 1}  $n", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
                Reveal(1300) {
                    AppButton(
                        onClick = { WrappedCard.share(context, s) },
                        containerColor = Color.White, contentColor = Color.Black,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(Icons.Rounded.IosShare, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Compartilhar nos Stories", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun Kicker(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.85f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun HourBars(hours: List<Long>) {
    val max = (hours.maxOrNull() ?: 1L).coerceAtLeast(1L).toFloat()
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(1200, easing = FastOutSlowInEasing)) }
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        val gap = size.width / 24
        val w = gap * 0.6f
        hours.forEachIndexed { i, v ->
            val h = (v / max) * size.height * grow.value
            drawRoundRect(Color.White.copy(alpha = if (v.toFloat() == max) 1f else 0.5f), Offset(i * gap, size.height - h), Size(w, h.coerceAtLeast(3f)), CornerRadius(w / 2))
        }
    }
    Row(Modifier.fillMaxWidth()) {
        listOf("0h", "6h", "12h", "18h", "23h").forEach { Text(it, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.weight(1f)) }
    }
}
