package com.github.dbarbosapn.pizzinotta.data.import

import com.github.dbarbosapn.pizzinotta.data.domain.Song
import com.github.dbarbosapn.pizzinotta.data.domain.SongOrder
import com.github.dbarbosapn.pizzinotta.data.repository.SongRepository
import io.legere.pdfiumandroid.api.PdfPasswordException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID

class PdfImporterTest {

    private class FakeSongRepository : SongRepository {
        val songs = mutableListOf<Song>()
        override fun observeAll(order: SongOrder): Flow<List<Song>> = flowOf(songs.toList())
        override fun observeById(id: UUID): Flow<Song> = flowOf(songs.first { it.id == id })
        override fun observeSearch(query: String): Flow<List<Song>> = flowOf(emptyList())
        override suspend fun existsByContentHash(contentHash: String) = songs.any { it.contentHash == contentHash }
        override suspend fun findSongByContentHash(contentHash: String) = songs.firstOrNull { it.contentHash == contentHash }
        override suspend fun insert(song: Song) { songs.add(song) }
        override suspend fun update(song: Song) { val i = songs.indexOfFirst { it.id == song.id }; if (i >= 0) songs[i] = song }
        override suspend fun delete(song: Song) { songs.remove(song) }
    }

    private class FakePdfValidator : PdfValidator {
        var pages = 3
        var error: Throwable? = null
        override suspend fun pageCount(file: File): Int {
            error?.let { throw it }
            return pages
        }
    }

    private class FakeImportSource(
        override val displayName: String,
        private val bytes: ByteArray?,
    ) : ImportSource {
        override fun openStream() = bytes?.let { ByteArrayInputStream(it) }
    }

    private fun tempDirs(): Pair<File, File> {
        val base = Files.createTempDirectory("import-test").toFile()
        return base to File(base, "pdf")
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHexString()

    @Test
    fun `strips the pdf extension from the display title`() {
        assertEquals("Canon in D", displayTitleFromFilename("Canon in D.pdf"))
    }

    @Test
    fun `imports a valid pdf and inserts the song with derived fields`() = runBlocking {
        val (tempDir, destDir) = tempDirs()
        val bytes = "fake-pdf-bytes".toByteArray()
        val repo = FakeSongRepository()
        val validator = FakePdfValidator().apply { pages = 5 }

        val results = PdfImporter(repo, validator, tempDir, destDir)
            .importSources(listOf(FakeImportSource("Music.pdf", bytes)))
            .toList()

        assertEquals(1, results.size)
        val imported = results[0] as ImportResult.Imported
        val song = repo.songs.single()
        assertEquals("Music", song.displayTitle)
        assertEquals("Music.pdf", song.originalFilename)
        assertEquals(5, song.pageCount)
        assertEquals(sha256(bytes), song.contentHash)
        assertEquals(imported.songId, song.id)
        assertEquals(File(destDir, "${song.id}.pdf").absolutePath, song.filePath)
    }

    @Test
    fun `treats an already-present file as a duplicate without writing anything`() = runBlocking {
        val (tempDir, destDir) = tempDirs()
        val bytes = "duplicate-bytes".toByteArray()
        val repo = FakeSongRepository().apply {
            songs.add(Song(UUID.randomUUID(), sha256(bytes), "Existing", "Existing.pdf", 2, 0, null, "/x/Existing.pdf"))
        }

        val results = PdfImporter(repo, FakePdfValidator().apply { pages = 4 }, tempDir, destDir)
            .importSources(listOf(FakeImportSource("Existing.pdf", bytes)))
            .toList()

        assertEquals(1, results.size)
        val duplicate = results[0] as ImportResult.Duplicate
        assertEquals(duplicate.songId, repo.songs.single().id)
        assertTrue(repo.songs.size == 1)

        @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        assertTrue(destDir.listFiles()?.isEmpty() ?: true)
    }

    @Test
    fun `rejects a zero-page pdf without storing anything`() = runBlocking {
        val (tempDir, destDir) = tempDirs()
        val repo = FakeSongRepository()

        val results = PdfImporter(repo, FakePdfValidator().apply { pages = 0 }, tempDir, destDir)
            .importSources(listOf(FakeImportSource("Empty.pdf", "x".toByteArray())))
            .toList()

        val failure = results[0] as ImportResult.Failure
        assertEquals(ImportResult.Failure.Reason.ZERO_PAGES, failure.reason)
        assertTrue(repo.songs.isEmpty())

        @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        assertTrue(destDir.listFiles()?.isEmpty() ?: true)
    }

    @Test
    fun `maps a password-protected error to PASSWORD_PROTECTED`() = runBlocking {
        val (tempDir, destDir) = tempDirs()
        val repo = FakeSongRepository()

        val results = PdfImporter(repo, FakePdfValidator().apply { error = PdfPasswordException() }, tempDir, destDir)
            .importSources(listOf(FakeImportSource("Locked.pdf", "x".toByteArray())))
            .toList()

        val failure = results[0] as ImportResult.Failure
        assertEquals(ImportResult.Failure.Reason.PASSWORD_PROTECTED, failure.reason)
        assertTrue(repo.songs.isEmpty())
    }

    @Test
    fun `maps a corrupt error to CORRUPT`() = runBlocking {
        val (tempDir, destDir) = tempDirs()
        val repo = FakeSongRepository()

        val results = PdfImporter(repo, FakePdfValidator().apply { error = IOException("boom") }, tempDir, destDir)
            .importSources(listOf(FakeImportSource("Broken.pdf", "x".toByteArray())))
            .toList()

        val failure = results[0] as ImportResult.Failure
        assertEquals(ImportResult.Failure.Reason.CORRUPT, failure.reason)
        assertTrue(repo.songs.isEmpty())
    }

    @Test
    fun `reports an unreadable source as IO_ERROR and stores nothing`() = runBlocking {
        val (tempDir, destDir) = tempDirs()
        val repo = FakeSongRepository()

        val results = PdfImporter(repo, FakePdfValidator(), tempDir, destDir)
            .importSources(listOf(FakeImportSource("Missing.pdf", null)))
            .toList()

        val failure = results[0] as ImportResult.Failure
        assertEquals(ImportResult.Failure.Reason.IO_ERROR, failure.reason)
        assertTrue(repo.songs.isEmpty())
    }

    @Test
    fun `deletes the temp file after a successful import`() = runBlocking {
        val (tempDir, destDir) = tempDirs()
        val repo = FakeSongRepository()

        PdfImporter(repo, FakePdfValidator().apply { pages = 3 }, tempDir, destDir)
            .importSources(listOf(FakeImportSource("Ok.pdf", "x".toByteArray())))
            .toList()

        assertTrue(leftoverTempFiles(tempDir).isEmpty())
    }

    @Test
    fun `deletes the temp file after a rejected import`() = runBlocking {
        val (tempDir, destDir) = tempDirs()
        val repo = FakeSongRepository()

        PdfImporter(repo, FakePdfValidator().apply { pages = 0 }, tempDir, destDir)
            .importSources(listOf(FakeImportSource("Ok.pdf", "x".toByteArray())))
            .toList()

        assertTrue(leftoverTempFiles(tempDir).isEmpty())
    }

    private fun leftoverTempFiles(dir: File): List<String> =
        dir.listFiles { _, name -> name.startsWith("import-") }?.map { it.name } ?: emptyList()
}
