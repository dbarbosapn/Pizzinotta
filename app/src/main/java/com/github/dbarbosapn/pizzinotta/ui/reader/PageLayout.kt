package com.github.dbarbosapn.pizzinotta.ui.reader

import kotlin.math.roundToInt

data class Fit(
    val renderWidth: Int,
    val renderHeight: Int,
    val offsetX: Int,
    val offsetY: Int,
)

object PageLayout {

    /**
     * Fits a page of natural size [pageW] x [pageH] into a surface of [surfaceW] x [surfaceH],
     * preserving aspect ratio and centring the result. [pageW]/[pageH] may be in any consistent
     * unit (only the ratio matters); the returned dimensions are in [surfaceW]/[surfaceH] units.
     */
    fun fit(pageW: Int, pageH: Int, surfaceW: Int, surfaceH: Int): Fit {
        require(pageW > 0 && pageH > 0) { "Page size must be positive: $pageW x $pageH" }
        require(surfaceW > 0 && surfaceH > 0) { "Surface size must be positive: $surfaceW x $surfaceH" }

        val scale = minOf(surfaceW.toDouble() / pageW, surfaceH.toDouble() / pageH)
        val renderW = (pageW * scale).roundToInt().coerceAtMost(surfaceW)
        val renderH = (pageH * scale).roundToInt().coerceAtMost(surfaceH)
        return Fit(
            renderWidth = renderW,
            renderHeight = renderH,
            offsetX = (surfaceW - renderW) / 2,
            offsetY = (surfaceH - renderH) / 2,
        )
    }
}
