package com.benton.lightchat.data.local.entity

import androidx.room.Entity

@Entity(tableName = "private_contacts", primaryKeys = ["ownerKey", "chatId"])
data class PrivateContactEntity(
    val ownerKey: String,
    val chatId: Long,
    val nickname: String,
    val dnd: Int,
    val updatedAt: Long,
)
