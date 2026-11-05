package com.github.dbarbosapn.pizzinotta.data.repository

import com.github.dbarbosapn.pizzinotta.data.domain.Playlist
import com.github.dbarbosapn.pizzinotta.data.domain.PlaylistEntry
import com.github.dbarbosapn.pizzinotta.data.domain.PlaylistSong
import com.github.dbarbosapn.pizzinotta.data.room.projection.PlaylistCount
import com.github.dbarbosapn.pizzinotta.data.room.projection.SongPlaylistName
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface PlaylistRepository {
    fun observeAll(): Flow<List<Playlist>>
    fun observeById(id: UUID): Flow<Playlist>
    suspend fun insert(playlist: Playlist)
    suspend fun update(playlist: Playlist)
    suspend fun delete(playlist: Playlist)

    fun observeEntries(playlistId: UUID): Flow<List<PlaylistEntry>>
    fun observePlaylistSongs(playlistId: UUID): Flow<List<PlaylistSong>>
    fun observeCounts(): Flow<List<PlaylistCount>>
    fun observeSongsWithPlaylists(): Flow<List<SongPlaylistName>>
    suspend fun countEntries(playlistId: UUID): Int
    suspend fun hasEntry(playlistId: UUID, songId: UUID): Boolean
    suspend fun insertEntry(entry: PlaylistEntry)
    suspend fun deleteEntry(entry: PlaylistEntry)
    suspend fun removeAllEntries(playlistId: UUID)
    suspend fun setOrder(playlistId: UUID, songIds: List<UUID>)
    suspend fun deleteEntriesForSong(songId: UUID)
}
