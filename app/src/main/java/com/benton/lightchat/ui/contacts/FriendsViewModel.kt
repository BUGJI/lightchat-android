package com.benton.lightchat.ui.contacts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.benton.lightchat.LightchatApplication
import com.benton.lightchat.data.remote.ApiResult
import com.benton.lightchat.data.remote.BlockedUserDto
import com.benton.lightchat.data.remote.FriendDto
import com.benton.lightchat.data.remote.FriendRequestDto
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FriendsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val friends: List<FriendDto> = emptyList(),
    val requests: List<FriendRequestDto> = emptyList(),
    val blocked: List<BlockedUserDto> = emptyList(),
    val actionInProgress: Boolean = false,
    val error: String? = null,
)

sealed interface FriendsEvent {
    data class OpenPrivateChat(
        val chatId: Long,
        val userId: Long,
        val title: String,
    ) : FriendsEvent

    data class ShowMessage(val text: String) : FriendsEvent
}

class FriendsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as LightchatApplication).container
    private val friendRepository = container.friendRepository
    private val chatRepository = container.chatRepository

    private val _state = MutableStateFlow(FriendsUiState())
    val state: StateFlow<FriendsUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<FriendsEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<FriendsEvent> = _events.asSharedFlow()

    private var started = false

    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            friendRepository.observeFriends().collect { data ->
                if (data != null) {
                    _state.update {
                        it.copy(
                            loading = false,
                            friends = data.friends,
                            requests = data.requests,
                            blocked = data.blocked,
                        )
                    }
                }
            }
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update {
                val hasData = it.friends.isNotEmpty() || it.requests.isNotEmpty() ||
                    it.blocked.isNotEmpty()
                it.copy(loading = !hasData, refreshing = hasData, error = null)
            }
            when (val result = friendRepository.list()) {
                is ApiResult.Success -> {
                    val data = result.data
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            friends = data?.friends.orEmpty(),
                            requests = data?.requests.orEmpty(),
                            blocked = data?.blocked.orEmpty(),
                        )
                    }
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, refreshing = false, error = result.message)
                }
            }
        }
    }

    fun respond(request: FriendRequestDto, accept: Boolean) {
        if (_state.value.actionInProgress) return
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, error = null) }
            when (val result = friendRepository.respond(request.id, accept)) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            actionInProgress = false,
                            requests = it.requests.filterNot { item -> item.id == request.id },
                        )
                    }
                    _events.emit(
                        FriendsEvent.ShowMessage(if (accept) "已添加为好友" else "已拒绝好友申请"),
                    )
                    load()
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(actionInProgress = false, error = result.message)
                }
            }
        }
    }

    fun remove(friend: FriendDto) {
        if (_state.value.actionInProgress) return
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, error = null) }
            when (val result = friendRepository.remove(friend.userId)) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            actionInProgress = false,
                            friends = it.friends.filterNot { item -> item.userId == friend.userId },
                        )
                    }
                    _events.emit(FriendsEvent.ShowMessage("已删除好友"))
                    load()
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(actionInProgress = false, error = result.message)
                }
            }
        }
    }

    fun block(friend: FriendDto) {
        if (_state.value.actionInProgress) return
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, error = null) }
            when (val result = friendRepository.setBlocked(friend.userId, true)) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            actionInProgress = false,
                            friends = it.friends.filterNot { item -> item.userId == friend.userId },
                        )
                    }
                    _events.emit(FriendsEvent.ShowMessage("已拉黑"))
                    load()
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(actionInProgress = false, error = result.message)
                }
            }
        }
    }

    fun unblock(user: BlockedUserDto) {
        if (_state.value.actionInProgress) return
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, error = null) }
            when (val result = friendRepository.setBlocked(user.userId, false)) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            actionInProgress = false,
                            blocked = it.blocked.filterNot { item -> item.userId == user.userId },
                        )
                    }
                    _events.emit(FriendsEvent.ShowMessage("已解除拉黑"))
                    load()
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(actionInProgress = false, error = result.message)
                }
            }
        }
    }

    fun openChat(userId: Long, title: String) {
        if (_state.value.actionInProgress) return
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, error = null) }
            when (val result = chatRepository.createPrivateChat(userId)) {
                is ApiResult.Success -> {
                    val chatId = result.data?.chatId ?: 0
                    _state.update { it.copy(actionInProgress = false) }
                    if (chatId > 0) {
                        _events.emit(FriendsEvent.OpenPrivateChat(chatId, userId, title))
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
