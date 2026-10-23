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
    private val byHash = java.util.concurrent.ConcurrentHashMap<String, File>()
    private val indexLock = Any()
    @Volatile
    private var indexed = false
    private val hashCache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > MAX_HASH_CACHE
    }

    fun cachedFile(url: String, fileNameHint: String? = null): File? {
        if (url.isBlank()) return null
        val key = hash(url)
        byHash[key]?.let { cached ->
            if (cached.exists() && cached.length() > 0) {
                return cached.also { it.setLastModified(System.currentTimeMillis()) }
            }
            byHash.remove(key, cached)
        }
        val direct = fileFor(url, fileNameHint)
        if (direct.exists() && direct.length() > 0) {
            byHash[key] = direct
            return direct.also { it.setLastModified(System.currentTimeMillis()) }
        }
        ensureIndex()
        return byHash[key]?.takeIf { it.exists() && it.length() > 0 }?.also {
            it.setLastModified(System.currentTimeMillis())
        }
    }

    private fun ensureIndex() {
        if (indexed) return
        synchronized(indexLock) {
            if (indexed) return
            directory.listFiles()?.forEach { file ->
                if (file.isFile && file.length() > 0) {
                    val name = file.name.substringBefore('.')
                    if (name.length == 64) byHash[name] = file
                }
            }
            indexed = true
        }
    }

    fun delete(url: String) {
        if (url.isBlank()) return
        val key = hash(url)
        val cached = byHash.remove(key)
        runCatching { (cached ?: fileFor(url, null)).delete() }
    }

    suspend fun download(
        url: String,
        fileNameHint: String? = null,
        onProgress: (Float) -> Unit = {},
    ): ApiResult<File> = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext ApiResult.Failure("invalid_url", "文件地址无效")
        cachedFile(url, fileNameHint)?.let { cached ->
            onProgress(1f)
            return@withContext ApiResult.Success(cached)
        }
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
            byHash[hash(url)] = target
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
        if (cachedFile(url, fileNameHint) != null) return
        val target = fileFor(url, fileNameHint)
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
                byHash[hash(url)] = target
                evictIfNeeded()
            } else {
                temp.delete()
            }
        }
    }

    fun copyFromFile(source: File, url: String, fileNameHint: String?) {
        if (url.isBlank() || !source.exists()) return
        if (cachedFile(url, fileNameHint) != null) return
        val target = fileFor(url, fileNameHint)
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
                byHash[hash(url)] = target
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
            if (file.delete()) {
                total -= length
                byHash.remove(file.name.substringBefore('.'))
            }
        }
    }

    fun clear() {
        directory.deleteRecursively()
        directory.mkdirs()
        byHash.clear()
        indexed = false
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
        return extension.takeIf { it.length in 1..8 && it.all(::isAsciiLetterOrDigit) }
    }

    private fun isAsciiLetterOrDigit(char: Char): Boolean =
        char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9'

    private fun hash(value: String): String = synchronized(hashCache) {
        hashCache.getOrPut(value) { sha256(value) }
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        val chars = CharArray(bytes.size * 2)
        for (index in bytes.indices) {
            val byte = bytes[index].toInt() and 0xFF
            chars[index * 2] = HEX_DIGITS[byte ushr 4]
            chars[index * 2 + 1] = HEX_DIGITS[byte and 0x0F]
        }
        return String(chars)
    }

    companion object {
        private const val MAX_BYTES = 200L * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 512L * 1024 * 1024
        private const val TARGET_TOTAL_BYTES = 384L * 1024 * 1024
        private const val MAX_HASH_CACHE = 512
        private val HEX_DIGITS = "0123456789abcdef".toCharArray()
    }
}

internal fun directorySize(directory: File): Long {
    if (!directory.exists()) return 0L
    if (directory.isFile) return directory.length()
    return directory.listFiles()?.sumOf { directorySize(it) } ?: 0L
}
