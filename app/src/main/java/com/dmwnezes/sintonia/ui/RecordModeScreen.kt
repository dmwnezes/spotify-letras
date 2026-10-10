package com.dmwnezes.sintonia.ui

import android.app.Activity
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.dmwnezes.sintonia.LyricsState
import com.dmwnezes.sintonia.UiState
import com.dmwnezes.sintonia.edit.EditLyricsView
import com.dmwnezes.sintonia.edit.EditRenderer
import com.dmwnezes.sintonia.lyrics.Lyrics
import kotlinx.coroutines.delay

private val Cream = Color(EditRenderer.CREAM)
private val Black = Color(EditRenderer.BG)

private enum class Phase { SETUP, COUNTDOWN, LIVE }

/**
 * Modo gravar para Stories: tela cheia, sem barras nem botões — só a capa, o nome da música,
 * a letra no estilo Edit e @dmwnezes. Feito para ligar o gravador de tela do celular com o áudio.
 */
@Composable
fun RecordModeScreen(
    state: UiState,
    onRestartSong: () -> Unit,
    onEnsurePlaying: () -> Unit,
    onExit: () -> Unit,
    startLive: Boolean = false, // só para pré-visualização/testes
) {
    var phase by remember { mutableStateOf(if (startLive) Phase.LIVE else Phase.SETUP) }
    var fromStart by remember { mutableStateOf(false) }
    var showExit by remember { mutableStateOf(false) }

    // Esconde barra de status e de navegação e mantém a tela acesa enquanto grava.
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val ctl = window?.let { WindowCompat.getInsetsController(it, view) }
        ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctl?.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            ctl?.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
        }
    }
    BackHandler { onExit() }

    LaunchedEffect(showExit) { if (showExit) { delay(2500); showExit = false } }

    Box(
        Modifier.fillMaxSize().background(Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (phase == Phase.LIVE) showExit = !showExit
            }
    ) {
        RecordContent(state, dim = phase != Phase.LIVE)

        when (phase) {
            Phase.SETUP -> SetupPanel(
                onStart = { fromSong ->
                    fromStart = fromSong
                    phase = Phase.COUNTDOWN
                },
                onExit = onExit,
            )
            Phase.COUNTDOWN -> Countdown {
                if (fromStart) onRestartSong() else onEnsurePlaying()
                phase = Phase.LIVE
            }
            Phase.LIVE -> {}
        }

        AnimatedVisibility(showExit, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopEnd)) {
            TextButton(onClick = onExit, modifier = Modifier.padding(16.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f))) {
                Text("Sair do modo gravar", color = Cream)
            }
        }
    }
}

/** O que aparece na gravação. */
@Composable
private fun RecordContent(state: UiState, dim: Boolean) {
    val now = state.now
    val track = now?.track
    val lines = ((state.lyrics as? LyricsState.Ready)?.lyrics as? Lyrics.Synced)?.lines
    val nowState by rememberUpdatedState(now)
    val offset by rememberUpdatedState(state.lyricsOffsetMs)

    BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer { alpha = if (dim) 0.35f else 1f }) {
        val h = maxHeight
        if (lines != null) {
            EditLyricsView(
                lines = lines,
                positionMs = { (nowState?.positionAt(SystemClock.elapsedRealtime()) ?: 0L) + offset + 120 },
                centerFraction = 0.69f,
                maxWidthFraction = 0.80f,
            )
        }
        Column(
            Modifier.fillMaxWidth().padding(top = h * 0.085f, start = 32.dp, end = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AsyncImage(
                model = track?.imageUrl,
                contentDescription = "Capa",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth(0.56f)
                    .aspectRatio(1f)
                    .shadow(30.dp, RoundedCornerShape(16.dp), ambientColor = Cream.copy(alpha = 0.35f), spotColor = Cream.copy(alpha = 0.35f))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Cream.copy(alpha = 0.06f)),
            )
            Spacer(Modifier.height(22.dp))
            Text(
                track?.name ?: "Toque uma música no Spotify",
                color = Cream, fontFamily = Montserrat, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            track?.let {
                Text(
                    it.artistLine, color = Cream.copy(alpha = 0.65f), fontFamily = Montserrat, fontWeight = FontWeight.Medium,
                    fontSize = 15.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (track != null && lines == null && state.lyrics !is LyricsState.Loading) {
                Text(
                    "Esta música não tem letra sincronizada.",
                    color = Cream.copy(alpha = 0.5f), fontFamily = Montserrat, fontSize = 13.sp,
                    modifier = Modifier.padding(top = h * 0.12f),
                )
            }
        }
        Text(
            "@dmwnezes",
            color = Cream.copy(alpha = 0.6f), fontFamily = Montserrat, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            letterSpacing = 0.5.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = h * 0.045f),
        )
    }
}

@Composable
private fun SetupPanel(onStart: (fromSongStart: Boolean) -> Unit, onExit: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp).clip(RoundedCornerShape(26.dp)).background(Color(0xFF15120F)).padding(22.dp),
        ) {
            Text("Modo gravar para Stories", color = Cream, fontFamily = Montserrat, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
            Spacer(Modifier.height(14.dp))
            Step("1", "Puxe a barra de notificações e ligue o Gravador de tela com o áudio do dispositivo (\"Mídia\" ou \"Som do dispositivo\").")
            Step("2", "Volte aqui e toque em Começar. Depois de 3 segundos a tela fica limpa: só capa, nome, letra e @dmwnezes.")
            Step("3", "Para sair, toque na tela e em \"Sair\", ou use o gesto de voltar. Corte o começo e o fim no editor do Instagram.")
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onStart(true) }, modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Cream, contentColor = Color.Black),
            ) { Text("Começar do início da música", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { onStart(false) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("Começar de onde está", color = Cream)
            }
            TextButton(onClick = onExit, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Cancelar", color = Cream.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun Step(n: String, text: String) {
    Row(Modifier.padding(vertical = 5.dp)) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(Cream.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Text(n, color = Cream, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, color = Cream.copy(alpha = 0.85f), fontSize = 14.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun Countdown(onDone: () -> Unit) {
    var n by remember { mutableIntStateOf(3) }
    val scale = remember { Animatable(1.4f) }
    LaunchedEffect(Unit) {
        for (i in 3 downTo 1) {
            n = i
            scale.snapTo(1.5f)
            scale.animateTo(1f, tween(450))
            delay(550)
        }
        onDone()
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                "$n", color = Cream, fontFamily = Montserrat, fontWeight = FontWeight.ExtraBold, fontSize = 120.sp,
                modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value },
            )
            Text("a tela vai ficar limpa", color = Cream.copy(alpha = 0.6f), fontSize = 14.sp)
        }
    }
}
