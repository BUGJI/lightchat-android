package com.benton.lightchat.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import com.benton.lightchat.data.remote.InviteChannelRequest
import com.benton.lightchat.data.remote.MemberActionRequest
import com.benton.lightchat.data.remote.MemberActionResult
import com.benton.lightchat.data.remote.PrivateContactData
import com.benton.lightchat.data.remote.EmptyData
import com.benton.lightchat.data.remote.HistoryData
import com.benton.lightchat.data.remote.PrivateChatDto
import com.benton.lightchat.data.remote.PrivateChatsData
import com.benton.lightchat.data.remote.RecallRequest
import com.benton.lightchat.data.remote.RecallResultData
import com.benton.lightchat.data.remote.SearchMessagesData
import com.benton.lightchat.data.remote.SendMessageRequest
import com.benton.lightchat.data.remote.SendResultData
import com.benton.lightchat.data.remote.SyncData
import com.benton.lightchat.data.remote.UpdateChannelRequest
import com.benton.lightchat.data.remote.UpdatePrivateContactRequest
import com.benton.lightchat.data.remote.UploadData
import kotlinx.serialization.json.JsonElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class ChatRepository(
    private val context: Context,
    private val executor: ApiExecutor,
) {

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
            is ApiResult.Success -> ApiResult.Success((result.data as ChannelsData?)?.channels.orEmpty())
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
    ): ApiResult<SendResultData> = executor.execute { service ->
        service.sendChannelMessage(
            SendMessageRequest(
                channelId = channelId,
                content = content,
                type = type,
                fileId = fileId,
            ),
        )
    }

    suspend fun sendPrivateMessage(
        toUserId: Long,
        content: String,
        type: String = "text",
        fileId: Long? = null,
    ): ApiResult<SendResultData> = executor.execute { service ->
        service.sendPrivateMessage(
            SendMessageRequest(
                toUserId = toUserId,
                content = content,
                type = type,
                fileId = fileId,
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

    suspend fun joinChannel(channelId: Long): ApiResult<EmptyData> =
        executor.execute { service -> service.joinChannel(ChannelIdRequest(channelId)) }

    suspend fun leaveChannel(channelId: Long): ApiResult<EmptyData> =
        executor.execute { service -> service.leaveChannel(ChannelIdRequest(channelId)) }

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

    suspend fun channelMembers(channelId: Long): ApiResult<ChannelMembersData> =
        executor.execute { service -> service.channelMembers(channelId) }

    suspend fun inviteChannel(
        channelId: Long,
        username: String? = null,
        userId: Long? = null,
    ): ApiResult<EmptyData> = executor.execute { service ->
        service.inviteChannel(InviteChannelRequest(channelId, username, userId))
    }

    suspend fun updateChannel(
        channelId: Long,
        displayName: String? = null,
        type: String? = null,
        description: String? = null,
    ): ApiResult<EmptyData> = executor.execute { service ->
        service.updateChannel(UpdateChannelRequest(channelId, displayName, type, description))
    }

    suspend fun deleteChannel(channelId: Long): ApiResult<JsonElement> =
        executor.execute { service -> service.deleteChannel(DeleteChannelRequest(channelId)) }

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
    ): ApiResult<MemberActionResult> = executor.execute { service ->
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

    suspend fun upload(uri: Uri): ApiResult<UploadData> = withContext(Dispatchers.IO) {
        val size = querySize(uri)
        if (size != null && size > 12L * 1024 * 1024) {
            return@withContext ApiResult.Failure("file_too_large", "文件过大（超过 12MB）")
        }
        val resolver = context.contentResolver
        val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            ?: return@withContext ApiResult.Failure("upload_failed", "无法读取所选文件")
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val name = queryDisplayName(uri) ?: "upload_${System.currentTimeMillis()}"
        val body = bytes.toRequestBody(mime.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", name, body)
        executor.execute { service -> service.uploadFile(part) }
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
