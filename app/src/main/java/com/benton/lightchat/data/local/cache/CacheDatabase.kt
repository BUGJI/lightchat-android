package com.benton.lightchat.data.local.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CacheEntryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class CacheDatabase : RoomDatabase() {

    abstract fun cacheDao(): CacheDao

    companion object {
        private const val NAME = "lightchat_cache.db"

        fun create(context: Context): CacheDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                CacheDatabase::class.java,
                NAME,
            ).build()
    }
}
