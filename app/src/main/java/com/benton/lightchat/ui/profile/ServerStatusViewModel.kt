package com.benton.lightchat.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.benton.lightchat.LightchatApplication
import com.benton.lightchat.data.remote.ApiResult
import com.benton.lightchat.data.remote.ServerStatusData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServerStatusUiState(
    val loading: Boolean = true,
    val loaded: Boolean = false,
    val refreshing: Boolean = false,
    val status: ServerStatusData? = null,
    val error: String? = null,
)

class ServerStatusViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as LightchatApplication).container
    private val _state = MutableStateFlow(ServerStatusUiState())
    val state: StateFlow<ServerStatusUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.serverRepository.observeServerStatus().collect { status ->
                if (status != null) {
                    _state.update {
                        it.copy(loading = false, loaded = true, status = status)
                    }
                }
            }
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update {
                it.copy(loading = !it.loaded, refreshing = it.loaded, error = null)
            }
            when (val result = container.serverRepository.serverStatus()) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        loaded = true,
                        refreshing = false,
                        status = result.data,
                    )
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, refreshing = false, error = result.message)
                }
            }
        }
    }
}
