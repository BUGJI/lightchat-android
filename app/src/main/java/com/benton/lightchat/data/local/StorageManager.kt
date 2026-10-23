package com.benton.lightchat.data.local

import android.content.Context
import android.os.StatFs
import androidx.room.withTransaction
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class StorageSnapshot(
    val databaseBytes: Long,
    val imageBytes: Long,
    val attachmentBytes: Long,
    val totalBytes: Long,
    val freeBytes: Long,
    val diskTotalBytes: Long,
    val messageCount: Long,
    val conversationCount: Long,
)

@OptIn(ExperimentalCoilApi::class)
class StorageManager(
    private val context: Context,
    private val database: ChatDatabase,
    private val imageLoader: ImageLoader,
    private val attachmentCache: AttachmentCache,
) {

    private val imageDirectory = File(context.filesDir, "image_cache")

    suspend fun snapshot(ownerKey: String?): StorageSnapshot = withContext(Dispatchers.IO) {
        val dao = database.chatDao()
        val databaseBytes = databaseBytes()
        val imageBytes = directorySize(imageDirectory)
        val attachmentBytes = attachmentCache.sizeBytes()
        val stat = runCatching { StatFs(context.filesDir.absolutePath) }.getOrNull()
        val freeBytes = stat?.availableBytes ?: 0L
        val diskTotalBytes = stat?.totalBytes ?: 0L
        val messageCount = ownerKey?.let { dao.countMessagesForOwner(it) } ?: 0L
        val conversationCount = ownerKey?.let {
            dao.countChannels(it) + dao.countPrivateChats(it)
        } ?: 0L
        StorageSnapshot(
            databaseBytes = databaseBytes,
            imageBytes = imageBytes,
            attachmentBytes = attachmentBytes,
            totalBytes = databaseBytes + imageBytes + attachmentBytes,
            freeBytes = freeBytes,
            diskTotalBytes = diskTotalBytes,
            messageCount = messageCount,
            conversationCount = conversationCount,
        )
    }

    suspend fun clearMessages(ownerKey: String) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val dao = database.chatDao()
            dao.deleteMessagesForOwner(ownerKey)
            dao.deleteCursorsForOwner(ownerKey)
            dao.deleteSyncState(ownerKey)
        }
        vacuum()
    }

    suspend fun clearImages() = withContext(Dispatchers.IO) {
        imageLoader.memoryCache?.clear()
        imageLoader.diskCache?.clear()
    }

    suspend fun clearAttachments() = withContext(Dispatchers.IO) {
        attachmentCache.clear()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        database.withTransaction {
            val dao = database.chatDao()
            dao.deleteAllMessages()
            dao.deleteAllCursors()
            dao.deleteAllChannels()
            dao.deleteAllPrivateChats()
            dao.deleteAllContacts()
            dao.deleteAllDrafts()
            dao.deleteAllSyncState()
        }
        imageLoader.memoryCache?.clear()
        imageLoader.diskCache?.clear()
        attachmentCache.clear()
        vacuum()
    }

    private fun databaseBytes(): Long {
        val file = context.getDatabasePath("lightchat.db")
        val parent = file.parentFile
        val name = file.name
        return listOf(
            file,
            File(parent, "$name-wal"),
            File(parent, "$name-shm"),
        ).sumOf { if (it.exists()) it.length() else 0L }
    }

    private fun vacuum() {
        val db = database.openHelper.writableDatabase
        runCatching {
            db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        }
        runCatching {
            db.execSQL("VACUUM")
        }
    }
}
