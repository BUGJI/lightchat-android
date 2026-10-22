package com.benton.lightchat.data.remote

import com.benton.lightchat.data.local.AppPreferences
import com.benton.lightchat.util.ErrorMessages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T?) : ApiResult<T>
    data class Failure(
        val code: String,
        val message: String,
        val httpCode: Int = 0,
    ) : ApiResult<Nothing>
}

class ApiExecutor(
    private val apiClient: ApiClient,
    private val tokenStore: TokenStore,
    private val preferences: AppPreferences,
) {

    private val refreshMutex = Mutex()

    suspend fun <T> execute(call: suspend (ApiService) -> Response<ApiEnvelope<T>>): ApiResult<T> {
        val service = ensureService()
            ?: return ApiResult.Failure("no_server", ErrorMessages.humanize("no_server"))
        if (tokenStore.token == null) {
            tokenStore.token = preferences.current().token
        }

        val staleToken = tokenStore.token
        var response: Response<ApiEnvelope<T>> = try {
            call(service)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return networkFailure(error)
        }

        if (response.code() == 401 && !staleToken.isNullOrBlank()) {
            when (refresh(staleToken)) {
                RefreshResult.Success -> {
                    response = try {
                        call(service)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        return networkFailure(error)
                    }
                }

                RefreshResult.Invalid -> {
                    preferences.clearSession()
                    tokenStore.token = null
                    return ApiResult.Failure(
                        "token_expired",
                        ErrorMessages.humanize("token_expired"),
                        401,
                    )
                }

                RefreshResult.Unavailable -> Unit
            }
        }

        return parse(response)
    }

    suspend fun <T> parse(response: Response<ApiEnvelope<T>>): ApiResult<T> {
        val body = response.body()
        if (response.isSuccessful) {
            if (body == null) {
                return ApiResult.Failure("empty_response", ErrorMessages.humanize("empty_response"))
            }
            if (body.success) return ApiResult.Success(body.data)
            val code = body.error?.code ?: "unknown_error"
            return ApiResult.Failure(code, ErrorMessages.humanize(code, body.error?.message))
        }

        val raw = runCatching { response.errorBody()?.string() }.getOrNull()
        val envelope = raw?.let {
            runCatching { apiClient.json.decodeFromString<ApiEnvelope<JsonElement>>(it) }.getOrNull()
        }
        val code = envelope?.error?.code ?: "http_${response.code()}"
        val fallback = envelope?.error?.message ?: "服务器返回错误（HTTP ${response.code()}）"
        return ApiResult.Failure(code, ErrorMessages.humanize(code, fallback), response.code())
    }

    private suspend fun ensureService(): ApiService? {
        apiClient.service?.let { return it }
        val baseUrl = preferences.current().baseUrl ?: return null
        apiClient.setBaseUrl(baseUrl)
        return apiClient.service
    }

    private suspend fun refresh(staleToken: String): RefreshResult = refreshMutex.withLock {
        val current = tokenStore.token
        if (current.isNullOrBlank()) return@withLock RefreshResult.Invalid
        if (current != staleToken) return@withLock RefreshResult.Success

        val service = apiClient.service ?: return@withLock RefreshResult.Unavailable
        val response = try {
            service.refresh()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return@withLock RefreshResult.Unavailable
        }

        if (response.isSuccessful) {
            val data = response.body()?.data
            if (!data?.token.isNullOrBlank()) {
                preferences.updateToken(data.token, data.expiresAt)
                tokenStore.token = data.token
                return@withLock RefreshResult.Success
            }
        }

        if (response.code() == 401 || response.code() == 403) {
            return@withLock RefreshResult.Invalid
        }
        RefreshResult.Unavailable
    }

    private fun networkFailure(error: Exception): ApiResult.Failure {
        val message = when (error) {
            is UnknownHostException -> "无法解析服务器地址，请检查配置"
            is ConnectException -> "无法连接服务器，请检查地址和网络"
            is SocketTimeoutException -> "连接服务器超时"
            is IOException -> "网络错误：${error.message ?: "请稍后重试"}"
            else -> "请求失败：${error.message ?: error.javaClass.simpleName}"
        }
        return ApiResult.Failure("network_error", message)
    }

    private enum class RefreshResult { Success, Invalid, Unavailable }
}
