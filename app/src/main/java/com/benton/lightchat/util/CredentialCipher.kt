package com.benton.lightchat.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object CredentialCipher {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "lightchat_credential_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val STORAGE_PREFIX = "enc:v1:"

    @Volatile
    private var cachedKey: SecretKey? = null

    fun encrypt(plainText: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }.getOrNull()

    fun decrypt(encoded: String): String? = runCatching {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        if (payload.size <= IV_BYTES) return@runCatching null
        val iv = payload.copyOfRange(0, IV_BYTES)
        val data = payload.copyOfRange(IV_BYTES, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
        String(cipher.doFinal(data), Charsets.UTF_8)
    }.getOrNull()

    fun encryptForStorage(plainText: String): String? =
        encrypt(plainText)?.let { STORAGE_PREFIX + it }

    fun isEncryptedStorage(value: String): Boolean = value.startsWith(STORAGE_PREFIX)

    fun decryptStored(value: String): String? = when {
        value.startsWith(STORAGE_PREFIX) -> decrypt(value.removePrefix(STORAGE_PREFIX))
        else -> decrypt(value) ?: value
    }

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        cachedKey?.let { return it }
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val key = (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: generateKey()
        cachedKey = key
        return key
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }
}
