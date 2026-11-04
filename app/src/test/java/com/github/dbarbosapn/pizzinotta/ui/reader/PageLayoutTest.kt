package com.github.dbarbosapn.pizzinotta.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PageLayoutTest {

    @Test
    fun `fits a square page into a wider screen using height as the limiting dimension`() {
        val fit = PageLayout.fit(pageW = 100, pageH = 100, surfaceW = 800, surfaceH = 400)

        assertEquals(400, fit.renderWidth)
        assertEquals(400, fit.renderHeight)
        assertEquals(200, fit.offsetX)
        assertEquals(0, fit.offsetY)
    }

    @Test
    fun `fits a tall page into a wide screen using width as the limiting dimension`() {
        val fit = PageLayout.fit(pageW = 100, pageH = 200, surfaceW = 800, surfaceH = 400)

        assertEquals(200, fit.renderWidth)
        assertEquals(400, fit.renderHeight)
        assertEquals(300, fit.offsetX)
        assertEquals(0, fit.offsetY)
    }

    @Test
    fun `fills the surface exactly when aspect ratios match`() {
        val fit = PageLayout.fit(pageW = 1, pageH = 2, surfaceW = 400, surfaceH = 800)

        assertEquals(400, fit.renderWidth)
        assertEquals(800, fit.renderHeight)
        assertEquals(0, fit.offsetX)
        assertEquals(0, fit.offsetY)
    }

    @Test
    fun `centres the rendered page on both axes`() {
        val fit = PageLayout.fit(pageW = 200, pageH = 200, surfaceW = 1000, surfaceH = 600)

        assertEquals(200, fit.offsetX)
        assertEquals(0, fit.offsetY)
    }

    @Test
    fun `never exceeds the surface bounds even with rounding`() {
        val fit = PageLayout.fit(pageW = 3, pageH = 5, surfaceW = 1000, surfaceH = 1000)

        assert(fit.renderWidth <= 1000)
        assert(fit.renderHeight <= 1000)
    }

    @Test
    fun `rejects a non-positive page size`() {
        assertThrows(IllegalArgumentException::class.java) { PageLayout.fit(0, 10, 10, 10) }
        assertThrows(IllegalArgumentException::class.java) { PageLayout.fit(10, -1, 10, 10) }
    }

    @Test
    fun `rejects a non-positive surface size`() {
        assertThrows(IllegalArgumentException::class.java) { PageLayout.fit(10, 10, 0, 10) }
    }
}
