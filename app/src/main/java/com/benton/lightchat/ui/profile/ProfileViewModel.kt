package com.benton.lightchat.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.benton.lightchat.LightchatApplication
import com.benton.lightchat.data.remote.ApiResult
import com.benton.lightchat.data.remote.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val user: UserDto? = null,
    val loading: Boolean = true,
    val loggingOut: Boolean = false,
    val error: String? = null,
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as LightchatApplication).container
    private val userRepository = container.userRepository
    private val authRepository = container.authRepository

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.user == null, error = null) }
            when (val result = userRepository.profile()) {
                is ApiResult.Success -> {
                    _state.update { it.copy(loading = false, user = result.data) }
                }

                is ApiResult.Failure -> {
                    _state.update { it.copy(loading = false, error = result.message) }
                }
            }
        }
    }

    fun logout() {
        if (_state.value.loggingOut) return
        viewModelScope.launch {
            _state.update { it.copy(loggingOut = true) }
            authRepository.logout()
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
