package com.dmwnezes.sintonia.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Botões do Sintonia: pílulas com leve gradiente, brilho no topo, sombra colorida
 * e um "afundar" com mola ao tocar — no lugar dos botões chapados do Material.
 */

private val Pill = RoundedCornerShape(50)

/** Afunda um pouco enquanto está pressionado e volta com mola. */
@Composable
private fun Modifier.pressable(source: MutableInteractionSource, enabled: Boolean): Modifier {
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed && enabled) 0.94f else 1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow), label = "aperto")
    return this.graphicsLayer { scaleX = s; scaleY = s }
}

/** Botão principal: cheio, com gradiente e brilho. */
@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = Palette.ink,
    contentColor: Color = Palette.onInk,
    contentPadding: PaddingValues = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val light = containerColor.luminance() > 0.5f
    val top = lerp(containerColor, Color.White, if (light) 0.25f else 0.16f)
    val bottom = lerp(containerColor, Color.Black, if (light) 0.10f else 0.22f)
    Row(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .pressable(source, enabled)
            .alpha(if (enabled) 1f else 0.45f)
            .shadow(if (enabled) 14.dp else 0.dp, Pill, ambientColor = containerColor, spotColor = containerColor)
            .clip(Pill)
            .background(Brush.verticalGradient(listOf(top, containerColor, bottom)))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.0f))), Pill)
            .clickable(source, ripple(color = contentColor), enabled = enabled, onClick = onClick)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor, LocalTextStyle provides LocalTextStyle.current.copy(color = contentColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)) {
            content()
        }
    }
}

/** Botão secundário: vidro translúcido com borda luminosa. */
@Composable
fun AppOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Palette.ink,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .pressable(source, enabled)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(Pill)
            .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.13f), tint.copy(alpha = 0.06f))))
            .border(1.dp, Brush.verticalGradient(listOf(tint.copy(alpha = 0.34f), tint.copy(alpha = 0.08f))), Pill)
            .clickable(source, ripple(color = tint), enabled = enabled, onClick = onClick)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides tint, LocalTextStyle provides LocalTextStyle.current.copy(color = tint, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)) {
            content()
        }
    }
}

/** Ícone redondo de vidro (controles, ações). [selected] acende o fundo. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = Palette.ink,
    selected: Boolean = false,
    iconSize: Dp = size * 0.5f,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(size)
            .pressable(source, enabled)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(CircleShape)
            .background(
                if (selected) Brush.verticalGradient(listOf(lerp(tint, Color.White, 0.15f), tint))
                else Brush.verticalGradient(listOf(tint.copy(alpha = 0.14f), tint.copy(alpha = 0.05f)))
            )
            .border(1.dp, Brush.verticalGradient(listOf(tint.copy(alpha = if (selected) 0.0f else 0.30f), tint.copy(alpha = 0.04f))), CircleShape)
            .clickable(source, ripple(color = tint), enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = if (selected) Palette.onInk else tint, modifier = Modifier.size(iconSize))
    }
}

/** Ícone sem fundo, só com o aperto e um brilho redondo ao tocar (anterior / próxima). */
@Composable
fun BareIconButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit, size: Dp = 56.dp, iconSize: Dp = 34.dp, tint: Color = Palette.ink) {
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier.size(size).pressable(source, true).clip(CircleShape)
            .clickable(source, ripple(color = tint), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(iconSize)) }
}

/** Play/pausa grande: gradiente, sombra na cor da música e o ícone trocando com animação. */
@Composable
fun PlayButton(playing: Boolean, onClick: () -> Unit, glow: Color, modifier: Modifier = Modifier, size: Dp = 68.dp) {
    val source = remember { MutableInteractionSource() }
    val ink = Palette.ink
    Box(
        modifier
            .size(size)
            .pressable(source, true)
            .shadow(22.dp, CircleShape, ambientColor = glow, spotColor = glow)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(lerp(ink, glow, 0.10f), ink, lerp(ink, glow, 0.22f))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.6f), Color.Transparent)), CircleShape)
            .clickable(source, ripple(color = Palette.onInk), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(playing, transitionSpec = { (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.6f) + fadeOut()) }, label = "play") { p ->
            Icon(if (p) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (p) "Pausar" else "Tocar", tint = Palette.onInk, modifier = Modifier.size(size * 0.52f))
        }
    }
}

/** Pílula de escolha (abas, filtros, estilos). */
@Composable
fun AppChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Palette.ink, onTint: Color = Palette.onInk) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressable(source, true)
            .clip(Pill)
            .background(
                if (selected) Brush.verticalGradient(listOf(lerp(tint, Color.White, 0.12f), tint))
                else Brush.verticalGradient(listOf(tint.copy(alpha = 0.12f), tint.copy(alpha = 0.05f)))
            )
            .border(1.dp, Brush.verticalGradient(listOf(tint.copy(alpha = if (selected) 0f else 0.26f), tint.copy(alpha = 0.04f))), Pill)
            .clickable(source, ripple(color = tint), onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) onTint else tint.copy(alpha = 0.85f), fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
    }
}
