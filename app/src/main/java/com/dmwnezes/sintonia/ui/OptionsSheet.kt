package com.dmwnezes.sintonia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.sintonia.R
import com.dmwnezes.sintonia.edit.LyricsMode
import com.dmwnezes.sintonia.viz.VizTheme

private val InterFamily by lazy { FontFamily(Font(R.font.inter, FontWeight.Bold)) }
private val ArchivoFamily by lazy { FontFamily(Font(R.font.archivo, FontWeight.Black)) }

/** Mini prévia de cada estilo: cores e fonte que lembram o estilo. */
private data class StylePreview(val bg: Brush, val ink: Color, val family: FontFamily, val text: String, val italic: Boolean = false, val glow: Color? = null)

private fun previewFor(m: LyricsMode, accent: Color): StylePreview = when (m) {
    LyricsMode.EDIT -> StylePreview(Brush.verticalGradient(listOf(Color(0xFF0A0808), Color(0xFF060505))), Color(0xFFF2E2CA), Montserrat, "Aa", glow = Color(0xFFFFCC91))
    LyricsMode.VIDRO -> StylePreview(Brush.linearGradient(listOf(Color(0xFF6A46DC), Color(0xFFC43C6E), Color(0xFFDC5A46))), Color.White, InterFamily, "Aa", italic = true)
    LyricsMode.CILINDRO -> StylePreview(Brush.radialGradient(listOf(Color(0xFF4A3F38), Color(0xFF141414))), Color.White, Montserrat, "Aa")
    LyricsMode.COLAGEM -> StylePreview(Brush.linearGradient(listOf(Color(0xFF9C2A22), Color(0xFF3A100D))), Color(0xFFF3E9DA), ArchivoFamily, "AA")
    LyricsMode.CLASSICO -> StylePreview(Brush.linearGradient(listOf(accent.copy(alpha = 0.8f), Color(0xFF1C1430))), Color.White, FontFamily.Default, "Aa")
}

/**
 * Painel de opções da letra (no lugar do menu ⋮): estilos em cartões com prévia,
 * ações rápidas em blocos e, só no Clássico, as opções do visualizer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsOptionsSheet(
    mode: LyricsMode,
    accent: Color,
    karaoke: Boolean,
    realViz: Boolean,
    vizTheme: VizTheme,
    playerOnly: Boolean,
    onMode: (LyricsMode) -> Unit,
    onRecord: () -> Unit,
    onNote: () -> Unit,
    onTune: () -> Unit,
    onKaraoke: (Boolean) -> Unit,
    onRealViz: (Boolean) -> Unit,
    onVizTheme: (VizTheme) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF15121B),
        contentColor = Color.White,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(40.dp, 5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))) },
    ) {
        LyricsOptionsContent(mode, accent, karaoke, realViz, vizTheme, playerOnly, onMode, onRecord, onNote, onTune, onKaraoke, onRealViz, onVizTheme, onDismiss)
    }
}

/** Conteúdo do painel (separado para dar para ver em testes). */
@Composable
fun LyricsOptionsContent(
    mode: LyricsMode,
    accent: Color,
    karaoke: Boolean,
    realViz: Boolean,
    vizTheme: VizTheme,
    playerOnly: Boolean,
    onMode: (LyricsMode) -> Unit,
    onRecord: () -> Unit,
    onNote: () -> Unit,
    onTune: () -> Unit,
    onKaraoke: (Boolean) -> Unit,
    onRealViz: (Boolean) -> Unit,
    onVizTheme: (VizTheme) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(LocalAppPalette provides DarkPalette) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 18.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionTile(Icons.Rounded.Videocam, "Gravar\nStories", Modifier.weight(1f)) { onDismiss(); onRecord() }
                    ActionTile(Icons.Rounded.EditNote, "Anotar\nmomento", Modifier.weight(1f)) { onDismiss(); onNote() }
                    if (!playerOnly) ActionTile(Icons.Rounded.Timer, "Ajustar\nsincronia", Modifier.weight(1f)) { onDismiss(); onTune() }
                }

                if (!playerOnly) {
                    Text("Estilo da letra", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 10.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(LyricsMode.entries) { m -> StyleCard(m, m == mode, accent) { onMode(m) } }
                    }
                    Text(mode.hint, color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, top = 8.dp))
                }

                if (!playerOnly && mode == LyricsMode.CLASSICO) {
                    Spacer(Modifier.height(16.dp))
                    ToggleRow(Icons.Rounded.TextFields, "Acender palavra por palavra", karaoke, accent, onKaraoke)
                    ToggleRow(Icons.Rounded.GraphicEq, "Visualizer reage ao som", realViz, accent, onRealViz)
                    Text("Tema do visualizer", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(VizTheme.entries) { t -> AppChip(t.label, t == vizTheme, { onVizTheme(t) }) }
                    }
                }
            }
    }
}

@Composable
private fun StyleCard(m: LyricsMode, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val pv = previewFor(m, accent)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(86.dp)) {
        Box(
            Modifier.size(86.dp, 112.dp)
                .shadow(if (selected) 14.dp else 0.dp, RoundedCornerShape(20.dp), ambientColor = accent, spotColor = accent)
                .clip(RoundedCornerShape(20.dp))
                .background(pv.bg)
                .border(if (selected) 2.5.dp else 1.dp, if (selected) Color.White else Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                pv.text, color = pv.ink, fontFamily = pv.family, fontSize = 34.sp, fontWeight = FontWeight.Black,
                fontStyle = if (pv.italic) FontStyle.Italic else FontStyle.Normal,
                style = TextStyle(shadow = pv.glow?.let { androidx.compose.ui.graphics.Shadow(it, blurRadius = 24f) }),
            )
            if (selected) Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Check, null, tint = Color.Black, modifier = Modifier.size(15.dp)) }
        }
        Spacer(Modifier.height(6.dp))
        Text(m.label, color = if (selected) Color.White else Color.White.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun ToggleRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean, accent: Color, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = Color.Black))
    }
}
