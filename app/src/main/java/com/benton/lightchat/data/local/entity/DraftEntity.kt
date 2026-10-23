package com.benton.lightchat.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "drafts",
    primaryKeys = ["ownerKey", "conversationType", "conversationId"],
)
data class DraftEntity(
    val ownerKey: String,
    val conversationType: String,
    val conversationId: Long,
    val text: String,
    val parentId: Long = 0,
    val updatedAt: Long,
)
