package com.dmwnezes.sintonia

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.dmwnezes.sintonia.data.Artist
import com.dmwnezes.sintonia.data.ProfileData
import com.dmwnezes.sintonia.data.RecentPlay
import com.dmwnezes.sintonia.data.TimeRange
import com.dmwnezes.sintonia.data.Track
import com.dmwnezes.sintonia.data.UserProfile
import com.dmwnezes.sintonia.edit.LyricsMode
import com.dmwnezes.sintonia.history.HistoryAggregator
import com.dmwnezes.sintonia.history.HistoryUi
import com.dmwnezes.sintonia.history.RawStream
import com.dmwnezes.sintonia.notebook.DiaryEntry
import com.dmwnezes.sintonia.notebook.SavedLine
import com.dmwnezes.sintonia.ui.AppBackground
import com.dmwnezes.sintonia.ui.FloatingNavBar
import com.dmwnezes.sintonia.ui.HistoryScreen
import com.dmwnezes.sintonia.ui.LyricsOptionsContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.dmwnezes.sintonia.ui.NavItem
import com.dmwnezes.sintonia.ui.NotebookScreen
import com.dmwnezes.sintonia.ui.ProfileScreen
import com.dmwnezes.sintonia.ui.SearchScreen
import com.dmwnezes.sintonia.ui.TrackColors
import com.dmwnezes.sintonia.ProfileState
import com.dmwnezes.sintonia.viz.VizTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

/** Telas redesenhadas com dados inventados (artistas, músicas e trechos fictícios). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi")
class RedesignRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val out = File("build/frames/redesign").apply { mkdirs() }
    private val colors = TrackColors(Color(0xFF2A1E0E), Color(0xFF5A4318), Color(0xFFE8B23A), Color(0xFFFF8A5C), Color(0xFFFFD27A))
    private val accent = colors.glow1

    private fun cover(name: String, a: Int, b: Int): String {
        val f = File(rule.activity.cacheDir, "$name.png")
        val bmp = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawRect(0f, 0f, 300f, 300f, Paint().apply { shader = LinearGradient(0f, 0f, 300f, 300f, a, b, Shader.TileMode.CLAMP) })
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f.toURI().toString()
    }

    private fun save(name: String) {
        rule.mainClock.advanceTimeBy(2500)
        rule.waitForIdle()
        rule.runOnUiThread {
            val v = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bmp))
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Composable
    private fun Frame(tab: Int, content: @Composable (PaddingValues) -> Unit) {
        AppBackground(colors) {
            Scaffold(containerColor = Color.Transparent, bottomBar = {
                FloatingNavBar(
                    listOf(
                        NavItem("Letras", Icons.Rounded.Lyrics, 0), NavItem("Buscar", Icons.Rounded.Search, 5),
                        NavItem("Descobrir", Icons.Rounded.Explore, 4), NavItem("Perfil", Icons.Rounded.Person, 1),
                        NavItem("Histórico", Icons.Rounded.History, 2), NavItem("Caderno", Icons.Rounded.AutoStories, 3),
                    ), tab, {},
                )
            }) { p -> Box(Modifier.fillMaxSize()) { content(p) } }
        }
    }

    private fun history() = run {
        val zone = ZoneId.of("America/Fortaleza")
        val agg = HistoryAggregator(zone)
        fun add(at: String, t: String, a: String, n: Int) = repeat(n) { i ->
            agg.add(RawStream(LocalDateTime.parse(at).plusDays(i.toLong()).atZone(zone).toInstant().toEpochMilli(), 210_000, t, a, "Álbum $a", "spotify:track:$t$a",
                null, null, "android", "BR", "clickrow", "trackdone", i % 3 == 0, i % 7 == 0, false, false))
        }
        add("2024-03-01T22:00:00", "Mar Aberto", "Banda Aurora", 60)
        add("2024-04-01T21:00:00", "Janela Azul", "Lia Ventura", 40)
        add("2024-05-01T20:00:00", "Neon de Domingo", "Os Satélites", 25)
        add("2023-06-01T10:00:00", "Rua das Flores", "Banda Aurora", 30)
        add("2023-08-01T19:00:00", "Café Frio", "Theo Lume", 22)
        add("2022-02-01T18:00:00", "Primeiro Verão", "Lia Ventura", 18)
        agg.build(1)
    }

    @Test fun historyScreen() {
        val s = history()
        rule.setContent { Frame(2) { p -> HistoryScreen(HistoryUi(loaded = true, stats = s), accent, {}, {}, { null }, {}, {}, {}, p) } }
        save("historico")
    }

    @Test fun profileScreen() {
        val tracks = listOf("Mar Aberto" to "Banda Aurora", "Janela Azul" to "Lia Ventura", "Neon de Domingo" to "Os Satélites", "Café Frio" to "Theo Lume", "Rua das Flores" to "Banda Aurora", "Lua Nova" to "Duo Maré")
            .mapIndexed { i, (n, a) -> Track("t$i", n, listOf(a), "Álbum", cover("c$i", (0xFF000000 + i * 0x203040).toInt() or 0x402010, 0xFF1A1030.toInt()), 200_000) }
        val artists = listOf("Banda Aurora", "Lia Ventura", "Os Satélites", "Theo Lume", "Duo Maré")
            .mapIndexed { i, n -> Artist("a$i", n, cover("a$i", 0xFFB06A2A.toInt() + i * 0x1020, 0xFF2A1A40.toInt()), listOf("indie", "mpb", "pop rock").take(1 + i % 3)) }
        val now = System.currentTimeMillis()
        val data = ProfileData(UserProfile("Daniel", null), artists, tracks, tracks.mapIndexed { i, t -> RecentPlay(t, now - i * 3_600_000L * 3) })
        rule.setContent { Frame(1) { p -> ProfileScreen(ProfileState.Ready(data), TimeRange.MEDIUM, accent, {}, {}, {}, {}, {}, p) } }
        save("perfil")
    }

    @Test fun notebookScreen() {
        AppGraph.init(rule.activity.application)
        val nb = AppGraph.notebook
        nb.state.value.lines.forEach { nb.deleteLine(it.id) }
        nb.saveLine(SavedLine("1", "t1", "Janela Azul", "Lia Ventura", cover("n1", 0xFF3A6AD0.toInt(), 0xFF101830.toInt()), "Deixa a luz entrar pela janela de manhã", null, 1000, 1))
        nb.saveLine(SavedLine("2", "t2", "Mar Aberto", "Banda Aurora", cover("n2", 0xFFE8B23A.toInt(), 0xFF402010.toInt()), "Cada onda leva um pouco do que eu fui", "Each wave takes a bit of who I was", 2000, 2))
        rule.setContent { Frame(3) { p -> NotebookScreen(null, { 0L }, accent, p) } }
        save("caderno")
    }

    @Test fun searchScreen() {
        val results = listOf("Janela Azul" to "Lia Ventura", "Janela Aberta" to "Theo Lume", "Azul de Março" to "Duo Maré", "Janelas" to "Os Satélites")
            .mapIndexed { i, (n, a) -> Track("s$i", n, listOf(a), "Álbum", cover("s$i", 0xFF3A6AD0.toInt() + i * 0x300000, 0xFF101830.toInt()), 190_000L + i * 7000) }
        rule.setContent { Frame(5) { p -> SearchScreen({}, {}, accent, p, searcher = { results }) } }
        save("buscar-vazio")
    }

    @Test fun optionsSheet() = sheet(LyricsMode.VIDRO, "menu-opcoes")
    @Test fun optionsSheetClassic() = sheet(LyricsMode.CLASSICO, "menu-classico")

    private fun sheet(mode: LyricsMode, name: String) {
        rule.setContent {
            AppBackground(colors) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.background(Color(0xFF15121B), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).padding(top = 20.dp)) {
                        LyricsOptionsContent(mode, accent, true, true, VizTheme.BRILHOS, false, {}, {}, {}, {}, {}, {}, {}, {})
                    }
                }
            }
        }
        save(name)
    }

    @Test @Config(qualifiers = "w360dp-h2400dp-xhdpi") fun historyTall() {
        val s = history()
        rule.setContent { Frame(2) { p -> HistoryScreen(HistoryUi(loaded = true, stats = s), accent, {}, {}, { null }, {}, {}, {}, p) } }
        save("historico-longo")
    }
}
