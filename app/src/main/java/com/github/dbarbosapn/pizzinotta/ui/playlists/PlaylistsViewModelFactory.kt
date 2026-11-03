package com.github.dbarbosapn.pizzinotta.ui.playlists

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.github.dbarbosapn.pizzinotta.PizzinottaApplication

class PlaylistsViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val app = application as PizzinottaApplication
        return PlaylistsViewModel(app.playlistRepository) as T
    }
}
