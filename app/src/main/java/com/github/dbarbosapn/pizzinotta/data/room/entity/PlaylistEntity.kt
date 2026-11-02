package com.github.dbarbosapn.pizzinotta.data.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: UUID,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)
