package com.dmwnezes.sintonia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.sintonia.data.SpotifyAuth

private val SpotifyGreen = Color(0xFF1ED760)

@Composable
fun LoginScreen(
    clientId: String,
    loggingIn: Boolean,
    error: String?,
    onSaveClientId: (String) -> Unit,
    onLogin: () -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(clientId.isBlank()) }
    var input by rememberSaveable { mutableStateOf(clientId) }
    val clipboard = LocalClipboardManager.current

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Sintonia", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            "A letra acompanhando a música, com um visualizer que dança junto.",
            color = Color.White.copy(alpha = 0.75f), fontSize = 17.sp,
        )
        Spacer(Modifier.height(36.dp))

        if (editing) {
            Text("1. Cole o Client ID do seu app do Spotify", color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                "Fica em developer.spotify.com → Dashboard → seu app → Settings.",
                color = Color.White.copy(alpha = 0.65f), fontSize = 14.sp,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.trim() },
                singleLine = true,
                placeholder = { Text("Client ID") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White, unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
                    cursorColor = Color.White,
                ),
            )
            Spacer(Modifier.height(18.dp))
            Text("2. No mesmo painel, em Redirect URIs, adicione:", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.1f)).padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(SpotifyAuth.REDIRECT_URI, color = Color.White, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                IconButton(onClick = { clipboard.setText(AnnotatedString(SpotifyAuth.REDIRECT_URI)) }) {
                    Icon(Icons.Rounded.ContentCopy, "Copiar", tint = Color.White)
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onSaveClientId(input); editing = false },
                enabled = input.length >= 20,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            ) { Text("Salvar", fontWeight = FontWeight.Bold) }
        } else {
            Button(
                onClick = onLogin,
                enabled = !loggingIn,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = Color.Black),
            ) {
                if (loggingIn) CircularProgressIndicator(color = Color.Black, modifier = Modifier.height(22.dp))
                else Text("Entrar com Spotify", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            TextButton(onClick = { editing = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Trocar Client ID", color = Color.White.copy(alpha = 0.7f))
            }
        }

        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Text(error, color = Color(0xFFFFB4B4), fontSize = 14.sp)
        }
    }
}
