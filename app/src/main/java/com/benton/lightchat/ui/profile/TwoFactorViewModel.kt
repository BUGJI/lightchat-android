package com.benton.lightchat.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.benton.lightchat.LightchatApplication
import com.benton.lightchat.data.remote.ApiResult
import com.benton.lightchat.data.remote.TwoFactorData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TwoFactorUiState(
    val loading: Boolean = true,
    val enabled: Boolean = false,
    val configured: Boolean = false,
    val setup: TwoFactorData? = null,
    val startingSetup: Boolean = false,
    val code: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    val info: String? = null,
    val error: String? = null,
)

class TwoFactorViewModel(application: Application) : AndroidViewModel(application) {

    private val userRepository = (application as LightchatApplication).container.userRepository

    private val _state = MutableStateFlow(TwoFactorUiState())
    val state: StateFlow<TwoFactorUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val result = userRepository.twoFactorStatus()) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        enabled = result.data?.enabled == true,
                        configured = result.data?.configured == true,
                    )
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, error = result.message)
                }
            }
        }
    }

    fun setCode(value: String) = _state.update {
        it.copy(code = value.filter { ch -> ch.isDigit() }.take(6), error = null)
    }

    fun setPassword(value: String) = _state.update { it.copy(password = value, error = null) }

    fun startSetup() {
        if (_state.value.startingSetup) return
        viewModelScope.launch {
            _state.update { it.copy(startingSetup = true, error = null, info = null) }
            when (val result = userRepository.setupTwoFactor()) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        startingSetup = false,
                        setup = result.data,
                        info = "请用身份验证器扫码或手动输入密钥，然后输入 6 位验证码完成启用",
                    )
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(startingSetup = false, error = result.message)
                }
            }
        }
    }

    fun cancelSetup() = _state.update { it.copy(setup = null, code = "", info = null) }

    fun enable() {
        val current = _state.value
        if (current.submitting) return
        if (current.code.length != 6) {
            _state.update { it.copy(error = "请输入 6 位验证码") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null, info = null) }
            when (val result = userRepository.enableTwoFactor(current.code)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        submitting = false,
                        enabled = true,
                        configured = true,
                        setup = null,
                        code = "",
                        info = "两步验证已启用",
                    )
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(submitting = false, error = result.message)
                }
            }
        }
    }

    fun disable() {
        val current = _state.value
        if (current.submitting) return
        when {
            current.password.isBlank() -> {
                _state.update { it.copy(error = "请输入当前密码") }
                return
            }

            current.code.length != 6 -> {
                _state.update { it.copy(error = "请输入 6 位验证码") }
                return
            }
        }
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null, info = null) }
            when (val result = userRepository.disableTwoFactor(current.password, current.code)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        submitting = false,
                        enabled = false,
                        configured = false,
                        password = "",
                        code = "",
                        info = "两步验证已关闭",
                    )
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(submitting = false, error = result.message)
                }
            }
        }
    }

    fun clearMessages() = _state.update { it.copy(info = null, error = null) }
}
