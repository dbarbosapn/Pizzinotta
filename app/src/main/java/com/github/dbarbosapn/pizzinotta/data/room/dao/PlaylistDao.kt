package com.github.dbarbosapn.pizzinotta.data.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.github.dbarbosapn.pizzinotta.data.room.entity.PlaylistEntity
import com.github.dbarbosapn.pizzinotta.data.room.entity.PlaylistEntryEntity
import com.github.dbarbosapn.pizzinotta.data.room.projection.PlaylistCount
import com.github.dbarbosapn.pizzinotta.data.room.projection.PlaylistSongProjection
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observeById(id: UUID): Flow<PlaylistEntity>

    @Insert
    suspend fun insert(playlist: PlaylistEntity)

    @Update
    suspend fun update(playlist: PlaylistEntity)

    @Delete
    suspend fun delete(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlist_entries WHERE playlistId = :playlistId ORDER BY position ASC")
    fun observeEntries(playlistId: UUID): Flow<List<PlaylistEntryEntity>>

    @Query("SELECT playlistId AS playlistId, CAST(COUNT(*) AS INTEGER) AS count FROM playlist_entries GROUP BY playlistId")
    fun observeCounts(): Flow<List<PlaylistCount>>

    @Query(
        "SELECT playlist_entries.position AS position, songs.* " +
            "FROM playlist_entries " +
            "INNER JOIN songs ON playlist_entries.songId = songs.id " +
            "WHERE playlist_entries.playlistId = :playlistId " +
            "ORDER BY playlist_entries.position ASC",
    )
    fun observePlaylistSongs(playlistId: UUID): Flow<List<PlaylistSongProjection>>

    @Query("DELETE FROM playlist_entries WHERE playlistId = :playlistId")
    suspend fun deleteEntriesForPlaylist(playlistId: UUID)

    @Query("SELECT COUNT(*) FROM playlist_entries WHERE playlistId = :playlistId")
    suspend fun countEntries(playlistId: UUID): Int

    @Query("SELECT COUNT(*) > 0 FROM playlist_entries WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun hasEntry(playlistId: UUID, songId: UUID): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntry(entry: PlaylistEntryEntity)

    @Delete
    suspend fun deleteEntry(entry: PlaylistEntryEntity)

    @Query("DELETE FROM playlist_entries WHERE songId = :songId")
    suspend fun deleteEntriesForSong(songId: UUID)

    @Query("UPDATE playlist_entries SET position = :position WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun setPosition(playlistId: UUID, songId: UUID, position: Int)
}
