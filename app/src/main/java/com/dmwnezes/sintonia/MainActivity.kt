package com.dmwnezes.sintonia

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dmwnezes.sintonia.history.HistoryViewModel
import com.dmwnezes.sintonia.live.LiveLyricsService
import com.dmwnezes.sintonia.ui.AppBackground
import com.dmwnezes.sintonia.ui.HistoryScreen
import com.dmwnezes.sintonia.ui.LoginScreen
import com.dmwnezes.sintonia.ui.LyricsScreen
import com.dmwnezes.sintonia.ui.NotebookScreen
import com.dmwnezes.sintonia.ui.ProfileScreen
import com.dmwnezes.sintonia.ui.QuizDialog
import com.dmwnezes.sintonia.ui.SettingsDialog
import com.dmwnezes.sintonia.ui.SplashCredits
import com.dmwnezes.sintonia.update.Release
import com.dmwnezes.sintonia.update.UpdateDialog
import com.dmwnezes.sintonia.update.Updater
import com.dmwnezes.sintonia.wrapped.WrappedDialog
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()
    private val historyVm: HistoryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        intent?.data?.let(vm::handleCallback)

        // Só consulta o Spotify enquanto o app está aberto na tela (ou o serviço da tela de bloqueio está ligado).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.startPolling()
                val prefs = AppGraph.prefs
                if (prefs.liveLyrics && prefs.isLoggedIn && !LiveLyricsService.running) {
                    runCatching { LiveLyricsService.start(this@MainActivity) }
                }
                try { awaitCancellation() } finally { vm.stopPolling() }
            }
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color.White, background = Color.Black)) {
                val state by vm.ui.collectAsStateWithLifecycle()
                var splash by rememberSaveable { mutableStateOf(true) }
                AppBackground(state.colors) {
                    if (splash) {
                        SplashCredits(onDone = { splash = false })
                    } else if (!state.loggedIn) {
                        LoginScreen(
                            clientId = state.clientId,
                            loggingIn = state.loggingIn,
                            error = state.authError,
                            onSaveClientId = vm::saveClientId,
                            onLogin = { openLogin() },
                        )
                    } else {
                        MainTabs()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let(vm::handleCallback)
    }

    private fun openLogin() {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, vm.loginUri())
    }

    @Composable
    private fun MainTabs() {
        val state by vm.ui.collectAsStateWithLifecycle()
        val profile by vm.profile.collectAsStateWithLifecycle()
        val range by vm.profileRange.collectAsStateWithLifecycle()
        val history by historyVm.ui.collectAsStateWithLifecycle()
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var showSettings by remember { mutableStateOf(false) }
        var showQuiz by remember { mutableStateOf(false) }
        var wrappedKey by remember { mutableStateOf<String?>(null) }
        var showUpdate by remember { mutableStateOf(false) }
        var foundUpdate by remember { mutableStateOf<Release?>(null) }

        // Checa atualização uma vez ao abrir; só avisa se a pessoa não dispensou esta versão.
        LaunchedEffect(Unit) {
            val updater = Updater(AppGraph.http)
            runCatching { updater.latest() }.getOrNull()?.let { r ->
                if (updater.isNewer(r) && AppGraph.prefs.skippedUpdate != r.tag) foundUpdate = r
            }
        }
        val snackbar = remember { SnackbarHostState() }

        LaunchedEffect(state.message) {
            state.message?.let {
                snackbar.showSnackbar(it)
                vm.clearMessage()
            }
        }
        LaunchedEffect(history.message) {
            history.message?.let {
                snackbar.showSnackbar(it)
                historyVm.clearMessage()
            }
        }

        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar(containerColor = Color.Black.copy(alpha = 0.45f), tonalElevation = 0.dp) {
                    val itemColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = Color.White,
                        indicatorColor = Color.White,
                        unselectedIconColor = Color.White.copy(alpha = 0.6f),
                        unselectedTextColor = Color.White.copy(alpha = 0.6f),
                    )
                    listOf(
                        Triple("Letras", Icons.Rounded.Lyrics, 0),
                        Triple("Perfil", Icons.Rounded.Person, 1),
                        Triple("Histórico", Icons.Rounded.History, 2),
                        Triple("Caderno", Icons.Rounded.AutoStories, 3),
                    ).forEach { (label, icon, i) ->
                        NavigationBarItem(
                            selected = tab == i, onClick = { tab = i },
                            icon = { Icon(icon, null) }, label = { Text(label) }, colors = itemColors,
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize()) {
                when (tab) {
                    0 -> LyricsScreen(
                        state = state,
                        onTogglePlay = vm::togglePlay,
                        onNext = vm::next,
                        onPrevious = vm::previous,
                        onSeek = vm::seek,
                        onSetRealViz = vm::setRealAudioViz,
                        onSetVizTheme = vm::setVizTheme,
                        onNudgeOffset = vm::nudgeOffset,
                        onSetPlayerOnly = vm::setPlayerOnly,
                        onSetShowTranslation = vm::setShowTranslation,
                        onRetryLyrics = vm::retryLyrics,
                        bottomPadding = padding,
                    )
                    1 -> ProfileScreen(
                        state = profile,
                        range = range,
                        accent = state.colors.glow1,
                        onRange = { vm.loadProfile(it) },
                        onRefresh = { vm.loadProfile(range, force = true) },
                        onLogout = {
                            LiveLyricsService.stop(this@MainActivity)
                            vm.logout()
                        },
                        onOpenSettings = { showSettings = true },
                        onOpenQuiz = { showQuiz = true },
                        bottomPadding = padding,
                    )
                    2 -> HistoryScreen(
                        ui = history,
                        accent = state.colors.glow1,
                        onImport = historyVm::import,
                        onSelect = historyVm::select,
                        summaryText = historyVm::summaryText,
                        onSaveBackup = historyVm::saveBackup,
                        onClear = historyVm::clear,
                        onWrapped = { wrappedKey = it },
                        bottomPadding = padding,
                    )
                    else -> NotebookScreen(
                        currentTrack = state.now?.track,
                        positionMs = { state.now?.positionAt(SystemClock.elapsedRealtime()) ?: 0L },
                        accent = state.colors.glow1,
                        bottomPadding = padding,
                    )
                }
            }
        }

        if (showSettings) {
            SettingsDialog(
                state = state,
                onLiveLyrics = vm::setLiveLyrics,
                onTranslation = vm::setShowTranslation,
                onTheme = vm::setVizTheme,
                onCheckUpdates = { showSettings = false; showUpdate = true },
                onDismiss = { showSettings = false },
            )
        }
        if (showUpdate) UpdateDialog(onDismiss = { showUpdate = false })
        foundUpdate?.let { r ->
            UpdateDialog(initial = r, onSkip = { AppGraph.prefs.skippedUpdate = it.tag }, onDismiss = { foundUpdate = null })
        }
        if (showQuiz) {
            QuizDialog(accent = state.colors.glow1, onPlay = vm::playTrack, onDismiss = { showQuiz = false })
        }
        wrappedKey?.let { key ->
            history.stats?.let { stats -> WrappedDialog(stats, key) { wrappedKey = null } }
        }
    }
}
