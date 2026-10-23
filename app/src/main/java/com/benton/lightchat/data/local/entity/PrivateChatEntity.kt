package com.benton.lightchat.data.local.entity

import androidx.room.Entity

@Entity(tableName = "private_chats", primaryKeys = ["ownerKey", "id"])
data class PrivateChatEntity(
    val ownerKey: String,
    val id: Long,
    val otherUserId: Long,
    val otherUsername: String,
    val otherNickname: String?,
    val otherAvatar: String?,
    val otherRole: String?,
    val lastMessage: String?,
    val lastMessageAt: String?,
    val lastMessageAtEpoch: Long,
    val unreadCount: Int,
    val dnd: Int,
    val createdAt: String,
    val updatedAt: Long,
)
