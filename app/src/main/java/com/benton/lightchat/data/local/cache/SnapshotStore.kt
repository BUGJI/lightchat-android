package com.benton.lightchat.data.local.cache

import com.benton.lightchat.util.CredentialCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json

class SnapshotStore(private val database: CacheDatabase) {

    private val dao = database.cacheDao()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun <T> observe(
        ownerKey: String?,
        key: String,
        deserializer: DeserializationStrategy<T>,
    ): Flow<T?> {
        if (ownerKey == null) return flowOf(null)
        return dao.observe(ownerKey, key).map { entry ->
            entry?.let { decode(it.payload, deserializer) }
        }
    }

    suspend fun <T> read(
        ownerKey: String?,
        key: String,
        deserializer: DeserializationStrategy<T>,
    ): T? {
        if (ownerKey == null) return null
        return dao.entry(ownerKey, key)?.let { decode(it.payload, deserializer) }
    }

    suspend fun <T> write(
        ownerKey: String?,
        key: String,
        serializer: SerializationStrategy<T>,
        value: T,
    ) {
        if (ownerKey == null) return
        val encoded = runCatching { json.encodeToString(serializer, value) }.getOrNull() ?: return
        val payload = CredentialCipher.encryptForStorage(encoded) ?: encoded
        dao.upsert(
            CacheEntryEntity(
                ownerKey = ownerKey,
                entryKey = key,
                payload = payload,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun updatedAt(ownerKey: String?, key: String): Long? {
        if (ownerKey == null) return null
        return dao.entry(ownerKey, key)?.updatedAt
    }

    suspend fun isFresh(ownerKey: String?, key: String, ttlMillis: Long): Boolean {
        val timestamp = updatedAt(ownerKey, key) ?: return false
        return System.currentTimeMillis() - timestamp <= ttlMillis
    }

    suspend fun delete(ownerKey: String?, key: String) {
        if (ownerKey == null) return
        dao.delete(ownerKey, key)
    }

    suspend fun clearOwner(ownerKey: String) {
        dao.deleteForOwner(ownerKey)
    }

    suspend fun clearAll() {
        dao.deleteAll()
    }

    private fun <T> decode(payload: String, deserializer: DeserializationStrategy<T>): T? {
        val plain = CredentialCipher.decryptStored(payload) ?: return null
        return runCatching { json.decodeFromString(deserializer, plain) }.getOrNull()
    }
}
