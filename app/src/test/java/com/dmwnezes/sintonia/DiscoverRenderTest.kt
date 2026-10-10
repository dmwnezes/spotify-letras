package com.dmwnezes.sintonia

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dmwnezes.sintonia.reco.DiscoverViewModel
import com.dmwnezes.sintonia.reco.RecoStore
import com.dmwnezes.sintonia.reco.RecoTrack
import com.dmwnezes.sintonia.ui.AppBackground
import com.dmwnezes.sintonia.ui.DiscoverScreen
import com.dmwnezes.sintonia.ui.TrackColors
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xhdpi")
class DiscoverRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val out = File("build/frames").apply { mkdirs() }
    private val colors = TrackColors(Color(0xFF1A0F2E), Color(0xFF4A2160), Color(0xFFE0457B), Color(0xFF7B8CFF), Color(0xFFFFC24A))

    private fun cover(name: String, a: Int, b: Int): String {
        val f = File(rule.activity.cacheDir, "$name.png")
        val bmp = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawRect(0f, 0f, 300f, 300f, Paint().apply { shader = LinearGradient(0f, 0f, 300f, 300f, a, b, Shader.TileMode.CLAMP) })
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f.toURI().toString()
    }

    private fun save(name: String) {
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        rule.runOnUiThread {
            val v = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bmp))
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun discoverScreens() {
        val palette = listOf(0xFFE65A3C to 0xFF3C2896, 0xFF1EC8B5 to 0xFF1B3A8C, 0xFFFFB347 to 0xFFD7263D, 0xFF9B5CFF to 0xFF16133A, 0xFFB5F44A to 0xFF14804A, 0xFF4AC6FF to 0xFF3B1F7A)
        val songs = listOf(
            Triple("Força Estranha", "Gal Costa", "Djavan"), Triple("Devolva-me", "Adriana Calcanhotto", "Marisa Monte"),
            Triple("Mania De Você", "Rita Lee", "Tim Maia"), Triple("Tangerina", "TIAGO IORC", "Ana Vilela"),
            Triple("Amado", "Vanessa da Mata", "Marisa Monte"), Triple("Casinha Branca", "Roberta Campos", "Ana Vilela"),
        )
        val tracks = songs.mapIndexed { i, (t, a, r) ->
            val (c1, c2) = palette[i]
            RecoTrack(i + 1L, t, a, i * 10L, "Álbum de $a", cover("c$i", c1.toInt(), c2.toInt()), null, 210, r)
        }
        lateinit var vm: DiscoverViewModel
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread {
            vm = DiscoverViewModel(rule.activity.application)
            vm.store.update { it.copy(mix = tracks, mixDay = RecoStore.today(), likes = tracks.take(2)) }
            vm.setDeckForPreview(tracks.drop(2))
        }
        rule.setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color.White)) {
                AppBackground(colors) { DiscoverScreen(vm, colors.glow1, {}, PaddingValues()) }
            }
        }
        save("descobrir-mix")
        rule.onNodeWithText("Deslizar").performClick()
        save("descobrir-deslizar")
        rule.onNodeWithText("Curtidas (2)").performClick()
        save("descobrir-curtidas")
    }
}
