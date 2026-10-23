package com.benton.lightchat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val ownerKey: String,
    val channelCursor: Long = 0,
    val recallCursor: Long = 0,
    val privateChatId: Long = 0,
    val privateCursor: Long = 0,
    val lastSyncAt: Long = 0,
)
