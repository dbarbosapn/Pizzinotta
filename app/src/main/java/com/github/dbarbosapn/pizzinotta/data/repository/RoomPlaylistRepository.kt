package com.github.dbarbosapn.pizzinotta.data.repository

import androidx.room.Transaction
import com.github.dbarbosapn.pizzinotta.data.domain.Playlist
import com.github.dbarbosapn.pizzinotta.data.domain.PlaylistEntry
import com.github.dbarbosapn.pizzinotta.data.room.dao.PlaylistDao
import com.github.dbarbosapn.pizzinotta.data.room.entity.PlaylistEntity
import com.github.dbarbosapn.pizzinotta.data.room.entity.PlaylistEntryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class RoomPlaylistRepository(private val dao: PlaylistDao) : PlaylistRepository {

    override fun observeAll(): Flow<List<Playlist>> =
        dao.observeAll().map { list -> list.map { entity -> entity.toDomain() } }

    override fun observeById(id: UUID): Flow<Playlist> =
        dao.observeById(id).map { it.toDomain() }

    override suspend fun insert(playlist: Playlist) = dao.insert(playlist.toEntity())
    override suspend fun update(playlist: Playlist) = dao.update(playlist.toEntity())
    override suspend fun delete(playlist: Playlist) = dao.delete(playlist.toEntity())

    override fun observeEntries(playlistId: UUID): Flow<List<PlaylistEntry>> =
        dao.observeEntries(playlistId).map { list -> list.map { entity -> entity.toDomain() } }

    override suspend fun hasEntry(playlistId: UUID, songId: UUID): Boolean =
        dao.hasEntry(playlistId, songId)

    override suspend fun insertEntry(entry: PlaylistEntry) = dao.insertEntry(entry.toEntity())
    override suspend fun deleteEntry(entry: PlaylistEntry) = dao.deleteEntry(entry.toEntity())
    override suspend fun deleteEntriesForSong(songId: UUID) = dao.deleteEntriesForSong(songId)

    @Transaction
    override suspend fun setOrder(playlistId: UUID, songIds: List<UUID>) {
        songIds.forEachIndexed { index, songId -> dao.setPosition(playlistId, songId, index) }
    }

    private fun PlaylistEntity.toDomain(): Playlist = Playlist(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun Playlist.toEntity(): PlaylistEntity = PlaylistEntity(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun PlaylistEntryEntity.toDomain(): PlaylistEntry = PlaylistEntry(
        playlistId = playlistId,
        songId = songId,
        position = position
    )

    private fun PlaylistEntry.toEntity(): PlaylistEntryEntity = PlaylistEntryEntity(
        playlistId = playlistId,
        songId = songId,
        position = position
    )
}
