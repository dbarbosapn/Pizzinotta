package com.github.dbarbosapn.pizzinotta.ui.reader

import com.github.dbarbosapn.pizzinotta.data.domain.Song
import com.github.dbarbosapn.pizzinotta.data.reader.QueueLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ReaderControllerTest {

    private class FakeQueueLoader(val songs: List<Song>) : QueueLoader {
        override suspend fun size(): Int = songs.size
        override suspend fun get(index: Int): Song? = songs.getOrNull(index)
    }

    private fun song(title: String, pageCount: Int) = Song(
        id = UUID.nameUUIDFromBytes(title.toByteArray()),
        contentHash = "hash-$title",
        displayTitle = title,
        originalFilename = "$title.pdf",
        pageCount = pageCount,
        addedAt = 0,
        lastOpenedAt = null,
        filePath = "path/$title.pdf",
    )

    private class Harness(songs: List<Song>, val startIndex: Int = 0) {
        var now = 1_000L
        val toasts = mutableListOf<String>()
        val loader = FakeQueueLoader(songs)
        val controller = ReaderController(
            loader = loader,
            clock = { now },
            onToast = { toasts.add(it) },
        )

        fun start() = runBlocking { controller.initialize(startIndex) }
        fun tap(g: ReaderGesture) = runBlocking { controller.tap(g) }
        fun nextSong() = runBlocking { controller.nextSong() }
        fun previousSong() = runBlocking { controller.previousSong() }
        val state get() = controller.state.value
    }

    private fun left() = ReaderGesture.Tap(Zone.LEFT)
    private fun right() = ReaderGesture.Tap(Zone.RIGHT)
    private fun center() = ReaderGesture.Tap(Zone.CENTER)
    private fun doubleLeft() = ReaderGesture.DoubleTap(Zone.LEFT)
    private fun doubleRight() = ReaderGesture.DoubleTap(Zone.RIGHT)
    private fun doubleCenter() = ReaderGesture.DoubleTap(Zone.CENTER)

    @Test
    fun `initialize loads the start song at page one`() = runBlocking {
        val h = Harness(listOf(song("A", 5), song("B", 3)))
        h.start()

        assertEquals(2, h.state.queueSize)
        assertEquals(0, h.state.songIndex)
        assertEquals(0, h.state.pageIndex)
        assertEquals("A", h.state.title)
        assertEquals(5, h.state.pageCount)
    }

    @Test
    fun `initialize with an empty queue leaves state default`() = runBlocking {
        val h = Harness(emptyList())
        h.start()

        assertEquals(0, h.state.queueSize)
        assertNull(h.state.currentSong)
    }

    @Test
    fun `single tap on the right edge advances a page`() = runBlocking {
        val h = Harness(listOf(song("A", 5)))
        h.start()
        h.tap(right())

        assertEquals(1, h.state.pageIndex)
    }

    @Test
    fun `single tap on the left edge goes back a page`() = runBlocking {
        val h = Harness(listOf(song("A", 5)))
        h.start()
        h.tap(right())
        h.tap(right())
        h.tap(left())

        assertEquals(1, h.state.pageIndex)
    }

    @Test
    fun `single tap on the right edge does nothing on the last page`() = runBlocking {
        val h = Harness(listOf(song("A", 3)))
        h.start()
        h.tap(right())
        h.tap(right())
        h.tap(right())

        assertEquals(2, h.state.pageIndex)
    }

    @Test
    fun `single tap on the left edge does nothing on the first page`() = runBlocking {
        val h = Harness(listOf(song("A", 3)))
        h.start()
        h.tap(left())

        assertEquals(0, h.state.pageIndex)
    }

    @Test
    fun `single tap in the center toggles the bar on then off`() = runBlocking {
        val h = Harness(listOf(song("A", 1)))
        h.start()
        h.tap(center())
        h.tap(center())

        assertFalse(h.state.barVisible)
    }

    @Test
    fun `double tap in the center is a no-op in phase one`() = runBlocking {
        val h = Harness(listOf(song("A", 1)))
        h.start()
        h.tap(center())
        h.tap(doubleCenter())

        assertTrue(h.state.barVisible)
        assertTrue(h.toasts.isEmpty())
    }

    @Test
    fun `double tap on the right edge moves to the next song and resets to page one`() = runBlocking {
        val h = Harness(listOf(song("A", 2), song("B", 4)))
        h.start()
        h.tap(right()) // page 1 of A
        h.tap(doubleRight())

        assertEquals(1, h.state.songIndex)
        assertEquals(0, h.state.pageIndex)
        assertEquals("B", h.state.title)
        assertTrue(h.toasts.isEmpty())
    }

    @Test
    fun `double tap on the left edge moves to the previous song`() = runBlocking {
        val h = Harness(listOf(song("A", 2), song("B", 2)))
        h.start()
        h.tap(doubleRight()) // -> B
        h.tap(doubleLeft()) // -> A

        assertEquals(0, h.state.songIndex)
        assertEquals("A", h.state.title)
    }

    @Test
    fun `double tap on the last song shows a toast and waits for confirmation`() = runBlocking {
        val h = Harness(listOf(song("A", 2), song("B", 2)))
        h.start()
        h.tap(doubleRight())
        h.tap(doubleRight()) // at last song, first time

        assertEquals(1, h.state.songIndex)
        assertEquals(listOf("Double-tap again to start over."), h.toasts)
    }

    @Test
    fun `a second double tap within the window starts over`() = runBlocking {
        val h = Harness(listOf(song("A", 2), song("B", 2)))
        h.start()
        h.tap(doubleRight())
        h.tap(doubleRight())
        h.tap(doubleRight())

        assertEquals(0, h.state.songIndex)
        assertEquals("A", h.state.title)
        assertEquals(listOf("Double-tap again to start over."), h.toasts)
    }

    @Test
    fun `double tap on the first song waits before going to the last song`() = runBlocking {
        val h = Harness(listOf(song("A", 2), song("B", 2)))
        h.start()
        h.tap(doubleLeft())

        assertEquals(0, h.state.songIndex)
        assertEquals(listOf("Double-tap again to go to the last song."), h.toasts)
    }

    @Test
    fun `confirming goes to the last song`() = runBlocking {
        val h = Harness(listOf(song("A", 2), song("B", 2)))
        h.start()
        h.tap(doubleLeft())
        h.tap(doubleLeft())

        assertEquals(1, h.state.songIndex)
        assertEquals("B", h.state.title)
    }

    @Test
    fun `an expired confirmation window starts the toast over again`() = runBlocking {
        val h = Harness(listOf(song("A", 2), song("B", 2)))
        h.start()
        h.tap(doubleRight())
        h.tap(doubleRight())
        h.now += 4_000L // past the reset lifetime
        h.tap(doubleRight())

        assertEquals(1, h.state.songIndex)
        assertEquals(2, h.toasts.size)
    }

    @Test
    fun `bar buttons jump directly to the next song`() = runBlocking {
        val h = Harness(listOf(song("A", 2), song("B", 2), song("C", 2)))
        h.start()
        h.nextSong()

        assertEquals(1, h.state.songIndex)
        assertTrue(h.toasts.isEmpty())
    }

    @Test
    fun `bar buttons jump directly to the previous song`() = runBlocking {
        val h = Harness(listOf(song("A", 2), song("B", 2)))
        h.start()
        h.nextSong()
        h.previousSong()

        assertEquals(0, h.state.songIndex)
    }

    @Test
    fun `a bar button at a boundary does nothing`() = runBlocking {
        val h = Harness(listOf(song("A", 2)))
        h.start()
        h.nextSong()

        assertEquals(0, h.state.songIndex)
    }

    @Test
    fun `page turns keep the bar state untouched`() = runBlocking {
        val h = Harness(listOf(song("A", 5)))
        h.start()
        h.tap(center())
        assertTrue(h.state.barVisible)
        h.tap(right())
        h.tap(right())

        assertTrue(h.state.barVisible)
        assertEquals(2, h.state.pageIndex)
    }
}
