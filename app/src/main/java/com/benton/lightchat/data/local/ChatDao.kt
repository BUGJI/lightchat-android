package com.benton.lightchat.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.benton.lightchat.data.local.entity.ChannelEntity
import com.benton.lightchat.data.local.entity.DraftEntity
import com.benton.lightchat.data.local.entity.MessageEntity
import com.benton.lightchat.data.local.entity.PrivateChatEntity
import com.benton.lightchat.data.local.entity.PrivateContactEntity
import com.benton.lightchat.data.local.entity.SyncCursorEntity
import com.benton.lightchat.data.local.entity.SyncStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Query("SELECT * FROM channels WHERE ownerKey = :ownerKey ORDER BY lastMessageAtEpoch DESC")
    fun observeChannels(ownerKey: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE ownerKey = :ownerKey AND id = :channelId LIMIT 1")
    suspend fun channel(ownerKey: String, channelId: Long): ChannelEntity?

    @Query("SELECT * FROM private_chats WHERE ownerKey = :ownerKey ORDER BY lastMessageAtEpoch DESC")
    fun observePrivateChats(ownerKey: String): Flow<List<PrivateChatEntity>>

    @Query(
        "SELECT * FROM (" +
            "SELECT * FROM messages " +
            "WHERE ownerKey = :ownerKey AND conversationType = :conversationType " +
            "AND conversationId = :conversationId " +
            "ORDER BY createdAtEpoch DESC, id DESC LIMIT :limit" +
            ") ORDER BY createdAtEpoch ASC, id ASC",
    )
    fun observeMessages(
        ownerKey: String,
        conversationType: String,
        conversationId: Long,
        limit: Int,
    ): Flow<List<MessageEntity>>

    @Query(
        "SELECT COUNT(*) FROM messages WHERE ownerKey = :ownerKey " +
            "AND conversationType = :conversationType AND conversationId = :conversationId",
    )
    suspend fun countMessages(
        ownerKey: String,
        conversationType: String,
        conversationId: Long,
    ): Long

    @Query(
        "SELECT COUNT(*) FROM messages WHERE ownerKey = :ownerKey " +
            "AND conversationType = :conversationType AND conversationId = :conversationId " +
            "AND id < :beforeId AND id > 0",
    )
    suspend fun countMessagesBefore(
        ownerKey: String,
        conversationType: String,
        conversationId: Long,
        beforeId: Long,
    ): Long

    @Query(
        "SELECT COUNT(*) FROM messages WHERE ownerKey = :ownerKey " +
            "AND conversationType = :conversationType AND conversationId = :conversationId " +
            "AND id >= :fromId AND id > 0",
    )
    suspend fun countMessagesFrom(
        ownerKey: String,
        conversationType: String,
        conversationId: Long,
        fromId: Long,
    ): Long

    @Query(
        "SELECT MIN(id) FROM messages WHERE ownerKey = :ownerKey " +
            "AND conversationType = :conversationType AND conversationId = :conversationId " +
            "AND id > 0",
    )
    suspend fun oldestMessageId(
        ownerKey: String,
        conversationType: String,
        conversationId: Long,
    ): Long?

    @Query(
        "SELECT MAX(id) FROM messages WHERE ownerKey = :ownerKey " +
            "AND conversationType = 'channel' AND id > 0",
    )
    suspend fun maxChannelMessageId(ownerKey: String): Long?

    @Query(
        "SELECT MAX(id) FROM messages WHERE ownerKey = :ownerKey " +
            "AND conversationType = :conversationType AND conversationId = :conversationId " +
            "AND id > 0",
    )
    suspend fun latestMessageId(
        ownerKey: String,
        conversationType: String,
        conversationId: Long,
    ): Long?

    @Query(
        "SELECT * FROM messages WHERE ownerKey = :ownerKey " +
            "AND conversationType = :conversationType AND conversationId = :conversationId " +
            "AND id = :messageId LIMIT 1",
    )
    suspend fun message(
        ownerKey: String,
        conversationType: String,
        conversationId: Long,
        messageId: Long,
    ): MessageEntity?

    @Query(
        "SELECT COUNT(*) FROM messages WHERE ownerKey = :ownerKey " +
            "AND conversationType = :conversationType AND conversationId = :conversationId " +
            "AND id = :messageId",
    )
    suspend fun hasMessage(
        ownerKey: String,
        conversationType: String,
        conversationId: Long,
        messageId: Long,
    ): Int

    @Query(
        "SELECT * FROM messages WHERE ownerKey = :ownerKey " +
            "AND conversationType = :conversationType AND conversationId = :conversationId " +
            "AND content LIKE :pattern ESCAPE '\\' " +
            "AND (:beforeId IS NULL OR id < :beforeId) " +
            "ORDER BY createdAtEpoch DESC, id DESC LIMIT :limit",
    )
    suspend fun searchMessages(
        ownerKey: String,
        conversationType: String,
        conversationId: Long,
        pattern: String,
        beforeId: Long?,
        limit: Int,
    ): List<MessageEntity>

    @Query(
        "SELECT * FROM messages WHERE ownerKey = :ownerKey AND status = 'pending' " +
            "ORDER BY createdAtEpoch ASC, id ASC",
    )
    suspend fun pendingMessages(ownerKey: String): List<MessageEntity>

    @Query(
        "UPDATE messages SET status = :status WHERE ownerKey = :ownerKey " +
            "AND conversationType = :conversationType AND conversationId = :conversationId " +
            "AND id = :messageId",
    )
    suspend fun updateStatus(
        ownerKey: String,
        conversationType: String,
        conversationId: Long,
        messageId: Long,
        status: String,
    )

    @Query(
        "SELECT id FROM channels WHERE ownerKey = :ownerKey AND isJoined = 1 ORDER BY id ASC",
    )
    suspend fun joinedChannelIds(ownerKey: String): List<Long>

    @Query("SELECT COUNT(*) FROM messages WHERE fileUrl = :fileUrl")
    suspend fun countMessagesByFileUrl(fileUrl: String): Int

    @Query(
        "SELECT * FROM drafts WHERE ownerKey = :ownerKey " +
            "AND conversationType = :conversationType AND conversationId = :conversationId LIMIT 1",
    )
    suspend fun draft(ownerKey: String, conversationType: String, conversationId: Long): DraftEntity?

    @Upsert
    suspend fun upsertDraft(draft: DraftEntity)

    @Query(
        "DELETE FROM drafts WHERE ownerKey = :ownerKey " +
            "AND conversationType = :conversationType AND conversationId = :conversationId",
    )
    suspend fun deleteDraft(ownerKey: String, conversationType: String, conversationId: Long)

    @Query("DELETE FROM drafts WHERE ownerKey = :ownerKey")
    suspend fun deleteDraftsForOwner(ownerKey: String)

    @Query("DELETE FROM drafts")
    suspend fun deleteAllDrafts()

    @Query("SELECT * FROM sync_state WHERE ownerKey = :ownerKey LIMIT 1")
    suspend fun syncState(ownerKey: String): SyncStateEntity?

    @Upsert
    suspend fun upsertSyncState(state: SyncStateEntity)

    @Query("DELETE FROM sync_state WHERE ownerKey = :ownerKey")
    suspend fun deleteSyncState(ownerKey: String)

    @Query("DELETE FROM sync_state")
    suspend fun deleteAllSyncState()

    @Upsert
    suspend fun upsertChannels(items: List<ChannelEntity>)

    @Upsert
    suspend fun upsertPrivateChats(items: List<PrivateChatEntity>)

    @Upsert
    suspend fun upsertMessages(items: List<MessageEntity>)

    @Upsert
    suspend fun upsertContact(contact: PrivateContactEntity)

    @Upsert
    suspend fun upsertCursor(cursor: SyncCursorEntity)

    @Query(
        "SELECT * FROM sync_cursors WHERE ownerKey = :ownerKey " +
            "AND conversationType = :conversationType AND conversationId = :conversationId LIMIT 1",
    )
    suspend fun cursor(
        ownerKey: String,
        conversationType: String,
        conversationId: Long,
    ): SyncCursorEntity?

    @Query(
        "SELECT * FROM private_contacts WHERE ownerKey = :ownerKey AND chatId = :chatId LIMIT 1",
    )
    suspend fun contact(ownerKey: String, chatId: Long): PrivateContactEntity?

    @Query(
        "UPDATE channels SET unreadCount = 0 WHERE ownerKey = :ownerKey AND id = :channelId",
    )
    suspend fun markChannelRead(ownerKey: String, channelId: Long)

    @Query(
        "UPDATE private_chats SET dnd = :dnd WHERE ownerKey = :ownerKey AND id = :chatId",
    )
    suspend fun updatePrivateChatDnd(ownerKey: String, chatId: Long, dnd: Int)

    @Query(
        "DELETE FROM messages WHERE ownerKey = :ownerKey " +
            "AND conversationType = :conversationType AND conversationId = :conversationId " +
            "AND id = :messageId",
    )
    suspend fun deleteMessage(
        ownerKey: String,
        conversationType: String,
        conversationId: Long,
        messageId: Long,
    )

    @Query(
        "UPDATE messages SET type = 'recalled', content = '', fileUrl = NULL " +
            "WHERE ownerKey = :ownerKey " +
            "AND conversationType = :conversationType AND conversationId = :conversationId " +
            "AND id = :messageId",
    )
    suspend fun markRecalled(
        ownerKey: String,
        conversationType: String,
        conversationId: Long,
        messageId: Long,
    )

    @Query(
        "DELETE FROM messages WHERE ownerKey = :ownerKey " +
            "AND conversationType = :conversationType AND conversationId = :conversationId",
    )
    suspend fun deleteMessagesForConversation(
        ownerKey: String,
        conversationType: String,
        conversationId: Long,
    )

    @Query("DELETE FROM messages WHERE ownerKey = :ownerKey")
    suspend fun deleteMessagesForOwner(ownerKey: String)

    @Query(
        "DELETE FROM sync_cursors WHERE ownerKey = :ownerKey " +
            "AND conversationType = :conversationType AND conversationId = :conversationId",
    )
    suspend fun deleteCursor(
        ownerKey: String,
        conversationType: String,
        conversationId: Long,
    )

    @Query("DELETE FROM sync_cursors WHERE ownerKey = :ownerKey")
    suspend fun deleteCursorsForOwner(ownerKey: String)

    @Query("DELETE FROM channels WHERE ownerKey = :ownerKey AND id NOT IN (:ids)")
    suspend fun deleteChannelsNotIn(ownerKey: String, ids: List<Long>)

    @Query("DELETE FROM private_chats WHERE ownerKey = :ownerKey AND id NOT IN (:ids)")
    suspend fun deletePrivateChatsNotIn(ownerKey: String, ids: List<Long>)

    @Query("DELETE FROM channels WHERE ownerKey = :ownerKey")
    suspend fun deleteChannelsForOwner(ownerKey: String)

    @Query("DELETE FROM private_chats WHERE ownerKey = :ownerKey")
    suspend fun deletePrivateChatsForOwner(ownerKey: String)

    @Query("DELETE FROM private_contacts WHERE ownerKey = :ownerKey")
    suspend fun deleteContactsForOwner(ownerKey: String)

    @Query("DELETE FROM private_chats WHERE ownerKey = :ownerKey AND id = :chatId")
    suspend fun deletePrivateChat(ownerKey: String, chatId: Long)

    @Query("DELETE FROM private_contacts WHERE ownerKey = :ownerKey AND chatId = :chatId")
    suspend fun deleteContact(ownerKey: String, chatId: Long)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()

    @Query("DELETE FROM sync_cursors")
    suspend fun deleteAllCursors()

    @Query("DELETE FROM channels")
    suspend fun deleteAllChannels()

    @Query("DELETE FROM private_chats")
    suspend fun deleteAllPrivateChats()

    @Query("DELETE FROM private_contacts")
    suspend fun deleteAllContacts()

    @Query("SELECT COUNT(*) FROM channels WHERE ownerKey = :ownerKey AND isJoined = 1")
    suspend fun countChannels(ownerKey: String): Long

    @Query("SELECT COUNT(*) FROM private_chats WHERE ownerKey = :ownerKey")
    suspend fun countPrivateChats(ownerKey: String): Long

    @Query("SELECT COUNT(*) FROM messages WHERE ownerKey = :ownerKey")
    suspend fun countMessagesForOwner(ownerKey: String): Long
}
