package com.dmwnezes.sintonia.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.dmwnezes.sintonia.AppGraph
import com.dmwnezes.sintonia.data.Track
import com.dmwnezes.sintonia.quiz.QuizEngine
import com.dmwnezes.sintonia.quiz.QuizQuestion
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

private const val ROUNDS = 10
private val Right = Color(0xFF3DDC84)
private val Wrong = Color(0xFFFF6B6B)

private sealed interface QuizUi {
    data object Loading : QuizUi
    data class Asking(val q: QuizQuestion, val round: Int, val picked: Track? = null) : QuizUi
    data class Finished(val score: Int, val best: Int, val newRecord: Boolean) : QuizUi
    data class Error(val message: String) : QuizUi
}

@Composable
fun QuizDialog(accent: Color, onPlay: (String) -> Unit, onDismiss: () -> Unit) {
    val engine = remember { QuizEngine(AppGraph.spotify, AppGraph.lrclib) }
    val scope = rememberCoroutineScope()
    var ui by remember { mutableStateOf<QuizUi>(QuizUi.Loading) }
    var score by remember { mutableIntStateOf(0) }
    var nextQ by remember { mutableStateOf<Deferred<QuizQuestion?>?>(null) }

    fun startGame() {
        score = 0
        ui = QuizUi.Loading
        scope.launch {
            val n = runCatching { engine.prepare() }.getOrDefault(0)
            if (n < 6) { ui = QuizUi.Error("Preciso de mais músicas no seu Spotify para montar o quiz. Ouça um pouco mais e volte!"); return@launch }
            val q = engine.next()
            if (q == null) { ui = QuizUi.Error("Não encontrei letras suficientes das suas músicas agora. Tente de novo mais tarde."); return@launch }
            ui = QuizUi.Asking(q, 1)
            nextQ = async { engine.next() }
        }
    }

    fun advance(current: QuizUi.Asking) {
        if (current.round >= ROUNDS) {
            val prefs = AppGraph.prefs
            val record = score > prefs.quizBest
            if (record) prefs.quizBest = score
            ui = QuizUi.Finished(score, prefs.quizBest, record)
            return
        }
        ui = QuizUi.Loading
        scope.launch {
            val q = nextQ?.await() ?: engine.next()
            if (q == null) {
                val prefs = AppGraph.prefs
                val record = score > prefs.quizBest
                if (record) prefs.quizBest = score
                ui = QuizUi.Finished(score, prefs.quizBest, record)
                return@launch
            }
            ui = QuizUi.Asking(q, current.round + 1)
            nextQ = async { engine.next() }
        }
    }

    LaunchedEffect(Unit) { startGame() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.55f), Palette.surface, Palette.surface)))
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Quiz da letra", color = Palette.ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        val s = ui
                        if (s is QuizUi.Asking) Text("Pergunta ${s.round} de $ROUNDS · $score ponto(s)", color = Palette.ink.copy(alpha = 0.7f), fontSize = 13.sp)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Fechar", tint = Palette.ink) }
                }
                AnimatedContent(ui, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) }, label = "quiz", modifier = Modifier.weight(1f)) { s ->
                    when (s) {
                        QuizUi.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Palette.ink)
                                Spacer(Modifier.height(12.dp))
                                Text("Escolhendo um trecho…", color = Palette.ink.copy(alpha = 0.7f))
                            }
                        }
                        is QuizUi.Error -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(s.message, color = Palette.ink, fontSize = 17.sp, textAlign = TextAlign.Center)
                        }
                        is QuizUi.Asking -> Question(s, onPick = { t ->
                            if (s.picked == null) {
                                if (t.id == s.q.answer.id) score++
                                ui = s.copy(picked = t)
                            }
                        }, onPlay = { onPlay(s.q.answer.id) }, onNext = { advance(s) })
                        is QuizUi.Finished -> Finished(s, onAgain = ::startGame, onClose = onDismiss)
                    }
                }
            }
        }
    }
}

@Composable
private fun Question(s: QuizUi.Asking, onPick: (Track) -> Unit, onPlay: () -> Unit, onNext: () -> Unit) {
    val answered = s.picked != null
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(18.dp))
        Text("De qual música é este trecho?", color = Palette.ink.copy(alpha = 0.75f), fontSize = 15.sp)
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.ink.copy(alpha = 0.09f)).padding(22.dp)
        ) {
            Text("“", color = Palette.ink.copy(alpha = 0.5f), fontSize = 54.sp, fontWeight = FontWeight.Black, lineHeight = 40.sp)
            s.q.excerpt.forEach { Text(it, color = Palette.ink, fontSize = 23.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold) }
        }
        Spacer(Modifier.height(18.dp))
        s.q.options.forEach { t ->
            val isAnswer = t.id == s.q.answer.id
            val border = when {
                !answered -> Color.Transparent
                isAnswer -> Right
                t.id == s.picked?.id -> Wrong
                else -> Color.Transparent
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(16.dp))
                    .background(Palette.ink.copy(alpha = if (answered && !isAnswer && t.id != s.picked?.id) 0.04f else 0.10f))
                    .border(2.dp, border, RoundedCornerShape(16.dp))
                    .clickable(enabled = !answered) { onPick(t) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(t.imageUrl, null, Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.name, color = Palette.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artistLine, color = Palette.ink.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (answered) {
            Spacer(Modifier.height(10.dp))
            val right = s.picked?.id == s.q.answer.id
            Text(
                if (right) "Acertou! 🎉" else "Era \"${s.q.answer.name}\".",
                color = if (right) Right else Wrong, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppOutlinedButton(onClick = onPlay, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = Palette.ink)
                    Spacer(Modifier.width(4.dp))
                    Text("Ouvir", color = Palette.ink)
                }
                AppButton(
                    onClick = onNext, modifier = Modifier.weight(1f),
                    containerColor = Palette.ink, contentColor = Palette.onInk,
                ) { Text(if (s.round >= ROUNDS) "Ver resultado" else "Próxima") }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Finished(s: QuizUi.Finished, onAgain: () -> Unit, onClose: () -> Unit) {
    val msg = when {
        s.score >= 9 -> "Você sabe tudo das suas músicas!"
        s.score >= 7 -> "Mandou muito bem."
        s.score >= 4 -> "Nada mal! Dá para melhorar."
        else -> "Hora de prestar mais atenção nas letras 😄"
    }
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${s.score}/$ROUNDS", color = Palette.ink, fontSize = 72.sp, fontWeight = FontWeight.Black)
        Text(msg, color = Palette.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            if (s.newRecord) "Novo recorde!" else "Seu recorde: ${s.best}/$ROUNDS",
            color = Palette.ink.copy(alpha = 0.7f), fontSize = 15.sp,
        )
        Spacer(Modifier.height(28.dp))
        AppButton(onClick = onAgain, containerColor = Palette.ink, contentColor = Palette.onInk, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text("Jogar de novo", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        AppOutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Fechar", color = Palette.ink) }
    }
}
