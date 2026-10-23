package com.benton.lightchat.data.repository

import com.benton.lightchat.data.local.AccountKey
import com.benton.lightchat.data.local.AppPreferences
import com.benton.lightchat.data.local.cache.SnapshotStore
import com.benton.lightchat.data.remote.AccountData
import com.benton.lightchat.data.remote.ApiExecutor
import com.benton.lightchat.data.remote.ApiResult
import com.benton.lightchat.data.remote.ForgotPasswordData
import com.benton.lightchat.data.remote.ForgotPasswordRequest
import com.benton.lightchat.data.remote.LoginRequest
import com.benton.lightchat.data.remote.RegisterRequest
import com.benton.lightchat.data.remote.ResendVerificationData
import com.benton.lightchat.data.remote.ResetPasswordData
import com.benton.lightchat.data.remote.ResetPasswordRequest
import com.benton.lightchat.data.remote.TokenStore
import com.benton.lightchat.data.remote.VerifyEmailData
import com.benton.lightchat.data.remote.VerifyEmailRequest

class AuthRepository(
    private val executor: ApiExecutor,
    private val preferences: AppPreferences,
    private val tokenStore: TokenStore,
    private val snapshotStore: SnapshotStore,
) {

    suspend fun login(username: String, password: String): ApiResult<AccountData> {
        val result = executor.execute { service ->
            service.login(LoginRequest(username = username, password = password))
        }
        return persistSession(result)
    }

    suspend fun register(username: String, password: String, email: String): ApiResult<AccountData> {
        val result = executor.execute { service ->
            service.register(RegisterRequest(username = username, password = password, email = email))
        }
        return persistSession(result)
    }

    suspend fun logout() {
        preferences.disableAutoLogin()
        runCatching { executor.execute { service -> service.logout() } }
        clearCachedPages()
        preferences.clearSession()
        tokenStore.token = null
    }

    suspend fun clearSession() {
        clearCachedPages()
        preferences.clearSession()
        tokenStore.token = null
    }

    private suspend fun clearCachedPages() {
        val prefs = preferences.current()
        AccountKey.of(prefs.baseUrl, prefs.userId)?.let { snapshotStore.clearOwner(it) }
    }

    suspend fun forgotPassword(account: String): ApiResult<ForgotPasswordData> =
        executor.execute { service ->
            service.forgotPassword(ForgotPasswordRequest(account = account))
        }

    suspend fun resetPassword(token: String, password: String): ApiResult<ResetPasswordData> =
        executor.execute { service ->
            service.resetPassword(ResetPasswordRequest(token = token, password = password))
        }

    suspend fun verifyEmail(token: String): ApiResult<VerifyEmailData> =
        executor.execute { service ->
            service.verifyEmail(VerifyEmailRequest(token = token))
        }

    suspend fun resendVerification(): ApiResult<ResendVerificationData> =
        executor.execute { service -> service.resendVerification() }

    private suspend fun persistSession(result: ApiResult<AccountData>): ApiResult<AccountData> {
        if (result is ApiResult.Success) {
            val data = result.data
            val token = data?.token
            if (token.isNullOrBlank()) {
                return ApiResult.Failure("invalid_token", "服务器未返回登录令牌")
            }
            preferences.saveSession(
                token = token,
                expiresAt = data.expiresAt,
                absoluteExpiresAt = data.absoluteExpiresAt,
                userId = data.userId,
                username = data.username,
                role = data.role,
            )
            tokenStore.token = token
        }
        return result
    }
}
