package com.github.dbarbosapn.pizzinotta.ui.nav

import java.util.UUID

object Routes {
    const val LIBRARY = "library"
    const val PLAYLISTS = "playlists"

    fun playlistDetailRoute(playlistId: UUID): String = "playlistDetail/$playlistId"

    fun readerRoute(playlistId: UUID, startIndex: Int = 0): String = "reader/$playlistId/$startIndex"
}
