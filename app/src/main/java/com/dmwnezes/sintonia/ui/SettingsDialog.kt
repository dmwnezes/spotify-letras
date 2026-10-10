package com.dmwnezes.sintonia.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.dmwnezes.sintonia.UiState
import com.dmwnezes.sintonia.live.LiveLyricsService
import com.dmwnezes.sintonia.update.Updater
import com.dmwnezes.sintonia.viz.VizTheme

@Composable
fun SettingsDialog(
    state: UiState,
    onLiveLyrics: (Boolean) -> Unit,
    onTranslation: (Boolean) -> Unit,
    onTheme: (VizTheme) -> Unit,
    onCheckUpdates: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit = {},
    onLyricsStyle: (LyricsStyle) -> Unit = {},
    onLyricsMode: (com.dmwnezes.sintonia.edit.LyricsMode) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Mesmo sem a permissão o widget funciona; a notificação só não aparece.
        onLiveLyrics(true)
        LiveLyricsService.start(context)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajustes") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Aparência", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ThemeMode.entries.forEach { m -> AppChip(m.label, selected = m == state.themeMode, onClick = { onThemeMode(m) }) }
                }
                Spacer(Modifier.height(10.dp))
                Text("Estilo da letra", fontSize = 13.sp, color = Palette.ink.copy(alpha = 0.7f))
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.dmwnezes.sintonia.edit.LyricsMode.entries.forEach { m ->
                        AppChip(m.label, selected = m == state.lyricsMode, onClick = { onLyricsMode(m) })
                    }
                }
                Text(state.lyricsMode.hint, fontSize = 12.sp, color = Palette.ink.copy(alpha = 0.55f))
                Text("Fonte da letra (estilo Clássico)", fontSize = 13.sp, color = Palette.ink.copy(alpha = 0.7f), modifier = Modifier.padding(top = 6.dp))
                val st = state.lyricsStyle
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LyricsFont.entries.take(2).forEach { f ->
                        FilterChip(selected = f == st.font, onClick = { onLyricsStyle(st.copy(font = f)) }, label = { Text(f.label, fontFamily = f.family) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LyricsFont.entries.drop(2).forEach { f ->
                        FilterChip(selected = f == st.font, onClick = { onLyricsStyle(st.copy(font = f)) }, label = { Text(f.label, fontFamily = f.family) })
                    }
                }
                Text("Tamanho da letra", fontSize = 13.sp, color = Palette.ink.copy(alpha = 0.7f), modifier = Modifier.padding(top = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("P" to 0.85f, "M" to 1f, "G" to 1.15f, "GG" to 1.3f).forEach { (label, k) ->
                        AppChip(label, selected = st.scale == k, onClick = { onLyricsStyle(st.copy(scale = k)) })
                    }
                }
                Text(
                    "Assim fica a letra",
                    fontFamily = st.family, fontWeight = FontWeight.ExtraBold, fontSize = (24 * st.k).sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                HorizontalDivider(Modifier.padding(vertical = 14.dp))
                SettingSwitch(
                    title = "Letra na tela de bloqueio e no widget",
                    desc = "Mostra a linha que está tocando numa notificação (visível com o celular bloqueado) e no widget. " +
                        "Desliga sozinho depois de 15 minutos sem música.",
                    checked = state.liveLyrics,
                ) { on ->
                    if (on) {
                        val needs = Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        if (needs) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else { onLiveLyrics(true); LiveLyricsService.start(context) }
                    } else {
                        onLiveLyrics(false)
                        LiveLyricsService.stop(context)
                    }
                }
                Text(
                    "Para colocar o widget: segure o dedo na tela inicial → Widgets → Sintonia.",
                    fontSize = 12.sp, color = Palette.ink.copy(alpha = 0.5f), modifier = Modifier.padding(top = 4.dp),
                )
                HorizontalDivider(Modifier.padding(vertical = 14.dp))
                SettingSwitch(
                    title = "Tradução das letras",
                    desc = "Quando a letra está em outro idioma, mostra a tradução em português abaixo de cada linha. " +
                        "O tradutor de cada idioma é baixado uma vez (~30 MB).",
                    checked = state.showTranslation,
                    onChange = onTranslation,
                )
                HorizontalDivider(Modifier.padding(vertical = 14.dp))
                Text("Tema do visualizer", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VizTheme.entries.take(2).forEach { t -> AppChip(t.label, selected = t == state.vizTheme, onClick = { onTheme(t) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VizTheme.entries.drop(2).forEach { t -> AppChip(t.label, selected = t == state.vizTheme, onClick = { onTheme(t) }) }
                }
                HorizontalDivider(Modifier.padding(vertical = 14.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Atualizações", fontWeight = FontWeight.SemiBold)
                        Text("Versão instalada: ${Updater.currentName}", fontSize = 12.sp, color = Palette.ink.copy(alpha = 0.6f))
                    }
                    AppOutlinedButton(onClick = onCheckUpdates, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)) { Text("Buscar") }
                }
                HorizontalDivider(Modifier.padding(vertical = 14.dp))
                CreatorCredit()
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}

@Composable
private fun SettingSwitch(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(desc, fontSize = 12.sp, color = Palette.ink.copy(alpha = 0.6f), lineHeight = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
