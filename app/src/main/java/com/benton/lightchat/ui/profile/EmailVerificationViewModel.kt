package com.benton.lightchat.ui.profile

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

data class EmailVerificationUiState(
    val loading: Boolean = true,
    val loaded: Boolean = false,
    val refreshing: Boolean = false,
    val email: String = "",
    val verified: Boolean = false,
    val sending: Boolean = false,
    val verifying: Boolean = false,
    val token: String = "",
    val info: String? = null,
    val error: String? = null,
)

class EmailVerificationViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as LightchatApplication).container
    private val userRepository = container.userRepository
    private val authRepository = container.authRepository

    private val _state = MutableStateFlow(EmailVerificationUiState())
    val state: StateFlow<EmailVerificationUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            userRepository.observeProfile().collect { user ->
                if (user != null && !_state.value.loaded) {
                    _state.update {
                        it.copy(
                            loading = false,
                            loaded = true,
                            email = user.email.orEmpty(),
                            verified = user.emailVerified,
                        )
                    }
                }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.update {
                it.copy(loading = !it.loaded, refreshing = it.loaded, error = null)
            }
            when (val result = userRepository.profile()) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        loaded = true,
                        refreshing = false,
                        email = result.data?.email.orEmpty(),
                        verified = result.data?.emailVerified == true,
                    )
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, refreshing = false, error = result.message)
                }
            }
        }
    }

    fun setToken(value: String) = _state.update { it.copy(token = value, error = null) }

    fun resend() {
        if (_state.value.sending) return
        viewModelScope.launch {
            _state.update { it.copy(sending = true, error = null, info = null) }
            when (val result = authRepository.resendVerification()) {
                is ApiResult.Success -> _state.update {
                    it.copy(sending = false, info = "验证邮件已发送，请查收")
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(sending = false, error = result.message)
                }
            }
        }
    }

    fun verify() {
        val current = _state.value
        if (current.verifying) return
        val token = current.token.trim()
        if (token.isEmpty()) {
            _state.update { it.copy(error = "请输入验证令牌") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(verifying = true, error = null, info = null) }
            when (val result = authRepository.verifyEmail(token)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        verifying = false,
                        verified = true,
                        token = "",
                        info = "邮箱验证成功",
                    )
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(verifying = false, error = result.message)
                }
            }
        }
    }
}
