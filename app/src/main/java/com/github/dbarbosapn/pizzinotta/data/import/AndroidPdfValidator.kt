package com.github.dbarbosapn.pizzinotta.data.import

import io.legere.pdfiumandroid.api.Config
import io.legere.pdfiumandroid.suspend.PdfiumCoreKt
import kotlinx.coroutines.Dispatchers
import java.io.File
import android.os.ParcelFileDescriptor

class AndroidPdfValidator(
    private val core: PdfiumCoreKt = PdfiumCoreKt(Dispatchers.IO, Config()),
) : PdfValidator {
    override suspend fun pageCount(file: File): Int {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return pfd.use { pfd ->
            val document = core.newDocument(pfd)
            document.use { document ->
                document.getPageCount()
            }
        }
    }
}
