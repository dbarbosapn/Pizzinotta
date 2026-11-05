package com.github.dbarbosapn.pizzinotta.ui.reader

import com.github.dbarbosapn.pizzinotta.data.reader.QueueLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Pure-Kotlin brain of the reader. It owns all navigation and boundary logic — page/song movement,
 * tap-zone actions, the bottom-bar toggle, and the double-tap-again confirmation window — with no
 * Android dependencies, so it stays unit-testable. It reads the queue through the [QueueLoader]
 * interface only, publishes its [state] via a StateFlow, and reports toasts through an injected
 * callback (a Toast needs a Context, which is the one platform concern kept outside this class).
 *
 * Navigation methods are `suspend` because resolving a song requires a suspend [QueueLoader] call;
 * callers run them on their own scope. The double-tap window itself is pure and driven by an
 * injected [clock] so tests can control time deterministically.
 */
class ReaderController(
    private val loader: QueueLoader,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onToast: (String) -> Unit = {},
    private val resetLifetimeMs: Long = RESET_LIFETIME_MS,
) {

    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    @Volatile
    private var pendingResetTo: Int? = null

    @Volatile
    private var pendingResetUntil: Long = 0L

    suspend fun initialize(startIndex: Int) {
        val size = loader.size()
        _state.value = ReaderState(queueSize = size)
        if (size == 0) return
        goToSong(startIndex.coerceIn(0, size - 1))
    }

    suspend fun tap(gesture: ReaderGesture) {
        when (gesture) {
            is ReaderGesture.Tap -> handleTap(gesture.zone)
            is ReaderGesture.DoubleTap -> handleDoubleTap(gesture.zone)
        }
    }

    /** Bottom-bar previous/next-song buttons: a deliberate direct jump, no confirmation window. */
    suspend fun nextSong() = skipSongDirect(+1)

    suspend fun previousSong() = skipSongDirect(-1)

    private fun handleTap(zone: Zone) {
        when (zone) {
            Zone.CENTER -> toggleBar()
            Zone.RIGHT -> advancePage(+1)
            Zone.LEFT -> advancePage(-1)
        }
    }

    private suspend fun handleDoubleTap(zone: Zone) {
        when (zone) {
            Zone.CENTER -> Unit // fit-to-screen is a Phase 2 zoom action; nothing in Phase 1
            Zone.RIGHT -> skipSong(+1)
            Zone.LEFT -> skipSong(-1)
        }
    }

    private fun toggleBar() {
        _state.update { it.copy(barVisible = !it.barVisible) }
    }

    private fun advancePage(delta: Int) {
        val state = _state.value
        if (state.currentSong == null) return
        val next = state.pageIndex + delta
        if (next in 0 until state.pageCount) {
            _state.update { it.copy(pageIndex = next) }
        }
    }

    private suspend fun skipSong(delta: Int) {
        val state = _state.value
        if (state.queueSize == 0) return
        val atEnd = if (delta > 0) state.isLastSong else state.isFirstSong
        if (!atEnd) {
            goToSong(state.songIndex + delta)
            return
        }
        val targetIfConfirmed = if (delta > 0) 0 else state.queueSize - 1
        val now = clock()
        if (pendingResetTo == targetIfConfirmed && now < pendingResetUntil) {
            clearPendingReset()
            goToSong(targetIfConfirmed)
        } else {
            pendingResetTo = targetIfConfirmed
            pendingResetUntil = now + resetLifetimeMs
            onToast(toastMessage(startingOver = delta > 0))
        }
    }

    private suspend fun skipSongDirect(delta: Int) {
        val state = _state.value
        if (state.queueSize == 0) return
        val target = (state.songIndex + delta).coerceIn(0, state.queueSize - 1)
        if (target != state.songIndex) goToSong(target)
    }

    private suspend fun goToSong(index: Int) {
        val song = loader.get(index) ?: return
        clearPendingReset()
        _state.update { it.copy(songIndex = index, pageIndex = 0, currentSong = song) }
    }

    private fun clearPendingReset() {
        pendingResetTo = null
        pendingResetUntil = 0L
    }

    private fun toastMessage(startingOver: Boolean): String =
        if (startingOver) START_OVER_MESSAGE else LAST_SONG_MESSAGE

    companion object {
        private const val RESET_LIFETIME_MS = 3000L
        private const val START_OVER_MESSAGE = "Double-tap again to start over."
        private const val LAST_SONG_MESSAGE = "Double-tap again to go to the last song."
    }
}
