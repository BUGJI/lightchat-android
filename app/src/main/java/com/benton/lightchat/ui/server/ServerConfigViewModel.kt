package com.benton.lightchat.ui.server

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.benton.lightchat.LightchatApplication
import com.benton.lightchat.data.repository.ServerTestResult
import com.benton.lightchat.util.UrlUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServerConfigState(
    val testing: Boolean = false,
    val saving: Boolean = false,
    val testResult: ServerTestResult? = null,
    val savedUrl: String? = null,
    val error: String? = null,
)

class ServerConfigViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as LightchatApplication).container
    private val _state = MutableStateFlow(ServerConfigState())
    val state: StateFlow<ServerConfigState> = _state.asStateFlow()

    fun test(input: String) {
        val normalized = UrlUtils.normalize(input)
        if (normalized == null) {
            _state.update { it.copy(error = "地址格式不正确，请填写 http(s)://主机[:端口]/api") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(testing = true, error = null, testResult = null) }
            val result = container.serverRepository.testConnection(normalized)
            _state.update { it.copy(testing = false, testResult = result) }
        }
    }

    fun save(input: String, clearSessionOnSave: Boolean) {
        val normalized = UrlUtils.normalize(input)
        if (normalized == null) {
            _state.update { it.copy(error = "地址格式不正确，请填写 http(s)://主机[:端口]/api") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            if (clearSessionOnSave) {
                container.preferences.disableAutoLogin()
                container.authRepository.clearSession()
            }
            container.preferences.setServer(normalized)
            container.apiClient.setBaseUrl(normalized)
            _state.update { it.copy(saving = false, savedUrl = normalized) }
        }
    }

    fun consumeSaved() {
        _state.update { it.copy(savedUrl = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
