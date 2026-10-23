package com.benton.lightchat.data.local.cache

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "cache_entries",
    primaryKeys = ["ownerKey", "entryKey"],
    indices = [Index(value = ["ownerKey", "updatedAt"])],
)
data class CacheEntryEntity(
    val ownerKey: String,
    val entryKey: String,
    val payload: String,
    val updatedAt: Long,
)
