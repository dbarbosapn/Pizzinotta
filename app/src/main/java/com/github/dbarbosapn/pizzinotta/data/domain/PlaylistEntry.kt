package com.github.dbarbosapn.pizzinotta.data.domain

import java.util.UUID

data class PlaylistEntry(
    val playlistId: UUID,
    val songId: UUID,
    val position: Int
)
