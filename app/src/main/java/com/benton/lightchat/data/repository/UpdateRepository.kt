package com.benton.lightchat.data.repository

import android.content.Context
import com.benton.lightchat.data.remote.ApiResult
import com.benton.lightchat.data.remote.GitHubReleaseDto
import com.benton.lightchat.data.remote.LatestRelease
import com.benton.lightchat.data.remote.toLatestRelease
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class UpdateRepository(context: Context) {

    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var activeDownload: Call? = null

    fun cancelDownload() {
        activeDownload?.cancel()
    }

    suspend fun fetchLatest(): ApiResult<LatestRelease?> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Lightchat-Android")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 404 -> ApiResult.Success(null)
                    response.code == 403 || response.code == 429 -> ApiResult.Failure(
                        "github_rate_limited",
                        "检查更新过于频繁，请稍后再试",
                        response.code,
                    )
                    !response.isSuccessful -> ApiResult.Failure(
                        "http_${response.code}",
                        "检查更新失败（HTTP ${response.code}）",
                        response.code,
                    )
                    else -> {
                        val body = response.body?.string()
                        if (body.isNullOrBlank()) {
                            ApiResult.Failure("empty_response", "检查更新失败：响应为空")
                        } else {
                            val release = json.decodeFromString<GitHubReleaseDto>(body)
                            if (release.draft || release.prerelease) {
                                ApiResult.Success(null)
                            } else {
                                ApiResult.Success(release.toLatestRelease())
                            }
                        }
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            ApiResult.Failure(
                "network_error",
                "检查更新失败：${error.message ?: "网络错误"}",
            )
        }
    }

    suspend fun download(
        release: LatestRelease,
        onProgress: (Int?) -> Unit,
    ): ApiResult<File> = withContext(Dispatchers.IO) {
        val apkUrl = release.apkUrl
        if (apkUrl.isNullOrBlank()) {
            return@withContext ApiResult.Failure("no_apk", "该版本没有可下载的安装包")
        }
        if (!apkUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext ApiResult.Failure("invalid_url", "安装包地址不安全，已取消下载")
        }

        val directory = File(appContext.cacheDir, "updates")
        if (!directory.exists() && !directory.mkdirs()) {
            return@withContext ApiResult.Failure("storage_error", "无法创建下载目录")
        }
        directory.listFiles()?.forEach { it.delete() }

        val safeVersion = release.version.replace(Regex("[^0-9A-Za-z._-]"), "_")
        val target = File(directory, "lightchat-$safeVersion.apk")
        val temp = File(directory, "lightchat-$safeVersion.apk.part")
        var completed = false
        try {
            val request = Request.Builder()
                .url(apkUrl)
                .header("User-Agent", "Lightchat-Android")
                .build()
            val call = client.newCall(request)
            activeDownload = call
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext ApiResult.Failure(
                        "http_${response.code}",
                        "下载失败（HTTP ${response.code}）",
                        response.code,
                    )
                }
                val stream = response.body?.byteStream()
                    ?: return@withContext ApiResult.Failure("empty_body", "下载失败：响应为空")
                val total = response.body?.contentLength()?.takeIf { it > 0 }
                    ?: release.apkSize.takeIf { it > 0 }
                stream.use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var copied = 0L
                        var lastPercent = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            val percent = if (total != null) {
                                ((copied * 100) / total).toInt().coerceIn(0, 100)
                            } else {
                                -1
                            }
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent.takeIf { it >= 0 })
                            }
                        }
                    }
                }
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                return@withContext ApiResult.Failure("storage_error", "无法保存安装包")
            }
            completed = true
            ApiResult.Success(target)
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            ApiResult.Failure("download_failed", "下载失败：${error.message ?: "网络错误"}")
        } catch (error: Exception) {
            ApiResult.Failure("download_failed", "下载失败：${error.message ?: "未知错误"}")
        } finally {
            activeDownload = null
            if (!completed) temp.delete()
        }
    }

    companion object {
        private const val OWNER = "BUGJI"
        private const val REPO = "lightchat-android"
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    }
}
