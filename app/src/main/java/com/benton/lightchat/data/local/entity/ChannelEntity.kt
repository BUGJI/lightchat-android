package com.benton.lightchat.data.local.entity

import androidx.room.Entity

@Entity(tableName = "channels", primaryKeys = ["ownerKey", "id"])
data class ChannelEntity(
    val ownerKey: String,
    val id: Long,
    val name: String,
    val displayName: String,
    val type: String,
    val description: String,
    val announcement: String?,
    val ownerId: Long,
    val memberCount: Int,
    val isJoined: Boolean,
    val unreadCount: Int,
    val lastMessage: String?,
    val lastMessageAt: String?,
    val lastMessageAtEpoch: Long,
    val createdAt: String,
    val updatedAt: Long,
)
