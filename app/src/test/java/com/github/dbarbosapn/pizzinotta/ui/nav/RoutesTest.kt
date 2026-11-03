package com.github.dbarbosapn.pizzinotta.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class RoutesTest {

    @Test
    fun `reader route embeds playlist id and default start index`() {
        val playlistId = UUID(1L, 2L)
        val route = Routes.readerRoute(playlistId)
        assertEquals("reader/$playlistId/0", route)
    }

    @Test
    fun `reader route embeds a custom start index`() {
        val playlistId = UUID(3L, 4L)
        val route = Routes.readerRoute(playlistId, startIndex = 7)
        assertEquals("reader/$playlistId/7", route)
    }

    @Test
    fun `reader route is well formed with an integer start index segment`() {
        val playlistId = UUID(5L, 6L)
        val segments = Routes.readerRoute(playlistId).split('/')
        assertEquals("reader", segments[0])
        assertEquals(playlistId.toString(), segments[1])
        assertTrue(segments[2].toInt() >= 0)
    }
}
