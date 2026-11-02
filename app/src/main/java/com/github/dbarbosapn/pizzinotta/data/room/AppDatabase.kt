package com.github.dbarbosapn.pizzinotta.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.github.dbarbosapn.pizzinotta.data.room.dao.PlaylistDao
import com.github.dbarbosapn.pizzinotta.data.room.dao.SongDao
import com.github.dbarbosapn.pizzinotta.data.room.entity.PlaylistEntity
import com.github.dbarbosapn.pizzinotta.data.room.entity.PlaylistEntryEntity
import com.github.dbarbosapn.pizzinotta.data.room.entity.SongEntity

@Database(
    entities = [SongEntity::class, PlaylistEntity::class, PlaylistEntryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "pizzinotta.db"
            )
                .fallbackToDestructiveMigration(false)
                .build()
    }
}
