package com.github.dbarbosapn.pizzinotta.data.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.github.dbarbosapn.pizzinotta.data.room.entity.PlaylistEntity
import com.github.dbarbosapn.pizzinotta.data.room.entity.PlaylistEntryEntity
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
