package com.github.dbarbosapn.pizzinotta.ui.library

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.github.dbarbosapn.pizzinotta.PizzinottaApplication
import com.github.dbarbosapn.pizzinotta.data.import.AndroidPdfValidator
import com.github.dbarbosapn.pizzinotta.data.import.PdfImporter
import com.github.dbarbosapn.pizzinotta.data.storage.PdfFileStorage
import java.io.File

class LibraryViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val app = application as PizzinottaApplication
        val importer = PdfImporter(
            songRepository = app.songRepository,
            pdfValidator = AndroidPdfValidator(),
            tempDir = app.cacheDir,
            destinationDir = File(app.filesDir, "pdf"),
        )
        return LibraryViewModel(
            songRepository = app.songRepository,
            playlistRepository = app.playlistRepository,
            fileStorage = PdfFileStorage(),
            importer = importer,
        ) as T
    }
}
