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
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as LightchatApplication).container
    private val authRepository = container.authRepository
    private val preferences = container.preferences
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = preferences.current()
            val password = if (prefs.rememberPassword) {
                CredentialCipher.decrypt(prefs.savedPassword)
            } else {
                null
            }
            _state.update {
                it.copy(
                    rememberUsername = prefs.rememberUsername,
                    rememberPassword = prefs.rememberPassword,
                    username = if (prefs.rememberUsername && it.username.isEmpty()) {
                        prefs.savedUsername
                    } else {
                        it.username
                    },
                    password = if (password != null && it.password.isEmpty()) {
                        password
                    } else {
                        it.password
                    },
                )
            }
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
        if (_state.value.rememberUsername == value) return
        _state.update {
            it.copy(rememberUsername = value, rememberPassword = value && it.rememberPassword)
        }
        if (!value) viewModelScope.launch { preferences.clearRememberedUsername() }
    }

    fun setRememberPassword(value: Boolean) {
        if (_state.value.rememberPassword == value) return
        _state.update { it.copy(rememberPassword = value) }
        if (!value) viewModelScope.launch { preferences.clearRememberedPassword() }
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
                    if (state.mode == AuthMode.Login) {
                        rememberLogin(username, state.password)
                    }
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

    private suspend fun rememberLogin(username: String, password: String) {
        val rememberUsername = _state.value.rememberUsername
        val rememberPassword = rememberUsername && _state.value.rememberPassword
        val encryptedPassword = if (rememberPassword) CredentialCipher.encrypt(password) else null
        preferences.saveRememberedLogin(
            username = username,
            encryptedPassword = encryptedPassword,
            rememberUsername = rememberUsername,
            rememberPassword = rememberPassword,
        )
    }
}
