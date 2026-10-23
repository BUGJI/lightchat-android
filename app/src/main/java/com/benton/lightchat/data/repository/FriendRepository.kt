package com.benton.lightchat.data.repository

import com.benton.lightchat.data.local.AccountKey
import com.benton.lightchat.data.local.AppPreferences
import com.benton.lightchat.data.local.cache.CacheKeys
import com.benton.lightchat.data.local.cache.SnapshotStore
import com.benton.lightchat.data.remote.ApiExecutor
import com.benton.lightchat.data.remote.ApiResult
import com.benton.lightchat.data.remote.BlockUserData
import com.benton.lightchat.data.remote.BlockUserRequest
import com.benton.lightchat.data.remote.FriendRequestData
import com.benton.lightchat.data.remote.FriendRequestBody
import com.benton.lightchat.data.remote.FriendsData
import com.benton.lightchat.data.remote.RemoveFriendData
import com.benton.lightchat.data.remote.RemoveFriendRequest
import com.benton.lightchat.data.remote.RespondFriendData
import com.benton.lightchat.data.remote.RespondFriendRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class FriendRepository(
    private val executor: ApiExecutor,
    private val preferences: AppPreferences,
    private val snapshotStore: SnapshotStore,
) {

    private val ownerKeyFlow = preferences.stateFlow
        .map { AccountKey.of(it.baseUrl, it.userId) }
        .distinctUntilChanged()

    fun observeFriends(): Flow<FriendsData?> = ownerKeyFlow.flatMapLatest { ownerKey ->
        snapshotStore.observe(ownerKey, CacheKeys.FRIENDS, FriendsData.serializer())
    }.flowOn(Dispatchers.Default)

    suspend fun list(): ApiResult<FriendsData> {
        val result = executor.execute { service -> service.friends() }
        if (result is ApiResult.Success) {
            result.data?.let {
                snapshotStore.write(
                    ownerKey(),
                    CacheKeys.FRIENDS,
                    FriendsData.serializer(),
                    it,
                )
            }
        }
        return result
    }

    suspend fun sendRequest(
        userId: Long? = null,
        username: String? = null,
        message: String? = null,
    ): ApiResult<FriendRequestData> = executor.execute { service ->
        service.sendFriendRequest(
            FriendRequestBody(
                userId = userId,
                username = username,
                message = message?.trim()?.takeIf { it.isNotBlank() },
            ),
        )
    }

    suspend fun respond(requestId: Long, accept: Boolean): ApiResult<RespondFriendData> {
        val result = executor.execute { service ->
            service.respondFriendRequest(
                RespondFriendRequest(
                    requestId = requestId,
                    action = if (accept) "accept" else "reject",
                ),
            )
        }
        if (result is ApiResult.Success) invalidate()
        return result
    }

    suspend fun remove(userId: Long): ApiResult<RemoveFriendData> {
        val result = executor.execute { service -> service.removeFriend(RemoveFriendRequest(userId)) }
        if (result is ApiResult.Success) invalidate()
        return result
    }

    suspend fun setBlocked(userId: Long, blocked: Boolean): ApiResult<BlockUserData> {
        val result = executor.execute { service ->
            service.blockUser(
                BlockUserRequest(
                    userId = userId,
                    action = if (blocked) "block" else "unblock",
                ),
            )
        }
        if (result is ApiResult.Success) invalidate()
        return result
    }

    suspend fun invalidate() {
        snapshotStore.delete(ownerKey(), CacheKeys.FRIENDS)
    }

    private suspend fun ownerKey(): String? {
        val prefs = preferences.current()
        return AccountKey.of(prefs.baseUrl, prefs.userId)
    }
}
