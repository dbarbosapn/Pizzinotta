package com.github.dbarbosapn.pizzinotta.ui.nav

import com.github.dbarbosapn.pizzinotta.data.domain.SongOrder
import java.net.URLEncoder
import java.util.UUID

object Routes {
    const val LIBRARY = "library"
    const val PLAYLISTS = "playlists"

    fun playlistDetailRoute(playlistId: UUID): String = "playlistDetail/$playlistId"

    fun readerRoute(playlistId: UUID, startIndex: Int = 0): String = "reader/$playlistId/$startIndex"

    fun libraryReaderRoute(startIndex: Int, query: String, order: SongOrder): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return "readerLibrary/$startIndex/$encoded/${order.name}"
    }
}
