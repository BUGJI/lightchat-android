package com.benton.lightchat.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.benton.lightchat.ui.components.FormSkeleton
import com.benton.lightchat.ui.components.SyncBarContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    viewModel: NotificationSettingsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var emailExpanded by rememberSaveable { mutableStateOf(false) }
    var pushplusExpanded by rememberSaveable { mutableStateOf(false) }
    var webhookExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("离线通知") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.loading) {
            FormSkeleton(Modifier.padding(padding), blocks = 3)
            return@Scaffold
        }

        SyncBarContent(
            visible = state.refreshing,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            columnModifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("通知方式", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "仅在你离线时，通过以下方式提醒新消息。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    NotificationMode.entries.forEach { mode ->
                        val available = mode == NotificationMode.NONE ||
                            mode.value in state.availableMethods
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = available) { viewModel.setMode(mode) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = state.mode == mode,
                                onClick = { if (available) viewModel.setMode(mode) },
                                enabled = available,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (available) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                Text(
                                    text = if (available) {
                                        mode.description
                                    } else {
                                        "服务器未启用该通知方式"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (state.availableMethods.isEmpty()) {
                        Text(
                            text = "服务器暂未启用任何通知方式，请联系管理员。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (state.mode != NotificationMode.NONE &&
                        state.availableMethods.isNotEmpty() &&
                        state.mode.value !in state.availableMethods
                    ) {
                        Text(
                            text = "当前选择的通知方式已被服务器禁用，请重新选择。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            if (state.mode != NotificationMode.NONE) {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("账号与凭据", style = MaterialTheme.typography.titleMedium)
                        when (state.mode) {
                            NotificationMode.EMAIL -> {
                                OutlinedTextField(
                                    value = state.email,
                                    onValueChange = viewModel::setEmail,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("通知邮箱") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Email,
                                        imeAction = ImeAction.Next,
                                    ),
                                    supportingText = {
                                        Text(
                                            if (state.accountEmail.isNotBlank()) {
                                                "留空时使用账号邮箱：${state.accountEmail}"
                                            } else {
                                                "用于接收离线提醒"
                                            },
                                        )
                                    },
                                )
                            }

                            NotificationMode.PUSHPLUS -> {
                                OutlinedTextField(
                                    value = state.pushplusKey,
                                    onValueChange = viewModel::setPushplusKey,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("PushPlus Token") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    supportingText = { Text("在 PushPlus 官网获取的 token") },
                                )
                            }

                            NotificationMode.WEBHOOK -> {
                                OutlinedTextField(
                                    value = state.webhookUrl,
                                    onValueChange = viewModel::setWebhookUrl,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Webhook URL") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Uri,
                                        imeAction = ImeAction.Next,
                                    ),
                                    supportingText = { Text("服务器会向该地址 POST JSON 数据") },
                                )
                                OutlinedTextField(
                                    value = state.webhookSecret,
                                    onValueChange = viewModel::setWebhookSecret,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("签名密钥（可选）") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    supportingText = { Text("会通过 X-Webhook-Secret 请求头发送") },
                                )
                            }

                            NotificationMode.NONE -> Unit
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("提醒范围", style = MaterialTheme.typography.titleMedium)
                    SettingSwitchRow(
                        title = "群聊消息",
                        description = "接收频道中的离线消息提醒",
                        checked = state.groupEnabled,
                        onCheckedChange = viewModel::setGroupEnabled,
                    )
                    SettingSwitchRow(
                        title = "私聊消息",
                        description = "接收私聊中的离线消息提醒",
                        checked = state.privateEnabled,
                        onCheckedChange = viewModel::setPrivateEnabled,
                    )
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "消息模板（可选）",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::resetTemplates) { Text("恢复默认") }
                    }
                    Text(
                        text = "留空时使用服务器默认模板。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "可用变量：{nickname}  {unread_count}  {messages_preview}  " +
                            "{sender_name}  {last_message_time}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    if (state.availableMethods.isEmpty()) {
                        Text(
                            text = "服务器未启用可配置的通知方式。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if ("email" in state.availableMethods) {
                        TemplateSection(
                            title = "邮件模板",
                            expanded = emailExpanded,
                            onToggle = { emailExpanded = !emailExpanded },
                        ) {
                            OutlinedTextField(
                                value = state.emailSubject,
                                onValueChange = viewModel::setEmailSubject,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("邮件主题") },
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = state.emailBody,
                                onValueChange = viewModel::setEmailBody,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("邮件正文") },
                                minLines = 5,
                                maxLines = 10,
                            )
                        }
                    }

                    if ("pushplus" in state.availableMethods) {
                        TemplateSection(
                            title = "PushPlus 模板",
                            expanded = pushplusExpanded,
                            onToggle = { pushplusExpanded = !pushplusExpanded },
                        ) {
                            OutlinedTextField(
                                value = state.pushplusTitle,
                                onValueChange = viewModel::setPushplusTitle,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("推送标题") },
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = state.pushplusContent,
                                onValueChange = viewModel::setPushplusContent,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("推送内容（支持 HTML）") },
                                minLines = 5,
                                maxLines = 10,
                            )
                        }
                    }

                    if ("webhook" in state.availableMethods) {
                        TemplateSection(
                            title = "Webhook 模板",
                            expanded = webhookExpanded,
                            onToggle = { webhookExpanded = !webhookExpanded },
                        ) {
                            OutlinedTextField(
                                value = state.webhookTitle,
                                onValueChange = viewModel::setWebhookTitle,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("标题") },
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = state.webhookContent,
                                onValueChange = viewModel::setWebhookContent,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("内容") },
                                minLines = 5,
                                maxLines = 10,
                            )
                        }
                    }
                }
            }

            state.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.saving && !state.testing,
            ) {
                if (state.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(if (state.saving) "保存中…" else "保存")
            }

            OutlinedButton(
                onClick = viewModel::test,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.saving && !state.testing,
            ) {
                if (state.testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Icon(
                    Icons.Filled.Notifications,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(if (state.testing) "发送中…" else "发送测试通知")
            }
            Text(
                text = "测试会先保存当前配置，再使用已保存的通知方式发送。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun TemplateSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                content()
            }
        }
    }
}
