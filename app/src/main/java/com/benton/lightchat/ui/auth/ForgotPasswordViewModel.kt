package com.benton.lightchat.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.benton.lightchat.LightchatApplication
import com.benton.lightchat.data.remote.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ForgotPasswordUiState(
    val account: String = "",
    val token: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val sending: Boolean = false,
    val resetting: Boolean = false,
    val info: String? = null,
    val error: String? = null,
    val resetDone: Boolean = false,
)

class ForgotPasswordViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = (application as LightchatApplication).container.authRepository

    private val _state = MutableStateFlow(ForgotPasswordUiState())
    val state: StateFlow<ForgotPasswordUiState> = _state.asStateFlow()

    fun setAccount(value: String) = _state.update { it.copy(account = value, error = null) }

    fun setToken(value: String) = _state.update { it.copy(token = value, error = null) }

    fun setPassword(value: String) = _state.update { it.copy(password = value, error = null) }

    fun setConfirmPassword(value: String) =
        _state.update { it.copy(confirmPassword = value, error = null) }

    fun sendReset() {
        val current = _state.value
        if (current.sending) return
        val account = current.account.trim()
        if (account.isEmpty()) {
            _state.update { it.copy(error = "请输入邮箱或用户名") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(sending = true, error = null, info = null) }
            when (val result = authRepository.forgotPassword(account)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        sending = false,
                        info = "如果该账号存在且已绑定邮箱，重置邮件已发送，请查收",
                    )
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(sending = false, error = result.message)
                }
            }
        }
    }

    fun reset() {
        val current = _state.value
        if (current.resetting) return
        val token = current.token.trim()
        val validationError = when {
            token.isEmpty() -> "请输入邮件中的重置令牌"
            current.password.length < 6 -> "新密码至少需要 6 位"
            current.password != current.confirmPassword -> "两次输入的密码不一致"
            else -> null
        }
        if (validationError != null) {
            _state.update { it.copy(error = validationError) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(resetting = true, error = null, info = null) }
            when (val result = authRepository.resetPassword(token, current.password)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        resetting = false,
                        resetDone = true,
                        info = "密码已重置，请使用新密码登录",
                        password = "",
                        confirmPassword = "",
                    )
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(resetting = false, error = result.message)
                }
            }
        }
    }
}
