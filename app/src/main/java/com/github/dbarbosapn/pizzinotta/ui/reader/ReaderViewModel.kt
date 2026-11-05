package com.github.dbarbosapn.pizzinotta.ui.reader

import android.content.Context
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
import android.os.Handler
import android.widget.Toast
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** UI-facing state derived from [ReaderState]; the bottom bar reads only these fields. */
data class ReaderUiState(
    val queueSize: Int = 0,
    val songIndex: Int = 0,
    val pageIndex: Int = 0,
    val title: String = "",
    val pageCount: Int = 0,
    val barVisible: Boolean = false,
) {
    val isFirstSong: Boolean get() = songIndex <= 0
    val isLastSong: Boolean get() = queueSize > 0 && songIndex >= queueSize - 1
    val hasQueue: Boolean get() = queueSize > 1
}

fun ReaderState.toUiState(): ReaderUiState = ReaderUiState(
    queueSize = queueSize,
    songIndex = songIndex,
    pageIndex = pageIndex,
    title = title,
    pageCount = pageCount,
    barVisible = barVisible,
)

/**
 * Reactive adapter between the pure [ReaderController] and the Android reader. The controller owns
 * navigation logic; this class owns everything platform-specific: the pdfium document lifecycle,
 * the bounded page cache, bitmap rendering, and pushing frames to the surface. It reacts to
 * controller state changes — swapping the document when the song changes and priming/pushing pages
 * as the index moves — so the brain never touches a bitmap or the PDF engine.
 */
class ReaderViewModel(
    private val core: PdfiumCoreKt,
    private val loader: QueueLoader,
    private val startIndex: Int,
    private val appContext: Context,
) : ViewModel() {

    val controller = ReaderController(
        loader = loader,
        onToast = { msg -> Handler(appContext.mainLooper).post { Toast.makeText(appContext, msg, Toast.LENGTH_SHORT).show() } },
    )

    private val vmScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val pageCache = PageCache(render = ::loadPage)

    private var document: PdfDocument? = null
    private var currentSongIndex = -1

    private val _renderFrame = MutableStateFlow<Bitmap?>(null)
    val renderFrame: StateFlow<Bitmap?> = _renderFrame.asStateFlow()

    val state: StateFlow<ReaderUiState> = controller.state
        .map { it.toUiState() }
        .stateIn(vmScope, SharingStarted.Eagerly, ReaderUiState())

    init {
        vmScope.launch { controller.initialize(startIndex) }
        vmScope.launch {
            controller.state.collect { s ->
                if (s.currentSong != null && s.songIndex != currentSongIndex) {
                    Log.d("reader-debug", "swap song index=$s.songIndex title=${s.title}")
                    swapDocument(s.currentSong)
                    currentSongIndex = s.songIndex
                    pageCache.clear()
                }
                primeAndPush(s.pageIndex)
            }
        }
    }

    fun onGesture(gesture: ReaderGesture) = vmScope.launch { controller.tap(gesture) }

    fun nextSong() = vmScope.launch { controller.nextSong() }

    fun previousSong() = vmScope.launch { controller.previousSong() }

    private fun swapDocument(song: Song) {
        closeDocument()
        document = PdfiumDocument(core, File(song.filePath))
    }

    private fun primeAndPush(pageIndex: Int) {
        val song = controller.state.value.currentSong ?: return
        if (pageIndex !in 0 until song.pageCount) return
        vmScope.launch {
            _renderFrame.value = pageCache.get(pageIndex)
            (pageIndex - 1 until pageIndex + 2).forEach { index ->
                if (index in 0 until song.pageCount) pageCache.get(index)
            }
        }
    }

    private fun loadPage(index: Int): Bitmap {
        val doc = document ?: throw IllegalStateException("No document open")
        val song = controller.state.value.currentSong ?: throw IllegalStateException("No song loaded")
        require(index in 0 until song.pageCount) { "Page $index out of range for ${song.displayTitle}" }
        return runBlocking(Dispatchers.IO) { doc.renderPage(index) }
    }

    private fun closeDocument() {
        document?.close()
        document = null
    }

    override fun onCleared() {
        closeDocument()
        vmScope.cancel()
    }
}

class ReaderViewModelFactory(
    private val loader: QueueLoader,
    private val startIndex: Int,
    private val appContext: Context,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val core = PdfiumCoreKt(Dispatchers.IO, Config())
        return ReaderViewModel(core, loader, startIndex, appContext) as T
    }
}
