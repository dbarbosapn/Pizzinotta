package com.github.dbarbosapn.pizzinotta.ui.playlists

import androidx.lifecycle.ViewModel
import com.github.dbarbosapn.pizzinotta.data.domain.Playlist
import com.github.dbarbosapn.pizzinotta.data.repository.PlaylistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID

data class PlaylistsUiState(
    val playlists: List<Playlist> = emptyList(),
    val counts: Map<UUID, Int> = emptyMap(),
) {
    val isEmpty: Boolean get() = playlists.isEmpty()
    fun countFor(id: UUID): Int = counts[id] ?: 0
}

class PlaylistsViewModel(
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistsUiState())
    val uiState: StateFlow<PlaylistsUiState> = _uiState.asStateFlow()

    private val uiStateScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        uiStateScope.launch {
            combine(
                playlistRepository.observeAll(),
                playlistRepository.observeCounts(),
            ) { playlists, counts ->
                PlaylistsUiState(playlists = playlists, counts = counts.associate { it.playlistId to it.count })
            }.collect { _uiState.value = it }
        }
    }

    override fun onCleared() {
        uiStateScope.cancel()
    }

    suspend fun create(name: String): Playlist? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val now = System.currentTimeMillis()
        val playlist = Playlist(id = UUID.randomUUID(), name = trimmed, createdAt = now, updatedAt = now)
        playlistRepository.insert(playlist)
        return playlist
    }
}
