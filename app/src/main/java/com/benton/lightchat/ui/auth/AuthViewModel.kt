package com.benton.lightchat.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.benton.lightchat.LightchatApplication
import com.benton.lightchat.data.remote.ApiResult
import com.benton.lightchat.util.CredentialCipher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode { Login, Register }

data class AuthUiState(
    val mode: AuthMode = AuthMode.Login,
    val username: String = "",
    val password: String = "",
    val email: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val rememberUsername: Boolean = false,
    val rememberPassword: Boolean = false,
    val autoLogin: Boolean = false,
    val autoLoginRunning: Boolean = false,
    val autoLoginFinished: Boolean = false,
)

private val deterministicLoginFailures = setOf(
    "invalid_credentials",
    "invalid_username",
    "missing_fields",
    "account_disabled",
    "account_locked",
)

internal fun isDeterministicLoginFailure(code: String): Boolean =
    code in deterministicLoginFailures

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as LightchatApplication).container
    private val authRepository = container.authRepository
    private val preferences = container.preferences
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private var autoLoginAttempted = false

    init {
        viewModelScope.launch { loadRememberedCredentials() }
    }

    fun refreshRememberedCredentials() {
        viewModelScope.launch { loadRememberedCredentials() }
    }

    fun onSessionActive() {
        autoLoginAttempted = false
        if (_state.value.autoLoginFinished) {
            _state.update { it.copy(autoLoginFinished = false) }
        }
    }

    private suspend fun loadRememberedCredentials() {
        val prefs = preferences.current()
        val password = if (prefs.rememberPassword && prefs.savedPassword.isNotBlank()) {
            CredentialCipher.decryptStored(prefs.savedPassword)
        } else {
            null
        }
        val passwordUnavailable = prefs.rememberPassword && password == null
        if (passwordUnavailable) {
            preferences.clearRememberedPassword()
        }
        _state.update { current ->
            current.copy(
                rememberUsername = prefs.rememberUsername,
                rememberPassword = if (passwordUnavailable) false else prefs.rememberPassword,
                autoLogin = if (passwordUnavailable) false else prefs.autoLogin,
                username = if (current.username.isEmpty() && prefs.rememberUsername) {
                    prefs.savedUsername
                } else {
                    current.username
                },
                password = if (current.password.isEmpty() && password != null) {
                    password
                } else {
                    current.password
                },
                error = if (passwordUnavailable) "已保存的密码无法读取，请重新登录" else current.error,
            )
        }
    }

    fun switchMode(mode: AuthMode) {
        if (_state.value.loading) return
        _state.update { it.copy(mode = mode, error = null) }
    }

    fun setUsername(value: String) = _state.update { it.copy(username = value, error = null) }

    fun setPassword(value: String) = _state.update { it.copy(password = value, error = null) }

    fun setEmail(value: String) = _state.update { it.copy(email = value, error = null) }

    fun setRememberUsername(value: Boolean) {
        _state.update {
            val keepPassword = value && it.rememberPassword
            it.copy(
                rememberUsername = value,
                rememberPassword = keepPassword,
                autoLogin = keepPassword && it.autoLogin,
            )
        }
        persistRememberOptions()
    }

    fun setRememberPassword(value: Boolean) {
        _state.update {
            it.copy(
                rememberUsername = it.rememberUsername || value,
                rememberPassword = value,
                autoLogin = value && it.autoLogin,
            )
        }
        persistRememberOptions()
    }

    fun setAutoLogin(value: Boolean) {
        _state.update {
            if (value) {
                it.copy(rememberUsername = true, rememberPassword = true, autoLogin = true)
            } else {
                it.copy(autoLogin = false)
            }
        }
        persistRememberOptions()
    }

    private fun persistRememberOptions() {
        viewModelScope.launch {
            val state = _state.value
            preferences.saveRememberOptions(
                rememberUsername = state.rememberUsername,
                rememberPassword = state.rememberPassword,
                autoLogin = state.autoLogin,
            )
        }
    }

    fun submit() {
        val state = _state.value
        if (state.loading) return

        val username = state.username.trim()
        val email = state.email.trim()
        val validationError = when {
            username.isBlank() -> "请输入用户名"
            state.password.isBlank() -> "请输入密码"
            state.mode == AuthMode.Register && username.length !in 3..20 ->
                "用户名需为 3-20 位字母、数字、下划线或中文"

            state.mode == AuthMode.Register && state.password.length < 6 -> "密码至少需要 6 位"
            state.mode == AuthMode.Register && !email.matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) ->
                "请输入有效的邮箱地址"

            else -> null
        }
        if (validationError != null) {
            _state.update { it.copy(error = validationError) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val result = if (state.mode == AuthMode.Login) {
                authRepository.login(username, state.password)
            } else {
                authRepository.register(username, state.password, email)
            }
            when (result) {
                is ApiResult.Success -> {
                    rememberLogin(username, state.password)
                    _state.update {
                        it.copy(loading = false, password = "", email = "", error = null)
                    }
                }

                is ApiResult.Failure -> {
                    _state.update { it.copy(loading = false, error = result.message) }
                }
            }
        }
    }

    fun tryAutoLogin() {
        if (autoLoginAttempted || _state.value.loading || _state.value.autoLoginRunning) return
        autoLoginAttempted = true
        _state.update {
            it.copy(autoLoginRunning = true, autoLoginFinished = false, error = null)
        }
        viewModelScope.launch {
            val prefs = preferences.current()
            if (!prefs.token.isNullOrBlank() || !prefs.autoLogin) {
                _state.update {
                    it.copy(autoLoginRunning = false, autoLoginFinished = true)
                }
                return@launch
            }

            val username = prefs.savedUsername.ifBlank { _state.value.username }
            val password = if (prefs.rememberPassword && prefs.savedPassword.isNotBlank()) {
                CredentialCipher.decryptStored(prefs.savedPassword)
            } else {
                null
            }
            if (username.isBlank() || !prefs.rememberPassword || password.isNullOrBlank()) {
                preferences.clearRememberedPassword()
                _state.update {
                    it.copy(
                        rememberUsername = prefs.rememberUsername && username.isNotBlank(),
                        rememberPassword = false,
                        autoLogin = false,
                        autoLoginRunning = false,
                        autoLoginFinished = true,
                        username = username,
                        error = "已保存的登录信息不可用，请重新登录",
                    )
                }
                return@launch
            }

            _state.update {
                it.copy(
                    username = username,
                    password = password,
                    rememberUsername = prefs.rememberUsername,
                    rememberPassword = true,
                    autoLogin = true,
                    loading = true,
                    autoLoginRunning = true,
                    error = null,
                )
            }

            when (val result = authRepository.login(username, password)) {
                is ApiResult.Success -> {
                    rememberLogin(username, password)
                    _state.update {
                        it.copy(
                            loading = false,
                            autoLoginRunning = false,
                            autoLoginFinished = true,
                            password = "",
                            error = null,
                        )
                    }
                }

                is ApiResult.Failure -> {
                    val deterministic = isDeterministicLoginFailure(result.code)
                    if (deterministic) {
                        preferences.clearRememberedPassword()
                    }
                    _state.update {
                        it.copy(
                            loading = false,
                            autoLoginRunning = false,
                            autoLoginFinished = true,
                            rememberPassword = if (deterministic) false else it.rememberPassword,
                            autoLogin = if (deterministic) false else it.autoLogin,
                            password = if (deterministic) "" else it.password,
                            error = if (deterministic) {
                                result.message
                            } else {
                                "自动登录失败：${result.message}"
                            },
                        )
                    }
                }
            }
        }
    }

    private suspend fun rememberLogin(username: String, password: String) {
        val state = _state.value
        val rememberUsername = state.rememberUsername
        val rememberPassword = rememberUsername && state.rememberPassword
        val encryptedPassword = if (rememberPassword) {
            CredentialCipher.encryptForStorage(password)
        } else {
            null
        }
        preferences.saveRememberedLogin(
            username = username,
            encryptedPassword = encryptedPassword,
            rememberUsername = rememberUsername,
            rememberPassword = rememberPassword,
            autoLogin = state.autoLogin && rememberPassword,
        )
    }
}
