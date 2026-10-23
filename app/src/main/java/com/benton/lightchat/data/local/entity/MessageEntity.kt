package com.benton.lightchat.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "messages",
    primaryKeys = ["ownerKey", "conversationType", "conversationId", "id"],
    indices = [
        Index(value = ["ownerKey", "conversationType", "conversationId", "createdAtEpoch"]),
        Index(value = ["ownerKey", "conversationType", "conversationId", "status"]),
        Index(value = ["ownerKey", "status"]),
        Index(value = ["fileUrl"]),
    ],
)
data class MessageEntity(
    val ownerKey: String,
    val conversationType: String,
    val conversationId: Long,
    val id: Long,
    val channelId: Long?,
    val chatId: Long?,
    val privateChatId: Long?,
    val userId: Long?,
    val fromUserId: Long?,
    val toUserId: Long?,
    val username: String?,
    val avatar: String?,
    val role: String?,
    val parentId: Long,
    val type: String,
    val content: String,
    val fileUrl: String?,
    val fileSize: Long?,
    val mentionedUsers: String?,
    val recalledById: Long?,
    val recalledByUsername: String?,
    val isRead: Int,
    val createdAt: String,
    val createdAtEpoch: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "sent") val status: String = "sent",
    val clientMsgId: String? = null,
    val fileId: Long? = null,
    val parentPreviewJson: String? = null,
)
