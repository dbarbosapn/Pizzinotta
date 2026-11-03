package com.github.dbarbosapn.pizzinotta.ui.library

import androidx.lifecycle.ViewModel
import com.github.dbarbosapn.pizzinotta.data.domain.Song
import com.github.dbarbosapn.pizzinotta.data.domain.SongOrder
import com.github.dbarbosapn.pizzinotta.data.import.ImportResult
import com.github.dbarbosapn.pizzinotta.data.import.ImportSource
import com.github.dbarbosapn.pizzinotta.data.import.PdfImporter
import com.github.dbarbosapn.pizzinotta.data.repository.PlaylistRepository
import com.github.dbarbosapn.pizzinotta.data.repository.SongRepository
import com.github.dbarbosapn.pizzinotta.data.storage.FileStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

data class LibraryUiState(
    val songs: List<Song> = emptyList(),
    val order: SongOrder = SongOrder.TITLE,
    val searchQuery: String = "",
    val searching: Boolean = false,
    val selection: Set<UUID> = emptySet(),
) {
    val multiSelecting: Boolean get() = selection.isNotEmpty()
    val isEmpty: Boolean get() = songs.isEmpty() && searchQuery.isBlank()
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
    private val fileStorage: FileStorage,
    private val importer: PdfImporter,
) : ViewModel() {

    private val order = MutableStateFlow(SongOrder.TITLE)
    private val searchQueryRaw = MutableStateFlow("")
    private val searchExpanded = MutableStateFlow(false)
    private val _selection = MutableStateFlow<Set<UUID>>(emptySet())
    val selection: StateFlow<Set<UUID>> = _selection.asStateFlow()

    private val debouncedQuery = searchQueryRaw.debounce(300.milliseconds)

    val songs: Flow<List<Song>> =
        combine(order, debouncedQuery) { currentOrder, query -> currentOrder to query }
            .flatMapMerge { (currentOrder, query) ->
                if (query.isBlank()) songRepository.observeAll(currentOrder)
                else songRepository.observeSearch(query)
            }

    private val _uiState = MutableStateFlow(LibraryUiState())

    private val uiStateScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        uiStateScope.launch {
            combine(order, searchQueryRaw, _selection, songs) { orderValue, query, selected, songList ->
                LibraryUiState(
                    songs = songList,
                    order = orderValue,
                    searchQuery = query,
                    searching = searchExpanded.value,
                    selection = selected,
                )
            }.collect { _uiState.value = it }
        }
    }

    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    override fun onCleared() {
        uiStateScope.cancel()
    }

    fun setOrder(value: SongOrder) {
        order.value = value
    }

    fun enterSearch() {
        searchExpanded.value = true
    }

    fun exitSearch() {
        searchExpanded.value = false
        searchQueryRaw.value = ""
    }

    fun setQuery(query: String) {
        searchQueryRaw.value = query
    }

    fun toggleSelection(id: UUID) {
        val current = _selection.value
        _selection.value = if (id in current) current - id else current + id
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    suspend fun rename(song: Song, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isNotEmpty()) {
            songRepository.update(song.copy(displayTitle = trimmed))
        }
    }

    suspend fun deleteSelected(): Int {
        val ids = _selection.value.toList()
        if (ids.isEmpty()) return 0
        for (id in ids) {
            songRepository.observeById(id).firstOrNull()?.let { removeSong(it) }
        }
        _selection.value = emptySet()
        return ids.size
    }

    suspend fun deleteSong(song: Song) {
        removeSong(song)
    }

    private suspend fun removeSong(song: Song) {
        playlistRepository.deleteEntriesForSong(song.id)
        songRepository.delete(song)
        fileStorage.remove(song.filePath)
    }

    fun importSources(sources: List<ImportSource>): Flow<ImportResult> =
        importer.importSources(sources)
}
