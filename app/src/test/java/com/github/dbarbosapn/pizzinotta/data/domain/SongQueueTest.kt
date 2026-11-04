package com.github.dbarbosapn.pizzinotta.data.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SongQueueTest {

    private fun song(index: Int) = Song(
        id = UUID(index.toLong(), index.toLong()),
        contentHash = "hash-$index",
        displayTitle = "Song $index",
        originalFilename = "song-$index.pdf",
        pageCount = 1,
        addedAt = index.toLong(),
        lastOpenedAt = null,
        filePath = "path/song-$index.pdf",
    )

    private fun songs(count: Int) = (0 until count).map { song(it) }

    @Test
    fun `defaults position to the first song`() {
        val queue = SongQueue(songs(3))
        assertEquals(0, queue.position)
        assertEquals(queue.songs.first(), queue.current)
    }

    @Test
    fun `empty queue has no current and reports both boundaries`() {
        val queue = SongQueue(emptyList())
        assertTrue(queue.isEmpty)
        assertNull(queue.current)
        assertTrue(queue.isFirst)
        assertTrue(queue.isLast)
    }

    @Test
    fun `boundaries are correct at the ends and in the middle`() {
        val queue = SongQueue(songs(3), position = 0)
        assertTrue(queue.isFirst)
        assertFalse(queue.isLast)

        val mid = queue.copy(position = 1)
        assertFalse(mid.isFirst)
        assertFalse(mid.isLast)

        val last = queue.copy(position = 2)
        assertFalse(last.isFirst)
        assertTrue(last.isLast)
    }

    @Test
    fun `next advances within range and clamps at the last song`() {
        val queue = SongQueue(songs(3), position = 1)
        assertEquals(2, queue.next().position)
        val atLast = queue.next()
        assertSame(atLast, atLast.next())
    }

    @Test
    fun `previous retreats within range and clamps at the first song`() {
        val queue = SongQueue(songs(3), position = 1)
        assertEquals(0, queue.previous().position)
        val atFirst = queue.previous()
        assertSame(atFirst, atFirst.previous())
    }

    @Test
    fun `goTo coerces out-of-range indices into bounds`() {
        val queue = SongQueue(songs(3))
        assertEquals(0, queue.goTo(-5).position)
        assertEquals(2, queue.goTo(99).position)
        assertEquals(1, queue.goTo(1).position)
    }

    @Test
    fun `songAt returns null outside the range`() {
        val queue = SongQueue(songs(2))
        assertNull(queue.songAt(-1))
        assertNull(queue.songAt(2))
        assertEquals(queue.songs[0], queue.songAt(0))
    }
}
