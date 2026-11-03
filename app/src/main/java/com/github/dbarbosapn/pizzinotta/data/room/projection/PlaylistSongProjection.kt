package com.github.dbarbosapn.pizzinotta.data.room.projection

import androidx.room.Embedded
import com.github.dbarbosapn.pizzinotta.data.room.entity.SongEntity

class PlaylistSongProjection(
    @Embedded val song: SongEntity,
    val position: Int,
)
