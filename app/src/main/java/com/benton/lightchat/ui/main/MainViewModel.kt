package com.benton.lightchat.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.benton.lightchat.LightchatApplication
import com.benton.lightchat.data.remote.ApiResult
import com.benton.lightchat.data.remote.ChannelDto
import com.benton.lightchat.data.remote.PrivateChatDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MainUiState(
    val channels: List<ChannelDto> = emptyList(),
    val privateChats: List<PrivateChatDto> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val offline: Boolean = false,
    val error: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as LightchatApplication).container
    private val chatRepository = container.chatRepository

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private var started = false

    fun start() {
        if (started) return
        started = true
        viewModelScope.launch { loadLists(showLoading = true) }
    }

    suspend fun pollLoop() {
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            val online = loadLists(showLoading = false)
            _state.update { it.copy(offline = !online) }
            delay(3000)
        }
    }

    fun refresh() {
        viewModelScope.launch { loadLists(showLoading = false, userInitiated = true) }
    }

    fun leaveChannel(channel: ChannelDto) {
        viewModelScope.launch {
            when (val result = chatRepository.leaveChannel(channel.id)) {
                is ApiResult.Success -> loadLists(showLoading = false)
                is ApiResult.Failure -> _state.update { it.copy(error = result.message) }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private suspend fun loadLists(showLoading: Boolean, userInitiated: Boolean = false): Boolean {
        if (showLoading) {
            _state.update { it.copy(loading = true) }
        } else if (userInitiated) {
            _state.update { it.copy(refreshing = true) }
        }

        return when (val result = chatRepository.sync(lists = true)) {
            is ApiResult.Success -> {
                val data = result.data
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        offline = false,
                        channels = data?.channels ?: it.channels,
                        privateChats = data?.privateChats ?: it.privateChats,
                    )
                }
                true
            }

            is ApiResult.Failure -> {
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = if (userInitiated || showLoading) result.message else it.error,
                    )
                }
                false
            }
        }
    }
}
