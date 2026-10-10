package com.dmwnezes.sintonia.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/* Peças visuais repetidas nas telas: cartões, blocos de número, controle segmentado, barra de tempo e a barra de abas. */

/** Cartão de vidro: fundo translúcido com borda clarinha em cima. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    tint: Color = Palette.ink,
    corner: Dp = 24.dp,
    padding: Dp = 18.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.10f), tint.copy(alpha = 0.05f))))
            .border(1.dp, Brush.verticalGradient(listOf(tint.copy(alpha = 0.16f), tint.copy(alpha = 0.03f))), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding)
            .animateContentSize(),
        content = content,
    )
}

/** Título de seção com ação opcional à direita ("Ver tudo"). */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 26.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Palette.ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action, color = Palette.ink.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onAction).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/** Número grande que "conta" até o valor quando aparece. */
@Composable
fun CountUpText(target: Long, format: (Long) -> String, fontSize: TextUnit, color: Color = Palette.ink, modifier: Modifier = Modifier) {
    val anim = remember(target) { Animatable(0f) }
    LaunchedEffect(target) { anim.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    Text(format((target * anim.value).toLong()), color = color, fontSize = fontSize, fontWeight = FontWeight.Black, modifier = modifier, maxLines = 1)
}

/** Bloco com um número e o que ele significa, com ícone opcional. */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, icon: ImageVector? = null, accent: Color = Palette.ink) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.ink.copy(alpha = 0.07f))
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        if (icon != null) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(accent.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.height(10.dp))
        }
        Text(value, color = Palette.ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, color = Palette.ink.copy(alpha = 0.6f), fontSize = 12.sp, lineHeight = 15.sp, maxLines = 2)
    }
}

/** Controle segmentado: uma pílula com a opção escolhida deslizando por baixo. */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, tint: Color = Palette.ink, onTint: Color = Palette.onInk) {
    BoxWithConstraints(
        modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(50)).background(tint.copy(alpha = 0.08f))
            .border(1.dp, tint.copy(alpha = 0.10f), RoundedCornerShape(50)).padding(4.dp)
    ) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * selected, spring(dampingRatio = 0.8f, stiffness = 500f), label = "seg")
        Box(
            Modifier.offset(x = x).width(w).fillMaxHeight().shadow(6.dp, RoundedCornerShape(50))
                .clip(RoundedCornerShape(50)).background(Brush.verticalGradient(listOf(tint, tint.copy(alpha = 0.88f))))
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                val c by animateColorAsState(if (i == selected) onTint else tint.copy(alpha = 0.75f), label = "segc")
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(50))
                        .clickable(remember { MutableInteractionSource() }, null) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = c, fontSize = 14.sp, fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/**
 * Barra de tempo que dá para arrastar ou tocar para pular. Enquanto arrasta, mostra o tempo escolhido;
 * ao soltar, chama [onSeek].
 */
@Composable
fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit, color: Color = Palette.ink, modifier: Modifier = Modifier) {
    var dragging by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableFloatStateOf(0f) }
    val seek by rememberUpdatedState(onSeek)
    val dur by rememberUpdatedState(durationMs.coerceAtLeast(1))
    val frac = if (dragging) dragFrac else (positionMs.toFloat() / dur).coerceIn(0f, 1f)
    val thick by animateDpAsState(if (dragging) 8.dp else 4.dp, label = "barra")
    val thumb by animateFloatAsState(if (dragging) 1f else 0.0f, label = "bolinha")
    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(26.dp)
                .pointerInput(Unit) {
                    detectTapGestures { o -> seek((o.x / size.width * dur).toLong()) }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> dragging = true; dragFrac = (o.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = { dragging = false; seek((dragFrac * dur).toLong()) },
                        onDragCancel = { dragging = false },
                    ) { change, _ -> change.consume(); dragFrac = (change.position.x / size.width).coerceIn(0f, 1f) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val full = maxWidth
            Box(Modifier.fillMaxWidth().height(thick).clip(CircleShape).background(color.copy(alpha = 0.22f)))
            Box(Modifier.width(full * frac).height(thick).clip(CircleShape).background(color))
            val d = LocalDensity.current
            Box(
                Modifier.offset(x = full * frac - 8.dp).size(16.dp)
                    .graphicsLayer { scaleX = 0.5f + 0.5f * thumb; scaleY = 0.5f + 0.5f * thumb; alpha = 0.35f + 0.65f * thumb }
                    .shadow(4.dp, CircleShape).clip(CircleShape).background(color)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val shown = if (dragging) (dragFrac * dur).toLong() else positionMs
            Text(fmtTime(shown), color = color.copy(alpha = if (dragging) 1f else 0.6f), fontSize = 12.sp, fontWeight = if (dragging) FontWeight.Bold else FontWeight.Normal)
            Text("-" + fmtTime(dur - shown), color = color.copy(alpha = 0.6f), fontSize = 12.sp)
        }
    }
}

fun fmtTime(ms: Long): String {
    val s = ms.coerceAtLeast(0) / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

data class NavItem(val label: String, val icon: ImageVector, val id: Int)

/**
 * Barra de abas flutuante: pílula escura com a aba escolhida destacada (ícone + nome);
 * as outras mostram só o ícone. Um degradê embaixo esconde o que rola por trás.
 */
@Composable
fun FloatingNavBar(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Palette.surface.copy(alpha = 0.92f), Palette.surface)))
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().height(64.dp)
                .shadow(18.dp, RoundedCornerShape(32.dp))
                .clip(RoundedCornerShape(32.dp))
                .background(Brush.verticalGradient(listOf(lerpTo(Palette.surface, Palette.ink, 0.10f), lerpTo(Palette.surface, Palette.ink, 0.04f))))
                .border(1.dp, Brush.verticalGradient(listOf(Palette.ink.copy(alpha = 0.18f), Palette.ink.copy(alpha = 0.04f))), RoundedCornerShape(32.dp))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val sel = item.id == selected
                val weight by animateFloatAsState(if (sel) 2.1f else 1f, spring(dampingRatio = 0.75f, stiffness = 420f), label = "aba")
                Box(Modifier.weight(weight).fillMaxHeight().padding(vertical = 8.dp, horizontal = 2.dp), contentAlignment = Alignment.Center) {
                    Row(
                        Modifier.fillMaxSize().clip(RoundedCornerShape(50))
                            .background(if (sel) Brush.verticalGradient(listOf(Palette.ink, Palette.ink.copy(alpha = 0.86f))) else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)))
                            .clickable { onSelect(item.id) },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(item.icon, item.label, tint = if (sel) Palette.onInk else Palette.ink.copy(alpha = 0.6f), modifier = Modifier.size(22.dp))
                        if (sel) {
                            Spacer(Modifier.width(6.dp))
                            Text(item.label, color = Palette.onInk, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

private fun lerpTo(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)

/** Linha de pílula horizontal com ícone, usada como "ação rápida". */
@Composable
fun ActionTile(icon: ImageVector, label: String, modifier: Modifier = Modifier, active: Boolean = false, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (active) Palette.ink else Palette.ink.copy(alpha = 0.08f))
            .border(1.dp, Palette.ink.copy(alpha = if (active) 0f else 0.10f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = if (active) Palette.onInk else Palette.ink, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, color = if (active) Palette.onInk else Palette.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, lineHeight = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Suppress("unused") private fun Float.pct() = "${(this * 100).roundToInt()}%"
