package com.github.dbarbosapn.pizzinotta.ui.nav

import java.util.UUID

object Routes {
    const val LIBRARY = "library"
    const val PLAYLISTS = "playlists"
    const val ARG_PLAYLIST_ID = "playlistId"

    fun playlistDetailRoute(playlistId: UUID): String = "playlistDetail/$playlistId"

    fun readerRoute(playlistId: UUID): String = "reader/$playlistId"
}
