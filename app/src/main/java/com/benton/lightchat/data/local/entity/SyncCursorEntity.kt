package com.benton.lightchat.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "sync_cursors",
    primaryKeys = ["ownerKey", "conversationType", "conversationId"],
)
data class SyncCursorEntity(
    val ownerKey: String,
    val conversationType: String,
    val conversationId: Long,
    val lastMessageId: Long,
    val recallId: Long,
    val historyComplete: Boolean,
    val recallEnabled: Boolean,
    val recallWindow: Int,
    val lastSyncAt: Long,
    @ColumnInfo(defaultValue = "0") val lastReadMessageId: Long = 0,
)
