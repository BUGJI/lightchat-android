package com.benton.lightchat.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.benton.lightchat.LightchatApplication
import com.benton.lightchat.ui.theme.AccentColor
import com.benton.lightchat.ui.theme.DEFAULT_CORNER_RADIUS
import com.benton.lightchat.ui.theme.DEFAULT_FONT_SCALE
import com.benton.lightchat.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppearanceSettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val amoledDark: Boolean = false,
    val accentColor: AccentColor = AccentColor.DEFAULT,
    val cornerRadius: Int = DEFAULT_CORNER_RADIUS,
    val fontScale: Float = DEFAULT_FONT_SCALE,
)

class AppearanceSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as LightchatApplication).container
    private val _state = MutableStateFlow(AppearanceSettingsUiState())
    val state: StateFlow<AppearanceSettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = container.preferences.current()
            _state.value = AppearanceSettingsUiState(
                themeMode = ThemeMode.fromValue(prefs.themeMode),
                dynamicColor = prefs.dynamicColor,
                amoledDark = prefs.amoledDark,
                accentColor = AccentColor.fromValue(prefs.accentColor),
                cornerRadius = prefs.cornerRadius,
                fontScale = prefs.fontScale,
            )
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

    fun setAmoledDark(enabled: Boolean) {
        if (_state.value.amoledDark == enabled) return
        _state.update { it.copy(amoledDark = enabled) }
        viewModelScope.launch { container.preferences.setAmoledDark(enabled) }
    }

    fun setAccentColor(color: AccentColor) {
        if (_state.value.accentColor == color) return
        _state.update { it.copy(accentColor = color) }
        viewModelScope.launch { container.preferences.setAccentColor(color.value) }
    }

    fun setCornerRadius(radius: Int) {
        val value = radius.coerceIn(0, 28)
        if (_state.value.cornerRadius == value) return
        _state.update { it.copy(cornerRadius = value) }
        viewModelScope.launch { container.preferences.setCornerRadius(value) }
    }

    fun setFontScale(scale: Float) {
        if (_state.value.fontScale == scale) return
        _state.update { it.copy(fontScale = scale) }
        viewModelScope.launch { container.preferences.setFontScale(scale) }
    }
}
