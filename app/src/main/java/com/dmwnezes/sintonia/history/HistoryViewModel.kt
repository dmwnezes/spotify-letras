package com.dmwnezes.sintonia.history

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HistoryUi(
    val loaded: Boolean = false,          // terminou de ler o que estava salvo
    val stats: HistoryStats? = null,
    val importing: Boolean = false,
    val importedCount: Int = 0,
    val selected: String = "all",
    val message: String? = null,
)

class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val store = HistoryStore(app)
    private val importer = HistoryImporter(app.contentResolver)

    private val _ui = MutableStateFlow(HistoryUi())
    val ui = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { store.load() }
            _ui.update { it.copy(loaded = true, stats = saved) }
        }
    }

    fun select(key: String) = _ui.update { it.copy(selected = key) }

    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _ui.update { it.copy(importing = true, importedCount = 0, message = null) }
        viewModelScope.launch {
            try {
                val stats = withContext(Dispatchers.IO) {
                    importer.import(uris) { n -> _ui.update { it.copy(importedCount = n) } }
                        .also(store::save)
                }
                _ui.update {
                    it.copy(
                        importing = false, stats = stats, selected = "all",
                        message = "Histórico importado: ${fmtInt(stats.totalEntries)} reproduções lidas.",
                    )
                }
            } catch (e: HistoryImportException) {
                _ui.update { it.copy(importing = false, message = e.message) }
            } catch (e: Exception) {
                _ui.update { it.copy(importing = false, message = "Não consegui ler o arquivo (${e.javaClass.simpleName}).") }
            }
        }
    }

    /** Texto para o compartilhar do Android (ex.: mandar para o app do Claude). */
    fun summaryText(): String? = _ui.value.stats?.let(HistorySummary::build)

    fun saveBackup(target: Uri) {
        val stats = _ui.value.stats ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(target, "wt")?.use {
                        it.write(HistoryStore.toJson(stats).toByteArray())
                    } ?: error("sem acesso")
                }.isSuccess
            }
            _ui.update { it.copy(message = if (ok) "Backup salvo." else "Não consegui salvar o backup.") }
        }
    }

    fun clear() {
        store.clear()
        _ui.update { HistoryUi(loaded = true, message = "Histórico apagado do celular.") }
    }

    fun clearMessage() = _ui.update { it.copy(message = null) }
}
