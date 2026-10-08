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
    fun lyricsWithTranslation() {
        rule.mainClock.autoAdvance = false
        val lines = listOf(
            LyricLine(0, "I've been waiting for a long time"),
            LyricLine(5000, "For this moment to come"),
            LyricLine(9000, "I'm destined for anything at all"),
            LyricLine(14000, ""),
            LyricLine(18000, "Every word you say to me"),
        )
        val tr = listOf("Eu esperei por muito tempo", "Para este momento chegar", "Estou destinado a qualquer coisa", null, "Cada palavra que você me diz")
        val track = Track("x", "Song Name", listOf("Some Artist"), "Album", null, 200_000)
        val state = UiState(
            loggedIn = true, firstLoadDone = true,
            now = NowPlaying(track, true, 6000, SystemClock.elapsedRealtime()),
            lyrics = LyricsState.Ready(Lyrics.Synced(lines)),
            translation = TranslationState.Ready("inglês", tr),
            colors = colors, realAudioViz = false, showTranslation = true,
        )
        rule.setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color.White)) {
                AppBackground(colors) {
                    LyricsScreen(state, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, PaddingValues())
                }
            }
        }
        save("letras-traducao")
    }
}
