package com.benton.lightchat.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.benton.lightchat.data.local.AccountKey
import com.benton.lightchat.data.local.AppPreferences
import com.benton.lightchat.data.local.AttachmentCache
import com.benton.lightchat.data.local.ChatLocalStore
import com.benton.lightchat.data.local.ConversationCursor
import com.benton.lightchat.data.local.ConversationRef
import com.benton.lightchat.data.local.cache.CacheKeys
import com.benton.lightchat.data.local.cache.SnapshotStore
import com.benton.lightchat.data.local.toDto
import com.benton.lightchat.data.remote.ApiExecutor
import com.benton.lightchat.data.remote.ApiResult
import com.benton.lightchat.data.remote.ChannelCreateData
import com.benton.lightchat.data.remote.ChannelDto
import com.benton.lightchat.data.remote.ChannelIdRequest
import com.benton.lightchat.data.remote.ChannelMembersData
import com.benton.lightchat.data.remote.ChannelsData
import com.benton.lightchat.data.remote.CreateChannelRequest
import com.benton.lightchat.data.remote.CreatePrivateData
import com.benton.lightchat.data.remote.CreatePrivateRequest
import com.benton.lightchat.data.remote.DeleteChannelRequest
import com.benton.lightchat.data.remote.DeleteMessageRequest
import com.benton.lightchat.data.remote.EmptyData
import com.benton.lightchat.data.remote.HistoryData
import com.benton.lightchat.data.remote.InviteChannelRequest
import com.benton.lightchat.data.remote.MemberActionRequest
import com.benton.lightchat.data.remote.MemberActionResult
import com.benton.lightchat.data.remote.MessageDto
import com.benton.lightchat.data.remote.PrivateChatDto
import com.benton.lightchat.data.remote.PrivateChatsData
import com.benton.lightchat.data.remote.PrivateContactData
import com.benton.lightchat.data.remote.RecallRequest
import com.benton.lightchat.data.remote.RecallResultData
import com.benton.lightchat.data.remote.SearchMessagesData
import com.benton.lightchat.data.remote.SendMessageRequest
import com.benton.lightchat.data.remote.SendResultData
import com.benton.lightchat.data.remote.SyncData
import com.benton.lightchat.data.remote.UpdateChannelRequest
import com.benton.lightchat.data.remote.UpdatePrivateContactRequest
import com.benton.lightchat.data.remote.UploadData
import com.benton.lightchat.util.ImageCompressor
import com.benton.lightchat.util.UrlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.io.FileInputStream
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ChatRepository(
    private val context: Context,
    private val executor: ApiExecutor,
    private val preferences: AppPreferences,
    private val localStore: ChatLocalStore,
    private val attachmentCache: AttachmentCache,
    private val snapshotStore: SnapshotStore,
) {

    private val ownerKeyFlow = preferences.stateFlow
        .map { AccountKey.of(it.baseUrl, it.userId) }
        .distinctUntilChanged()

    fun observeChannels(ownerKey: String): Flow<List<ChannelDto>> =
        localStore.observeChannels(ownerKey)
            .map { items -> items.map { it.toDto() } }
            .flowOn(Dispatchers.Default)

    fun observeDiscoverableChannels(): Flow<List<ChannelDto>?> =
        ownerKeyFlow.flatMapLatest { ownerKey ->
            snapshotStore.observe(ownerKey, CacheKeys.DISCOVER_CHANNELS, ChannelsData.serializer())
                .map { it?.channels }
        }.flowOn(Dispatchers.Default)

    fun observeChannelInfo(channelId: Long): Flow<ChannelMembersData?> =
        ownerKeyFlow.flatMapLatest { ownerKey ->
            snapshotStore.observe(
                ownerKey,
                CacheKeys.channelInfo(channelId),
                ChannelMembersData.serializer(),
            )
        }.flowOn(Dispatchers.Default)

    suspend fun channel(ownerKey: String, channelId: Long): ChannelDto? =
        localStore.channel(ownerKey, channelId)

    fun observePrivateChats(ownerKey: String): Flow<List<PrivateChatDto>> =
        localStore.observePrivateChats(ownerKey)
            .map { items -> items.map { it.toDto() } }
            .flowOn(Dispatchers.Default)

    fun observeMessages(
        ownerKey: String,
        ref: ConversationRef,
        limit: Int,
    ): Flow<List<MessageDto>> =
        localStore.observeMessages(ownerKey, ref, limit)
            .map { items -> items.map { it.toDto() } }
            .flowOn(Dispatchers.Default)

    suspend fun cursor(ownerKey: String, ref: ConversationRef): ConversationCursor? =
        localStore.cursor(ownerKey, ref)

    suspend fun countMessages(ownerKey: String, ref: ConversationRef): Long =
        localStore.countMessages(ownerKey, ref)

    suspend fun countMessagesBefore(ownerKey: String, ref: ConversationRef, beforeId: Long): Long =
        localStore.countMessagesBefore(ownerKey, ref, beforeId)

    suspend fun countMessagesFrom(ownerKey: String, ref: ConversationRef, fromId: Long): Long =
        localStore.countMessagesFrom(ownerKey, ref, fromId)

    suspend fun syncLists(ownerKey: String): Boolean {
        val result = sync(lists = true)
        return when (result) {
            is ApiResult.Success -> {
                result.data?.let { localStore.applyLists(ownerKey, it) }
                true
            }

            is ApiResult.Failure -> false
        }
    }

    suspend fun syncConversation(ownerKey: String, ref: ConversationRef): Boolean {
        val cursor = localStore.cursor(ownerKey, ref)
        val result = executor.execute { service ->
            if (ref.isChannel) {
                service.sync(
                    channels = ref.id.toString(),
                    sinceId = cursor?.lastMessageId?.takeIf { it > 0 },
                    recallSinceId = cursor?.recallId?.takeIf { it > 0 },
                    lists = 0,
                )
            } else {
                service.sync(
                    privateChatId = ref.id,
                    privateSinceId = cursor?.lastMessageId?.takeIf { it > 0 },
                    recallSinceId = cursor?.recallId?.takeIf { it > 0 },
                    lists = 0,
                )
            }
        }
        return when (result) {
            is ApiResult.Success -> {
                result.data?.let { localStore.applySync(ownerKey, ref, it) }
                true
            }

            is ApiResult.Failure -> false
        }
    }

    suspend fun refreshHistory(ownerKey: String, ref: ConversationRef): Boolean =
        fetchHistory(ownerKey, ref, before = null)

    suspend fun loadOlderHistory(
        ownerKey: String,
        ref: ConversationRef,
        beforeId: Long,
    ): Boolean = fetchHistory(ownerKey, ref, before = beforeId)

    suspend fun message(ownerKey: String, ref: ConversationRef, messageId: Long): MessageDto? =
        localStore.message(ownerKey, ref, messageId)

    suspend fun ensureMessageAvailable(
        ownerKey: String,
        ref: ConversationRef,
        messageId: Long,
    ): Boolean {
        if (localStore.hasMessage(ownerKey, ref, messageId)) return true
        if (localStore.oldestMessageId(ownerKey, ref) == null) {
            if (!refreshHistory(ownerKey, ref)) return false
            if (localStore.hasMessage(ownerKey, ref, messageId)) return true
        }
        repeat(20) {
            val cursor = localStore.cursor(ownerKey, ref)
            if (cursor?.historyComplete == true) return false
            val oldest = localStore.oldestMessageId(ownerKey, ref) ?: return false
            if (!loadOlderHistory(ownerKey, ref, oldest)) return false
            if (localStore.hasMessage(ownerKey, ref, messageId)) return true
        }
        return false
    }

    suspend fun cacheOutgoing(ownerKey: String, ref: ConversationRef, message: MessageDto) {
        localStore.cacheOutgoing(ownerKey, ref, message)
    }

    suspend fun markRecalled(ownerKey: String, ref: ConversationRef, messageId: Long) {
        localStore.markRecalled(ownerKey, ref, messageId)
    }

    suspend fun deleteCachedMessage(ownerKey: String, ref: ConversationRef, messageId: Long) {
        localStore.deleteMessage(ownerKey, ref, messageId)
    }

    suspend fun deleteCachedAttachment(url: String?) {
        if (url.isNullOrBlank()) return
        if (localStore.countMessagesByFileUrl(url) == 0) {
            attachmentCache.delete(url)
        }
    }

    suspend fun syncGlobal(
        ownerKey: String,
        channelIds: List<Long>,
        activeRef: ConversationRef?,
    ): ApiResult<SyncData> {
        val state = localStore.syncState(ownerKey)
        val channelCursor = if ((state?.channelCursor ?: 0) == 0L) {
            localStore.maxChannelMessageId(ownerKey) ?: 0L
        } else {
            state?.channelCursor ?: 0L
        }
        val activePrivate = activeRef?.takeUnless { it.isChannel }
        val privateSince = activePrivate?.let { ref ->
            if (state?.privateChatId == ref.id) {
                state.privateCursor
            } else {
                localStore.latestMessageId(ownerKey, ref) ?: 0
            }
        }
        return executor.execute { service ->
            service.sync(
                channels = channelIds.takeIf { it.isNotEmpty() }?.joinToString(","),
                sinceId = channelCursor.takeIf { it > 0 },
                privateChatId = activePrivate?.id,
                privateSinceId = if (activePrivate != null) privateSince ?: 0L else null,
                recallSinceId = state?.recallCursor?.takeIf { it > 0 },
                lists = 1,
            )
        }
    }

    suspend fun markChannelRead(ownerKey: String, channelId: Long): Boolean {
        val ref = ConversationRef.channel(channelId)
        val latest = localStore.latestMessageId(ownerKey, ref)
        val result = markChannelRead(channelId)
        if (result is ApiResult.Success) {
            localStore.markChannelRead(ownerKey, channelId)
            if (latest != null) localStore.setLastRead(ownerKey, ref, latest)
            return true
        }
        return false
    }

    suspend fun draft(ownerKey: String, ref: ConversationRef): com.benton.lightchat.data.local.entity.DraftEntity? =
        localStore.draft(ownerKey, ref)

    suspend fun saveDraft(ownerKey: String, ref: ConversationRef, text: String, parentId: Long) =
        localStore.saveDraft(ownerKey, ref, text, parentId)

    suspend fun clearDraft(ownerKey: String, ref: ConversationRef) =
        localStore.deleteDraft(ownerKey, ref)

    suspend fun localPrivateContact(ownerKey: String, chatId: Long): PrivateContactData? {
        val contact = localStore.contact(ownerKey, chatId) ?: return null
        return PrivateContactData(
            chatId = contact.chatId,
            nickname = contact.nickname,
            dnd = contact.dnd,
            hidden = 0,
        )
    }

    suspend fun savePrivateContactLocal(
        ownerKey: String,
        chatId: Long,
        nickname: String,
        dnd: Boolean,
    ) {
        localStore.savePrivateContact(ownerKey, chatId, nickname, dnd)
    }

    suspend fun deletePrivateChatLocal(ownerKey: String, chatId: Long) {
        localStore.deletePrivateChat(ownerKey, chatId)
    }

    suspend fun searchMessages(
        ownerKey: String,
        ref: ConversationRef,
        query: String,
        beforeId: Long? = null,
        limit: Int = 30,
        remote: Boolean = true,
    ): ApiResult<SearchMessagesData> {
        val local = localStore.searchMessages(ownerKey, ref, query, beforeId, limit)
        if (!remote) {
            return ApiResult.Success(
                SearchMessagesData(
                    messages = local,
                    hasMore = local.size >= limit,
                    query = query,
                ),
            )
        }
        val remoteResult = if (ref.isChannel) {
            searchChannelMessages(ref.id, query, beforeId, limit)
        } else {
            searchPrivateMessages(ref.id, query, beforeId, limit)
        }
        return when (remoteResult) {
            is ApiResult.Success -> {
                val data = remoteResult.data
                val remoteMessages = data?.messages.orEmpty()
                if (remoteMessages.isNotEmpty()) {
                    localStore.cacheMessages(ownerKey, ref, remoteMessages)
                }
                val merged = (remoteMessages + local)
                    .distinctBy { it.id }
                    .sortedByDescending { it.id }
                    .take(limit)
                ApiResult.Success(
                    SearchMessagesData(
                        messages = merged,
                        hasMore = data?.hasMore ?: (local.size >= limit),
                        query = query,
                    ),
                )
            }

            is ApiResult.Failure -> {
                if (local.isEmpty()) {
                    remoteResult
                } else {
                    ApiResult.Success(
                        SearchMessagesData(
                            messages = local,
                            hasMore = local.size >= limit,
                            query = query,
                        ),
                    )
                }
            }
        }
    }

    suspend fun downloadAttachment(
        url: String,
        fileNameHint: String? = null,
        onProgress: (Float) -> Unit = {},
    ): ApiResult<File> {
        val baseUrl = preferences.current().baseUrl
        val resolved = UrlUtils.resolve(baseUrl, url)
            ?: return ApiResult.Failure("invalid_url", "文件地址无效")
        attachmentCache.cachedFile(resolved)?.let { file ->
            onProgress(1f)
            return ApiResult.Success(file)
        }
        return attachmentCache.download(resolved, fileNameHint, onProgress)
    }

    suspend fun uploadVoice(
        file: File,
        onProgress: (Float) -> Unit = {},
    ): ApiResult<UploadData> = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() <= 0) {
            return@withContext ApiResult.Failure("upload_failed", "录音文件无效")
        }
        if (file.length() > 5L * 1024 * 1024) {
            return@withContext ApiResult.Failure("file_too_large", "语音不能超过 5MB")
        }
        val body = FileRequestBody(file, "audio/mp4".toMediaTypeOrNull(), onProgress)
        val part = MultipartBody.Part.createFormData("file", file.name, body)
        executor.execute { service -> service.uploadFile(part) }
    }

    suspend fun cacheUploadedVoice(file: File, url: String, fileNameHint: String?) {
        withContext(Dispatchers.IO) {
            val baseUrl = preferences.current().baseUrl
            val resolved = UrlUtils.resolve(baseUrl, url) ?: return@withContext
            attachmentCache.copyFromFile(file, resolved, fileNameHint ?: file.name)
        }
    }

    private suspend fun fetchHistory(
        ownerKey: String,
        ref: ConversationRef,
        before: Long?,
    ): Boolean {
        val result = if (ref.isChannel) {
            channelHistory(ref.id, before, limit = 100)
        } else {
            privateHistory(ref.id, before, limit = 100)
        }
        return when (result) {
            is ApiResult.Success -> {
                result.data?.let { localStore.applyHistory(ownerKey, ref, it) }
                true
            }

            is ApiResult.Failure -> false
        }
    }

    suspend fun sync(
        channelId: Long? = null,
        sinceId: Long? = null,
        privateChatId: Long? = null,
        privateSinceId: Long? = null,
        recallSinceId: Long? = null,
        lists: Boolean = true,
    ): ApiResult<SyncData> = executor.execute { service ->
        service.sync(
            channels = channelId?.toString(),
            sinceId = sinceId,
            privateChatId = privateChatId,
            privateSinceId = privateSinceId,
            recallSinceId = recallSinceId,
            lists = if (lists) 1 else 0,
        )
    }

    suspend fun channels(): ApiResult<List<ChannelDto>> {
        val result = executor.execute { service -> service.channels() }
        return when (result) {
            is ApiResult.Success -> {
                val channels = (result.data as ChannelsData?)?.channels.orEmpty()
                snapshotStore.write(
                    ownerKey(),
                    CacheKeys.DISCOVER_CHANNELS,
                    ChannelsData.serializer(),
                    ChannelsData(channels),
                )
                ApiResult.Success(channels)
            }

            is ApiResult.Failure -> result
        }
    }

    suspend fun privateChats(): ApiResult<List<PrivateChatDto>> {
        val result = executor.execute { service -> service.privateChats() }
        return when (result) {
            is ApiResult.Success -> ApiResult.Success((result.data as PrivateChatsData?)?.chats.orEmpty())
            is ApiResult.Failure -> result
        }
    }

    suspend fun channelHistory(
        channelId: Long,
        before: Long? = null,
        limit: Int = 100,
    ): ApiResult<HistoryData> =
        executor.execute { service -> service.channelHistory(channelId, before, limit) }

    suspend fun privateHistory(
        chatId: Long,
        before: Long? = null,
        limit: Int = 100,
    ): ApiResult<HistoryData> =
        executor.execute { service -> service.privateHistory(chatId, before, limit) }

    suspend fun sendChannelMessage(
        channelId: Long,
        content: String,
        type: String = "text",
        fileId: Long? = null,
        parentId: Long? = null,
        mentionedUsers: List<Long>? = null,
        clientMsgId: String? = null,
    ): ApiResult<SendResultData> = executor.execute { service ->
        service.sendChannelMessage(
            SendMessageRequest(
                channelId = channelId,
                content = content,
                type = type,
                fileId = fileId,
                parentId = parentId,
                mentionedUsers = mentionedUsers,
                clientMsgId = clientMsgId,
            ),
        )
    }

    suspend fun sendPrivateMessage(
        toUserId: Long,
        content: String,
        type: String = "text",
        fileId: Long? = null,
        parentId: Long? = null,
        clientMsgId: String? = null,
    ): ApiResult<SendResultData> = executor.execute { service ->
        service.sendPrivateMessage(
            SendMessageRequest(
                toUserId = toUserId,
                content = content,
                type = type,
                fileId = fileId,
                parentId = parentId,
                clientMsgId = clientMsgId,
            ),
        )
    }

    suspend fun recall(messageId: Long, scope: String): ApiResult<RecallResultData> =
        executor.execute { service -> service.recallMessage(RecallRequest(messageId, scope)) }

    suspend fun deleteMessage(messageId: Long): ApiResult<EmptyData> =
        executor.execute { service -> service.deleteMessage(DeleteMessageRequest(messageId)) }

    suspend fun markChannelRead(channelId: Long): ApiResult<EmptyData> =
        executor.execute { service -> service.markChannelRead(ChannelIdRequest(channelId)) }

    suspend fun createChannel(
        name: String,
        displayName: String,
        type: String,
        description: String?,
    ): ApiResult<ChannelCreateData> = executor.execute { service ->
        service.createChannel(CreateChannelRequest(name, displayName, type, description))
    }

    suspend fun joinChannel(channelId: Long): ApiResult<EmptyData> {
        val result = executor.execute { service -> service.joinChannel(ChannelIdRequest(channelId)) }
        if (result is ApiResult.Success) {
            snapshotStore.delete(ownerKey(), CacheKeys.DISCOVER_CHANNELS)
        }
        return result
    }

    suspend fun leaveChannel(channelId: Long): ApiResult<EmptyData> {
        val result = executor.execute { service -> service.leaveChannel(ChannelIdRequest(channelId)) }
        if (result is ApiResult.Success) invalidateChannelInfo(channelId)
        return result
    }

    suspend fun createPrivateChat(userId: Long): ApiResult<CreatePrivateData> =
        executor.execute { service -> service.createPrivateChat(CreatePrivateRequest(userId)) }

    suspend fun privateContact(chatId: Long): ApiResult<PrivateContactData> =
        executor.execute { service -> service.privateContact(chatId) }

    suspend fun updatePrivateContact(
        chatId: Long,
        nickname: String? = null,
        dnd: Boolean? = null,
    ): ApiResult<EmptyData> = executor.execute { service ->
        service.updatePrivateContact(
            UpdatePrivateContactRequest(
                chatId = chatId,
                action = "update",
                nickname = nickname,
                dnd = dnd?.let { if (it) 1 else 0 },
            ),
        )
    }

    suspend fun deletePrivateContact(chatId: Long): ApiResult<EmptyData> =
        executor.execute { service ->
            service.updatePrivateContact(
                UpdatePrivateContactRequest(chatId = chatId, action = "delete"),
            )
        }

    suspend fun channelMembers(channelId: Long): ApiResult<ChannelMembersData> {
        val result = executor.execute { service -> service.channelMembers(channelId) }
        if (result is ApiResult.Success) {
            result.data?.let {
                snapshotStore.write(
                    ownerKey(),
                    CacheKeys.channelInfo(channelId),
                    ChannelMembersData.serializer(),
                    it,
                )
            }
        }
        return result
    }

    suspend fun inviteChannel(
        channelId: Long,
        username: String? = null,
        userId: Long? = null,
    ): ApiResult<EmptyData> {
        val result = executor.execute { service ->
            service.inviteChannel(InviteChannelRequest(channelId, username, userId))
        }
        if (result is ApiResult.Success) invalidateChannelInfo(channelId)
        return result
    }

    suspend fun updateChannel(
        channelId: Long,
        displayName: String? = null,
        type: String? = null,
        description: String? = null,
    ): ApiResult<EmptyData> {
        val result = executor.execute { service ->
            service.updateChannel(UpdateChannelRequest(channelId, displayName, type, description))
        }
        if (result is ApiResult.Success) invalidateChannelInfo(channelId)
        return result
    }

    suspend fun deleteChannel(channelId: Long): ApiResult<JsonElement> {
        val result = executor.execute { service ->
            service.deleteChannel(DeleteChannelRequest(channelId))
        }
        if (result is ApiResult.Success) invalidateChannelInfo(channelId)
        return result
    }

    suspend fun searchChannelMessages(
        channelId: Long,
        query: String,
        before: Long? = null,
        limit: Int = 30,
    ): ApiResult<SearchMessagesData> = executor.execute { service ->
        service.searchChannelMessages(channelId, query, before, limit)
    }

    suspend fun searchPrivateMessages(
        chatId: Long,
        query: String,
        before: Long? = null,
        limit: Int = 30,
    ): ApiResult<SearchMessagesData> = executor.execute { service ->
        service.searchPrivateMessages(chatId, query, before, limit)
    }

    suspend fun memberAction(
        channelId: Long,
        userId: Long,
        action: String,
        role: String? = null,
        durationMinutes: Int? = null,
    ): ApiResult<MemberActionResult> {
        val result = executor.execute { service ->
            service.memberAction(
                MemberActionRequest(
                    channelId = channelId,
                    userId = userId,
                    action = action,
                    role = role,
                    durationMinutes = durationMinutes,
                ),
            )
        }
        if (result is ApiResult.Success) invalidateChannelInfo(channelId)
        return result
    }

    private suspend fun invalidateChannelInfo(channelId: Long) {
        snapshotStore.delete(ownerKey(), CacheKeys.channelInfo(channelId))
    }

    private suspend fun ownerKey(): String? {
        val prefs = preferences.current()
        return AccountKey.of(prefs.baseUrl, prefs.userId)
    }

    suspend fun upload(
        uri: Uri,
        onProgress: (Float) -> Unit = {},
    ): ApiResult<UploadData> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(uri)
        val originalMime = resolver.getType(uri)
            ?: displayName
                ?.substringAfterLast('.', "")
                ?.lowercase()
                ?.takeIf { it.isNotBlank() }
                ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
            ?: "application/octet-stream"
        val prepared = ImageCompressor.prepare(context, uri)
        try {
            val mime = prepared?.mime ?: originalMime
            val name = prepared?.name ?: displayName ?: "upload_${System.currentTimeMillis()}"
            val size = prepared?.file?.length() ?: querySize(uri)
            val category = when {
                mime.startsWith("image/") -> "image"
                mime.startsWith("audio/") -> "audio"
                else -> "file"
            }
            val limit = when (category) {
                "image" -> 2L * 1024 * 1024
                "audio" -> 5L * 1024 * 1024
                else -> 5L * 1024 * 1024
            }
            if (size != null && size > limit) {
                val label = when (category) {
                    "image" -> "图片不能超过 2MB"
                    "audio" -> "音频不能超过 5MB"
                    else -> "文件不能超过 5MB"
                }
                return@withContext ApiResult.Failure("file_too_large", label)
            }
            val body: RequestBody = if (prepared != null) {
                FileRequestBody(prepared.file, mime.toMediaTypeOrNull(), onProgress)
            } else {
                UriRequestBody(
                    resolver = resolver,
                    uri = uri,
                    mediaType = mime.toMediaTypeOrNull(),
                    length = size ?: -1L,
                    onProgress = onProgress,
                )
            }
            val part = MultipartBody.Part.createFormData("file", name, body)
            val result = executor.execute { service -> service.uploadFile(part) }
            if (result is ApiResult.Success) {
                result.data?.let { data ->
                    val resolved = UrlUtils.resolve(preferences.current().baseUrl, data.fileUrl)
                    if (resolved != null) {
                        if (prepared != null) {
                            attachmentCache.copyFromFile(
                                prepared.file,
                                resolved,
                                data.fileName.ifBlank { prepared.name },
                            )
                        } else {
                            attachmentCache.copyFromUri(
                                uri,
                                resolved,
                                data.fileName.ifBlank { name },
                                resolver,
                            )
                        }
                    }
                }
            }
            result
        } finally {
            prepared?.file?.delete()
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        val resolver = context.contentResolver
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }

    private fun querySize(uri: Uri): Long? {
        val resolver = context.contentResolver
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
        }.getOrNull()
    }
}

private class FileRequestBody(
    private val file: File,
    private val mediaType: MediaType?,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {

    override fun contentType(): MediaType? = mediaType

    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: BufferedSink) {
        val total = file.length()
        if (total <= 0) {
            onProgress(1f)
            return
        }
        var written = 0L
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                sink.write(buffer, 0, read)
                written += read
                onProgress((written.toFloat() / total).coerceIn(0f, 1f))
            }
        }
    }
}

private class UriRequestBody(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val mediaType: MediaType?,
    private val length: Long,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {

    override fun contentType(): MediaType? = mediaType

    override fun contentLength(): Long = length

    override fun writeTo(sink: BufferedSink) {
        val input = resolver.openInputStream(uri) ?: throw IOException("无法读取所选文件")
        input.use { stream ->
            val buffer = ByteArray(64 * 1024)
            var written = 0L
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                sink.write(buffer, 0, read)
                written += read
                if (length > 0) {
                    onProgress((written.toFloat() / length).coerceIn(0f, 1f))
                }
            }
        }
        onProgress(1f)
    }
}
