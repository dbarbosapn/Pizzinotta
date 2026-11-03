package com.github.dbarbosapn.pizzinotta.ui.nav

sealed interface Screen {
    data object Library : Screen
    data object Playlists : Screen
}
