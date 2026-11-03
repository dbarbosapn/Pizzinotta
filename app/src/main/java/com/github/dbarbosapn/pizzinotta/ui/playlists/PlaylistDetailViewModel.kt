package com.github.dbarbosapn.pizzinotta.ui.playlists

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import com.github.dbarbosapn.pizzinotta.data.domain.Playlist
import com.github.dbarbosapn.pizzinotta.data.domain.PlaylistEntry
import com.github.dbarbosapn.pizzinotta.data.domain.PlaylistSong
import com.github.dbarbosapn.pizzinotta.data.repository.PlaylistRepository
import java.util.UUID

data class PlaylistDetailUiState(
    val playlist: Playlist? = null,
    val songs: List<PlaylistSong> = emptyList(),
) {
    val isEmpty: Boolean get() = songs.isEmpty()
}

class PlaylistDetailViewModel(
    private val playlistId: UUID,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistDetailUiState())
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    // Display order of songs. Seeded from the repository and updated synchronously on reorder so
    // the drag library always sees a consistent list (avoids snap-back during rapid swaps).
    private val displaySongs = MutableStateFlow<List<PlaylistSong>>(emptyList())

    private var currentPlaylist: Playlist? = null

    private val uiStateScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        uiStateScope.launch {
            try {
                playlistRepository.observeById(playlistId).collect { playlist ->
                    currentPlaylist = playlist
                    _uiState.value = PlaylistDetailUiState(playlist, displaySongs.value)
                }
            } catch (e: IllegalStateException) {
                // The playlist was deleted out from under us; the single-row query returned no row.
                currentPlaylist = null
                _uiState.value = PlaylistDetailUiState(null, displaySongs.value)
            }
        }
        uiStateScope.launch {
            playlistRepository.observePlaylistSongs(playlistId).collect { songs ->
                displaySongs.value = songs
                _uiState.value = PlaylistDetailUiState(currentPlaylist, songs)
            }
        }
    }

    override fun onCleared() {
        uiStateScope.cancel()
    }

    suspend fun removeSong(id: UUID): Int {
        val songs = playlistRepository.observePlaylistSongs(playlistId).firstOrNull() ?: return 0
        val song = songs.firstOrNull { it.song.id == id } ?: return 0
        playlistRepository.deleteEntry(PlaylistEntry(playlistId, id, song.position))
        return 1
    }

    suspend fun removeAll(): Int {
        val count = playlistRepository.countEntries(playlistId)
        playlistRepository.removeAllEntries(playlistId)
        return count
    }

    // Applies the reorder to the displayed list synchronously (so dragging stays smooth and
    // consistent) and persists the new order in the background.
    fun reorder(from: Int, to: Int) {
        val ids = displaySongs.value.map { it.song.id }
        if (from in ids.indices && to in ids.indices && from != to) {
            val orderedIds = ids.toMutableList().apply { add(to, removeAt(from)) }
            val reordered = orderedIds.mapNotNull { id -> displaySongs.value.firstOrNull { it.song.id == id } }
            displaySongs.value = reordered
            _uiState.value = PlaylistDetailUiState(currentPlaylist, reordered)
            uiStateScope.launch { playlistRepository.setOrder(playlistId, orderedIds) }
        }
    }

    suspend fun rename(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isNotEmpty()) {
            currentPlaylist?.let { playlist ->
                playlistRepository.update(
                    playlist.copy(name = trimmed, updatedAt = System.currentTimeMillis()),
                )
            }
        }
    }

    suspend fun delete() {
        currentPlaylist?.let { playlistRepository.delete(it) }
    }
}
