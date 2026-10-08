package com.dmwnezes.sintonia.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.sintonia.R
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

const val CREATOR_HANDLE = "dmwnezes"

/** Abre o perfil do criador no app do Instagram (ou no navegador, se o app não estiver instalado). */
fun openCreatorInstagram(context: Context) {
    val app = Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/_u/$CREATOR_HANDLE")).setPackage("com.instagram.android")
    try {
        context.startActivity(app)
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/$CREATOR_HANDLE/")))
    }
}

/** Foto redonda pequena + "criado por: @dmwnezes", tocável, que leva ao Instagram. */
@Composable
fun CreatorCredit(modifier: Modifier = Modifier, light: Boolean = true) {
    val context = LocalContext.current
    val text = if (light) Color.White else Color.Black
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(text.copy(alpha = 0.08f))
            .clickable { openCreatorInstagram(context) }
            .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.criador),
            contentDescription = "Foto de @$CREATOR_HANDLE",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(34.dp).clip(CircleShape).border(1.5.dp, text.copy(alpha = 0.7f), CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Text("criado por: ", color = text.copy(alpha = 0.7f), fontSize = 14.sp)
        Text(
            "@$CREATOR_HANDLE",
            color = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textDecoration = TextDecoration.Underline,
        )
    }
}

/**
 * Tela de abertura: nome do app, barrinhas animadas e os créditos.
 * Some sozinha depois de ~2,8 s ou ao tocar fora do link.
 */
@Composable
fun SplashCredits(onDone: () -> Unit) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        delay(2100)
        onDone()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF3B1E6E), Color(0xFF120A24), Color(0xFF050308))))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDone),
    ) {
        Column(
            Modifier.fillMaxSize().graphicsLayer { alpha = appear.value; translationY = (1f - appear.value) * 40f },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EqualizerMark()
            Spacer(Modifier.height(22.dp))
            Text("Sintonia", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
            Text("a letra no ritmo da música", color = Color.White.copy(alpha = 0.65f), fontSize = 16.sp)
        }
        CreatorCredit(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 48.dp)
                .graphicsLayer { alpha = appear.value },
        )
    }
}

/** As cinco barrinhas do ícone, dançando. */
@Composable
private fun EqualizerMark() {
    val t = rememberInfiniteTransition(label = "eq")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "p")
    Canvas(Modifier.size(96.dp)) {
        val n = 5
        val gap = size.width / n
        val w = gap * 0.55f
        val base = listOf(0.35f, 0.65f, 0.9f, 0.55f, 0.3f)
        for (i in 0 until n) {
            val wave = 0.75f + 0.25f * sin((phase + i * 0.18f) * 2f * PI.toFloat())
            val h = size.height * base[i] * wave
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFFE0457B), Color(0xFF7B5CFF))),
                topLeft = Offset(i * gap + (gap - w) / 2, (size.height - h) / 2),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2),
            )
        }
    }
}
