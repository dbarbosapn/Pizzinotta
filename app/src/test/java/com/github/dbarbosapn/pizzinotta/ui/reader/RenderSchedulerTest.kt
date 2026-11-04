package com.github.dbarbosapn.pizzinotta.ui.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderSchedulerTest {

    @Test
    fun `reports a redraw for the first frame`() {
        val scheduler = RenderScheduler()

        assertTrue(scheduler.needsRedraw(frame = 1, width = 800, height = 600))
    }

    @Test
    fun `does not request a redraw when the same frame and size are pushed again`() {
        val scheduler = RenderScheduler()

        assertTrue(scheduler.needsRedraw(frame = 1, width = 800, height = 600))
        assertFalse(scheduler.needsRedraw(frame = 1, width = 800, height = 600))
    }

    @Test
    fun `requests a redraw when the frame identity changes`() {
        val scheduler = RenderScheduler()

        assertTrue(scheduler.needsRedraw(frame = 1, width = 800, height = 600))
        assertTrue(scheduler.needsRedraw(frame = 2, width = 800, height = 600))
    }

    @Test
    fun `requests a redraw when only the surface size changes`() {
        val scheduler = RenderScheduler()

        assertTrue(scheduler.needsRedraw(frame = 1, width = 800, height = 600))
        assertFalse(scheduler.needsRedraw(frame = 1, width = 800, height = 600))
        assertTrue(scheduler.needsRedraw(frame = 1, width = 1000, height = 600))
    }

    @Test
    fun `never requests a redraw after a stop`() {
        val scheduler = RenderScheduler()

        assertTrue(scheduler.needsRedraw(frame = 1, width = 800, height = 600))
        scheduler.requestStop()

        assertFalse(scheduler.needsRedraw(frame = 2, width = 800, height = 600))
        assertFalse(scheduler.needsRedraw(frame = 3, width = 800, height = 600))
    }

    @Test
    fun `reset clears accepted state and unstops`() {
        val scheduler = RenderScheduler()

        scheduler.requestStop()
        scheduler.reset()

        assertTrue(scheduler.needsRedraw(frame = 1, width = 800, height = 600))
    }
}
