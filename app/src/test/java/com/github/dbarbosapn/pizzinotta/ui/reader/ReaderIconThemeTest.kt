package com.github.dbarbosapn.pizzinotta.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.github.dbarbosapn.pizzinotta.ui.theme.DarkColorScheme
import com.github.dbarbosapn.pizzinotta.ui.theme.LightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReaderIconThemeTest {

    private val readerIcons = listOf("ic_arrow_back", "ic_chevron_left", "ic_chevron_right")

    @Test
    fun readerIconDrawablesDoNotBakeFixedBlackTint() {
        val dir = findReaderDrawableDir()
        readerIcons.forEach { name ->
            val xml = File(dir, "$name.xml").readText()
            assertTrue(
                "$name.xml must not bake a fixed black tint (icons must be recolorable per theme)",
                !xml.contains("tint=\"#000000\""),
            )
        }
    }

    @Test
    fun onSurfaceAdaptsBetweenLightAndDarkThemes() {
        val lightOnSurface = LightColorScheme.onSurface
        val darkOnSurface = DarkColorScheme.onSurface
        assertNotEquals(
            "onSurface must differ between themes so icon tint adapts",
            lightOnSurface,
            darkOnSurface,
        )
        assertTrue(
            "dark-mode onSurface must be bright to stay visible on the reader's dark bar",
            darkOnSurface.luminance() > lightOnSurface.luminance(),
        )
    }

    private fun findReaderDrawableDir(): File {
        var dir = File(System.getProperty("user.dir")!!)
        while (true) {
            val candidate = File(dir, "src/main/res/drawable/ic_arrow_back.xml")
            if (candidate.isFile) return candidate.parentFile ?: error("Missing drawable parent dir for $candidate")
            val alt = File(dir, "app/src/main/res/drawable/ic_arrow_back.xml")
            if (alt.isFile) return alt.parentFile ?: error("Missing drawable parent dir for $alt")
            val parent = dir.parentFile
            if (parent == null || parent == dir) {
                error("Could not locate app/src/main/res/drawable from ${System.getProperty("user.dir")}")
            }
            dir = parent
        }
    }
}
