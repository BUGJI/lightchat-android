package com.benton.lightchat.data.sync

import com.benton.lightchat.data.local.AccountKey
import com.benton.lightchat.data.local.AppPreferences
import com.benton.lightchat.data.local.ChatLocalStore
import com.benton.lightchat.data.local.ConversationRef
import com.benton.lightchat.data.remote.ApiResult
import com.benton.lightchat.data.repository.ChatRepository
import com.benton.lightchat.data.repository.SendQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class SyncStatus(
    val online: Boolean = true,
    val syncing: Boolean = false,
    val friendPending: Int = 0,
    val error: String? = null,
)

class SyncCoordinator(
    private val scope: CoroutineScope,
    private val repository: ChatRepository,
    private val localStore: ChatLocalStore,
    private val preferences: AppPreferences,
    private val sendQueue: SendQueue,
) {

    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val _activeRef = MutableStateFlow<ConversationRef?>(null)
    val activeRef: StateFlow<ConversationRef?> = _activeRef.asStateFlow()

    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { runLoop() }
        wake.trySend(Unit)
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun setActive(ref: ConversationRef?) {
        if (_activeRef.value == ref) return
        _activeRef.value = ref
        requestSync()
    }

    fun clearActive(ref: ConversationRef) {
        if (_activeRef.value == ref) setActive(null)
    }

    fun requestSync() {
        wake.trySend(Unit)
    }

    private suspend fun runLoop() {
        var backoff = LIST_INTERVAL
        while (currentCoroutineContext().isActive) {
            val prefs = preferences.current()
            val ownerKey = AccountKey.of(prefs.baseUrl, prefs.userId)
            if (ownerKey == null || prefs.token.isNullOrBlank()) {
                _status.update { it.copy(online = true, syncing = false) }
                delay(LOGIN_WAIT)
                continue
            }

            val active = _activeRef.value
            val channelIds = localStore.joinedChannelIds(ownerKey)
            _status.update { it.copy(syncing = true) }

            val result = repository.syncGlobal(ownerKey, channelIds, active)
            val nextDelay = when (result) {
                is ApiResult.Success -> {
                    val data = result.data
                    if (data != null) {
                        val changed = localStore.applyGlobalSync(ownerKey, data, active)
                        if (active != null && changed.contains(active)) {
                            if (active.isChannel) {
                                repository.markChannelRead(ownerKey, active.id)
                            }
                            localStore.latestMessageId(ownerKey, active)?.let { latest ->
                                localStore.setLastRead(ownerKey, active, latest)
                            }
                        }
                        _status.update {
                            it.copy(
                                online = true,
                                syncing = false,
                                friendPending = data.friendPending,
                                error = null,
                            )
                        }
                    } else {
                        _status.update { it.copy(online = true, syncing = false, error = null) }
                    }
                    sendQueue.setOnline(true)
                    sendQueue.drain()
                    interval(active)
                }

                is ApiResult.Failure -> {
                    _status.update {
                        it.copy(
                            online = false,
                            syncing = false,
                            error = result.message,
                        )
                    }
                    sendQueue.setOnline(false)
                    (backoff * 2).coerceAtMost(MAX_BACKOFF)
                }
            }

            backoff = nextDelay
            withTimeoutOrNull(nextDelay) { wake.receive() }
        }
    }

    private fun interval(active: ConversationRef?): Long =
        if (active != null) CHAT_INTERVAL else LIST_INTERVAL

    companion object {
        private const val CHAT_INTERVAL = 3_000L
        private const val LIST_INTERVAL = 8_000L
        private const val MAX_BACKOFF = 30_000L
        private const val LOGIN_WAIT = 3_000L
    }
}
