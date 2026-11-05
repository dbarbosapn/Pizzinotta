package com.github.dbarbosapn.pizzinotta.ui.reader

/**
 * The minimal, platform-agnostic messages the reader view forwards for each down-gesture. The view
 * is responsible only for reducing raw touch into one of these; all navigation and boundary logic
 * lives in [ReaderController].
 */
sealed interface ReaderGesture {
    data class Tap(val zone: Zone) : ReaderGesture
    data class DoubleTap(val zone: Zone) : ReaderGesture
}
