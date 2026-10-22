package com.benton.lightchat.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.benton.lightchat.LightchatApplication
import com.benton.lightchat.data.remote.ApiResult
import com.benton.lightchat.data.remote.ServerStatusData
import com.benton.lightchat.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val loading: Boolean = true,
    val status: ServerStatusData? = null,
    val error: String? = null,
    val loggingOut: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as LightchatApplication).container
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val prefs = container.preferences.current()
            _state.update {
                it.copy(
                    themeMode = ThemeMode.fromValue(prefs.themeMode),
                    dynamicColor = prefs.dynamicColor,
                )
            }
            when (val result = container.serverRepository.serverStatus()) {
                is ApiResult.Success -> _state.update {
                    it.copy(loading = false, status = result.data)
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, error = result.message)
                }
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        if (_state.value.themeMode == mode) return
        _state.update { it.copy(themeMode = mode) }
        viewModelScope.launch { container.preferences.setThemeMode(mode.value) }
    }

    fun setDynamicColor(enabled: Boolean) {
        if (_state.value.dynamicColor == enabled) return
        _state.update { it.copy(dynamicColor = enabled) }
        viewModelScope.launch { container.preferences.setDynamicColor(enabled) }
    }

    fun logout() {
        if (_state.value.loggingOut) return
        viewModelScope.launch {
            _state.update { it.copy(loggingOut = true) }
            container.authRepository.logout()
        }
    }
}
