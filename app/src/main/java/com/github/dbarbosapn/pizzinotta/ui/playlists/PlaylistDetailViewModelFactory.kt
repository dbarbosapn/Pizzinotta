package com.github.dbarbosapn.pizzinotta.ui.playlists

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.github.dbarbosapn.pizzinotta.PizzinottaApplication
import java.util.UUID

class PlaylistDetailViewModelFactory(
    private val playlistId: UUID,
    private val application: Application,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val app = application as PizzinottaApplication
        return PlaylistDetailViewModel(playlistId, app.playlistRepository) as T
    }
}
