package com.github.dbarbosapn.pizzinotta.data.repository

import com.github.dbarbosapn.pizzinotta.data.domain.Song
import com.github.dbarbosapn.pizzinotta.data.domain.SongOrder
import com.github.dbarbosapn.pizzinotta.data.room.dao.SongDao
import com.github.dbarbosapn.pizzinotta.data.room.entity.SongEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class RoomSongRepository(private val dao: SongDao) : SongRepository {

    override fun observeAll(order: SongOrder): Flow<List<Song>> = when (order) {
        SongOrder.TITLE -> dao.observeByTitle()
        SongOrder.RECENTLY_OPENED -> dao.observeByRecentlyOpened()
    }.map { list -> list.map { entity -> entity.toDomain() } }

    override fun observeById(id: UUID): Flow<Song> =
        dao.observeById(id).map { it.toDomain() }

    override fun observeSearch(query: String): Flow<List<Song>> =
        dao.search(query).map { list -> list.map { entity -> entity.toDomain() } }

    override suspend fun existsByContentHash(contentHash: String): Boolean =
        dao.existsByContentHash(contentHash)

    override suspend fun findSongByContentHash(contentHash: String): Song? =
        dao.findSongByContentHash(contentHash)?.toDomain()

    override suspend fun insert(song: Song) = dao.insert(song.toEntity())
    override suspend fun update(song: Song) = dao.update(song.toEntity())
    override suspend fun delete(song: Song) = dao.delete(song.toEntity())

    private fun SongEntity.toDomain(): Song = Song(
        id = id,
        contentHash = contentHash,
        displayTitle = displayTitle,
        originalFilename = originalFilename,
        pageCount = pageCount,
        addedAt = addedAt,
        lastOpenedAt = lastOpenedAt,
        lastPage = lastPage,
        filePath = filePath
    )

    private fun Song.toEntity(): SongEntity = SongEntity(
        id = id,
        contentHash = contentHash,
        displayTitle = displayTitle,
        originalFilename = originalFilename,
        pageCount = pageCount,
        addedAt = addedAt,
        lastOpenedAt = lastOpenedAt,
        lastPage = lastPage,
        filePath = filePath
    )
}
