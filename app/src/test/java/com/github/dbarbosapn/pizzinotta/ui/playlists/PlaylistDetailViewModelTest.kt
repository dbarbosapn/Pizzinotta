package com.github.dbarbosapn.pizzinotta.ui.playlists

import com.github.dbarbosapn.pizzinotta.data.domain.Playlist
import com.github.dbarbosapn.pizzinotta.data.domain.PlaylistEntry
import com.github.dbarbosapn.pizzinotta.data.domain.PlaylistSong
import com.github.dbarbosapn.pizzinotta.data.domain.Song
import com.github.dbarbosapn.pizzinotta.data.repository.PlaylistRepository
import com.github.dbarbosapn.pizzinotta.data.room.projection.PlaylistCount
import com.github.dbarbosapn.pizzinotta.data.room.projection.SongPlaylistName
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

class PlaylistDetailViewModelTest {

    private class FakePlaylistRepository : PlaylistRepository {
        val playlistSongs = mutableListOf<PlaylistSong>()
        val deletedIds = mutableSetOf<UUID>()

        override fun observeAll(): Flow<List<Playlist>> = flowOf(emptyList())

        override fun observeById(id: UUID): Flow<Playlist> =
            flowOf(Playlist(id = id, name = "P", createdAt = 0L, updatedAt = 0L))

        override suspend fun insert(playlist: Playlist) {}
        override suspend fun update(playlist: Playlist) {}
        override suspend fun delete(playlist: Playlist) {}

        override fun observeEntries(playlistId: UUID): Flow<List<PlaylistEntry>> = flowOf(emptyList())

        override fun observePlaylistSongs(playlistId: UUID): Flow<List<PlaylistSong>> =
            flowOf(playlistSongs.toList())

        override fun observeCounts(): Flow<List<PlaylistCount>> = flowOf(emptyList())

        override fun observeSongsWithPlaylists(): Flow<List<SongPlaylistName>> = flowOf(emptyList())

        override suspend fun countEntries(playlistId: UUID): Int = playlistSongs.size

        override suspend fun hasEntry(playlistId: UUID, songId: UUID) =
            playlistSongs.any { it.song.id == songId }

        override suspend fun insertEntry(entry: PlaylistEntry) {}

        override suspend fun deleteEntry(entry: PlaylistEntry) {
            playlistSongs.removeAll { it.song.id == entry.songId }
            deletedIds.add(entry.songId)
        }

        override suspend fun removeAllEntries(playlistId: UUID) {
            playlistSongs.clear()
        }

        override suspend fun setOrder(playlistId: UUID, songIds: List<UUID>) {}

        override suspend fun deleteEntriesForSong(songId: UUID) {
            playlistSongs.removeAll { it.song.id == songId }
            deletedIds.add(songId)
        }
    }

    private class Harness(val viewModel: PlaylistDetailViewModel, val repo: FakePlaylistRepository)

    private fun newSong(title: String): Song =
        Song(
            id = UUID.randomUUID(),
            contentHash = "$title-hash",
            displayTitle = title,
            originalFilename = "$title.pdf",
            pageCount = 3,
            addedAt = 0L,
            lastOpenedAt = null,
            filePath = "/files/$title.pdf",
        )

    private fun newHarness(songs: List<Song>): Harness {
        val repo = FakePlaylistRepository()
        repo.playlistSongs += songs.mapIndexed { index, song -> PlaylistSong(song, index) }
        return Harness(PlaylistDetailViewModel(UUID.randomUUID(), repo), repo)
    }

    @Test
    fun `removeSong deletes a single song and reports it removed`() = runBlocking {
        val a = newSong("A")
        val b = newSong("B")
        val vm = newHarness(listOf(a, b))

        val removed = vm.viewModel.removeSong(a.id)

        assertEquals(1, removed)
        assertEquals(setOf(a.id), vm.repo.deletedIds)
        assertEquals(listOf(b), vm.repo.playlistSongs.map { it.song })
    }

    @Test
    fun `removeSong returns zero for an unknown song`() = runBlocking {
        val a = newSong("A")
        val vm = newHarness(listOf(a))

        val removed = vm.viewModel.removeSong(UUID.randomUUID())

        assertEquals(0, removed)
        assertTrue(vm.repo.deletedIds.isEmpty())
        assertEquals(listOf(a), vm.repo.playlistSongs.map { it.song })
    }

    @Test
    fun `removeAll clears every song and reports the count`() = runBlocking {
        val a = newSong("A")
        val b = newSong("B")
        val c = newSong("C")
        val vm = newHarness(listOf(a, b, c))

        val removed = vm.viewModel.removeAll()

        assertEquals(3, removed)
        assertTrue(vm.repo.playlistSongs.isEmpty())
    }

    @Test
    fun `removeAll with an empty playlist returns zero`() = runBlocking {
        val vm = newHarness(emptyList())

        val removed = vm.viewModel.removeAll()

        assertEquals(0, removed)
        assertTrue(vm.repo.playlistSongs.isEmpty())
    }

    private class DeletingPlaylistRepository : PlaylistRepository {
        override fun observeAll(): Flow<List<Playlist>> = flowOf(emptyList())

        override fun observeById(id: UUID): Flow<Playlist> = throw IllegalStateException(
            "The query result was empty, but expected a single row",
        )

        override suspend fun insert(playlist: Playlist) {}
        override suspend fun update(playlist: Playlist) {}
        override suspend fun delete(playlist: Playlist) {}

        override fun observeEntries(playlistId: UUID): Flow<List<PlaylistEntry>> = flowOf(emptyList())

        override fun observePlaylistSongs(playlistId: UUID): Flow<List<PlaylistSong>> =
            flowOf(emptyList())

        override fun observeCounts(): Flow<List<PlaylistCount>> = flowOf(emptyList())

        override fun observeSongsWithPlaylists(): Flow<List<SongPlaylistName>> = flowOf(emptyList())

        override suspend fun countEntries(playlistId: UUID): Int = 0

        override suspend fun hasEntry(playlistId: UUID, songId: UUID) = false

        override suspend fun insertEntry(entry: PlaylistEntry) {}

        override suspend fun deleteEntry(entry: PlaylistEntry) {}

        override suspend fun removeAllEntries(playlistId: UUID) {}

        override suspend fun setOrder(playlistId: UUID, songIds: List<UUID>) {}

        override suspend fun deleteEntriesForSong(songId: UUID) {}
    }

    @Test
    fun `deleting the playlist does not crash when observeById returns no row`() = runBlocking {
        val id = UUID.randomUUID()
        val repo = DeletingPlaylistRepository()
        val vm = PlaylistDetailViewModel(id, repo)

        // Give the init collector a chance to observe the empty/single-row failure.
        delay(100.milliseconds)

        assertEquals(null, vm.uiState.value.playlist)
    }
}
