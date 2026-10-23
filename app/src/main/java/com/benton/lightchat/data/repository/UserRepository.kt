package com.benton.lightchat.data.repository

import com.benton.lightchat.data.local.AccountKey
import com.benton.lightchat.data.local.AppPreferences
import com.benton.lightchat.data.local.cache.CacheKeys
import com.benton.lightchat.data.local.cache.SnapshotStore
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class UserRepository(
    private val executor: ApiExecutor,
    private val preferences: AppPreferences,
    private val snapshotStore: SnapshotStore,
) {

    private val ownerKeyFlow = preferences.stateFlow
        .map { AccountKey.of(it.baseUrl, it.userId) }
        .distinctUntilChanged()

    fun observeProfile(userId: Long? = null): Flow<UserDto?> {
        val key = userId?.let { CacheKeys.profile(it) } ?: CacheKeys.PROFILE_SELF
        return ownerKeyFlow.flatMapLatest { ownerKey ->
            snapshotStore.observe(ownerKey, key, UserDto.serializer())
        }
    }

    fun observeSessions(): Flow<List<SessionDto>?> = ownerKeyFlow.flatMapLatest { ownerKey ->
        snapshotStore.observe(ownerKey, CacheKeys.SESSIONS, SessionsData.serializer())
            .map { it?.sessions }
    }

    fun observeTwoFactor(): Flow<TwoFactorData?> = ownerKeyFlow.flatMapLatest { ownerKey ->
        snapshotStore.observe(ownerKey, CacheKeys.TWO_FACTOR, TwoFactorData.serializer())
    }

    fun observeUsers(): Flow<List<UserDto>?> = ownerKeyFlow.flatMapLatest { ownerKey ->
        snapshotStore.observe(ownerKey, CacheKeys.DISCOVER_USERS, UsersData.serializer())
            .map { it?.users }
    }

    suspend fun profile(): ApiResult<UserDto> {
        val result = executor.execute { service -> service.profile() }
        return when (result) {
            is ApiResult.Success -> {
                val user = (result.data as ProfileData?)?.user
                if (user == null) {
                    ApiResult.Failure("not_found", "未获取到用户资料")
                } else {
                    snapshotStore.write(ownerKey(), CacheKeys.PROFILE_SELF, UserDto.serializer(), user)
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
                    snapshotStore.write(
                        ownerKey(),
                        CacheKeys.profile(userId),
                        UserDto.serializer(),
                        user,
                    )
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
            is ApiResult.Success -> {
                val users = (result.data as UsersData?)?.users.orEmpty()
                snapshotStore.write(
                    ownerKey(),
                    CacheKeys.DISCOVER_USERS,
                    UsersData.serializer(),
                    UsersData(users),
                )
                ApiResult.Success(users)
            }

            is ApiResult.Failure -> result
        }
    }

    suspend fun sessions(): ApiResult<List<SessionDto>> {
        val result = executor.execute { service -> service.sessions() }
        return when (result) {
            is ApiResult.Success -> {
                val sessions = (result.data as SessionsData?)?.sessions.orEmpty()
                snapshotStore.write(
                    ownerKey(),
                    CacheKeys.SESSIONS,
                    SessionsData.serializer(),
                    SessionsData(sessions),
                )
                ApiResult.Success(sessions)
            }

            is ApiResult.Failure -> result
        }
    }

    suspend fun revokeSession(sessionId: Long): ApiResult<SessionActionData> {
        val result = executor.execute { service ->
            service.sessionAction(SessionActionRequest(action = "revoke", sessionId = sessionId))
        }
        if (result is ApiResult.Success) snapshotStore.delete(ownerKey(), CacheKeys.SESSIONS)
        return result
    }

    suspend fun revokeOtherSessions(): ApiResult<SessionActionData> {
        val result = executor.execute { service ->
            service.sessionAction(SessionActionRequest(action = "revoke_others"))
        }
        if (result is ApiResult.Success) snapshotStore.delete(ownerKey(), CacheKeys.SESSIONS)
        return result
    }

    suspend fun twoFactorStatus(): ApiResult<TwoFactorData> {
        val result = executor.execute { service -> service.twoFactorStatus() }
        if (result is ApiResult.Success) {
            result.data?.let {
                snapshotStore.write(
                    ownerKey(),
                    CacheKeys.TWO_FACTOR,
                    TwoFactorData.serializer(),
                    it,
                )
            }
        }
        return result
    }

    suspend fun setupTwoFactor(): ApiResult<TwoFactorData> =
        executor.execute { service ->
            service.twoFactorAction(TwoFactorActionRequest(action = "setup"))
        }

    suspend fun enableTwoFactor(code: String): ApiResult<TwoFactorData> {
        val result = executor.execute { service ->
            service.twoFactorAction(TwoFactorActionRequest(action = "enable", code = code))
        }
        if (result is ApiResult.Success) {
            result.data?.let {
                snapshotStore.write(
                    ownerKey(),
                    CacheKeys.TWO_FACTOR,
                    TwoFactorData.serializer(),
                    it,
                )
            }
        }
        return result
    }

    suspend fun disableTwoFactor(password: String, code: String): ApiResult<TwoFactorData> {
        val result = executor.execute { service ->
            service.twoFactorAction(
                TwoFactorActionRequest(
                    action = "disable",
                    password = password,
                    code = code.ifBlank { null },
                ),
            )
        }
        if (result is ApiResult.Success) {
            result.data?.let {
                snapshotStore.write(
                    ownerKey(),
                    CacheKeys.TWO_FACTOR,
                    TwoFactorData.serializer(),
                    it,
                )
            }
        }
        return result
    }

    private suspend fun ownerKey(): String? {
        val prefs = preferences.current()
        return AccountKey.of(prefs.baseUrl, prefs.userId)
    }
}
