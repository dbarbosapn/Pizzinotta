package com.github.dbarbosapn.pizzinotta.ui.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class PageCacheTest {

    private class RecordingRenderer {
        val rendered = mutableListOf<Int>()
        val calls = AtomicInteger(0)

        fun render(index: Int): Int {
            calls.incrementAndGet()
            rendered.add(index)
            return index
        }
    }

    @Test
    fun `renders and stores a missing page`() {
        val renderer = RecordingRenderer()
        val cache = PageCache(renderer::render)

        val value = cache.get(4)

        assertEqualsInt(4, value)
        assertTrue(cache.contains(4))
        assertEqualsInt(1, renderer.calls.get())
        assertEqualsInt(1, cache.size)
    }

    @Test
    fun `returns the cached page without re-rendering`() {
        val renderer = RecordingRenderer()
        val cache = PageCache(renderer::render)

        cache.get(2)
        val again = cache.get(2)

        assertEqualsInt(2, again)
        assertEqualsInt(1, renderer.calls.get())
    }

    @Test
    fun `evicts the oldest page once capacity is exceeded`() {
        val renderer = RecordingRenderer()
        val cache = PageCache(renderer::render, capacity = 3)

        cache.get(0)
        cache.get(1)
        cache.get(2)
        cache.get(3)

        assertFalse(cache.contains(0))
        assertTrue(cache.contains(1))
        assertTrue(cache.contains(2))
        assertTrue(cache.contains(3))
        assertEqualsInt(3, cache.size)
    }

    @Test
    fun `evicts by access order rather than insertion order`() {
        val renderer = RecordingRenderer()
        val cache = PageCache(renderer::render, capacity = 2)

        cache.get(0)
        cache.get(1)
        cache.get(0) // touch 0 so it becomes the most recently used
        cache.get(2) // should evict 1, the least recently used

        assertFalse(cache.contains(1))
        assertTrue(cache.contains(0))
        assertTrue(cache.contains(2))
    }

    @Test
    fun `tracks the current index as pages are fetched`() {
        val cache = PageCache<Int>({ 0 })

        cache.get(7)

        assertEqualsInt(7, cache.currentIndex)
    }

    @Test
    fun `moveTo updates the current index without rendering`() {
        val renderer = RecordingRenderer()
        val cache = PageCache(renderer::render)

        cache.moveTo(9)

        assertEqualsInt(9, cache.currentIndex)
        assertEqualsInt(0, cache.size)
    }

    @Test
    fun `clear empties the cache and resets the current index`() {
        val renderer = RecordingRenderer()
        val cache = PageCache(renderer::render, capacity = 3)

        cache.get(0)
        cache.get(1)
        cache.clear()

        assertEqualsInt(0, cache.size)
        assertEqualsInt(0, cache.currentIndex)
        assertFalse(cache.contains(0))
    }

    @Test
    fun `rejects a non-positive capacity`() {
        val renderer = RecordingRenderer()
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { PageCache(renderer::render, capacity = 0) }
    }

    private fun assertEqualsInt(expected: Int, actual: Int?) {
        org.junit.Assert.assertEquals(expected, actual)
    }
}
