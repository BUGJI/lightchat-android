package com.benton.lightchat.data.repository

import com.benton.lightchat.data.local.AccountKey
import com.benton.lightchat.data.local.ChatLocalStore
import com.benton.lightchat.data.local.ConversationRef
import com.benton.lightchat.data.local.entity.MessageEntity
import com.benton.lightchat.data.local.toDto
import com.benton.lightchat.data.local.AppPreferences
import com.benton.lightchat.data.remote.ApiResult
import com.benton.lightchat.util.TimeFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicBoolean

class SendQueue(
    private val scope: CoroutineScope,
    private val repository: ChatRepository,
    private val localStore: ChatLocalStore,
    private val preferences: AppPreferences,
) {

    private val drainMutex = Mutex()
    private val rerun = AtomicBoolean(false)
    private val lastSentAt = mutableMapOf<String, Long>()

    @Volatile
    private var online = true

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val errors = _errors.asSharedFlow()

    fun setOnline(value: Boolean) {
        val changed = online != value
        online = value
        if (value && changed) drain()
    }

    fun enqueue() {
        drain()
    }

    fun retry(ownerKey: String, ref: ConversationRef, messageId: Long) {
        scope.launch {
            localStore.setMessageStatus(ownerKey, ref, messageId, STATUS_PENDING)
            drain()
        }
    }

    fun discard(ownerKey: String, ref: ConversationRef, messageId: Long) {
        scope.launch {
            localStore.deleteMessage(ownerKey, ref, messageId)
        }
    }

    fun drain() {
        scope.launch {
            if (!drainMutex.tryLock()) {
                rerun.set(true)
                return@launch
            }
            try {
                do {
                    rerun.set(false)
                    drainOnce()
                } while (rerun.get())
            } finally {
                drainMutex.unlock()
            }
        }
    }

    private suspend fun drainOnce() {
        if (!online) return
        val prefs = preferences.current()
        if (prefs.token.isNullOrBlank()) return
        val ownerKey = AccountKey.of(prefs.baseUrl, prefs.userId) ?: return
        val pending = localStore.pendingEntities(ownerKey)
        if (pending.isEmpty()) return

        for (entity in pending) {
            if (!online) return
            val ref = ConversationRef(entity.conversationType, entity.conversationId)
            waitForCooldown(ref)
            when (val result = send(entity)) {
                is ApiResult.Success -> {
                    val data = result.data
                    if (data == null || data.messageId <= 0) {
                        markFailed(ownerKey, ref, entity)
                        continue
                    }
                    val confirmed = entity.toDto().copy(
                        id = data.messageId,
                        chatId = data.chatId ?: entity.chatId,
                        privateChatId = if (ref.isChannel) {
                            entity.privateChatId
                        } else {
                            data.chatId ?: entity.privateChatId ?: ref.id
                        },
                        content = data.content.takeIf { it.isNotBlank() } ?: entity.content,
                        createdAt = TimeFormat.nowServerString(),
                        status = STATUS_SENT,
                    )
                    localStore.confirmOutgoing(ownerKey, ref, entity.id, confirmed)
                    lastSentAt[cooldownKey(ref)] = System.currentTimeMillis()
                }

                is ApiResult.Failure -> {
                    lastSentAt[cooldownKey(ref)] = System.currentTimeMillis()
                    when (result.code) {
                        "network_error" -> {
                            online = false
                            return
                        }

                        "cooldown", "too_many_requests" -> return
                        else -> {
                            markFailed(ownerKey, ref, entity)
                            _errors.tryEmit(result.message)
                        }
                    }
                }
            }
        }
    }

    private suspend fun send(entity: MessageEntity): ApiResult<com.benton.lightchat.data.remote.SendResultData> {
        val parentId = entity.parentId.takeIf { it > 0 }
        val mentions = entity.mentionedUsers
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.takeIf { it.isNotEmpty() }
        return if (entity.conversationType == ConversationRef.TYPE_CHANNEL) {
            repository.sendChannelMessage(
                channelId = entity.conversationId,
                content = entity.content,
                type = entity.type,
                fileId = entity.fileId,
                parentId = parentId,
                mentionedUsers = mentions,
                clientMsgId = entity.clientMsgId,
            )
        } else {
            val toUserId = entity.toUserId ?: return ApiResult.Failure(
                "invalid_user",
                "私聊对象无效",
            )
            repository.sendPrivateMessage(
                toUserId = toUserId,
                content = entity.content,
                type = entity.type,
                fileId = entity.fileId,
                parentId = parentId,
                clientMsgId = entity.clientMsgId,
            )
        }
    }

    private suspend fun markFailed(
        ownerKey: String,
        ref: ConversationRef,
        entity: MessageEntity,
    ) {
        localStore.setMessageStatus(ownerKey, ref, entity.id, STATUS_FAILED)
    }

    private suspend fun waitForCooldown(ref: ConversationRef) {
        val last = lastSentAt[cooldownKey(ref)] ?: return
        val wait = COOLDOWN_MS - (System.currentTimeMillis() - last)
        if (wait > 0) delay(wait)
    }

    private fun cooldownKey(ref: ConversationRef): String = "${ref.type}:${ref.id}"

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_SENT = "sent"
        const val STATUS_FAILED = "failed"
        private const val COOLDOWN_MS = 2_100L
    }
}
