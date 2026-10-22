package com.benton.lightchat.data.repository

import com.benton.lightchat.data.remote.BlockUserData
import com.benton.lightchat.data.remote.BlockUserRequest
import com.benton.lightchat.data.remote.FriendRequestData
import com.benton.lightchat.data.remote.FriendRequestBody
import com.benton.lightchat.data.remote.FriendsData
import com.benton.lightchat.data.remote.RemoveFriendData
import com.benton.lightchat.data.remote.RemoveFriendRequest
import com.benton.lightchat.data.remote.RespondFriendData
import com.benton.lightchat.data.remote.RespondFriendRequest
import com.benton.lightchat.data.remote.ApiExecutor
import com.benton.lightchat.data.remote.ApiResult

class FriendRepository(private val executor: ApiExecutor) {

    suspend fun list(): ApiResult<FriendsData> =
        executor.execute { service -> service.friends() }

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

    suspend fun respond(requestId: Long, accept: Boolean): ApiResult<RespondFriendData> =
        executor.execute { service ->
            service.respondFriendRequest(
                RespondFriendRequest(
                    requestId = requestId,
                    action = if (accept) "accept" else "reject",
                ),
            )
        }

    suspend fun remove(userId: Long): ApiResult<RemoveFriendData> =
        executor.execute { service -> service.removeFriend(RemoveFriendRequest(userId)) }

    suspend fun setBlocked(userId: Long, blocked: Boolean): ApiResult<BlockUserData> =
        executor.execute { service ->
            service.blockUser(
                BlockUserRequest(
                    userId = userId,
                    action = if (blocked) "block" else "unblock",
                ),
            )
        }
}
