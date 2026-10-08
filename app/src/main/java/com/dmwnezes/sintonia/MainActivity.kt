package com.dmwnezes.sintonia

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.dmwnezes.sintonia.ui.AppBackground
import com.dmwnezes.sintonia.ui.HistoryScreen
import com.dmwnezes.sintonia.ui.LoginScreen
import com.dmwnezes.sintonia.ui.LyricsScreen
import com.dmwnezes.sintonia.ui.ProfileScreen
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()
    private val historyVm: HistoryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        intent?.data?.let(vm::handleCallback)

        // Só consulta o Spotify enquanto o app está aberto na tela.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.startPolling()
                try { awaitCancellation() } finally { vm.stopPolling() }
            }
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color.White, background = Color.Black)) {
                val state by vm.ui.collectAsStateWithLifecycle()
                AppBackground(state.colors) {
                    if (!state.loggedIn) {
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

    @androidx.compose.runtime.Composable
    private fun MainTabs() {
        val state by vm.ui.collectAsStateWithLifecycle()
        val profile by vm.profile.collectAsStateWithLifecycle()
        val range by vm.profileRange.collectAsStateWithLifecycle()
        val history by historyVm.ui.collectAsStateWithLifecycle()
        var tab by rememberSaveable { mutableIntStateOf(0) }
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
                    NavigationBarItem(
                        selected = tab == 0, onClick = { tab = 0 },
                        icon = { Icon(Icons.Rounded.Lyrics, null) }, label = { Text("Letras") }, colors = itemColors,
                    )
                    NavigationBarItem(
                        selected = tab == 1, onClick = { tab = 1 },
                        icon = { Icon(Icons.Rounded.Person, null) }, label = { Text("Perfil") }, colors = itemColors,
                    )
                    NavigationBarItem(
                        selected = tab == 2, onClick = { tab = 2 },
                        icon = { Icon(Icons.Rounded.History, null) }, label = { Text("Histórico") }, colors = itemColors,
                    )
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
                        onNudgeOffset = vm::nudgeOffset,
                        onSetPlayerOnly = vm::setPlayerOnly,
                        onRetryLyrics = vm::retryLyrics,
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
                        bottomPadding = padding,
                    )
                    else -> ProfileScreen(
                        state = profile,
                        range = range,
                        accent = state.colors.glow1,
                        onRange = { vm.loadProfile(it) },
                        onRefresh = { vm.loadProfile(range, force = true) },
                        onLogout = vm::logout,
                        bottomPadding = padding,
                    )
                }
            }
        }
    }
}

