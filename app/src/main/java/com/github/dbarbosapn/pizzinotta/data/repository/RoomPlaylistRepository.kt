package com.github.dbarbosapn.pizzinotta.data.repository

import androidx.room.Transaction
import com.github.dbarbosapn.pizzinotta.data.domain.Playlist
import com.github.dbarbosapn.pizzinotta.data.domain.PlaylistEntry
import com.github.dbarbosapn.pizzinotta.data.domain.PlaylistSong
import com.github.dbarbosapn.pizzinotta.data.domain.Song
import com.github.dbarbosapn.pizzinotta.data.room.dao.PlaylistDao
import com.github.dbarbosapn.pizzinotta.data.room.entity.PlaylistEntity
import com.github.dbarbosapn.pizzinotta.data.room.entity.PlaylistEntryEntity
import com.github.dbarbosapn.pizzinotta.data.room.entity.SongEntity
import com.github.dbarbosapn.pizzinotta.data.room.projection.PlaylistCount
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
    @Transaction
    override suspend fun delete(playlist: Playlist) {
        dao.deleteEntriesForPlaylist(playlist.id)
        dao.delete(playlist.toEntity())
    }

    override fun observeEntries(playlistId: UUID): Flow<List<PlaylistEntry>> =
        dao.observeEntries(playlistId).map { list -> list.map { entity -> entity.toDomain() } }

    override fun observePlaylistSongs(playlistId: UUID): Flow<List<PlaylistSong>> =
        dao.observePlaylistSongs(playlistId).map { projections ->
            projections.map { projection -> PlaylistSong(song = projection.song.toDomain(), position = projection.position) }
        }

    override fun observeCounts(): Flow<List<PlaylistCount>> = dao.observeCounts()

    override suspend fun countEntries(playlistId: UUID): Int =
        dao.countEntries(playlistId)

    override suspend fun hasEntry(playlistId: UUID, songId: UUID): Boolean =
        dao.hasEntry(playlistId, songId)

    override suspend fun insertEntry(entry: PlaylistEntry) = dao.insertEntry(entry.toEntity())
    override suspend fun deleteEntry(entry: PlaylistEntry) = dao.deleteEntry(entry.toEntity())
    override suspend fun removeAllEntries(playlistId: UUID) = dao.deleteEntriesForPlaylist(playlistId)
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

    private fun SongEntity.toDomain(): Song = Song(
        id = id,
        contentHash = contentHash,
        displayTitle = displayTitle,
        originalFilename = originalFilename,
        pageCount = pageCount,
        addedAt = addedAt,
        lastOpenedAt = lastOpenedAt,
        filePath = filePath
    )
}
