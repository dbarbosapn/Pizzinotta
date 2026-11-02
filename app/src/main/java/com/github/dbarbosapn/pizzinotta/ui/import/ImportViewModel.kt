package com.github.dbarbosapn.pizzinotta.ui.import

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.github.dbarbosapn.pizzinotta.PizzinottaApplication
import com.github.dbarbosapn.pizzinotta.data.import.AndroidPdfValidator
import com.github.dbarbosapn.pizzinotta.data.import.ImportResult
import com.github.dbarbosapn.pizzinotta.data.import.ImportSource
import com.github.dbarbosapn.pizzinotta.data.import.PdfImporter
import kotlinx.coroutines.flow.Flow
import java.io.File

class ImportViewModel(app: Application) : AndroidViewModel(app) {
    private val application = app as PizzinottaApplication
    private val importer = PdfImporter(
        songRepository = application.songRepository,
        pdfValidator = AndroidPdfValidator(),
        tempDir = app.cacheDir,
        destinationDir = File(app.filesDir, "pdf"),
    )

    fun importSources(sources: List<ImportSource>): Flow<ImportResult> =
        importer.importSources(sources)
}

class ImportViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ImportViewModel::class.java)) {
            "Unknown ViewModel class"
        }
        return ImportViewModel(application) as T
    }
}
