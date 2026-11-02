package com.github.dbarbosapn.pizzinotta

import android.app.Application
import com.github.dbarbosapn.pizzinotta.data.repository.PlaylistRepository
import com.github.dbarbosapn.pizzinotta.data.repository.RoomPlaylistRepository
import com.github.dbarbosapn.pizzinotta.data.repository.RoomSongRepository
import com.github.dbarbosapn.pizzinotta.data.repository.SongRepository
import com.github.dbarbosapn.pizzinotta.data.room.AppDatabase

class PizzinottaApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.create(this) }

    val songRepository: SongRepository by lazy { RoomSongRepository(database.songDao()) }

    val playlistRepository: PlaylistRepository by lazy {
        RoomPlaylistRepository(database.playlistDao())
    }
}
