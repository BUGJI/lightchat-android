package com.benton.lightchat.data.local

import androidx.room.withTransaction
import com.benton.lightchat.data.local.entity.ChannelEntity
import com.benton.lightchat.data.local.entity.DraftEntity
import com.benton.lightchat.data.local.entity.MessageEntity
import com.benton.lightchat.data.local.entity.PrivateChatEntity
import com.benton.lightchat.data.local.entity.PrivateContactEntity
import com.benton.lightchat.data.local.entity.SyncCursorEntity
import com.benton.lightchat.data.local.entity.SyncStateEntity
import com.benton.lightchat.data.remote.ChannelDto
import com.benton.lightchat.data.remote.HistoryData
import com.benton.lightchat.data.remote.MessageDto
import com.benton.lightchat.data.remote.ParentMessageDto
import com.benton.lightchat.data.remote.PrivateChatDto
import com.benton.lightchat.data.remote.RecallEventDto
import com.benton.lightchat.data.remote.RecalledByDto
import com.benton.lightchat.data.remote.SyncData
import com.benton.lightchat.util.TimeFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

class ChatLocalStore(private val database: ChatDatabase) {

    private val dao = database.chatDao()

    fun observeChannels(ownerKey: String): Flow<List<ChannelEntity>> =
        dao.observeChannels(ownerKey)

    suspend fun channel(ownerKey: String, channelId: Long): ChannelDto? =
        dao.channel(ownerKey, channelId)?.toDto()

    fun observePrivateChats(ownerKey: String): Flow<List<PrivateChatEntity>> =
        dao.observePrivateChats(ownerKey)

    fun observeMessages(
        ownerKey: String,
        ref: ConversationRef,
        limit: Int,
    ): Flow<List<MessageEntity>> = dao.observeMessages(
        ownerKey = ownerKey,
        conversationType = ref.type,
        conversationId = ref.id,
        limit = limit,
    )

    suspend fun cursor(ownerKey: String, ref: ConversationRef): ConversationCursor? =
        dao.cursor(ownerKey, ref.type, ref.id)?.toCursor()

    suspend fun contact(ownerKey: String, chatId: Long): PrivateContactEntity? =
        dao.contact(ownerKey, chatId)

    suspend fun countMessages(ownerKey: String, ref: ConversationRef): Long =
        dao.countMessages(ownerKey, ref.type, ref.id)

    suspend fun countMessagesBefore(ownerKey: String, ref: ConversationRef, beforeId: Long): Long =
        dao.countMessagesBefore(ownerKey, ref.type, ref.id, beforeId)

    suspend fun countMessagesFrom(ownerKey: String, ref: ConversationRef, fromId: Long): Long =
        dao.countMessagesFrom(ownerKey, ref.type, ref.id, fromId)

    suspend fun latestMessageId(ownerKey: String, ref: ConversationRef): Long? =
        dao.latestMessageId(ownerKey, ref.type, ref.id)

    suspend fun maxChannelMessageId(ownerKey: String): Long? =
        dao.maxChannelMessageId(ownerKey)

    suspend fun joinedChannelIds(ownerKey: String): List<Long> = dao.joinedChannelIds(ownerKey)

    suspend fun pendingMessages(ownerKey: String): List<MessageDto> =
        dao.pendingMessages(ownerKey).map { it.toDto() }

    suspend fun pendingEntities(ownerKey: String): List<MessageEntity> =
        dao.pendingMessages(ownerKey)

    suspend fun updateStatus(
        ownerKey: String,
        ref: ConversationRef,
        messageId: Long,
        status: String,
    ) {
        dao.updateStatus(ownerKey, ref.type, ref.id, messageId, status)
    }

    suspend fun countMessagesByFileUrl(fileUrl: String): Int = dao.countMessagesByFileUrl(fileUrl)

    suspend fun syncState(ownerKey: String): SyncStateEntity? = dao.syncState(ownerKey)

    suspend fun saveSyncState(state: SyncStateEntity) = dao.upsertSyncState(state)

    suspend fun draft(ownerKey: String, ref: ConversationRef): DraftEntity? =
        dao.draft(ownerKey, ref.type, ref.id)

    suspend fun saveDraft(ownerKey: String, ref: ConversationRef, text: String, parentId: Long) {
        if (text.isBlank() && parentId <= 0) {
            dao.deleteDraft(ownerKey, ref.type, ref.id)
        } else {
            dao.upsertDraft(
                DraftEntity(
                    ownerKey = ownerKey,
                    conversationType = ref.type,
                    conversationId = ref.id,
                    text = text,
                    parentId = parentId,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    suspend fun deleteDraft(ownerKey: String, ref: ConversationRef) {
        dao.deleteDraft(ownerKey, ref.type, ref.id)
    }

    suspend fun setLastRead(ownerKey: String, ref: ConversationRef, messageId: Long) {
        if (messageId <= 0) return
        val previous = dao.cursor(ownerKey, ref.type, ref.id)
        if (previous != null && previous.lastReadMessageId >= messageId) return
        dao.upsertCursor(
            SyncCursorEntity(
                ownerKey = ownerKey,
                conversationType = ref.type,
                conversationId = ref.id,
                lastMessageId = previous?.lastMessageId ?: 0,
                recallId = previous?.recallId ?: 0,
                historyComplete = previous?.historyComplete ?: false,
                recallEnabled = previous?.recallEnabled ?: true,
                recallWindow = previous?.recallWindow ?: 0,
                lastSyncAt = previous?.lastSyncAt ?: 0,
                lastReadMessageId = maxOf(previous?.lastReadMessageId ?: 0, messageId),
            ),
        )
    }

    suspend fun message(ownerKey: String, ref: ConversationRef, messageId: Long): MessageDto? =
        dao.message(ownerKey, ref.type, ref.id, messageId)?.toDto()

    suspend fun hasMessage(ownerKey: String, ref: ConversationRef, messageId: Long): Boolean =
        dao.hasMessage(ownerKey, ref.type, ref.id, messageId) > 0

    suspend fun oldestMessageId(ownerKey: String, ref: ConversationRef): Long? =
        dao.oldestMessageId(ownerKey, ref.type, ref.id)

    suspend fun searchMessages(
        ownerKey: String,
        ref: ConversationRef,
        query: String,
        beforeId: Long?,
        limit: Int,
    ): List<MessageDto> = dao.searchMessages(
        ownerKey = ownerKey,
        conversationType = ref.type,
        conversationId = ref.id,
        pattern = likePattern(query),
        beforeId = beforeId,
        limit = limit,
    ).map { it.toDto() }

    suspend fun applyLists(ownerKey: String, data: SyncData) {
        database.withTransaction {
            val now = System.currentTimeMillis()
            val channels = data.channels.map { it.toEntity(ownerKey, now) }
            if (channels.isNotEmpty()) {
                dao.upsertChannels(channels)
                dao.deleteChannelsNotIn(ownerKey, channels.map { it.id })
            } else {
                dao.deleteChannelsForOwner(ownerKey)
            }
            val chats = data.privateChats.map { it.toEntity(ownerKey, now) }
            if (chats.isNotEmpty()) {
                dao.upsertPrivateChats(chats)
                dao.deletePrivateChatsNotIn(ownerKey, chats.map { it.id })
            } else {
                dao.deletePrivateChatsForOwner(ownerKey)
            }
        }
    }

    suspend fun applySync(
        ownerKey: String,
        ref: ConversationRef,
        data: SyncData,
    ) {
        database.withTransaction {
            val now = System.currentTimeMillis()
            val incoming = data.messages
                .filter { it.belongsTo(ref) }
                .mapNotNull { it.toEntity(ownerKey, ref, now) }
            if (incoming.isNotEmpty()) dao.upsertMessages(incoming)

            data.recalls.filter { it.belongsTo(ref) }.forEach { event ->
                if (event.action == "delete") {
                    dao.deleteMessage(ownerKey, ref.type, ref.id, event.messageId)
                } else {
                    dao.markRecalled(ownerKey, ref.type, ref.id, event.messageId)
                }
            }

            val previous = dao.cursor(ownerKey, ref.type, ref.id)
            val maxIncoming = incoming.maxOfOrNull { it.id } ?: 0
            val serverCursor = if (ref.isChannel) data.latestId else data.privateLatestId
            dao.upsertCursor(
                SyncCursorEntity(
                    ownerKey = ownerKey,
                    conversationType = ref.type,
                    conversationId = ref.id,
                    lastMessageId = maxOf(
                        previous?.lastMessageId ?: 0,
                        maxIncoming,
                        serverCursor,
                    ),
                    recallId = maxOf(previous?.recallId ?: 0, data.recallLatestId),
                    historyComplete = previous?.historyComplete ?: false,
                    recallEnabled = previous?.recallEnabled ?: true,
                    recallWindow = previous?.recallWindow ?: 0,
                    lastSyncAt = now,
                    lastReadMessageId = previous?.lastReadMessageId ?: 0,
                ),
            )

            if (data.channels.isNotEmpty()) {
                dao.upsertChannels(data.channels.map { it.toEntity(ownerKey, now) })
            }
            if (data.privateChats.isNotEmpty()) {
                dao.upsertPrivateChats(data.privateChats.map { it.toEntity(ownerKey, now) })
            }
        }
    }

    suspend fun applyGlobalSync(
        ownerKey: String,
        data: SyncData,
        activeRef: ConversationRef?,
    ): Set<ConversationRef> {
        val changed = mutableSetOf<ConversationRef>()
        database.withTransaction {
            val now = System.currentTimeMillis()

            val incomingByRef = data.messages
                .mapNotNull { message -> message.conversationRef()?.let { it to message } }
                .groupBy({ it.first }, { it.second })

            incomingByRef.forEach { (ref, messages) ->
                val incoming = messages.mapNotNull { it.toEntity(ownerKey, ref, now) }
                if (incoming.isEmpty()) return@forEach
                dao.upsertMessages(incoming)
                changed += ref
                updateCursor(ownerKey, ref, incoming.maxOf { it.id }, null, now)
            }

            data.recalls.forEach { event ->
                val ref = event.conversationRef() ?: return@forEach
                if (event.action == "delete") {
                    dao.deleteMessage(ownerKey, ref.type, ref.id, event.messageId)
                } else {
                    dao.markRecalled(ownerKey, ref.type, ref.id, event.messageId)
                }
                changed += ref
                updateCursor(ownerKey, ref, null, event.recallId, now)
            }

            val channels = data.channels.map { it.toEntity(ownerKey, now) }
            if (channels.isNotEmpty()) {
                dao.upsertChannels(channels)
                dao.deleteChannelsNotIn(ownerKey, channels.map { it.id })
            } else {
                dao.deleteChannelsForOwner(ownerKey)
            }
            val chats = data.privateChats.map { it.toEntity(ownerKey, now) }
            if (chats.isNotEmpty()) {
                dao.upsertPrivateChats(chats)
                dao.deletePrivateChatsNotIn(ownerKey, chats.map { it.id })
            } else {
                dao.deletePrivateChatsForOwner(ownerKey)
            }

            val previous = dao.syncState(ownerKey)
            val activePrivate = activeRef?.takeUnless { it.isChannel }
            dao.upsertSyncState(
                SyncStateEntity(
                    ownerKey = ownerKey,
                    channelCursor = maxOf(previous?.channelCursor ?: 0, data.latestId),
                    recallCursor = maxOf(previous?.recallCursor ?: 0, data.recallLatestId),
                    privateChatId = activePrivate?.id ?: 0,
                    privateCursor = if (activePrivate == null) {
                        0
                    } else {
                        maxOf(
                            if (previous?.privateChatId == activePrivate.id) {
                                previous.privateCursor
                            } else {
                                0
                            },
                            data.privateLatestId,
                        )
                    },
                    lastSyncAt = now,
                ),
            )
        }
        return changed
    }

    private suspend fun updateCursor(
        ownerKey: String,
        ref: ConversationRef,
        messageId: Long?,
        recallId: Long?,
        now: Long,
    ) {
        val previous = dao.cursor(ownerKey, ref.type, ref.id)
        val lastMessageId = maxOf(previous?.lastMessageId ?: 0, messageId ?: 0)
        if (previous == null && lastMessageId == 0L && recallId == null) return
        dao.upsertCursor(
            SyncCursorEntity(
                ownerKey = ownerKey,
                conversationType = ref.type,
                conversationId = ref.id,
                lastMessageId = lastMessageId,
                recallId = maxOf(previous?.recallId ?: 0, recallId ?: 0),
                historyComplete = previous?.historyComplete ?: false,
                recallEnabled = previous?.recallEnabled ?: true,
                recallWindow = previous?.recallWindow ?: 0,
                lastSyncAt = now,
                lastReadMessageId = previous?.lastReadMessageId ?: 0,
            ),
        )
    }

    suspend fun applyHistory(
        ownerKey: String,
        ref: ConversationRef,
        data: HistoryData,
    ) {
        database.withTransaction {
            val now = System.currentTimeMillis()
            val incoming = data.messages
                .filter { it.belongsTo(ref) }
                .mapNotNull { it.toEntity(ownerKey, ref, now) }
            if (incoming.isNotEmpty()) dao.upsertMessages(incoming)

            val previous = dao.cursor(ownerKey, ref.type, ref.id)
            val maxIncoming = incoming.maxOfOrNull { it.id } ?: 0
            dao.upsertCursor(
                SyncCursorEntity(
                    ownerKey = ownerKey,
                    conversationType = ref.type,
                    conversationId = ref.id,
                    lastMessageId = maxOf(previous?.lastMessageId ?: 0, maxIncoming),
                    recallId = maxOf(previous?.recallId ?: 0, data.recallLatestId),
                    historyComplete = !data.hasMore,
                    recallEnabled = data.recallEnabled,
                    recallWindow = data.recallWindow,
                    lastSyncAt = now,
                    lastReadMessageId = previous?.lastReadMessageId ?: 0,
                ),
            )
        }
    }

    suspend fun cacheMessages(ownerKey: String, ref: ConversationRef, messages: List<MessageDto>) {
        val incoming = messages.mapNotNull { it.toEntity(ownerKey, ref, System.currentTimeMillis()) }
        if (incoming.isNotEmpty()) dao.upsertMessages(incoming)
    }

    suspend fun confirmOutgoing(
        ownerKey: String,
        ref: ConversationRef,
        localId: Long,
        message: MessageDto,
    ): Boolean {
        val entity = message.toEntity(ownerKey, ref, System.currentTimeMillis()) ?: return false
        database.withTransaction {
            dao.deleteMessage(ownerKey, ref.type, ref.id, localId)
            dao.upsertMessages(listOf(entity))
            val previous = dao.cursor(ownerKey, ref.type, ref.id)
            dao.upsertCursor(
                SyncCursorEntity(
                    ownerKey = ownerKey,
                    conversationType = ref.type,
                    conversationId = ref.id,
                    lastMessageId = maxOf(previous?.lastMessageId ?: 0, entity.id),
                    recallId = previous?.recallId ?: 0,
                    historyComplete = previous?.historyComplete ?: false,
                    recallEnabled = previous?.recallEnabled ?: true,
                    recallWindow = previous?.recallWindow ?: 0,
                    lastSyncAt = System.currentTimeMillis(),
                    lastReadMessageId = previous?.lastReadMessageId ?: 0,
                ),
            )
        }
        return true
    }

    suspend fun setMessageStatus(
        ownerKey: String,
        ref: ConversationRef,
        messageId: Long,
        status: String,
    ) {
        dao.updateStatus(ownerKey, ref.type, ref.id, messageId, status)
    }

    suspend fun cacheOutgoing(ownerKey: String, ref: ConversationRef, message: MessageDto) {
        val entity = message.toEntity(ownerKey, ref, System.currentTimeMillis()) ?: return
        database.withTransaction {
            dao.upsertMessages(listOf(entity))
            val previous = dao.cursor(ownerKey, ref.type, ref.id)
            dao.upsertCursor(
                SyncCursorEntity(
                    ownerKey = ownerKey,
                    conversationType = ref.type,
                    conversationId = ref.id,
                    lastMessageId = maxOf(previous?.lastMessageId ?: 0, entity.id),
                    recallId = previous?.recallId ?: 0,
                    historyComplete = previous?.historyComplete ?: false,
                    recallEnabled = previous?.recallEnabled ?: true,
                    recallWindow = previous?.recallWindow ?: 0,
                    lastSyncAt = System.currentTimeMillis(),
                    lastReadMessageId = previous?.lastReadMessageId ?: 0,
                ),
            )
        }
    }

    suspend fun markRecalled(ownerKey: String, ref: ConversationRef, messageId: Long) {
        dao.markRecalled(ownerKey, ref.type, ref.id, messageId)
    }

    suspend fun deleteMessage(ownerKey: String, ref: ConversationRef, messageId: Long) {
        dao.deleteMessage(ownerKey, ref.type, ref.id, messageId)
    }

    suspend fun markChannelRead(ownerKey: String, channelId: Long) {
        dao.markChannelRead(ownerKey, channelId)
    }

    suspend fun savePrivateContact(
        ownerKey: String,
        chatId: Long,
        nickname: String,
        dnd: Boolean,
    ) {
        val now = System.currentTimeMillis()
        database.withTransaction {
            dao.upsertContact(
                PrivateContactEntity(
                    ownerKey = ownerKey,
                    chatId = chatId,
                    nickname = nickname,
                    dnd = if (dnd) 1 else 0,
                    updatedAt = now,
                ),
            )
            dao.updatePrivateChatDnd(ownerKey, chatId, if (dnd) 1 else 0)
        }
    }

    suspend fun deletePrivateChat(ownerKey: String, chatId: Long) {
        val ref = ConversationRef.private(chatId)
        database.withTransaction {
            dao.deleteMessagesForConversation(ownerKey, ref.type, ref.id)
            dao.deleteCursor(ownerKey, ref.type, ref.id)
            dao.deletePrivateChat(ownerKey, chatId)
            dao.deleteContact(ownerKey, chatId)
        }
    }

    suspend fun clearMessages(ownerKey: String) {
        database.withTransaction {
            dao.deleteMessagesForOwner(ownerKey)
            dao.deleteCursorsForOwner(ownerKey)
            dao.deleteSyncState(ownerKey)
        }
    }

    suspend fun clearAll() {
        database.withTransaction {
            dao.deleteAllMessages()
            dao.deleteAllCursors()
            dao.deleteAllSyncState()
            dao.deleteAllChannels()
            dao.deleteAllPrivateChats()
            dao.deleteAllContacts()
            dao.deleteAllDrafts()
        }
    }

    suspend fun countConversations(ownerKey: String): Long =
        dao.countChannels(ownerKey) + dao.countPrivateChats(ownerKey)

    suspend fun countMessages(ownerKey: String): Long = dao.countMessagesForOwner(ownerKey)
}

private fun SyncCursorEntity.toCursor(): ConversationCursor = ConversationCursor(
    lastMessageId = lastMessageId,
    recallId = recallId,
    historyComplete = historyComplete,
    recallEnabled = recallEnabled,
    recallWindow = recallWindow,
    lastReadMessageId = lastReadMessageId,
)

fun ChannelDto.toEntity(ownerKey: String, updatedAt: Long): ChannelEntity = ChannelEntity(
    ownerKey = ownerKey,
    id = id,
    name = name,
    displayName = displayName,
    type = type,
    description = description,
    announcement = announcement,
    ownerId = ownerId,
    memberCount = memberCount,
    isJoined = isJoined,
    unreadCount = unreadCount,
    lastMessage = lastMessage,
    lastMessageAt = lastMessageAt,
    lastMessageAtEpoch = (TimeFormat.parse(lastMessageAt) ?: TimeFormat.parse(createdAt))?.time ?: 0L,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ChannelEntity.toDto(): ChannelDto = ChannelDto(
    id = id,
    name = name,
    displayName = displayName,
    type = type,
    description = description,
    announcement = announcement,
    ownerId = ownerId,
    memberCount = memberCount,
    isJoined = isJoined,
    unreadCount = unreadCount,
    lastMessage = lastMessage,
    lastMessageAt = lastMessageAt,
    createdAt = createdAt,
)

fun PrivateChatDto.toEntity(ownerKey: String, updatedAt: Long): PrivateChatEntity = PrivateChatEntity(
    ownerKey = ownerKey,
    id = id,
    otherUserId = otherUserId,
    otherUsername = otherUsername,
    otherNickname = otherNickname,
    otherAvatar = otherAvatar,
    otherRole = otherRole,
    lastMessage = lastMessage,
    lastMessageAt = lastMessageAt,
    lastMessageAtEpoch = (TimeFormat.parse(lastMessageAt) ?: TimeFormat.parse(createdAt))?.time ?: 0L,
    unreadCount = unreadCount,
    dnd = dnd,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun PrivateChatEntity.toDto(): PrivateChatDto = PrivateChatDto(
    id = id,
    otherUserId = otherUserId,
    otherUsername = otherUsername,
    otherNickname = otherNickname,
    otherAvatar = otherAvatar,
    otherRole = otherRole,
    lastMessage = lastMessage,
    lastMessageAt = lastMessageAt,
    unreadCount = unreadCount,
    dnd = dnd,
    createdAt = createdAt,
)

fun MessageDto.toEntity(
    ownerKey: String,
    ref: ConversationRef?,
    updatedAt: Long,
): MessageEntity? {
    if (id == 0L) return null
    val conversation = ref ?: when {
        channelId != null -> ConversationRef.channel(channelId)
        privateChatId != null -> ConversationRef.private(privateChatId)
        chatId != null -> ConversationRef.private(chatId)
        else -> return null
    }
    return MessageEntity(
        ownerKey = ownerKey,
        conversationType = conversation.type,
        conversationId = conversation.id,
        id = id,
        channelId = channelId ?: if (conversation.isChannel) conversation.id else null,
        chatId = chatId,
        privateChatId = privateChatId ?: if (!conversation.isChannel) conversation.id else null,
        userId = userId,
        fromUserId = fromUserId,
        toUserId = toUserId,
        username = username,
        avatar = avatar,
        role = role,
        parentId = parentId,
        type = type,
        content = content,
        fileUrl = fileUrl,
        fileSize = fileSize,
        mentionedUsers = mentionedUsers,
        recalledById = recalledBy?.id,
        recalledByUsername = recalledBy?.username,
        isRead = isRead,
        createdAt = createdAt,
        createdAtEpoch = TimeFormat.parse(createdAt)?.time ?: 0L,
        updatedAt = updatedAt,
        status = status,
        clientMsgId = clientMsgId,
        fileId = fileId,
        parentPreviewJson = parent?.let { preview ->
            runCatching { parentJson.encodeToString(ParentMessageDto.serializer(), preview) }.getOrNull()
        },
    )
}

fun MessageEntity.toDto(): MessageDto = MessageDto(
    id = id,
    channelId = channelId,
    chatId = chatId,
    privateChatId = privateChatId,
    userId = userId,
    fromUserId = fromUserId,
    toUserId = toUserId,
    username = username,
    avatar = avatar,
    role = role,
    parentId = parentId,
    parent = parentPreviewJson?.let { raw ->
        runCatching { parentJson.decodeFromString(ParentMessageDto.serializer(), raw) }.getOrNull()
    },
    type = type,
    content = content,
    fileUrl = fileUrl,
    fileSize = fileSize,
    fileId = fileId,
    mentionedUsers = mentionedUsers,
    recalledBy = recalledById?.let { RecalledByDto(id = it, username = recalledByUsername.orEmpty()) },
    isRead = isRead,
    createdAt = createdAt,
    status = status,
    clientMsgId = clientMsgId,
)

private fun MessageDto.conversationRef(): ConversationRef? = when {
    channelId != null -> ConversationRef.channel(channelId)
    privateChatId != null -> ConversationRef.private(privateChatId)
    chatId != null -> ConversationRef.private(chatId)
    else -> null
}

private fun RecallEventDto.conversationRef(): ConversationRef? = when (messageType) {
    "channel" -> channelId?.let { ConversationRef.channel(it) }
    "private" -> privateChatId?.let { ConversationRef.private(it) }
    else -> null
}

private fun MessageDto.belongsTo(ref: ConversationRef): Boolean = when {
    ref.isChannel -> channelId == ref.id ||
        (channelId == null && privateChatId == null && chatId == null)

    else -> privateChatId == ref.id || chatId == ref.id ||
        (channelId == null && privateChatId == null && chatId == null)
}

private fun RecallEventDto.belongsTo(ref: ConversationRef): Boolean = when {
    ref.isChannel -> messageType == "channel" && channelId == ref.id
    else -> messageType == "private" && privateChatId == ref.id
}

private val parentJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

private fun likePattern(query: String): String {
    val escaped = query
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
    return "%$escaped%"
}
