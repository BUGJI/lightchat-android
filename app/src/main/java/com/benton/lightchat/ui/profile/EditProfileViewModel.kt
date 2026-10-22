package com.benton.lightchat.ui.profile

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.benton.lightchat.LightchatApplication
import com.benton.lightchat.data.remote.ApiResult
import com.benton.lightchat.data.remote.UpdateProfileRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class EditProfileUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val username: String = "",
    val nickname: String = "",
    val email: String = "",
    val bio: String = "",
    val signature: String = "",
    val existingAvatarUrl: String? = null,
    val localAvatar: Uri? = null,
    val avatarDataUrl: String? = null,
    val error: String? = null,
    val saved: Boolean = false,
)

class EditProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as LightchatApplication).container
    private val userRepository = container.userRepository

    private val _state = MutableStateFlow(EditProfileUiState())
    val state: StateFlow<EditProfileUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val result = userRepository.profile()) {
                is ApiResult.Success -> {
                    val user = result.data
                    _state.update {
                        it.copy(
                            loading = false,
                            username = user?.username.orEmpty(),
                            nickname = user?.nickname.orEmpty(),
                            email = user?.email.orEmpty(),
                            bio = user?.bio.orEmpty(),
                            signature = user?.signature.orEmpty(),
                            existingAvatarUrl = user?.avatar,
                        )
                    }
                }

                is ApiResult.Failure -> {
                    _state.update { it.copy(loading = false, error = result.message) }
                }
            }
        }
    }

    fun setNickname(value: String) = _state.update { it.copy(nickname = value, error = null) }

    fun setEmail(value: String) = _state.update { it.copy(email = value, error = null) }

    fun setBio(value: String) = _state.update { it.copy(bio = value, error = null) }

    fun setSignature(value: String) = _state.update { it.copy(signature = value, error = null) }

    fun setAvatar(uri: Uri) {
        viewModelScope.launch {
            val dataUrl = withContext(Dispatchers.IO) { encodeAvatar(uri) }
            if (dataUrl == null) {
                _state.update { it.copy(error = "无法读取所选图片") }
            } else {
                _state.update {
                    it.copy(localAvatar = uri, avatarDataUrl = dataUrl, error = null)
                }
            }
        }
    }

    fun save() {
        val state = _state.value
        if (state.saving || state.loading) return
        val nickname = state.nickname.trim()
        val email = state.email.trim()
        val validationError = when {
            nickname.length > 20 -> "昵称最多 20 个字"
            state.bio.length > 200 -> "简介最多 200 个字"
            state.signature.length > 100 -> "签名最多 100 个字"
            email.isNotBlank() && !email.matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) ->
                "邮箱格式不正确"

            else -> null
        }
        if (validationError != null) {
            _state.update { it.copy(error = validationError) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            val result = userRepository.updateProfile(
                UpdateProfileRequest(
                    nickname = nickname,
                    bio = state.bio.trim(),
                    signature = state.signature.trim(),
                    email = email.ifBlank { null },
                    avatar = state.avatarDataUrl,
                ),
            )
            when (result) {
                is ApiResult.Success -> _state.update { it.copy(saving = false, saved = true) }
                is ApiResult.Failure -> _state.update { it.copy(saving = false, error = result.message) }
            }
        }
    }

    fun consumeSaved() = _state.update { it.copy(saved = false) }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun encodeAvatar(uri: Uri): String? {
        val resolver = getApplication<Application>().contentResolver
        val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            ?: return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val maxSide = 512
        val scale = minOf(1f, maxSide.toFloat() / maxOf(bitmap.width, bitmap.height))
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            bitmap
        }
        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, output)
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        val base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64"
    }
}
