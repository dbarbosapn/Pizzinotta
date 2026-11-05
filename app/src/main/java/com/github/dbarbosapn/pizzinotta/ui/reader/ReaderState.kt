package com.github.dbarbosapn.pizzinotta.ui.reader

import com.github.dbarbosapn.pizzinotta.data.domain.Song

/**
 * The single source of truth for reader navigation. It carries only what the boundary logic needs:
 * queue awareness, the current song (which supplies title + page count), and the visible bar. All
 * derived helpers live here so both the controller and the UI can reason about boundaries without
 * duplicating the math.
 */
data class ReaderState(
    val queueSize: Int = 0,
    val songIndex: Int = 0,
    val pageIndex: Int = 0,
    val currentSong: Song? = null,
    val barVisible: Boolean = false,
) {
    val pageCount: Int get() = currentSong?.pageCount ?: 0
    val title: String get() = currentSong?.displayTitle ?: ""

    val isFirstPage: Boolean get() = pageIndex <= 0
    val isLastPage: Boolean get() = pageCount > 0 && pageIndex >= pageCount - 1

    val isFirstSong: Boolean get() = songIndex <= 0
    val isLastSong: Boolean get() = queueSize > 0 && songIndex >= queueSize - 1

    /** Whether there is more than one song, i.e. whether song-level navigation is meaningful. */
    val hasQueue: Boolean get() = queueSize > 1
}
