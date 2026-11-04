package com.github.dbarbosapn.pizzinotta.ui.reader

/**
 * Decides whether the render loop needs to redraw.
 *
 * The reader's [com.github.dbarbosapn.pizzinotta.ui.reader.ReaderSurfaceView] only draws when this
 * reports a change; otherwise the render thread idles on a condition instead of spinning. Idling is
 * what makes shutdown prompt: at rest the thread is waiting, not mid-canvas-operation, so a stop
 * request is acknowledged on the next wake rather than after a fixed timeout.
 *
 */
class RenderScheduler {

    private var lastFrame: Any? = null
    private var lastWidth = 0
    private var lastHeight = 0

    @Volatile
    private var stopped = false

    fun requestStop() {
        stopped = true
    }

    /** Resets to the empty state so a restarted loop redraws its first frame. */
    fun reset() {
        lastFrame = null
        lastWidth = 0
        lastHeight = 0
        stopped = false
    }

    /**
     * Returns `true` only when [frame] identity or the surface size differs from what was last
     * accepted. Once [requestStop] has been called this always returns `false`.
     */
    fun needsRedraw(frame: Any?, width: Int, height: Int): Boolean {
        if (stopped) return false
        if (frame === lastFrame && width == lastWidth && height == lastHeight) return false
        lastFrame = frame
        lastWidth = width
        lastHeight = height
        return true
    }
}
