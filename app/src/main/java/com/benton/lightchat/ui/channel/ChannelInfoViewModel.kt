package com.benton.lightchat.ui.channel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.benton.lightchat.LightchatApplication
import com.benton.lightchat.data.remote.ApiResult
import com.benton.lightchat.data.remote.ChannelInfoDto
import com.benton.lightchat.data.remote.ChannelMemberDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChannelInfoUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val channel: ChannelInfoDto? = null,
    val members: List<ChannelMemberDto> = emptyList(),
    val actionInProgress: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class ChannelInfoViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as LightchatApplication).container
    private val chatRepository = container.chatRepository

    private val _state = MutableStateFlow(ChannelInfoUiState())
    val state: StateFlow<ChannelInfoUiState> = _state.asStateFlow()

    private var loadedChannelId = 0L
    private var observingChannelId = 0L

    fun load(channelId: Long) {
        loadedChannelId = channelId
        if (observingChannelId != channelId) {
            observingChannelId = channelId
            viewModelScope.launch {
                chatRepository.observeChannelInfo(channelId).collect { data ->
                    if (data != null) {
                        _state.update {
                            it.copy(
                                loading = false,
                                channel = data.channel,
                                members = data.members,
                            )
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = it.channel == null,
                    refreshing = it.channel != null,
                    error = null,
                )
            }
            when (val result = chatRepository.channelMembers(channelId)) {
                is ApiResult.Success -> {
                    val data = result.data
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            channel = data?.channel,
                            members = data?.members.orEmpty(),
                        )
                    }
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, refreshing = false, error = result.message)
                }
            }
        }
    }

    fun refresh() {
        if (loadedChannelId > 0) load(loadedChannelId)
    }

    fun invite(username: String, onDone: () -> Unit) {
        val channelId = loadedChannelId
        if (channelId <= 0 || _state.value.actionInProgress) return
        val name = username.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, error = null) }
            when (val result = chatRepository.inviteChannel(channelId, username = name)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(actionInProgress = false) }
                    onDone()
                    load(channelId)
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(actionInProgress = false, error = result.message)
                }
            }
        }
    }

    fun updateChannel(
        displayName: String,
        type: String,
        description: String,
        onDone: () -> Unit,
    ) {
        val channelId = loadedChannelId
        if (channelId <= 0 || _state.value.actionInProgress) return
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, error = null) }
            val result = chatRepository.updateChannel(
                channelId = channelId,
                displayName = displayName.trim().ifBlank { null },
                type = type,
                description = description.trim().ifBlank { null },
            )
            when (result) {
                is ApiResult.Success -> {
                    _state.update { current ->
                        current.copy(
                            actionInProgress = false,
                            channel = current.channel?.copy(
                                displayName = displayName.trim().ifBlank {
                                    current.channel.displayName
                                },
                                type = type,
                                memberCount = current.channel.memberCount,
                            ),
                        )
                    }
                    onDone()
                    load(channelId)
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(actionInProgress = false, error = result.message)
                }
            }
        }
    }

    fun dissolve(onDone: () -> Unit) {
        val channelId = loadedChannelId
        if (channelId <= 0 || _state.value.actionInProgress) return
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, error = null) }
            when (val result = chatRepository.deleteChannel(channelId)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(actionInProgress = false) }
                    onDone()
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(actionInProgress = false, error = result.message)
                }
            }
        }
    }

    fun leave(onDone: () -> Unit) {
        val channelId = loadedChannelId
        if (channelId <= 0 || _state.value.actionInProgress) return
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, error = null) }
            when (val result = chatRepository.leaveChannel(channelId)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(actionInProgress = false) }
                    onDone()
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(actionInProgress = false, error = result.message)
                }
            }
        }
    }

    fun memberAction(
        userId: Long,
        action: String,
        role: String? = null,
        durationMinutes: Int? = null,
        onDone: () -> Unit = {},
    ) {
        val channelId = loadedChannelId
        if (channelId <= 0 || _state.value.actionInProgress) return
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = true, error = null, message = null) }
            when (val result = chatRepository.memberAction(
                channelId = channelId,
                userId = userId,
                action = action,
                role = role,
                durationMinutes = durationMinutes,
            )) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            actionInProgress = false,
                            message = "操作成功",
                        )
                    }
                    onDone()
                    load(channelId)
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(actionInProgress = false, error = result.message)
                }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
