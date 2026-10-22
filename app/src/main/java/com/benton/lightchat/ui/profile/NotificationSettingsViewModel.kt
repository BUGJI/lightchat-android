package com.benton.lightchat.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.benton.lightchat.LightchatApplication
import com.benton.lightchat.data.remote.ApiResult
import com.benton.lightchat.data.remote.NotificationEmailTemplate
import com.benton.lightchat.data.remote.NotificationMessageTemplate
import com.benton.lightchat.data.remote.NotificationTemplateDto
import com.benton.lightchat.data.remote.UpdateProfileRequest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class NotificationMode(val value: String, val label: String, val description: String) {
    NONE("none", "不提醒", "不接收离线通知"),
    EMAIL("email", "邮件", "通过服务器 SMTP 发送邮件"),
    PUSHPLUS("pushplus", "PushPlus", "推送到微信 / 短信等渠道"),
    WEBHOOK("webhook", "Webhook", "POST 到你配置的地址"),
    ;

    companion object {
        fun fromValue(value: String?): NotificationMode =
            entries.firstOrNull { it.value == value } ?: NONE
    }
}

data class NotificationSettingsUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val testing: Boolean = false,
    val mode: NotificationMode = NotificationMode.NONE,
    val accountEmail: String = "",
    val email: String = "",
    val pushplusKey: String = "",
    val webhookUrl: String = "",
    val webhookSecret: String = "",
    val groupEnabled: Boolean = true,
    val privateEnabled: Boolean = true,
    val emailSubject: String = "",
    val emailBody: String = "",
    val pushplusTitle: String = "",
    val pushplusContent: String = "",
    val webhookTitle: String = "",
    val webhookContent: String = "",
    val availableMethods: Set<String> = emptySet(),
    val error: String? = null,
)

class NotificationSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as LightchatApplication).container
    private val userRepository = container.userRepository

    private val _state = MutableStateFlow(NotificationSettingsUiState())
    val state: StateFlow<NotificationSettingsUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val result = userRepository.profile()) {
                is ApiResult.Success -> {
                    val user = result.data
                    val notification = user?.notification
                    val template = notification?.template
                    val accountEmail = user?.email.orEmpty()
                    val notificationEmail = notification?.email?.takeIf { it.isNotBlank() }
                        ?: accountEmail
                    _state.update {
                        it.copy(
                            loading = false,
                            mode = NotificationMode.fromValue(notification?.mode),
                            accountEmail = accountEmail,
                            email = notificationEmail,
                            pushplusKey = notification?.pushplusKey.orEmpty(),
                            webhookUrl = notification?.webhookUrl.orEmpty(),
                            webhookSecret = notification?.webhookSecret.orEmpty(),
                            groupEnabled = notification?.groupEnabled ?: true,
                            privateEnabled = notification?.privateEnabled ?: true,
                            emailSubject = template?.email?.subject.orEmpty(),
                            emailBody = template?.email?.body.orEmpty(),
                            pushplusTitle = template?.pushplus?.title.orEmpty(),
                            pushplusContent = template?.pushplus?.content.orEmpty(),
                            webhookTitle = template?.webhook?.title.orEmpty(),
                            webhookContent = template?.webhook?.content.orEmpty(),
                            availableMethods = notification?.availableMethods.orEmpty().toSet(),
                        )
                    }
                }

                is ApiResult.Failure -> {
                    _state.update { it.copy(loading = false, error = result.message) }
                }
            }
        }
    }

    fun setMode(mode: NotificationMode) = _state.update { it.copy(mode = mode, error = null) }

    fun setEmail(value: String) = _state.update { it.copy(email = value, error = null) }

    fun setPushplusKey(value: String) = _state.update { it.copy(pushplusKey = value, error = null) }

    fun setWebhookUrl(value: String) = _state.update { it.copy(webhookUrl = value, error = null) }

    fun setWebhookSecret(value: String) =
        _state.update { it.copy(webhookSecret = value, error = null) }

    fun setGroupEnabled(enabled: Boolean) = _state.update { it.copy(groupEnabled = enabled) }

    fun setPrivateEnabled(enabled: Boolean) = _state.update { it.copy(privateEnabled = enabled) }

    fun setEmailSubject(value: String) =
        _state.update { it.copy(emailSubject = value, error = null) }

    fun setEmailBody(value: String) = _state.update { it.copy(emailBody = value, error = null) }

    fun setPushplusTitle(value: String) =
        _state.update { it.copy(pushplusTitle = value, error = null) }

    fun setPushplusContent(value: String) =
        _state.update { it.copy(pushplusContent = value, error = null) }

    fun setWebhookTitle(value: String) =
        _state.update { it.copy(webhookTitle = value, error = null) }

    fun setWebhookContent(value: String) =
        _state.update { it.copy(webhookContent = value, error = null) }

    fun resetTemplates() = _state.update {
        it.copy(
            emailSubject = "",
            emailBody = "",
            pushplusTitle = "",
            pushplusContent = "",
            webhookTitle = "",
            webhookContent = "",
            error = null,
        )
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun save() {
        val current = _state.value
        if (current.saving || current.testing) return
        viewModelScope.launch {
            if (persist()) {
                _messages.emit("通知设置已保存")
            }
        }
    }

    fun test() {
        val current = _state.value
        if (current.saving || current.testing) return
        if (current.mode == NotificationMode.NONE) {
            _state.update { it.copy(error = "请先选择通知方式") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(testing = true) }
            if (!persist()) {
                _state.update { it.copy(testing = false) }
                return@launch
            }
            when (val result = userRepository.testNotification()) {
                is ApiResult.Success -> {
                    _messages.emit(result.data?.message?.ifBlank { "测试通知已发送" } ?: "测试通知已发送")
                }

                is ApiResult.Failure -> {
                    _state.update { it.copy(error = result.message) }
                }
            }
            _state.update { it.copy(testing = false) }
        }
    }

    private suspend fun persist(): Boolean {
        val current = _state.value
        val validationError = validate(current)
        if (validationError != null) {
            _state.update { it.copy(error = validationError) }
            return false
        }

        _state.update { it.copy(saving = true, error = null) }
        val request = UpdateProfileRequest(
            notificationMode = current.mode.value,
            notificationEmail = current.email.trim(),
            notificationPushplusKey = current.pushplusKey.trim(),
            notificationWebhookUrl = current.webhookUrl.trim(),
            notificationWebhookSecret = current.webhookSecret.trim(),
            notificationGroupEnabled = current.groupEnabled,
            notificationPrivateEnabled = current.privateEnabled,
            notificationTemplate = buildTemplate(current),
        )
        return when (val result = userRepository.updateProfile(request)) {
            is ApiResult.Success -> {
                _state.update { it.copy(saving = false) }
                true
            }

            is ApiResult.Failure -> {
                _state.update { it.copy(saving = false, error = result.message) }
                false
            }
        }
    }

    private fun validate(state: NotificationSettingsUiState): String? {
        if (state.mode != NotificationMode.NONE &&
            state.availableMethods.isNotEmpty() &&
            state.mode.value !in state.availableMethods
        ) {
            return "服务器未启用该通知方式"
        }
        val email = state.email.trim()
        if (state.mode == NotificationMode.EMAIL && email.isBlank() && state.accountEmail.isBlank()) {
            return "请填写通知邮箱"
        }
        if (email.isNotBlank() && !email.matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))) {
            return "通知邮箱格式不正确"
        }
        if (state.mode == NotificationMode.PUSHPLUS && state.pushplusKey.trim().isBlank()) {
            return "请填写 PushPlus Token"
        }
        val webhookUrl = state.webhookUrl.trim()
        if (state.mode == NotificationMode.WEBHOOK && webhookUrl.isBlank()) {
            return "请填写 Webhook URL"
        }
        if (webhookUrl.isNotBlank() &&
            !webhookUrl.matches(Regex("^https?://.+", RegexOption.IGNORE_CASE))
        ) {
            return "Webhook URL 格式不正确"
        }
        return null
    }

    private fun buildTemplate(state: NotificationSettingsUiState): NotificationTemplateDto {
        val email = if (state.emailSubject.isNotBlank() || state.emailBody.isNotBlank()) {
            NotificationEmailTemplate(
                subject = state.emailSubject.trim(),
                body = state.emailBody.trim(),
            )
        } else {
            null
        }
        val pushplus = if (state.pushplusTitle.isNotBlank() || state.pushplusContent.isNotBlank()) {
            NotificationMessageTemplate(
                title = state.pushplusTitle.trim(),
                content = state.pushplusContent.trim(),
            )
        } else {
            null
        }
        val webhook = if (state.webhookTitle.isNotBlank() || state.webhookContent.isNotBlank()) {
            NotificationMessageTemplate(
                title = state.webhookTitle.trim(),
                content = state.webhookContent.trim(),
            )
        } else {
            null
        }
        return NotificationTemplateDto(email = email, pushplus = pushplus, webhook = webhook)
    }
}
