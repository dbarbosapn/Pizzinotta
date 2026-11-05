package com.github.dbarbosapn.pizzinotta.ui.reader

enum class Zone {
    LEFT,
    CENTER,
    RIGHT,
}

object TapZone {

    /**
     * Maps a touch x-coordinate to a tap zone measured against the full screen width, matching the
     * PRD edge/center/width split: left 25%, center 50%, right 25%. [x] is expected in the same
     * units as [width] (typically viewport pixels); values outside `0..width` still resolve to a
     * zone rather than throwing.
     */
    fun at(x: Float, width: Int): Zone {
        require(width > 0) { "Width must be positive: $width" }
        val ratio = x / width.toFloat()
        return when {
            ratio < 0.25f -> Zone.LEFT
            ratio < 0.75f -> Zone.CENTER
            else -> Zone.RIGHT
        }
    }
}
