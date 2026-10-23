package com.benton.lightchat.data.cache

import com.benton.lightchat.data.local.AccountKey
import com.benton.lightchat.data.local.AppPreferences
import com.benton.lightchat.data.local.cache.CacheKeys
import com.benton.lightchat.data.local.cache.SnapshotStore
import com.benton.lightchat.data.repository.ChatRepository
import com.benton.lightchat.data.repository.FriendRepository
import com.benton.lightchat.data.repository.ServerRepository
import com.benton.lightchat.data.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

class CacheWarmer(
    private val scope: CoroutineScope,
    private val preferences: AppPreferences,
    private val snapshotStore: SnapshotStore,
    private val userRepository: UserRepository,
    private val friendRepository: FriendRepository,
    private val chatRepository: ChatRepository,
    private val serverRepository: ServerRepository,
) {

    private var running = false

    fun warm() {
        if (running) return
        running = true
        scope.launch {
            try {
                val prefs = preferences.current()
                val ownerKey = AccountKey.of(prefs.baseUrl, prefs.userId) ?: return@launch
                if (prefs.token.isNullOrBlank()) return@launch

                withTimeoutOrNull(TIMEOUT_MS) {
                    supervisorScope {
                        val limiter = Semaphore(2)

                        suspend fun task(key: String, block: suspend () -> Any?) {
                            if (snapshotStore.isFresh(ownerKey, key, TTL_MS)) return
                            limiter.withPermit { runCatching { block() } }
                        }

                        listOf(
                            async { task(CacheKeys.PROFILE_SELF) { userRepository.profile() } },
                            async { task(CacheKeys.FRIENDS) { friendRepository.list() } },
                            async { task(CacheKeys.DISCOVER_USERS) { userRepository.users() } },
                            async {
                                task(CacheKeys.DISCOVER_CHANNELS) { chatRepository.channels() }
                            },
                            async { task(CacheKeys.SESSIONS) { userRepository.sessions() } },
                            async {
                                task(CacheKeys.TWO_FACTOR) { userRepository.twoFactorStatus() }
                            },
                            async {
                                task(CacheKeys.SERVER_STATUS) { serverRepository.serverStatus() }
                            },
                        ).awaitAll()
                    }
                }
            } finally {
                running = false
            }
        }
    }

    companion object {
        private const val TTL_MS = 5L * 60 * 1000
        private const val TIMEOUT_MS = 30_000L
    }
}
