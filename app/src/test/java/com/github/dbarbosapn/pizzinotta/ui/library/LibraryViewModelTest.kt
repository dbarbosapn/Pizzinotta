package com.github.dbarbosapn.pizzinotta.ui.library

import com.github.dbarbosapn.pizzinotta.data.domain.Playlist
import com.github.dbarbosapn.pizzinotta.data.domain.PlaylistEntry
import com.github.dbarbosapn.pizzinotta.data.domain.Song
import com.github.dbarbosapn.pizzinotta.data.domain.SongOrder
import com.github.dbarbosapn.pizzinotta.data.import.PdfImporter
import com.github.dbarbosapn.pizzinotta.data.import.PdfValidator
import com.github.dbarbosapn.pizzinotta.data.repository.PlaylistRepository
import com.github.dbarbosapn.pizzinotta.data.repository.SongRepository
import com.github.dbarbosapn.pizzinotta.data.storage.FileStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.UUID

class LibraryViewModelTest {

    private class FakeSongRepository : SongRepository {
        val songs = mutableListOf<Song>()

        override fun observeAll(order: SongOrder): Flow<List<Song>> =
            flowOf(songs.sortedWith(byOrder(order)).toList())

        override fun observeById(id: UUID): Flow<Song> = flowOf(songs.first { it.id == id })

        override fun observeSearch(query: String): Flow<List<Song>> =
            flowOf(songs.filter { it.displayTitle.contains(query, ignoreCase = true) })

        override suspend fun existsByContentHash(contentHash: String) =
            songs.any { it.contentHash == contentHash }

        override suspend fun findSongByContentHash(contentHash: String) =
            songs.firstOrNull { it.contentHash == contentHash }

        override suspend fun insert(song: Song) {
            songs.add(song)
        }

        override suspend fun update(song: Song) {
            val index = songs.indexOfFirst { it.id == song.id }
            if (index >= 0) songs[index] = song
        }

        override suspend fun delete(song: Song) {
            songs.remove(song)
        }
    }

    private class FakePlaylistRepository : PlaylistRepository {
        val entries = mutableListOf<PlaylistEntry>()
        val deletedSongs = mutableSetOf<UUID>()

        override fun observeAll(): Flow<List<Playlist>> = flowOf(emptyList())

        override fun observeById(id: UUID): Flow<Playlist> =
            flowOf(Playlist(id = id, name = "P", createdAt = 0L, updatedAt = 0L))

        override suspend fun insert(playlist: Playlist) {}
        override suspend fun update(playlist: Playlist) {}
        override suspend fun delete(playlist: Playlist) {}

        override fun observeEntries(playlistId: UUID): Flow<List<PlaylistEntry>> =
            flowOf(entries.filter { it.playlistId == playlistId })

        override suspend fun hasEntry(playlistId: UUID, songId: UUID) =
            entries.any { it.playlistId == playlistId && it.songId == songId }

        override suspend fun insertEntry(entry: PlaylistEntry) {
            entries.add(entry)
        }

        override suspend fun deleteEntry(entry: PlaylistEntry) {
            entries.remove(entry)
        }

        override suspend fun deleteEntriesForSong(songId: UUID) {
            entries.removeAll { it.songId == songId }
            deletedSongs.add(songId)
        }

        override suspend fun setOrder(playlistId: UUID, songIds: List<UUID>) {}
    }

    private class FakeFileStorage : FileStorage {
        val removed = mutableListOf<String>()
        override suspend fun remove(path: String): Boolean {
            removed.add(path)
            return true
        }
    }

    private class FakePdfValidator : PdfValidator {
        override suspend fun pageCount(file: File): Int = 1
    }

    private class Harness(
        val viewModel: LibraryViewModel,
        val songRepo: FakeSongRepository,
        val playlistRepo: FakePlaylistRepository,
        val storage: FakeFileStorage,
    )

    private fun newSong(title: String, hash: String = "$title-hash"): Song =
        Song(
            id = UUID.randomUUID(),
            contentHash = hash,
            displayTitle = title,
            originalFilename = "$title.pdf",
            pageCount = 3,
            addedAt = 0L,
            lastOpenedAt = null,
            lastPage = 0,
            filePath = "/files/$title.pdf",
        )

    private fun newViewModel(): Harness {
        val songRepo = FakeSongRepository()
        val playlistRepo = FakePlaylistRepository()
        val storage = FakeFileStorage()
        val base = Files.createTempDirectory("vm-test").toFile()
        val importer = PdfImporter(
            songRepository = songRepo,
            pdfValidator = FakePdfValidator(),
            tempDir = base,
            destinationDir = File(base, "pdf"),
        )
        val viewModel = LibraryViewModel(songRepo, playlistRepo, storage, importer)
        return Harness(viewModel, songRepo, playlistRepo, storage)
    }

    @Test
    fun `toggle selection adds and removes a single song`() {
        val vm = newViewModel()
        val id = UUID.randomUUID()

        vm.viewModel.toggleSelection(id)
        assertEquals(setOf(id), vm.viewModel.selection.value)

        vm.viewModel.toggleSelection(id)
        assertEquals(emptySet<UUID>(), vm.viewModel.selection.value)
    }

    @Test
    fun `clear selection empties the set`() {
        val vm = newViewModel()
        vm.viewModel.toggleSelection(UUID.randomUUID())
        vm.viewModel.toggleSelection(UUID.randomUUID())

        vm.viewModel.clearSelection()

        assertEquals(emptySet<UUID>(), vm.viewModel.selection.value)
    }

    @Test
    fun `rename updates the display title and trims whitespace`() = runBlocking {
        val vm = newViewModel()
        val song = newSong("Canon in D")
        vm.songRepo.insert(song)

        vm.viewModel.rename(song, "  Canon in D  ")

        assertEquals("Canon in D", vm.songRepo.songs.single().displayTitle)
    }

    @Test
    fun `rename ignores a blank title`() = runBlocking {
        val vm = newViewModel()
        val song = newSong("Canon in D")
        vm.songRepo.insert(song)

        vm.viewModel.rename(song, "   ")

        assertEquals("Canon in D", vm.songRepo.songs.single().displayTitle)
    }

    @Test
    fun `deleteSelected removes songs from the library playlists and disk`() = runBlocking {
        val vm = newViewModel()
        val a = newSong("A")
        val b = newSong("B")
        vm.songRepo.insert(a)
        vm.songRepo.insert(b)
        vm.playlistRepo.entries.addAll(
            listOf(
                PlaylistEntry(playlistId = UUID.randomUUID(), songId = a.id, position = 0),
                PlaylistEntry(playlistId = UUID.randomUUID(), songId = b.id, position = 1),
            )
        )
        vm.viewModel.toggleSelection(a.id)
        vm.viewModel.toggleSelection(b.id)

        val deleted = vm.viewModel.deleteSelected()

        assertEquals(2, deleted)
        assertTrue(vm.songRepo.songs.isEmpty())
        assertEquals(setOf(a.id, b.id), vm.playlistRepo.deletedSongs)
        assertEquals(setOf(a.filePath, b.filePath), vm.storage.removed.toSet())
        assertEquals(emptySet<UUID>(), vm.viewModel.selection.value)
    }

    @Test
    fun `deleteSelected with an empty selection returns zero and changes nothing`() = runBlocking {
        val vm = newViewModel()
        vm.songRepo.insert(newSong("A"))

        val deleted = vm.viewModel.deleteSelected()

        assertEquals(0, deleted)
        assertTrue(vm.songRepo.songs.isNotEmpty())
        assertTrue(vm.storage.removed.isEmpty())
    }
}

private fun byOrder(order: SongOrder): Comparator<Song> = when (order) {
    SongOrder.TITLE -> compareBy { it.displayTitle.lowercase() }
    SongOrder.RECENTLY_OPENED -> compareByDescending { it.lastOpenedAt ?: Long.MIN_VALUE }
}
