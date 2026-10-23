package com.benton.lightchat.data.repository

import com.benton.lightchat.data.local.AccountKey
import com.benton.lightchat.data.local.AppPreferences
import com.benton.lightchat.data.local.cache.CacheKeys
import com.benton.lightchat.data.local.cache.SnapshotStore
import com.benton.lightchat.data.remote.ApiClient
import com.benton.lightchat.data.remote.ApiEnvelope
import com.benton.lightchat.data.remote.ApiExecutor
import com.benton.lightchat.data.remote.ApiResult
import com.benton.lightchat.data.remote.HealthData
import com.benton.lightchat.data.remote.ServerStatusData
import com.benton.lightchat.util.UrlUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import retrofit2.Response
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

data class ServerTestResult(
    val normalizedUrl: String? = null,
    val reachable: Boolean = false,
    val allOk: Boolean = false,
    val latencyMs: Long = 0,
    val message: String = "",
    val phpVersion: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ServerRepository(
    private val apiClient: ApiClient,
    private val executor: ApiExecutor,
    private val preferences: AppPreferences,
    private val snapshotStore: SnapshotStore,
) {

    private val ownerKeyFlow = preferences.stateFlow
        .map { AccountKey.of(it.baseUrl, it.userId) }
        .distinctUntilChanged()

    fun observeServerStatus(): Flow<ServerStatusData?> = ownerKeyFlow.flatMapLatest { ownerKey ->
        snapshotStore.observe(ownerKey, CacheKeys.SERVER_STATUS, ServerStatusData.serializer())
    }

    suspend fun testConnection(input: String): ServerTestResult {
        val normalized = UrlUtils.normalize(input)
            ?: return ServerTestResult(message = "地址格式不正确，请填写 http(s)://主机[:端口]/api")

        val started = System.currentTimeMillis()
        return try {
            val service = apiClient.serviceFor(normalized)
            val response = service.health()
            val latency = System.currentTimeMillis() - started
            val envelope = readEnvelope(response)
            val health: HealthData? = envelope?.data
            val phpVersion = runCatching {
                service.serverStatus().body()?.data?.php?.version?.takeIf { it.isNotBlank() }
            }.getOrNull()

            if (health == null) {
                ServerTestResult(
                    normalizedUrl = normalized,
                    reachable = true,
                    latencyMs = latency,
                    message = envelope?.error?.message ?: "服务器已连接，但响应格式不正确",
                )
            } else {
                ServerTestResult(
                    normalizedUrl = normalized,
                    reachable = true,
                    allOk = health.allOk,
                    latencyMs = latency,
                    message = when {
                        health.allOk -> "服务器连接正常"
                        else -> health.hint ?: envelope?.error?.message ?: "服务器环境检查未通过"
                    },
                    phpVersion = phpVersion,
                )
            }
        } catch (error: Exception) {
            ServerTestResult(
                normalizedUrl = normalized,
                reachable = false,
                message = when (error) {
                    is UnknownHostException -> "无法解析服务器地址"
                    is ConnectException -> "无法连接服务器，请检查地址和网络"
                    is SocketTimeoutException -> "连接服务器超时"
                    else -> "连接失败：${error.message ?: error.javaClass.simpleName}"
                },
            )
        }
    }

    suspend fun serverStatus(): ApiResult<ServerStatusData> {
        val result = executor.execute { service -> service.serverStatus() }
        if (result is ApiResult.Success) {
            result.data?.let {
                snapshotStore.write(
                    ownerKey(),
                    CacheKeys.SERVER_STATUS,
                    ServerStatusData.serializer(),
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

    private fun readEnvelope(response: Response<ApiEnvelope<HealthData>>): ApiEnvelope<HealthData>? {
        response.body()?.let { return it }
        val raw = runCatching { response.errorBody()?.string() }.getOrNull() ?: return null
        return runCatching { apiClient.json.decodeFromString<ApiEnvelope<HealthData>>(raw) }.getOrNull()
    }
}
