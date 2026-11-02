package com.github.dbarbosapn.pizzinotta.data.repository

import com.github.dbarbosapn.pizzinotta.data.domain.Song
import com.github.dbarbosapn.pizzinotta.data.domain.SongOrder
import com.github.dbarbosapn.pizzinotta.data.room.dao.SongDao
import com.github.dbarbosapn.pizzinotta.data.room.entity.SongEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SongRepositoryTest {

    private class FakeSongDao : SongDao {
        private val songs = mutableListOf<SongEntity>()

        override fun observeByTitle(): Flow<List<SongEntity>> =
            flowOf(songs.sortedWith(compareBy { it.displayTitle.lowercase() }).toList())

        override fun observeByRecentlyOpened(): Flow<List<SongEntity>> =
            flowOf(songs.sortedByDescending { it.lastOpenedAt ?: Long.MIN_VALUE }.toList())

        override fun search(query: String): Flow<List<SongEntity>> =
            flowOf(songs.filter { it.displayTitle.contains(query, ignoreCase = true) })

        override fun observeById(id: UUID): Flow<SongEntity> =
            flowOf(songs.first { it.id == id })

        override suspend fun existsByContentHash(contentHash: String): Boolean =
            songs.any { it.contentHash == contentHash }

        override suspend fun findSongByContentHash(contentHash: String): SongEntity? =
            songs.firstOrNull { it.contentHash == contentHash }

        override suspend fun insert(song: SongEntity) {
            songs.add(song)
        }

        override suspend fun update(song: SongEntity) {
            val idx = songs.indexOfFirst { it.id == song.id }
            if (idx >= 0) songs[idx] = song
        }

        override suspend fun delete(song: SongEntity) {
            songs.remove(song)
        }
    }

    private fun newSong(title: String, hash: String = "$title-hash"): Song =
        Song(
            id = UUID.randomUUID(),
            contentHash = hash,
            displayTitle = title,
            originalFilename = "$title.pdf",
            pageCount = 3,
            addedAt = 0,
            lastOpenedAt = null,
            lastPage = 0,
            filePath = "/files/$title.pdf"
        )

    @Test
    fun `insert maps entities to domain and orders by title case-insensitively`() = runBlocking {
        val repo = RoomSongRepository(FakeSongDao())
        repo.insert(newSong("Banana"))
        repo.insert(newSong("apple"))

        val titles = repo.observeAll(SongOrder.TITLE).first().map { it.displayTitle }

        assertEquals(listOf("apple", "Banana"), titles)
    }

    @Test
    fun `existsByContentHash is exposed through the repository`() = runBlocking {
        val repo = RoomSongRepository(FakeSongDao())
        val song = newSong("Piece", "abc123")
        repo.insert(song)

        assertTrue(repo.existsByContentHash("abc123"))
        assertFalse(repo.existsByContentHash("missing"))
    }

    @Test
    fun `findSongByContentHash returns the matching song or null`() = runBlocking {
        val repo = RoomSongRepository(FakeSongDao())
        val song = newSong("Bach", "bach-hash")
        repo.insert(song)

        assertEquals(song.id, repo.findSongByContentHash("bach-hash")?.id)
        assertNull(repo.findSongByContentHash("does-not-exist"))
    }

    @Test
    fun `update reflects changes in observeById`() = runBlocking {
        val repo = RoomSongRepository(FakeSongDao())
        val song = newSong("Old")
        repo.insert(song)

        repo.update(song.copy(displayTitle = "New", lastPage = 4))

        val updated = repo.observeById(song.id).first()
        assertEquals("New", updated.displayTitle)
        assertEquals(4, updated.lastPage)
    }

    @Test
    fun `search filters by case-insensitive title substring`() = runBlocking {
        val repo = RoomSongRepository(FakeSongDao())
        repo.insert(newSong("Canon in D"))
        repo.insert(newSong("Minuet in C"))

        val results = repo.observeSearch("canon").first().map { it.displayTitle }

        assertEquals(listOf("Canon in D"), results)
    }

    @Test
    fun `delete removes the song from observeAll`() = runBlocking {
        val repo = RoomSongRepository(FakeSongDao())
        val song = newSong("Temporarily")
        repo.insert(song)

        repo.delete(song)

        assertTrue(repo.observeAll(SongOrder.TITLE).first().isEmpty())
    }
}
