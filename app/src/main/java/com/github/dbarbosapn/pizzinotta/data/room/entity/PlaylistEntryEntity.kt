package com.github.dbarbosapn.pizzinotta.data.room.entity

import androidx.room.Entity
import java.util.UUID

@Entity(tableName = "playlist_entries", primaryKeys = ["playlistId", "songId"])
data class PlaylistEntryEntity(
    val playlistId: UUID,
    val songId: UUID,
    val position: Int
)
