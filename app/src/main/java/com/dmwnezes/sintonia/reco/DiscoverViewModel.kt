package com.dmwnezes.sintonia.reco

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dmwnezes.sintonia.AppGraph
import com.dmwnezes.sintonia.data.SpotifyAuth
import com.dmwnezes.sintonia.data.SpotifyClient
import com.dmwnezes.sintonia.data.SpotifyException
import com.dmwnezes.sintonia.data.TimeRange
import com.dmwnezes.sintonia.history.HistoryStore
import com.dmwnezes.sintonia.reco.RecoStore.Companion.markSeen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class DiscoverUi(
    val mixLoading: Boolean = false,
    val mixError: String? = null,
    val deck: List<RecoTrack> = emptyList(),
    val deckLoading: Boolean = false,
    val deckError: String? = null,
    val working: String? = null,      // "Abrindo no Spotify…" etc.
    val canEditPlaylists: Boolean = false,
    val playlistId: String? = null,
    val autoPlaylist: Boolean = true,
)

class DiscoverViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = AppGraph.prefs
    private val spotify = AppGraph.spotify
    private val dz = DeezerClient(AppGraph.http)
    private val recommender = Recommender(dz)
    val store = RecoStore(app)
    val preview = PreviewPlayer(app)

    private val _ui = MutableStateFlow(
        DiscoverUi(
            canEditPlaylists = SpotifyAuth.canEditPlaylists(prefs.grantedScopes),
            playlistId = prefs.recoPlaylistId,
            autoPlaylist = prefs.autoPlaylist,
        )
    )
    val ui = _ui.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private var taste: Taste? = null
    private var candidates: List<Candidate> = emptyList()
    private val gatherLock = Mutex()
    private val isrcCache = HashMap<Long, String?>()

    // ---------- Gosto e candidatas ----------

    /** Monta o seu gosto a partir do Spotify (e do histórico importado, se houver). */
    private suspend fun buildTaste(): Taste {
        taste?.let { return it }
        val ranges = listOf(TimeRange.SHORT to 1f, TimeRange.MEDIUM to 0.8f, TimeRange.LONG to 0.5f)
        val weights = HashMap<String, Pair<String, Float>>()
        val knownTracks = HashSet<String>()
        val knownArtists = HashSet<String>()
        for ((r, base) in ranges) {
            val artists = runCatching { spotify.topArtists(r) }.getOrDefault(emptyList())
            artists.forEachIndexed { i, a ->
                val k = DeezerClient.norm(a.name)
                val w = base * (1f - i / 35f)
                weights[k] = a.name to ((weights[k]?.second ?: 0f) + w)
                knownArtists += k
            }
            runCatching { spotify.topTracks(r) }.getOrDefault(emptyList()).forEach { t ->
                knownTracks += Recommender.trackKey(t.artists.firstOrNull().orEmpty(), t.name)
            }
        }
        runCatching { spotify.recentlyPlayed() }.getOrDefault(emptyList()).forEach { p ->
            knownTracks += Recommender.trackKey(p.track.artists.firstOrNull().orEmpty(), p.track.name)
        }
        // Histórico importado: tudo o que você mais ouviu conta como "já conhece".
        withContext(Dispatchers.IO) { HistoryStore(getApplication()).load() }?.all?.let { all ->
            all.topTracks.forEach { knownTracks += Recommender.trackKey(it.sub, it.name) }
            all.topArtists.take(30).forEachIndexed { i, a ->
                val k = DeezerClient.norm(a.name)
                weights[k] = a.name to ((weights[k]?.second ?: 0f) + 0.4f * (1f - i / 30f))
                knownArtists += k
            }
        }
        if (weights.isEmpty()) throw IllegalStateException("Preciso conhecer um pouco do seu gosto: ouça algumas músicas no Spotify e volte aqui.")
        return Taste(weights.values.map { Seed(it.first, it.second) }, knownTracks, knownArtists).also { taste = it }
    }

    private suspend fun ensureCandidates(fresh: Boolean = false): List<Candidate> = gatherLock.withLock {
        if (candidates.isEmpty() || fresh) {
            val t = buildTaste()
            candidates = recommender.gather(t, store.data, maxSeeds = 10)
        }
        candidates
    }

    // ---------- Para você ----------

    fun ensureMix(force: Boolean = false) {
        val d = store.data
        if (!force && d.mixDay == RecoStore.today() && d.mix.isNotEmpty()) return
        if (_ui.value.mixLoading) return
        _ui.update { it.copy(mixLoading = true, mixError = null) }
        viewModelScope.launch {
            try {
                val c = ensureCandidates(fresh = force)
                val mix = Recommender.rank(c, buildTaste(), store.data, 30, exclude = store.data.mix.map { it.dzId }.toSet())
                if (mix.isEmpty()) error("Não achei novidades agora. Tente de novo mais tarde.")
                store.update { it.copy(mix = mix, mixDay = RecoStore.today()).markSeen(*mix.map { m -> m.dzId }.toLongArray()) }
                _ui.update { it.copy(mixLoading = false) }
            } catch (e: Exception) {
                _ui.update { it.copy(mixLoading = false, mixError = e.message ?: "Não consegui montar o mix agora.") }
            }
        }
    }

    // ---------- Deslizar ----------

    fun ensureDeck() {
        if (_ui.value.deck.size >= 6 || _ui.value.deckLoading) return
        _ui.update { it.copy(deckLoading = true, deckError = null) }
        viewModelScope.launch {
            try {
                val exclude = (store.data.mix.map { it.dzId } + _ui.value.deck.map { it.dzId }).toSet()
                var more = Recommender.rank(ensureCandidates(), buildTaste(), store.data, 25, freshDays = 30, exclude = exclude)
                if (more.size < 5) {
                    // Acabaram as candidatas: busca outra rodada (sementes sorteadas diferentes).
                    more = more + Recommender.rank(ensureCandidates(fresh = true), buildTaste(), store.data, 25, freshDays = 30,
                        exclude = exclude + more.map { it.dzId })
                }
                _ui.update { s -> s.copy(deck = (s.deck + more).distinctBy { it.dzId }, deckLoading = false,
                    deckError = if (s.deck.isEmpty() && more.isEmpty()) "Você já viu todas as sugestões por hoje!" else null) }
            } catch (e: Exception) {
                _ui.update { it.copy(deckLoading = false, deckError = e.message ?: "Não consegui buscar sugestões.") }
            }
        }
    }

    /** Resposta do cartão: curtir (direita) ou pular (esquerda). */
    fun swipe(t: RecoTrack, liked: Boolean) {
        preview.stop()
        _ui.update { s -> s.copy(deck = s.deck.filterNot { it.dzId == t.dzId }) }
        if (liked) like(t) else dislike(t)
        ensureDeck()
    }

    // ---------- Aprender ----------

    fun like(t: RecoTrack) {
        store.update { RecoStore.like(it, t) }
        if (prefs.autoPlaylist && _ui.value.canEditPlaylists) {
            viewModelScope.launch { runCatching { addToPlaylist(listOf(t), quiet = true) } }
        }
    }

    fun dislike(t: RecoTrack) = store.update { RecoStore.dislike(it, t) }

    fun unlike(t: RecoTrack) = store.update { d -> d.copy(likes = d.likes.filterNot { it.dzId == t.dzId }) }

    fun isLiked(t: RecoTrack) = store.data.likes.any { it.dzId == t.dzId }

    // ---------- Prévia ----------

    fun togglePreview(t: RecoTrack) {
        val s = preview.state.value
        if (s.dzId == t.dzId) { preview.toggle(); return }
        viewModelScope.launch {
            // Os links de prévia expiram: pega um novo (e aproveita o ISRC para achar no Spotify).
            val fresh = dz.track(t.dzId)
            fresh?.second?.let { isrcCache[t.dzId] = it }
            val url = fresh?.first?.previewUrl ?: t.previewUrl
            if (url == null) _messages.tryEmit("Esta música não tem prévia.") else preview.play(t.dzId, url)
        }
    }

    // ---------- Spotify ----------

    /** Acha os IDs do Spotify (guardando para não buscar de novo). */
    private suspend fun spotifyIds(list: List<RecoTrack>): List<Pair<RecoTrack, String?>> {
        val gate = Semaphore(4)
        val found = kotlinx.coroutines.coroutineScope {
            list.map { t ->
                async {
                    gate.withPermit {
                        t.spotifyId?.let { return@withPermit t to it }
                        val isrc = isrcCache[t.dzId] ?: dz.track(t.dzId)?.second?.also { isrcCache[t.dzId] = it }
                        t to spotify.findTrack(t.title, t.artist, isrc)
                    }
                }
            }.awaitAll()
        }
        val map = found.mapNotNull { (t, id) -> id?.let { t.dzId to it } }.toMap()
        if (map.isNotEmpty()) store.update { d ->
            d.copy(
                likes = d.likes.map { it.copy(spotifyId = it.spotifyId ?: map[it.dzId]) },
                mix = d.mix.map { it.copy(spotifyId = it.spotifyId ?: map[it.dzId]) },
            )
        }
        return found
    }

    fun playInSpotify(list: List<RecoTrack>, start: Int = 0) {
        preview.stop()
        viewModelScope.launch {
            _ui.update { it.copy(working = "Abrindo no Spotify…") }
            try {
                val slice = list.drop(start).take(40)
                val ids = spotifyIds(slice).mapNotNull { it.second }
                if (ids.isEmpty()) throw IllegalStateException("Não achei essas músicas no Spotify.")
                spotify.playTracks(ids)
                val missing = slice.size - ids.size
                if (missing > 0) _messages.tryEmit("$missing música(s) não estão no Spotify e ficaram de fora.")
            } catch (e: Exception) {
                _messages.tryEmit(e.message ?: "Não consegui tocar no Spotify.")
            } finally {
                _ui.update { it.copy(working = null) }
            }
        }
    }

    private suspend fun ensurePlaylist(): String {
        prefs.recoPlaylistId?.let { if (spotify.playlistExists(it)) return it }
        val id = spotify.createPlaylist("Sintonia · Descobertas", "Músicas que eu curti nas recomendações do app Sintonia.")
        prefs.recoPlaylistId = id
        _ui.update { it.copy(playlistId = id) }
        return id
    }

    private suspend fun addToPlaylist(list: List<RecoTrack>, quiet: Boolean = false) {
        try {
            val pid = ensurePlaylist()
            val ids = spotifyIds(list).mapNotNull { it.second }
            if (ids.isNotEmpty()) spotify.addToPlaylist(pid, ids)
            if (!quiet) _messages.tryEmit("${ids.size} música(s) salvas na playlist \"Sintonia · Descobertas\".")
        } catch (e: SpotifyException) {
            if (e.message == SpotifyClient.NEEDS_RELOGIN) _ui.update { it.copy(canEditPlaylists = false) }
            if (!quiet || e.message == SpotifyClient.NEEDS_RELOGIN) _messages.tryEmit(e.message ?: "Não consegui salvar a playlist.")
        }
    }

    fun saveAllLikes() {
        viewModelScope.launch {
            _ui.update { it.copy(working = "Salvando no Spotify…") }
            addToPlaylist(store.data.likes)
            _ui.update { it.copy(working = null) }
        }
    }

    fun openPlaylist() {
        val id = prefs.recoPlaylistId ?: return
        val ctx = getApplication<Application>()
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("spotify:playlist:$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun setAutoPlaylist(on: Boolean) {
        prefs.autoPlaylist = on
        _ui.update { it.copy(autoPlaylist = on) }
    }

    /** Depois de um novo login, confere de novo se pode mexer em playlists. */
    fun refreshPermissions() {
        _ui.update { it.copy(canEditPlaylists = SpotifyAuth.canEditPlaylists(prefs.grantedScopes)) }
    }

    /** Só para pré-visualização/testes: preenche os cartões sem buscar na internet. */
    @androidx.annotation.VisibleForTesting
    fun setDeckForPreview(list: List<RecoTrack>) = _ui.update { it.copy(deck = list) }

    override fun onCleared() {
        preview.stop()
        super.onCleared()
    }
}
