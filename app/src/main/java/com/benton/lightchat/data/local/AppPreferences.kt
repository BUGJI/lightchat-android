package com.benton.lightchat.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.benton.lightchat.util.CredentialCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.lightchatDataStore: DataStore<Preferences> by preferencesDataStore(name = "lightchat")

data class PrefsState(
    val baseUrl: String? = null,
    val token: String? = null,
    val tokenExpiresAt: String? = null,
    val absoluteExpiresAt: String? = null,
    val userId: Long = 0,
    val username: String = "",
    val role: String = "",
    val themeMode: String = "system",
    val dynamicColor: Boolean = true,
    val amoledDark: Boolean = false,
    val accentColor: String = "default",
    val cornerRadius: Int = 16,
    val fontScale: Float = 1f,
    val rememberUsername: Boolean = false,
    val rememberPassword: Boolean = false,
    val autoLogin: Boolean = false,
    val savedUsername: String = "",
    val savedPassword: String = "",
    val autoCheckUpdates: Boolean = true,
    val lastUpdateCheckAt: Long = 0,
)

class AppPreferences(private val context: Context) {

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val TOKEN = stringPreferencesKey("token")
        val TOKEN_EXPIRES_AT = stringPreferencesKey("token_expires_at")
        val ABSOLUTE_EXPIRES_AT = stringPreferencesKey("absolute_expires_at")
        val USER_ID = longPreferencesKey("user_id")
        val USERNAME = stringPreferencesKey("username")
        val ROLE = stringPreferencesKey("role")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val AMOLED_DARK = booleanPreferencesKey("amoled_dark")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val CORNER_RADIUS = intPreferencesKey("corner_radius")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val REMEMBER_USERNAME = booleanPreferencesKey("remember_username")
        val REMEMBER_PASSWORD = booleanPreferencesKey("remember_password")
        val AUTO_LOGIN = booleanPreferencesKey("auto_login")
        val SAVED_USERNAME = stringPreferencesKey("saved_username")
        val SAVED_PASSWORD = stringPreferencesKey("saved_password")
        val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
        val LAST_UPDATE_CHECK_AT = longPreferencesKey("last_update_check_at")
    }

    private val tokenLock = Any()
    private var cachedEncryptedToken: String? = null
    private var cachedToken: String? = null

    val stateFlow: Flow<PrefsState> = context.lightchatDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            val storedThemeMode = prefs[Keys.THEME_MODE]
            val legacyAmoled = storedThemeMode == "amoled"
            PrefsState(
                baseUrl = prefs[Keys.BASE_URL],
                token = decryptToken(prefs[Keys.TOKEN]),
                tokenExpiresAt = prefs[Keys.TOKEN_EXPIRES_AT],
                absoluteExpiresAt = prefs[Keys.ABSOLUTE_EXPIRES_AT],
                userId = prefs[Keys.USER_ID] ?: 0,
                username = prefs[Keys.USERNAME].orEmpty(),
                role = prefs[Keys.ROLE].orEmpty(),
                themeMode = if (legacyAmoled) "dark" else storedThemeMode ?: "system",
                dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
                amoledDark = prefs[Keys.AMOLED_DARK] ?: legacyAmoled,
                accentColor = prefs[Keys.ACCENT_COLOR] ?: "default",
                cornerRadius = prefs[Keys.CORNER_RADIUS] ?: 16,
                fontScale = prefs[Keys.FONT_SCALE] ?: 1f,
                rememberUsername = prefs[Keys.REMEMBER_USERNAME] ?: false,
                rememberPassword = prefs[Keys.REMEMBER_PASSWORD] ?: false,
                autoLogin = prefs[Keys.AUTO_LOGIN] ?: false,
                savedUsername = prefs[Keys.SAVED_USERNAME].orEmpty(),
                savedPassword = prefs[Keys.SAVED_PASSWORD].orEmpty(),
                autoCheckUpdates = prefs[Keys.AUTO_CHECK_UPDATES] ?: true,
                lastUpdateCheckAt = prefs[Keys.LAST_UPDATE_CHECK_AT] ?: 0,
            )
        }
        .flowOn(Dispatchers.IO)

    private fun decryptToken(raw: String?): String? {
        synchronized(tokenLock) {
            if (raw == null) {
                cachedEncryptedToken = null
                cachedToken = null
                return null
            }
            if (raw == cachedEncryptedToken) return cachedToken
            val value = CredentialCipher.decryptStored(raw)
            if (value != null) {
                cachedEncryptedToken = raw
                cachedToken = value
            }
            return value
        }
    }

    suspend fun current(): PrefsState = stateFlow.first()

    suspend fun setServer(url: String?) {
        context.lightchatDataStore.edit { prefs ->
            if (url == null) prefs.remove(Keys.BASE_URL) else prefs[Keys.BASE_URL] = url
        }
    }

    suspend fun saveSession(
        token: String,
        expiresAt: String?,
        absoluteExpiresAt: String?,
        userId: Long,
        username: String,
        role: String?,
    ) {
        context.lightchatDataStore.edit { prefs ->
            prefs[Keys.TOKEN] = CredentialCipher.encryptForStorage(token) ?: token
            if (expiresAt == null) prefs.remove(Keys.TOKEN_EXPIRES_AT) else prefs[Keys.TOKEN_EXPIRES_AT] = expiresAt
            if (absoluteExpiresAt == null) {
                prefs.remove(Keys.ABSOLUTE_EXPIRES_AT)
            } else {
                prefs[Keys.ABSOLUTE_EXPIRES_AT] = absoluteExpiresAt
            }
            prefs[Keys.USER_ID] = userId
            prefs[Keys.USERNAME] = username
            if (role == null) prefs.remove(Keys.ROLE) else prefs[Keys.ROLE] = role
        }
    }

    suspend fun updateToken(token: String, expiresAt: String?) {
        context.lightchatDataStore.edit { prefs ->
            prefs[Keys.TOKEN] = CredentialCipher.encryptForStorage(token) ?: token
            if (expiresAt == null) prefs.remove(Keys.TOKEN_EXPIRES_AT) else prefs[Keys.TOKEN_EXPIRES_AT] = expiresAt
        }
    }

    suspend fun migrateTokenEncryption() {
        context.lightchatDataStore.edit { prefs ->
            val raw = prefs[Keys.TOKEN] ?: return@edit
            if (CredentialCipher.isEncryptedStorage(raw)) return@edit
            val plain = CredentialCipher.decrypt(raw) ?: raw
            CredentialCipher.encryptForStorage(plain)?.let { prefs[Keys.TOKEN] = it }
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.lightchatDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.lightchatDataStore.edit { prefs ->
            prefs[Keys.DYNAMIC_COLOR] = enabled
        }
    }

    suspend fun setAmoledDark(enabled: Boolean) {
        context.lightchatDataStore.edit { prefs ->
            prefs[Keys.AMOLED_DARK] = enabled
        }
    }

    suspend fun setAccentColor(value: String) {
        context.lightchatDataStore.edit { prefs ->
            prefs[Keys.ACCENT_COLOR] = value
        }
    }

    suspend fun setCornerRadius(value: Int) {
        context.lightchatDataStore.edit { prefs ->
            prefs[Keys.CORNER_RADIUS] = value
        }
    }

    suspend fun setFontScale(value: Float) {
        context.lightchatDataStore.edit { prefs ->
            prefs[Keys.FONT_SCALE] = value
        }
    }

    suspend fun updateUsername(username: String) {
        context.lightchatDataStore.edit { prefs ->
            prefs[Keys.USERNAME] = username
        }
    }

    suspend fun setAutoCheckUpdates(enabled: Boolean) {
        context.lightchatDataStore.edit { prefs ->
            prefs[Keys.AUTO_CHECK_UPDATES] = enabled
        }
    }

    suspend fun setLastUpdateCheckAt(timestamp: Long) {
        context.lightchatDataStore.edit { prefs ->
            prefs[Keys.LAST_UPDATE_CHECK_AT] = timestamp
        }
    }

    suspend fun saveRememberedLogin(
        username: String,
        encryptedPassword: String?,
        rememberUsername: Boolean,
        rememberPassword: Boolean,
        autoLogin: Boolean,
    ) {
        val keepUsername = rememberUsername
        val keepPassword = rememberUsername && rememberPassword
        val keepAutoLogin = keepPassword && autoLogin
        context.lightchatDataStore.edit { prefs ->
            prefs[Keys.REMEMBER_USERNAME] = keepUsername
            prefs[Keys.REMEMBER_PASSWORD] = keepPassword
            prefs[Keys.AUTO_LOGIN] = keepAutoLogin
            if (keepUsername) {
                prefs[Keys.SAVED_USERNAME] = username
            } else {
                prefs.remove(Keys.SAVED_USERNAME)
            }
            if (keepPassword && encryptedPassword != null) {
                prefs[Keys.SAVED_PASSWORD] = encryptedPassword
            } else {
                prefs.remove(Keys.SAVED_PASSWORD)
            }
        }
    }

    suspend fun saveRememberOptions(
        rememberUsername: Boolean,
        rememberPassword: Boolean,
        autoLogin: Boolean,
    ) {
        val keepUsername = rememberUsername
        val keepPassword = rememberUsername && rememberPassword
        val keepAutoLogin = keepPassword && autoLogin
        context.lightchatDataStore.edit { prefs ->
            prefs[Keys.REMEMBER_USERNAME] = keepUsername
            prefs[Keys.REMEMBER_PASSWORD] = keepPassword
            prefs[Keys.AUTO_LOGIN] = keepAutoLogin
            if (!keepUsername) prefs.remove(Keys.SAVED_USERNAME)
            if (!keepPassword) prefs.remove(Keys.SAVED_PASSWORD)
        }
    }

    suspend fun disableAutoLogin() {
        context.lightchatDataStore.edit { prefs ->
            prefs[Keys.AUTO_LOGIN] = false
        }
    }

    suspend fun clearRememberedUsername() {
        context.lightchatDataStore.edit { prefs ->
            prefs[Keys.REMEMBER_USERNAME] = false
            prefs[Keys.REMEMBER_PASSWORD] = false
            prefs[Keys.AUTO_LOGIN] = false
            prefs.remove(Keys.SAVED_USERNAME)
            prefs.remove(Keys.SAVED_PASSWORD)
        }
    }

    suspend fun clearRememberedPassword() {
        context.lightchatDataStore.edit { prefs ->
            prefs[Keys.REMEMBER_PASSWORD] = false
            prefs[Keys.AUTO_LOGIN] = false
            prefs.remove(Keys.SAVED_PASSWORD)
        }
    }

    suspend fun clearSession() {
        context.lightchatDataStore.edit { prefs ->
            prefs.remove(Keys.TOKEN)
            prefs.remove(Keys.TOKEN_EXPIRES_AT)
            prefs.remove(Keys.ABSOLUTE_EXPIRES_AT)
            prefs.remove(Keys.USER_ID)
            prefs.remove(Keys.USERNAME)
            prefs.remove(Keys.ROLE)
        }
    }
}
