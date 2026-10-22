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

data class ChangePasswordUiState(
    val oldPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

class ChangePasswordViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as LightchatApplication).container
    private val userRepository = container.userRepository

    private val _state = MutableStateFlow(ChangePasswordUiState())
    val state: StateFlow<ChangePasswordUiState> = _state.asStateFlow()

    fun setOldPassword(value: String) = _state.update { it.copy(oldPassword = value, error = null) }

    fun setNewPassword(value: String) = _state.update { it.copy(newPassword = value, error = null) }

    fun setConfirmPassword(value: String) =
        _state.update { it.copy(confirmPassword = value, error = null) }

    fun save() {
        val current = _state.value
        if (current.saving) return
        when {
            current.oldPassword.isBlank() -> {
                _state.update { it.copy(error = "请输入旧密码") }
                return
            }

            current.newPassword.length < 6 -> {
                _state.update { it.copy(error = "新密码至少 6 位") }
                return
            }

            current.newPassword != current.confirmPassword -> {
                _state.update { it.copy(error = "两次输入的新密码不一致") }
                return
            }
        }

        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            when (val result = userRepository.changePassword(current.oldPassword, current.newPassword)) {
                is ApiResult.Success -> _state.update {
                    it.copy(saving = false, saved = true)
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(saving = false, error = result.message)
                }
            }
        }
    }

    fun consumeSaved() = _state.update { it.copy(saved = false) }
}
