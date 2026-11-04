package com.github.dbarbosapn.pizzinotta.ui.reader

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.github.dbarbosapn.pizzinotta.data.domain.Song
import com.github.dbarbosapn.pizzinotta.data.reader.PdfDocument
import com.github.dbarbosapn.pizzinotta.data.reader.PdfiumDocument
import com.github.dbarbosapn.pizzinotta.data.reader.QueueLoader
import io.legere.pdfiumandroid.api.Config
import io.legere.pdfiumandroid.suspend.PdfiumCoreKt
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

data class ReaderUiState(
    val size: Int = 0,
    val songIndex: Int = 0,
    val pageIndex: Int = 0,
    val title: String = "",
    val pageCount: Int = 0,
) {
    val isEmpty: Boolean get() = size == 0
    val isFirstSong: Boolean get() = songIndex <= 0
    val isLastSong: Boolean get() = songIndex >= size - 1
    val isFirstPage: Boolean get() = pageIndex <= 0
    val isLastPage: Boolean get() = pageIndex >= pageCount - 1
}

class ReaderViewModel(
    private val core: PdfiumCoreKt,
    private val loader: QueueLoader,
    private val startIndex: Int = 0,
) : ViewModel() {

    private val documentLock = Any()
    private val vmScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val pageCache = PageCache(render = ::loadPage)

    private var queueSize = 0
    private var songIndex = 0
    private var pageIndex = 0
    private var currentSong: Song? = null
    private var currentDocument: PdfDocument? = null

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private val _renderFrame = MutableStateFlow<Bitmap?>(null)
    val renderFrame: StateFlow<Bitmap?> = _renderFrame.asStateFlow()

    init {
        vmScope.launch { initialize() }
    }

    private suspend fun initialize() {
        queueSize = loader.size()
        Log.d("reader-debug", "init queueSize=$queueSize startIndex=$startIndex")
        if (queueSize == 0) return
        swapTo(startIndex.coerceIn(0, queueSize - 1))
    }

    fun goToNextPage() = advancePage(1)
    fun goToPreviousPage() = advancePage(-1)
    fun goToNextSong() = changeSong(1)
    fun goToPreviousSong() = changeSong(-1)

    private fun advancePage(delta: Int) {
        vmScope.launch {
            val changed = synchronized(documentLock) {
                val song = currentSong ?: return@synchronized false
                val next = pageIndex + delta
                if (next in 0 until song.pageCount) {
                    pageIndex = next
                    true
                } else {
                    false
                }
            }
            if (changed) update()
        }
    }

    private fun changeSong(delta: Int) {
        vmScope.launch {
            val target = synchronized(documentLock) { songIndex + delta }
            if (queueSize > 0 && target in 0 until queueSize) swapTo(target)
        }
    }

    private suspend fun swapTo(index: Int) {
        val song = loader.get(index) ?: run {
            Log.d("reader-debug", "swapTo index=$index loader returned null -> no frame")
            return
        }
        Log.d("reader-debug", "swapTo index=$index title=${song.displayTitle} pageCount=${song.pageCount} path=${song.filePath}")
        synchronized(documentLock) {
            closeDocument()
            currentSong = song
            songIndex = index
            pageIndex = 0
            if (queueSize == 0) queueSize = index + 1
            currentDocument = PdfiumDocument(core, File(song.filePath))
            pageCache.clear()
        }
        update()
    }

    private fun update() {
        val song = currentSong ?: return
        val bitmap = synchronized(documentLock) { pageCache.get(pageIndex) }
        Log.d("reader-debug", "update pageIndex=$pageIndex bitmapNull=${bitmap == null}")
        _renderFrame.value = bitmap
        _state.value = ReaderUiState(
            size = queueSize,
            songIndex = songIndex,
            pageIndex = pageIndex,
            title = song.displayTitle,
            pageCount = song.pageCount,
        )
        primeNearby()
    }

    private fun primeNearby() {
        vmScope.launch {
            val song = currentSong ?: return@launch
            synchronized(documentLock) {
                (pageIndex - 1 until pageIndex + 2).forEach { index ->
                    if (index in 0 until song.pageCount) pageCache.get(index)
                }
            }
        }
    }

    private fun loadPage(index: Int): Bitmap {
        val doc = currentDocument ?: throw IllegalStateException("No document open")
        val song = currentSong ?: throw IllegalStateException("No song loaded")
        require(index in 0 until song.pageCount) { "Page $index out of range for ${song.displayTitle}" }
        val bitmap = runBlocking(Dispatchers.IO) { doc.renderPage(index) }
        Log.d("reader-debug", "loadPage K=$index dims=${bitmap.width} x ${bitmap.height}")
        return bitmap
    }

    private fun closeDocument() {
        currentDocument?.close()
        currentDocument = null
    }

    override fun onCleared() {
        closeDocument()
        vmScope.cancel()
    }
}

class ReaderViewModelFactory(
    private val loader: QueueLoader,
    private val startIndex: Int = 0,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val core = PdfiumCoreKt(Dispatchers.IO, Config())
        return ReaderViewModel(core, loader, startIndex) as T
    }
}
