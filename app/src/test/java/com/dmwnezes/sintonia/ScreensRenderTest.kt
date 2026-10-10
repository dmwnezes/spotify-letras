package com.dmwnezes.sintonia

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.dmwnezes.sintonia.data.NowPlaying
import com.dmwnezes.sintonia.data.Track
import com.dmwnezes.sintonia.lyrics.LyricLine
import com.dmwnezes.sintonia.lyrics.Lyrics
import com.dmwnezes.sintonia.ui.AppBackground
import com.dmwnezes.sintonia.ui.LyricsScreen
import com.dmwnezes.sintonia.ui.TrackColors
import com.dmwnezes.sintonia.ui.VisualizerCanvas
import com.dmwnezes.sintonia.viz.VizTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h740dp-xhdpi")
class ScreensRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val out = File("build/frames").apply { mkdirs() }
    private val colors = TrackColors(Color(0xFF1A0F2E), Color(0xFF4A2160), Color(0xFFE0457B), Color(0xFF7B8CFF), Color(0xFFFFC24A))

    private fun save(name: String) {
        rule.mainClock.advanceTimeBy(1500)
        rule.runOnUiThread {
            val v = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(android.graphics.Canvas(bmp))
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun visualizerThemes() {
        var theme = VizTheme.BRILHOS
        val levels = FloatArray(48) { i -> (0.85f * kotlin.math.exp(-i / 18f) + 0.15f * (0.5f + 0.5f * sin(i * 0.9f))).toFloat() }
        rule.mainClock.autoAdvance = false
        val state = androidx.compose.runtime.mutableStateOf(theme)
        rule.setContent {
            AppBackground(colors) {
                Box(Modifier.size(360.dp, 640.dp)) { VisualizerCanvas(colors, true, { levels }, theme = state.value) }
            }
        }
        for (t in VizTheme.entries) {
            state.value = t
            rule.mainClock.advanceTimeBy(100)
            save("viz-${t.name.lowercase()}")
        }
    }

    @Test
    fun lyricsLightSerif() = lyrics(light = true, name = "letras-claro-serifa")

    @Test
    fun lyricsWithTranslation() = lyrics(light = false, name = "letras-traducao")

    @Test
    fun lyricsEditStyle() = lyrics(light = true, name = "letras-edit", edit = true)

    private fun lyrics(light: Boolean, name: String, edit: Boolean = false) {
        rule.mainClock.autoAdvance = false
        val lines = listOf(
            LyricLine(0, "The kettle hums a quiet tune"),
            LyricLine(5000, "Paper boats along the street"),
            LyricLine(9000, "We count the windows of the train"),
            LyricLine(14000, ""),
            LyricLine(18000, "Orange light on Sunday floors"),
        )
        val tr = listOf("A chaleira cantarola baixinho", "Barquinhos de papel pela rua", "Contamos as janelas do trem", null, "Luz laranja no chão de domingo")
        val track = Track("x", "Song Name", listOf("Some Artist"), "Album", null, 200_000)
        val state = UiState(
            loggedIn = true, firstLoadDone = true,
            now = NowPlaying(track, true, 6200, SystemClock.elapsedRealtime()),
            lyrics = LyricsState.Ready(Lyrics.Synced(lines)),
            translation = TranslationState.Ready("inglês", tr),
            colors = colors.copy(soft1 = Color(0xFFF6E3F0), soft2 = Color(0xFFE3E6FF)), realAudioViz = false, showTranslation = true, lyricsMode = if (edit) com.dmwnezes.sintonia.edit.LyricsMode.EDIT else com.dmwnezes.sintonia.edit.LyricsMode.CLASSICO,
        )
        rule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                com.dmwnezes.sintonia.ui.LocalAppPalette provides if (light) com.dmwnezes.sintonia.ui.LightPalette else com.dmwnezes.sintonia.ui.DarkPalette,
                com.dmwnezes.sintonia.ui.LocalLyricsStyle provides com.dmwnezes.sintonia.ui.LyricsStyle(
                    if (light) com.dmwnezes.sintonia.ui.LyricsFont.SERIFA else com.dmwnezes.sintonia.ui.LyricsFont.PADRAO),
            ) {
                MaterialTheme(colorScheme = darkColorScheme(primary = Color.White)) {
                    AppBackground(state.colors) {
                        LyricsScreen(state, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, PaddingValues())
                    }
                }
            }
        }
        save(name)
    }

    @Test
    fun splash() {
        rule.mainClock.autoAdvance = false
        rule.setContent { com.dmwnezes.sintonia.ui.SplashCredits(onDone = {}) }
        rule.mainClock.advanceTimeBy(900)
        save("abertura")
    }

    @Test
    fun recordMode() {
        rule.mainClock.autoAdvance = false
        val f = java.io.File(rule.activity.cacheDir, "capa.png")
        val bmp = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bmp).drawRect(0f, 0f, 400f, 400f, android.graphics.Paint().apply {
            shader = android.graphics.LinearGradient(0f, 0f, 400f, 400f, 0xFFB0603C.toInt(), 0xFF1E2A50.toInt(), android.graphics.Shader.TileMode.CLAMP) })
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val lines = listOf(LyricLine(0, "você sabe que não é o mesmo"), LyricLine(6000, "de antes"))
        val track = Track("x", "Canção de Teste", listOf("Artista Exemplo"), "Álbum", f.toURI().toString(), 200_000)
        val state = UiState(loggedIn = true, firstLoadDone = true, now = NowPlaying(track, true, 2400, SystemClock.elapsedRealtime()),
            lyrics = LyricsState.Ready(Lyrics.Synced(lines)))
        rule.setContent { com.dmwnezes.sintonia.ui.RecordModeScreen(state, {}, {}, {}, startLive = true) }
        save("modo-gravar")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun recordModeTallPhone() {
        rule.mainClock.autoAdvance = false
        val lines = listOf(LyricLine(0, "você sabe que não é o mesmo"), LyricLine(6000, "de antes"))
        val track = Track("x", "Canção de Teste", listOf("Artista Exemplo"), "Álbum", null, 200_000)
        val state = UiState(loggedIn = true, firstLoadDone = true, now = NowPlaying(track, true, 4800, SystemClock.elapsedRealtime()),
            lyrics = LyricsState.Ready(Lyrics.Synced(lines)))
        rule.setContent { com.dmwnezes.sintonia.ui.RecordModeScreen(state, {}, {}, {}, startLive = true) }
        save("modo-gravar-alto")
    }
}
