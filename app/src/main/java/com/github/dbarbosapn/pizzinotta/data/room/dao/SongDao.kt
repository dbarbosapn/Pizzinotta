package com.github.dbarbosapn.pizzinotta.data.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.github.dbarbosapn.pizzinotta.data.room.entity.SongEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY displayTitle COLLATE NOCASE ASC")
    fun observeByTitle(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY lastOpenedAt IS NULL, lastOpenedAt DESC")
    fun observeByRecentlyOpened(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE displayTitle LIKE '%' || :query || '%' COLLATE NOCASE")
    fun search(query: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id")
    fun observeById(id: UUID): Flow<SongEntity>

    @Query("SELECT COUNT(*) > 0 FROM songs WHERE contentHash = :contentHash")
    suspend fun existsByContentHash(contentHash: String): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(song: SongEntity)

    @Update
    suspend fun update(song: SongEntity)

    @Delete
    suspend fun delete(song: SongEntity)
}
