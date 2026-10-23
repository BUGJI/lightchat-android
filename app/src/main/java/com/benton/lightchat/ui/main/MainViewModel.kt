package com.benton.lightchat.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.benton.lightchat.LightchatApplication
import com.benton.lightchat.data.local.AccountKey
import com.benton.lightchat.data.remote.ApiResult
import com.benton.lightchat.data.remote.ChannelDto
import com.benton.lightchat.data.remote.PrivateChatDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val channels: List<ChannelDto> = emptyList(),
    val privateChats: List<PrivateChatDto> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val offline: Boolean = false,
    val friendPending: Int = 0,
    val error: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as LightchatApplication).container
    private val chatRepository = container.chatRepository
    private val syncCoordinator = container.syncCoordinator

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private var started = false
    private var accountKey: String? = null

    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            val prefs = container.preferences.current()
            val key = AccountKey.of(prefs.baseUrl, prefs.userId)
            if (key == null) {
                _state.update { it.copy(loading = false, error = "无法读取本地账号信息") }
                return@launch
            }
            accountKey = key
            launch {
                combine(
                    chatRepository.observeChannels(key),
                    chatRepository.observePrivateChats(key),
                ) { channels, chats -> channels to chats }
                    .collect { (channels, chats) ->
                        _state.update {
                            it.copy(
                                channels = channels,
                                privateChats = chats,
                                loading = it.loading && channels.isEmpty() && chats.isEmpty(),
                            )
                        }
                    }
            }
            launch {
                syncCoordinator.status.collect { status ->
                    _state.update {
                        it.copy(
                            loading = if (status.syncing) it.loading else false,
                            refreshing = if (status.syncing) it.refreshing else false,
                            offline = !status.online,
                            friendPending = status.friendPending,
                            error = status.error ?: it.error,
                        )
                    }
                }
            }
            syncCoordinator.requestSync()
        }
    }

    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        syncCoordinator.requestSync()
    }

    fun leaveChannel(channel: ChannelDto) {
        viewModelScope.launch {
            when (val result = chatRepository.leaveChannel(channel.id)) {
                is ApiResult.Success -> {
                    _state.update { current ->
                        current.copy(channels = current.channels.filterNot { it.id == channel.id })
                    }
                    syncCoordinator.requestSync()
                }

                is ApiResult.Failure -> _state.update { it.copy(error = result.message) }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
