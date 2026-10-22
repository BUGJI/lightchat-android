package com.benton.lightchat.data.repository

import com.benton.lightchat.data.remote.ApiExecutor
import com.benton.lightchat.data.remote.ApiResult
import com.benton.lightchat.data.remote.EmptyData
import com.benton.lightchat.data.remote.ProfileData
import com.benton.lightchat.data.remote.SessionActionData
import com.benton.lightchat.data.remote.SessionActionRequest
import com.benton.lightchat.data.remote.SessionDto
import com.benton.lightchat.data.remote.SessionsData
import com.benton.lightchat.data.remote.TestNotificationData
import com.benton.lightchat.data.remote.TwoFactorActionRequest
import com.benton.lightchat.data.remote.TwoFactorData
import com.benton.lightchat.data.remote.UpdateProfileRequest
import com.benton.lightchat.data.remote.UserDto
import com.benton.lightchat.data.remote.UsersData

class UserRepository(private val executor: ApiExecutor) {

    suspend fun profile(): ApiResult<UserDto> {
        val result = executor.execute { service -> service.profile() }
        return when (result) {
            is ApiResult.Success -> {
                val user = (result.data as ProfileData?)?.user
                if (user == null) {
                    ApiResult.Failure("not_found", "未获取到用户资料")
                } else {
                    ApiResult.Success(user)
                }
            }

            is ApiResult.Failure -> result
        }
    }

    suspend fun profile(userId: Long): ApiResult<UserDto> {
        val result = executor.execute { service -> service.profile(userId) }
        return when (result) {
            is ApiResult.Success -> {
                val user = (result.data as ProfileData?)?.user
                if (user == null) {
                    ApiResult.Failure("not_found", "未获取到用户资料")
                } else {
                    ApiResult.Success(user)
                }
            }

            is ApiResult.Failure -> result
        }
    }

    suspend fun updateProfile(request: UpdateProfileRequest): ApiResult<EmptyData> =
        executor.execute { service -> service.updateProfile(request) }

    suspend fun changePassword(oldPassword: String, newPassword: String): ApiResult<EmptyData> =
        executor.execute { service ->
            service.updateProfile(
                UpdateProfileRequest(oldPassword = oldPassword, newPassword = newPassword),
            )
        }

    suspend fun testNotification(): ApiResult<TestNotificationData> =
        executor.execute { service -> service.testNotification() }

    suspend fun search(query: String): ApiResult<List<UserDto>> {
        val result = executor.execute { service -> service.searchUsers(query) }
        return when (result) {
            is ApiResult.Success -> ApiResult.Success((result.data as UsersData?)?.users.orEmpty())
            is ApiResult.Failure -> result
        }
    }

    suspend fun users(): ApiResult<List<UserDto>> {
        val result = executor.execute { service -> service.users() }
        return when (result) {
            is ApiResult.Success -> ApiResult.Success((result.data as UsersData?)?.users.orEmpty())
            is ApiResult.Failure -> result
        }
    }

    suspend fun sessions(): ApiResult<List<SessionDto>> {
        val result = executor.execute { service -> service.sessions() }
        return when (result) {
            is ApiResult.Success -> ApiResult.Success((result.data as SessionsData?)?.sessions.orEmpty())
            is ApiResult.Failure -> result
        }
    }

    suspend fun revokeSession(sessionId: Long): ApiResult<SessionActionData> =
        executor.execute { service ->
            service.sessionAction(SessionActionRequest(action = "revoke", sessionId = sessionId))
        }

    suspend fun revokeOtherSessions(): ApiResult<SessionActionData> =
        executor.execute { service ->
            service.sessionAction(SessionActionRequest(action = "revoke_others"))
        }

    suspend fun twoFactorStatus(): ApiResult<TwoFactorData> =
        executor.execute { service -> service.twoFactorStatus() }

    suspend fun setupTwoFactor(): ApiResult<TwoFactorData> =
        executor.execute { service ->
            service.twoFactorAction(TwoFactorActionRequest(action = "setup"))
        }

    suspend fun enableTwoFactor(code: String): ApiResult<TwoFactorData> =
        executor.execute { service ->
            service.twoFactorAction(TwoFactorActionRequest(action = "enable", code = code))
        }

    suspend fun disableTwoFactor(password: String, code: String): ApiResult<TwoFactorData> =
        executor.execute { service ->
            service.twoFactorAction(
                TwoFactorActionRequest(
                    action = "disable",
                    password = password,
                    code = code.ifBlank { null },
                ),
            )
        }
}
