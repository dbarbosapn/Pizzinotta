package com.github.dbarbosapn.pizzinotta.data.import

import com.github.dbarbosapn.pizzinotta.data.domain.Song
import com.github.dbarbosapn.pizzinotta.data.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

class PdfImporter(
    private val songRepository: SongRepository,
    private val pdfValidator: PdfValidator,
    private val tempDir: File,
    private val destinationDir: File,
) {
    fun importSources(sources: List<ImportSource>): Flow<ImportResult> = flow {
        for (source in sources) {
            emit(processOne(source))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun processOne(source: ImportSource): ImportResult {
        val filename = source.displayName
        val temp = File(tempDir, "import-${UUID.randomUUID()}.pdf")
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            source.openStream()?.use { input ->
                FileOutputStream(temp).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        digest.update(buffer, 0, read)
                        out.write(buffer, 0, read)
                    }
                }
            } ?: return ImportResult.Failure(
                filename,
                ImportResult.Failure.Reason.IO_ERROR,
                "Couldn't read this file",
            )
            val hash = digest.digest().toHexString()
            songRepository.findSongByContentHash(hash)?.let { existing ->
                ImportResult.Duplicate(existing.id, filename)
            } ?: importValid(hash, temp, filename)
        } catch (_: io.legere.pdfiumandroid.api.PdfPasswordException) {
            ImportResult.Failure(
                filename,
                ImportResult.Failure.Reason.PASSWORD_PROTECTED,
                "Password-protected PDFs can't be imported",
            )
        } catch (_: IOException) {
            ImportResult.Failure(
                filename,
                ImportResult.Failure.Reason.CORRUPT,
                "This file is not a valid PDF",
            )
        } catch (e: Exception) {
            ImportResult.Failure(
                filename,
                ImportResult.Failure.Reason.IO_ERROR,
                e.message ?: "Couldn't import this file",
            )
        } finally {
            if (!temp.delete()) temp.deleteOnExit()
        }
    }

    private suspend fun importValid(hash: String, temp: File, filename: String): ImportResult {
        val pageCount = pdfValidator.pageCount(temp)
        if (pageCount <= 0) {
            return ImportResult.Failure(
                filename,
                ImportResult.Failure.Reason.ZERO_PAGES,
                "This PDF has no pages",
            )
        }
        val id = UUID.randomUUID()
        destinationDir.mkdirs()
        val destinationFile = File(destinationDir, "$id.pdf")
        withContext(Dispatchers.IO) {
            FileInputStream(temp).use { source ->
                FileOutputStream(destinationFile).use { target -> source.copyTo(target) }
            }
        }
        songRepository.insert(
            Song(
                id = id,
                contentHash = hash,
                displayTitle = displayTitleFromFilename(filename),
                originalFilename = filename,
                pageCount = pageCount,
                addedAt = System.currentTimeMillis(),
                lastOpenedAt = null,
                lastPage = 0,
                filePath = destinationFile.absolutePath,
            )
        )
        return ImportResult.Imported(id, filename)
    }
}
