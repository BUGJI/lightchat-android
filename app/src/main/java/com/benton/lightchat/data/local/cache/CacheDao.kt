package com.benton.lightchat.data.local.cache

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CacheDao {

    @Query("SELECT * FROM cache_entries WHERE ownerKey = :ownerKey AND entryKey = :entryKey LIMIT 1")
    fun observe(ownerKey: String, entryKey: String): Flow<CacheEntryEntity?>

    @Query("SELECT * FROM cache_entries WHERE ownerKey = :ownerKey AND entryKey = :entryKey LIMIT 1")
    suspend fun entry(ownerKey: String, entryKey: String): CacheEntryEntity?

    @Upsert
    suspend fun upsert(entry: CacheEntryEntity)

    @Query("DELETE FROM cache_entries WHERE ownerKey = :ownerKey AND entryKey = :entryKey")
    suspend fun delete(ownerKey: String, entryKey: String)

    @Query("DELETE FROM cache_entries WHERE ownerKey = :ownerKey")
    suspend fun deleteForOwner(ownerKey: String)

    @Query("DELETE FROM cache_entries")
    suspend fun deleteAll()
}
