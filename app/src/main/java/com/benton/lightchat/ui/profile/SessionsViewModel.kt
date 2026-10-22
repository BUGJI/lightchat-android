package com.benton.lightchat.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.benton.lightchat.LightchatApplication
import com.benton.lightchat.data.remote.ApiResult
import com.benton.lightchat.data.remote.SessionDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionsUiState(
    val loading: Boolean = true,
    val sessions: List<SessionDto> = emptyList(),
    val actionInProgress: Boolean = false,
    val info: String? = null,
    val error: String? = null,
)

class SessionsViewModel(application: Application) : AndroidViewModel(application) {

    private val userRepository = (application as LightchatApplication).container.userRepository

    private val _state = MutableStateFlow(SessionsUiState())
    val state: StateFlow<SessionsUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.sessions.isEmpty(), error = null) }
            when (val result = userRepository.sessions()) {
                is ApiResult.Success -> _state.update {
                    it.copy(loading = false, sessions = result.data.orEmpty())
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, error = result.message)
                }
            }
        }
    }

    fun revoke(session: SessionDto) {
        if (session.current || _state.value.actionInProgress) return
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, error = null, info = null) }
            when (val result = userRepository.revokeSession(session.id)) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            actionInProgress = false,
                            sessions = it.sessions.filterNot { item -> item.id == session.id },
                            info = "已退出该设备",
                        )
                    }
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(actionInProgress = false, error = result.message)
                }
            }
        }
    }

    fun revokeOthers() {
        if (_state.value.actionInProgress) return
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, error = null, info = null) }
            when (val result = userRepository.revokeOtherSessions()) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            actionInProgress = false,
                            info = "已退出其他设备",
                            sessions = it.sessions.filter { session -> session.current },
                        )
                    }
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(actionInProgress = false, error = result.message)
                }
            }
        }
    }

    fun clearMessages() = _state.update { it.copy(info = null, error = null) }
}
