package com.github.dbarbosapn.pizzinotta.data.repository

import com.github.dbarbosapn.pizzinotta.data.domain.Song
import com.github.dbarbosapn.pizzinotta.data.domain.SongOrder
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface SongRepository {
    fun observeAll(order: SongOrder): Flow<List<Song>>
    fun observeById(id: UUID): Flow<Song>
    fun observeSearch(query: String): Flow<List<Song>>
    suspend fun existsByContentHash(contentHash: String): Boolean
    suspend fun findSongByContentHash(contentHash: String): Song?
    suspend fun insert(song: Song)
    suspend fun update(song: Song)
    suspend fun delete(song: Song)
}
