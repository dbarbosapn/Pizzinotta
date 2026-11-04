package com.github.dbarbosapn.pizzinotta.data.reader

import android.graphics.Bitmap
import java.io.Closeable
import java.io.File
import androidx.core.graphics.createBitmap

/**
 * Thin, testable boundary over the PDF engine. The reader only ever talks to this interface; the
 * pdfiumandroid implementation lives in [PdfiumDocument]. Pages are rendered at a fixed resolution
 * so cached bitmaps stay independent of screen size and survive configuration changes.
 */
interface PdfDocument : Closeable {
    suspend fun pageCount(): Int
    suspend fun renderPage(pageIndex: Int): Bitmap
}

class PdfiumDocument(
    private val core: io.legere.pdfiumandroid.suspend.PdfiumCoreKt,
    private val file: File,
    private val renderDpi: Int = RENDER_DPI,
) : PdfDocument {

    private var document: io.legere.pdfiumandroid.suspend.PdfDocumentKt? = null
    private var fileDescriptor: android.os.ParcelFileDescriptor? = null

    private suspend fun open(): io.legere.pdfiumandroid.suspend.PdfDocumentKt {
        document?.let { return it }
        val pfd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
        fileDescriptor = pfd
        return core.newDocument(pfd).also { document = it }
    }

    override suspend fun pageCount(): Int =
        open().getPageCount()

    override suspend fun renderPage(pageIndex: Int): Bitmap {
        val pdfDoc = open()
        val page = pdfDoc.openPage(pageIndex)
            ?: throw IndexOutOfBoundsException("No page $pageIndex in ${file.name}")
        return page.use { pdfPage ->
            val widthPx = pdfPage.getPageWidth(renderDpi).coerceAtLeast(1)
            val heightPx = pdfPage.getPageHeight(renderDpi).coerceAtLeast(1)
            createBitmap(widthPx, heightPx).also { bitmap ->
                pdfPage.renderPageBitmap(bitmap, 0, 0, widthPx, heightPx,
                    renderAnnot = false,
                    textMask = false,
                    canvasColor = WHITE,
                    pageBackgroundColor = WHITE
                )
            }
        }
    }

    override fun close() {
        synchronized(this) {
            document?.close()
            document = null
            fileDescriptor?.close()
            fileDescriptor = null
        }
    }

    companion object {
        const val RENDER_DPI = 150
        private const val WHITE = 0xFFFFFFFF.toInt()
    }
}
