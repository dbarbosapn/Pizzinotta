package com.github.dbarbosapn.pizzinotta.data.domain

import java.util.UUID

data class Playlist(
    val id: UUID,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)
