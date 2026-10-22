package com.benton.lightchat.ui.contacts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.benton.lightchat.LightchatApplication
import com.benton.lightchat.data.remote.ApiResult
import com.benton.lightchat.data.remote.ChannelDto
import com.benton.lightchat.data.remote.UserDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddContactsUiState(
    val users: List<UserDto> = emptyList(),
    val channels: List<ChannelDto> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<UserDto> = emptyList(),
    val searching: Boolean = false,
    val loading: Boolean = true,
    val actionInProgress: Boolean = false,
    val error: String? = null,
)

sealed interface AddContactsEvent {
    data class OpenPrivateChat(
        val chatId: Long,
        val userId: Long,
        val title: String,
    ) : AddContactsEvent

    data class ShowMessage(val text: String) : AddContactsEvent
}

class AddContactsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as LightchatApplication).container
    private val userRepository = container.userRepository
    private val chatRepository = container.chatRepository
    private val friendRepository = container.friendRepository

    private val _state = MutableStateFlow(AddContactsUiState())
    val state: StateFlow<AddContactsUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AddContactsEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<AddContactsEvent> = _events.asSharedFlow()

    private var searchJob: Job? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val currentUserId = container.preferences.current().userId

            val usersResult = userRepository.users()
            val channelsResult = chatRepository.channels()

            _state.update { state ->
                val users = when (usersResult) {
                    is ApiResult.Success -> usersResult.data.orEmpty()
                    is ApiResult.Failure -> emptyList()
                }.filter { it.id != currentUserId }
                val channels = when (channelsResult) {
                    is ApiResult.Success -> channelsResult.data.orEmpty()
                    is ApiResult.Failure -> emptyList()
                }.filter { !it.isJoined }
                val error = when (usersResult) {
                    is ApiResult.Failure -> usersResult.message
                    is ApiResult.Success -> when (channelsResult) {
                        is ApiResult.Failure -> channelsResult.message
                        is ApiResult.Success -> null
                    }
                }
                state.copy(
                    loading = false,
                    users = users,
                    channels = channels,
                    error = error,
                )
            }
        }
    }

    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(searchResults = emptyList(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            _state.update { it.copy(searching = true) }
            val currentUserId = container.preferences.current().userId
            when (val result = userRepository.search(query.trim())) {
                is ApiResult.Success -> {
                    val users = result.data.orEmpty().filter { it.id != currentUserId }
                    _state.update { it.copy(searching = false, searchResults = users) }
                }

                is ApiResult.Failure -> {
                    _state.update { it.copy(searching = false, error = result.message) }
                }
            }
        }
    }

    fun startPrivateChat(user: UserDto) {
        if (_state.value.actionInProgress) return
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true) }
            when (val result = chatRepository.createPrivateChat(user.id)) {
                is ApiResult.Success -> {
                    val chatId = result.data?.chatId ?: 0
                    val title = user.nickname?.takeIf { it.isNotBlank() } ?: user.username
                    _state.update { it.copy(actionInProgress = false) }
                    if (chatId > 0) {
                        _events.emit(AddContactsEvent.OpenPrivateChat(chatId, user.id, title))
                    } else {
                        _events.emit(AddContactsEvent.ShowMessage("创建会话失败"))
                    }
                }

                is ApiResult.Failure -> {
                    _state.update { it.copy(actionInProgress = false, error = result.message) }
                }
            }
        }
    }

    fun joinChannel(channel: ChannelDto) {
        if (_state.value.actionInProgress) return
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true) }
            when (val result = chatRepository.joinChannel(channel.id)) {
                is ApiResult.Success -> {
                    _state.update { state ->
                        state.copy(
                            actionInProgress = false,
                            channels = state.channels.filterNot { it.id == channel.id },
                        )
                    }
                    _events.emit(AddContactsEvent.ShowMessage("已加入 ${channel.displayName}"))
                }

                is ApiResult.Failure -> {
                    _state.update { it.copy(actionInProgress = false, error = result.message) }
                }
            }
        }
    }

    fun sendFriendRequest(user: UserDto) {
        if (_state.value.actionInProgress) return
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, error = null) }
            when (val result = friendRepository.sendRequest(userId = user.id)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(actionInProgress = false) }
                    val autoAccepted = result.data?.autoAccepted == true
                    _events.emit(
                        AddContactsEvent.ShowMessage(
                            if (autoAccepted) "你们已成为好友" else "好友申请已发送",
                        ),
                    )
                }

                is ApiResult.Failure -> {
                    _state.update { it.copy(actionInProgress = false, error = result.message) }
                }
            }
        }
    }

    fun createChannel(
        name: String,
        displayName: String,
        type: String,
        description: String,
        onDone: () -> Unit,
    ) {
        if (_state.value.actionInProgress) return
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true) }
            when (val result = chatRepository.createChannel(
                name,
                displayName,
                type,
                description.ifBlank { null },
            )) {
                is ApiResult.Success -> {
                    _state.update { it.copy(actionInProgress = false) }
                    onDone()
                    _events.emit(AddContactsEvent.ShowMessage("群聊创建成功"))
                    load()
                }

                is ApiResult.Failure -> {
                    _state.update { it.copy(actionInProgress = false, error = result.message) }
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
