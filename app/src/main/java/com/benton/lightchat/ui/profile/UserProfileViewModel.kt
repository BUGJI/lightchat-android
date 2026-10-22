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

data class UserProfileUiState(
    val loading: Boolean = true,
    val user: UserDto? = null,
    val actionInProgress: Boolean = false,
    val error: String? = null,
)

class UserProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as LightchatApplication).container
    private val userRepository = container.userRepository
    private val chatRepository = container.chatRepository

    private val _state = MutableStateFlow(UserProfileUiState())
    val state: StateFlow<UserProfileUiState> = _state.asStateFlow()

    fun load(userId: Long) {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.user == null, error = null) }
            when (val result = userRepository.profile(userId)) {
                is ApiResult.Success -> _state.update {
                    it.copy(loading = false, user = result.data)
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, error = result.message)
                }
            }
        }
    }

    fun startPrivateChat(userId: Long, title: String, onOpen: (Long, Long, String) -> Unit) {
        if (_state.value.actionInProgress) return
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, error = null) }
            when (val result = chatRepository.createPrivateChat(userId)) {
                is ApiResult.Success -> {
                    val chatId = result.data?.chatId ?: 0
                    _state.update { it.copy(actionInProgress = false) }
                    if (chatId > 0) {
                        onOpen(chatId, userId, title)
                    } else {
                        _state.update { it.copy(error = "创建会话失败") }
                    }
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(actionInProgress = false, error = result.message)
                }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
