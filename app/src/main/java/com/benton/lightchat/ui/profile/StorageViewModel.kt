package com.benton.lightchat.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.benton.lightchat.LightchatApplication
import com.benton.lightchat.data.local.AccountKey
import com.benton.lightchat.data.local.StorageSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StorageUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val snapshot: StorageSnapshot? = null,
    val message: String? = null,
    val error: String? = null,
)

class StorageViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as LightchatApplication).container
    private val storageManager = container.storageManager

    private val _state = MutableStateFlow(StorageUiState())
    val state: StateFlow<StorageUiState> = _state.asStateFlow()

    private var ownerKey: String? = null

    fun load() {
        viewModelScope.launch {
            val prefs = container.preferences.current()
            ownerKey = AccountKey.of(prefs.baseUrl, prefs.userId)
            _state.update { it.copy(loading = it.snapshot == null, error = null) }
            val snapshot = runCatching { storageManager.snapshot(ownerKey) }.getOrNull()
            _state.update {
                it.copy(
                    loading = false,
                    snapshot = snapshot ?: it.snapshot,
                    error = if (snapshot == null) "无法读取存储信息" else null,
                )
            }
        }
    }

    fun clearMessages() = runAction("聊天记录已清理") { key ->
        storageManager.clearMessages(key ?: error("无法读取本地账号信息"))
    }

    fun clearImages() = runAction("图片缓存已清理") {
        storageManager.clearImages()
    }

    fun clearAttachments() = runAction("文件缓存已清理") {
        storageManager.clearAttachments()
    }

    fun clearAll() = runAction("本地数据已清理") {
        storageManager.clearAll()
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun runAction(successMessage: String, block: suspend (String?) -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                block(ownerKey)
                val snapshot = storageManager.snapshot(ownerKey)
                _state.update {
                    it.copy(
                        busy = false,
                        loading = false,
                        snapshot = snapshot,
                        message = successMessage,
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        busy = false,
                        error = error.message ?: "清理失败，请稍后重试",
                    )
                }
            }
        }
    }
}
