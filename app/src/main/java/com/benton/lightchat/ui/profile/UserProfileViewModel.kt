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
    val refreshing: Boolean = false,
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

    private var observingUserId = 0L

    fun load(userId: Long) {
        if (userId <= 0) return
        if (observingUserId != userId) {
            observingUserId = userId
            viewModelScope.launch {
                userRepository.observeProfile(userId).collect { user ->
                    if (user != null) {
                        _state.update { it.copy(user = user, loading = false) }
                    }
                }
            }
        }
        viewModelScope.launch {
            _state.update {
                it.copy(loading = it.user == null, refreshing = it.user != null, error = null)
            }
            when (val result = userRepository.profile(userId)) {
                is ApiResult.Success -> _state.update {
                    it.copy(loading = false, refreshing = false, user = result.data)
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, refreshing = false, error = result.message)
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
