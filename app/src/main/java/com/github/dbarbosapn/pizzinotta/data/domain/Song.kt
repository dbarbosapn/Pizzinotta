package com.github.dbarbosapn.pizzinotta.data.domain

import java.util.UUID

data class Song(
    val id: UUID,
    val contentHash: String,
    val displayTitle: String,
    val originalFilename: String,
    val pageCount: Int,
    val addedAt: Long,
    val lastOpenedAt: Long?,
    val filePath: String
)
