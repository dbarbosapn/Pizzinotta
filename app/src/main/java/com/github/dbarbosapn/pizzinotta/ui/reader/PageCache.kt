package com.github.dbarbosapn.pizzinotta.ui.reader

/**
 * Bounded, access-ordered cache of page-rendered values keyed by page index.
 *
 * The [render] function supplies the value for a missing index and is the only place that knows
 * how to actually produce one (bitmap decoding, PDF rendering, and so on), which keeps this class
 * free of any platform dependency and unit-testable with a fake renderer.
 */
class PageCache<T>(
    private val render: (index: Int) -> T,
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    init {
        require(capacity >= 1) { "capacity must be >= 1 but was $capacity" }
    }

    // Access-order map: the first entry is always the least recently used (farthest from use).
    private val store = LinkedHashMap<Int, T>()

    var currentIndex: Int = 0
        private set

    val size: Int get() = store.size

    fun contains(index: Int): Boolean = index in store

    /** Returns the value for [index], rendering and storing it when absent. */
    fun get(index: Int): T? {
        val cached = store[index]
        if (cached != null) {
            store.remove(index)
            store[index] = cached
            currentIndex = index
            return cached
        }
        evictIfNeeded()
        currentIndex = index
        return render(index).also { store[index] = it }
    }

    fun moveTo(index: Int) {
        currentIndex = index
    }

    fun clear() {
        store.clear()
        currentIndex = 0
    }

    private fun evictIfNeeded() {
        while (store.size >= capacity) {
            val iterator = store.entries.iterator()
            if (!iterator.hasNext()) break
            iterator.next()
            iterator.remove()
        }
    }

    companion object {
        const val DEFAULT_CAPACITY = 3
    }
}
