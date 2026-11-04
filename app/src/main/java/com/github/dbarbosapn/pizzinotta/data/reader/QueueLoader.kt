package com.github.dbarbosapn.pizzinotta.data.reader

import com.github.dbarbosapn.pizzinotta.data.domain.Song
import com.github.dbarbosapn.pizzinotta.data.domain.SongOrder
import com.github.dbarbosapn.pizzinotta.data.room.dao.PlaylistDao
import com.github.dbarbosapn.pizzinotta.data.room.dao.SongDao
import com.github.dbarbosapn.pizzinotta.data.room.entity.SongEntity
import java.util.UUID

/**
 * Lazily resolves the songs of a reader queue on demand by index, so a queue can contain far more
 * songs than fit in memory. Playlist queues resolve from ordered entries; library queues resolve
 * from the applied search filter and sort order.
 */
interface QueueLoader {
    suspend fun size(): Int
    suspend fun get(index: Int): Song?
}

class PlaylistQueueLoader(
    private val dao: PlaylistDao,
    private val playlistId: UUID,
) : QueueLoader {
    override suspend fun size(): Int = dao.countEntries(playlistId)

    override suspend fun get(index: Int): Song? =
        dao.songAt(playlistId, index)?.toDomain()
}

class LibraryQueueLoader(
    private val dao: SongDao,
    query: String,
    private val order: SongOrder,
) : QueueLoader {
    private val query: String = query.trim()
    private val hasQuery: Boolean = query.isNotBlank()

    override suspend fun size(): Int =
        if (hasQuery) dao.searchCount(query) else dao.totalCount()

    override suspend fun get(index: Int): Song? = when {
        hasQuery && order == SongOrder.TITLE -> dao.searchByTitleAt(query, index)?.toDomain()
        hasQuery && order == SongOrder.RECENTLY_OPENED -> dao.searchByRecentlyOpenedAt(query, index)?.toDomain()
        !hasQuery && order == SongOrder.TITLE -> dao.titleAt(index)?.toDomain()
        else -> dao.recentlyOpenedAt(index)?.toDomain()
    }
}

private fun SongEntity.toDomain(): Song = Song(
    id = id,
    contentHash = contentHash,
    displayTitle = displayTitle,
    originalFilename = originalFilename,
    pageCount = pageCount,
    addedAt = addedAt,
    lastOpenedAt = lastOpenedAt,
    filePath = filePath,
)
