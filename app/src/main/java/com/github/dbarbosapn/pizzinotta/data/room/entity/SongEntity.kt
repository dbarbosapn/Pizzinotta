package com.github.dbarbosapn.pizzinotta.data.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: UUID,
    val contentHash: String,
    val displayTitle: String,
    val originalFilename: String,
    val pageCount: Int,
    val addedAt: Long,
    val lastOpenedAt: Long?,
    val filePath: String
)
