package com.github.dbarbosapn.pizzinotta.data.repository

import com.github.dbarbosapn.pizzinotta.data.domain.Playlist
import com.github.dbarbosapn.pizzinotta.data.domain.PlaylistEntry
import com.github.dbarbosapn.pizzinotta.data.room.dao.PlaylistDao
import com.github.dbarbosapn.pizzinotta.data.room.entity.PlaylistEntity
import com.github.dbarbosapn.pizzinotta.data.room.projection.PlaylistCount
import com.github.dbarbosapn.pizzinotta.data.room.projection.PlaylistSongProjection
import com.github.dbarbosapn.pizzinotta.data.room.entity.PlaylistEntryEntity
import com.github.dbarbosapn.pizzinotta.data.room.entity.SongEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class PlaylistRepositoryTest {

    private class FakePlaylistDao : PlaylistDao {
        private val playlists = mutableListOf<PlaylistEntity>()
        private val entries = mutableListOf<PlaylistEntryEntity>()
        private val songs = mutableListOf<SongEntity>()

        override fun observeAll(): Flow<List<PlaylistEntity>> = flowOf(playlists.toList())

        override fun observeById(id: UUID): Flow<PlaylistEntity> =
            flowOf(playlists.first { it.id == id })

        override suspend fun insert(playlist: PlaylistEntity) {
            playlists.add(playlist)
        }

        override suspend fun update(playlist: PlaylistEntity) {
            val idx = playlists.indexOfFirst { it.id == playlist.id }
            if (idx >= 0) playlists[idx] = playlist
        }

        override suspend fun delete(playlist: PlaylistEntity) {
            playlists.remove(playlist)
        }

        override fun observeEntries(playlistId: UUID): Flow<List<PlaylistEntryEntity>> =
            flowOf(entries.filter { it.playlistId == playlistId }.sortedBy { it.position })

        override suspend fun hasEntry(playlistId: UUID, songId: UUID): Boolean =
            entries.any { it.playlistId == playlistId && it.songId == songId }

        override suspend fun insertEntry(entry: PlaylistEntryEntity) {
            entries.add(entry)
        }

        override suspend fun deleteEntry(entry: PlaylistEntryEntity) {
            entries.remove(entry)
        }

        override suspend fun deleteEntriesForSong(songId: UUID) {
            entries.removeAll { it.songId == songId }
        }

        override suspend fun setPosition(playlistId: UUID, songId: UUID, position: Int) {
            val idx = entries.indexOfFirst { it.playlistId == playlistId && it.songId == songId }
            if (idx >= 0) entries[idx] = entries[idx].copy(position = position)
        }

        override fun observePlaylistSongs(playlistId: UUID): Flow<List<PlaylistSongProjection>> =
            flowOf(emptyList())

        override fun observeCounts(): Flow<List<PlaylistCount>> =
            flowOf(emptyList())

        override suspend fun deleteEntriesForPlaylist(playlistId: UUID) {
            entries.removeAll { it.playlistId == playlistId }
        }

        override suspend fun countEntries(playlistId: UUID): Int =
            entries.count { it.playlistId == playlistId }

        override suspend fun songAt(playlistId: UUID, offset: Int): SongEntity? {
            val ordered = entries.filter { it.playlistId == playlistId }.sortedBy { it.position }
            return ordered.getOrNull(offset)?.let { entry -> songs.firstOrNull { it.id == entry.songId } }
        }
    }

    private fun newPlaylist(name: String) =
        Playlist(id = UUID.randomUUID(), name = name, createdAt = 0, updatedAt = 0)

    private fun newEntry(playlistId: UUID, songId: UUID, position: Int) =
        PlaylistEntry(playlistId = playlistId, songId = songId, position = position)

    @Test
    fun `setOrder assigns ascending positions and observeEntries returns them in order`() = runBlocking {
        val repo = RoomPlaylistRepository(FakePlaylistDao())
        val playlist = newPlaylist("Setlist")
        repo.insert(playlist)

        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val c = UUID.randomUUID()
        repo.insertEntry(newEntry(playlist.id, a, 0))
        repo.insertEntry(newEntry(playlist.id, b, 1))
        repo.insertEntry(newEntry(playlist.id, c, 2))
        repo.setOrder(playlist.id, listOf(c, a, b))

        val ordered = repo.observeEntries(playlist.id).first().map { it.songId }

        assertEquals(listOf(c, a, b), ordered)
    }

    @Test
    fun `hasEntry tracks insertions and deletions`() = runBlocking {
        val repo = RoomPlaylistRepository(FakePlaylistDao())
        val playlist = newPlaylist("Setlist")
        repo.insert(playlist)
        val song = UUID.randomUUID()

        assertFalse(repo.hasEntry(playlist.id, song))

        repo.insertEntry(newEntry(playlist.id, song, 0))
        assertTrue(repo.hasEntry(playlist.id, song))

        repo.deleteEntry(newEntry(playlist.id, song, 0))
        assertFalse(repo.hasEntry(playlist.id, song))
    }

    @Test
    fun `deleteEntriesForSong removes the song from every playlist`() = runBlocking {
        val repo = RoomPlaylistRepository(FakePlaylistDao())
        val one = newPlaylist("One")
        val two = newPlaylist("Two")
        repo.insert(one)
        repo.insert(two)
        val song = UUID.randomUUID()
        repo.insertEntry(newEntry(one.id, song, 0))
        repo.insertEntry(newEntry(two.id, song, 0))

        repo.deleteEntriesForSong(song)

        assertFalse(repo.hasEntry(one.id, song))
        assertFalse(repo.hasEntry(two.id, song))
    }
}
