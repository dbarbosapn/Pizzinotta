package com.github.dbarbosapn.pizzinotta.data.domain

data class SongQueue(
    val songs: List<Song>,
    val position: Int = 0,
) {
    val size: Int get() = songs.size
    val isEmpty: Boolean get() = songs.isEmpty()
    val current: Song? get() = songs.getOrNull(position)
    val isFirst: Boolean get() = position <= 0
    val isLast: Boolean get() = position >= songs.size - 1

    fun songAt(index: Int): Song? = songs.getOrNull(index)

    fun goTo(index: Int): SongQueue = copy(position = when {
        songs.isEmpty() -> 0
        index < 0 -> 0
        index >= songs.size -> songs.size - 1
        else -> index
    })

    fun next(): SongQueue = if (isLast) this else copy(position = position + 1)

    fun previous(): SongQueue = if (isFirst) this else copy(position = position - 1)
}
