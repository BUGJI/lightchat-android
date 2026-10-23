package com.benton.lightchat.data.local

import android.content.Context
import android.net.Uri
import com.benton.lightchat.data.remote.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class AttachmentCache(
    context: Context,
    private val client: OkHttpClient,
) {

    private val directory = File(context.filesDir, "attachments").apply { mkdirs() }

    fun cachedFile(url: String, fileNameHint: String? = null): File? {
        if (url.isBlank()) return null
        val file = fileFor(url, fileNameHint)
        return file.takeIf { it.exists() && it.length() > 0 }?.also {
            it.setLastModified(System.currentTimeMillis())
        }
    }

    fun delete(url: String) {
        if (url.isBlank()) return
        runCatching { fileFor(url, null).delete() }
    }

    suspend fun download(
        url: String,
        fileNameHint: String? = null,
        onProgress: (Float) -> Unit = {},
    ): ApiResult<File> = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext ApiResult.Failure("invalid_url", "文件地址无效")
        val target = fileFor(url, fileNameHint)
        if (target.exists() && target.length() > 0) {
            onProgress(1f)
            return@withContext ApiResult.Success(target)
        }
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.part")
        try {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext ApiResult.Failure(
                        "download_failed",
                        "下载失败（HTTP ${response.code}）",
                    )
                }
                val body = response.body
                    ?: return@withContext ApiResult.Failure("download_failed", "下载内容为空")
                val total = body.contentLength()
                if (total > MAX_BYTES) {
                    return@withContext ApiResult.Failure("file_too_large", "文件过大，无法缓存")
                }
                var written = 0L
                body.byteStream().use { input ->
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            written += read
                            if (written > MAX_BYTES) {
                                throw IllegalStateException("file_too_large")
                            }
                            if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                if (written <= 0) {
                    return@withContext ApiResult.Failure("download_failed", "下载内容为空")
                }
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                return@withContext ApiResult.Failure("download_failed", "无法保存文件")
            }
            target.setLastModified(System.currentTimeMillis())
            evictIfNeeded()
            onProgress(1f)
            ApiResult.Success(target)
        } catch (error: Exception) {
            temp.delete()
            val message = if (error.message == "file_too_large") {
                "文件过大，无法缓存"
            } else {
                "下载失败：${error.message ?: "请稍后重试"}"
            }
            ApiResult.Failure("download_failed", message)
        }
    }

    fun copyFromUri(uri: Uri, url: String, fileNameHint: String?, resolver: android.content.ContentResolver) {
        if (url.isBlank()) return
        val target = fileFor(url, fileNameHint)
        if (target.exists() && target.length() > 0) return
        runCatching {
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, "${target.name}.part")
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { output -> input.copyTo(output) }
            } ?: return
            if (temp.length() > 0) {
                if (target.exists()) target.delete()
                temp.renameTo(target)
                target.setLastModified(System.currentTimeMillis())
                evictIfNeeded()
            } else {
                temp.delete()
            }
        }
    }

    fun copyFromFile(source: File, url: String, fileNameHint: String?) {
        if (url.isBlank() || !source.exists()) return
        val target = fileFor(url, fileNameHint)
        if (target.exists() && target.length() > 0) return
        runCatching {
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, "${target.name}.part")
            source.inputStream().use { input ->
                FileOutputStream(temp).use { output -> input.copyTo(output) }
            }
            if (temp.length() > 0) {
                if (target.exists()) target.delete()
                temp.renameTo(target)
                target.setLastModified(System.currentTimeMillis())
                evictIfNeeded()
            } else {
                temp.delete()
            }
        }
    }

    private fun evictIfNeeded() {
        val files = directory.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_TOTAL_BYTES) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= TARGET_TOTAL_BYTES) break
            val length = file.length()
            if (file.delete()) total -= length
        }
    }

    fun clear() {
        directory.deleteRecursively()
        directory.mkdirs()
    }

    fun sizeBytes(): Long = directorySize(directory)

    private fun fileFor(url: String, fileNameHint: String?): File {
        val extension = extensionOf(fileNameHint) ?: extensionOf(url) ?: "bin"
        return File(directory, "${hash(url)}.$extension")
    }

    private fun extensionOf(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val path = runCatching { Uri.parse(raw).lastPathSegment }.getOrNull() ?: raw
        val extension = path.substringAfterLast('.', "")
        return extension.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) }
    }

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    companion object {
        private const val MAX_BYTES = 200L * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 512L * 1024 * 1024
        private const val TARGET_TOTAL_BYTES = 384L * 1024 * 1024
    }
}

internal fun directorySize(directory: File): Long {
    if (!directory.exists()) return 0L
    if (directory.isFile) return directory.length()
    return directory.listFiles()?.sumOf { directorySize(it) } ?: 0L
}
