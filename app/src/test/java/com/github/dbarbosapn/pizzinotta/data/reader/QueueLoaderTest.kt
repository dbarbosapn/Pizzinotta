package com.github.dbarbosapn.pizzinotta.data.reader

import com.github.dbarbosapn.pizzinotta.data.domain.SongOrder
import com.github.dbarbosapn.pizzinotta.data.room.dao.PlaylistDao
import com.github.dbarbosapn.pizzinotta.data.room.dao.SongDao
import com.github.dbarbosapn.pizzinotta.data.room.entity.PlaylistEntity
import com.github.dbarbosapn.pizzinotta.data.room.entity.PlaylistEntryEntity
import com.github.dbarbosapn.pizzinotta.data.room.entity.SongEntity
import com.github.dbarbosapn.pizzinotta.data.room.projection.PlaylistCount
import com.github.dbarbosapn.pizzinotta.data.room.projection.PlaylistSongProjection
import com.github.dbarbosapn.pizzinotta.data.room.projection.SongPlaylistName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class QueueLoaderTest {

    private fun songEntity(
        title: String,
        pageCount: Int = 1,
        lastOpenedAt: Long? = null,
    ) = SongEntity(
        id = UUID.nameUUIDFromBytes(title.toByteArray()),
        contentHash = "hash-$title",
        displayTitle = title,
        originalFilename = "$title.pdf",
        pageCount = pageCount,
        addedAt = 0,
        lastOpenedAt = lastOpenedAt,
        filePath = "path/$title.pdf",
    )

    private class FakeSongDao(var songs: List<SongEntity>) : SongDao {
        private fun byTitle() = songs.sortedBy { it.displayTitle.uppercase() }
        private fun byRecent() = songs.sortedWith(compareByDescending<SongEntity> { it.lastOpenedAt }.thenBy { it.displayTitle.uppercase() })
        private fun filtered(query: String) = songs.filter { it.displayTitle.contains(query, ignoreCase = true) }

        override fun observeByTitle(): Flow<List<SongEntity>> = flowOf(byTitle())
        override fun observeByRecentlyOpened(): Flow<List<SongEntity>> = flowOf(byRecent())
        override fun search(query: String): Flow<List<SongEntity>> = flowOf(filtered(query))
        override fun observeById(id: UUID): Flow<SongEntity> = flowOf(songs.first { it.id == id })

        override suspend fun totalCount(): Int = songs.size
        override suspend fun titleAt(offset: Int): SongEntity? = byTitle().getOrNull(offset)
        override suspend fun recentlyOpenedAt(offset: Int): SongEntity? = byRecent().getOrNull(offset)
        override suspend fun searchCount(query: String): Int = filtered(query).size
        override suspend fun searchByTitleAt(query: String, offset: Int): SongEntity? = byTitleIntersect(
            filtered(query)
        ).getOrNull(offset)
        override suspend fun searchByRecentlyOpenedAt(query: String, offset: Int): SongEntity? = byRecentIntersect(
            filtered(query)
        ).getOrNull(offset)

        private fun byTitleIntersect(matched: List<SongEntity>) = byTitle().filter { it in matched }
        private fun byRecentIntersect(matched: List<SongEntity>) = byRecent().filter { it in matched }

        override suspend fun existsByContentHash(contentHash: String): Boolean = songs.any { it.contentHash == contentHash }
        override suspend fun findSongByContentHash(contentHash: String): SongEntity? = songs.firstOrNull { it.contentHash == contentHash }
        override suspend fun insert(song: SongEntity) { songs = songs + song }
        override suspend fun update(song: SongEntity) { songs = songs.map { if (it.id == song.id) song else it } }
        override suspend fun delete(song: SongEntity) { songs = songs.filterNot { it.id == song.id } }
    }

    private class FakePlaylistDao(var allSongs: List<SongEntity>) : PlaylistDao {
        val entryIds: MutableList<UUID> = mutableListOf()

        override fun observeAll(): Flow<List<PlaylistEntity>> = flowOf(emptyList())
        override fun observeById(id: UUID): Flow<PlaylistEntity> = flowOf(PlaylistEntity(id, "P", 0, 0))
        override suspend fun insert(playlist: PlaylistEntity) {}
        override suspend fun update(playlist: PlaylistEntity) {}
        override suspend fun delete(playlist: PlaylistEntity) {}
        override fun observeEntries(playlistId: UUID): Flow<List<PlaylistEntryEntity>> = flowOf(emptyList())
        override fun observeCounts(): Flow<List<PlaylistCount>> = flowOf(emptyList())
        override fun observeSongsWithPlaylists(): Flow<List<SongPlaylistName>> = flowOf(emptyList())
        override fun observePlaylistSongs(playlistId: UUID): Flow<List<PlaylistSongProjection>> = flowOf(emptyList())
        override suspend fun deleteEntriesForPlaylist(playlistId: UUID) { entryIds.clear() }
        override suspend fun countEntries(playlistId: UUID): Int = entryIds.size
        override suspend fun songAt(playlistId: UUID, offset: Int): SongEntity? =
            entryIds.getOrNull(offset)?.let { id -> allSongs.firstOrNull { it.id == id } }
        override suspend fun hasEntry(playlistId: UUID, songId: UUID): Boolean = songId in entryIds
        override suspend fun insertEntry(entry: PlaylistEntryEntity) { entryIds.add(entry.songId) }
        override suspend fun deleteEntry(entry: PlaylistEntryEntity) { entryIds.remove(entry.songId) }
        override suspend fun deleteEntriesForSong(songId: UUID) { entryIds.remove(songId) }
        override suspend fun setPosition(playlistId: UUID, songId: UUID, position: Int) {}
    }

    @Test
    fun `library loader returns songs ordered by title when there is no query`() = runBlocking {
        val dao = FakeSongDao(listOf(songEntity("Chord Sheet"), songEntity("Ave Maria"), songEntity("Bach Invention")))
        val loader = LibraryQueueLoader(dao, query = "", order = SongOrder.TITLE)

        assertEquals(3, loader.size())
        assertEquals("Ave Maria", loader.get(0)?.displayTitle)
        assertEquals("Bach Invention", loader.get(1)?.displayTitle)
        assertEquals("Chord Sheet", loader.get(2)?.displayTitle)
    }

    @Test
    fun `library loader filters by title substring ignoring case`() = runBlocking {
        val dao = FakeSongDao(
            listOf(
                songEntity("Canon"),
                songEntity("Canon in D"),
                songEntity("Bach"),
            ),
        )
        val loader = LibraryQueueLoader(dao, query = "canon", order = SongOrder.TITLE)

        assertEquals(2, loader.size())
        assertEquals("Canon", loader.get(0)?.displayTitle)
        assertEquals("Canon in D", loader.get(1)?.displayTitle)
    }

    @Test
    fun `library loader orders by recently opened when selected`() = runBlocking {
        val dao = FakeSongDao(
            listOf(
                songEntity("Old", lastOpenedAt = 100),
                songEntity("Fresh", lastOpenedAt = 300),
                songEntity("Mid", lastOpenedAt = 200),
            ),
        )
        val loader = LibraryQueueLoader(dao, query = "", order = SongOrder.RECENTLY_OPENED)

        assertEquals("Fresh", loader.get(0)?.displayTitle)
        assertEquals("Mid", loader.get(1)?.displayTitle)
        assertEquals("Old", loader.get(2)?.displayTitle)
    }

    @Test
    fun `library loader returns null for an out-of-range index`() = runBlocking {
        val dao = FakeSongDao(listOf(songEntity("Only")))
        val loader = LibraryQueueLoader(dao, query = "", order = SongOrder.TITLE)

        assertEquals(null, loader.get(5))
    }

    @Test
    fun `playlist loader returns songs in entry order`() = runBlocking {
        val a = songEntity("Alpha")
        val b = songEntity("Beta")
        val c = songEntity("Gamma")
        val dao = FakePlaylistDao(allSongs = listOf(a, b, c))
        dao.entryIds.clear()
        dao.entryIds.addAll(listOf(c.id, a.id, b.id))
        val loader = PlaylistQueueLoader(dao, UUID.nameUUIDFromBytes("playlist".toByteArray()))

        assertEquals(3, loader.size())
        assertEquals("Gamma", loader.get(0)?.displayTitle)
        assertEquals("Alpha", loader.get(1)?.displayTitle)
        assertEquals("Beta", loader.get(2)?.displayTitle)
    }

    @Test
    fun `playlist loader reports zero for an empty playlist`() = runBlocking {
        val dao = FakePlaylistDao(emptyList())
        val loader = PlaylistQueueLoader(dao, UUID.randomUUID())

        assertEquals(0, loader.size())
        assertEquals(null, loader.get(0))
    }
}
