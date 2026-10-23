package com.benton.lightchat.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    serverUrl: String?,
    onBack: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenServer: () -> Unit,
    onOpenServerStatus: () -> Unit,
    onOpenPassword: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenTwoFactor: () -> Unit,
    onOpenEmailVerification: () -> Unit,
    onOpenAbout: () -> Unit,
    updateViewModel: UpdateViewModel,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    var confirmLogout by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsSection(title = "通用") {
                SettingsRow(
                    icon = Icons.Filled.Palette,
                    title = "外观",
                    subtitle = "主题、强调色与字体大小",
                    onClick = onOpenAppearance,
                )
                SettingsRow(
                    icon = Icons.Filled.Notifications,
                    title = "离线通知",
                    subtitle = "邮件、PushPlus 或 Webhook 提醒",
                    onClick = onOpenNotifications,
                    showDivider = true,
                )
                SettingsRow(
                    icon = Icons.Filled.Storage,
                    title = "存储空间",
                    subtitle = "本地聊天记录、图片与文件缓存",
                    onClick = onOpenStorage,
                    showDivider = true,
                )
            }

            SettingsSection(title = "服务器") {
                SettingsRow(
                    icon = Icons.Filled.Dns,
                    title = "服务器地址",
                    subtitle = serverUrl.orEmpty().ifBlank { "未配置" },
                    onClick = onOpenServer,
                )
                SettingsRow(
                    icon = Icons.Filled.Cloud,
                    title = "服务器状态",
                    subtitle = "PHP、磁盘与流量信息",
                    onClick = onOpenServerStatus,
                    showDivider = true,
                )
            }

            SettingsSection(title = "账号安全") {
                SettingsRow(
                    icon = Icons.Filled.Lock,
                    title = "修改密码",
                    subtitle = "定期更换密码可以保护账号安全",
                    onClick = onOpenPassword,
                )
                SettingsRow(
                    icon = Icons.Filled.Devices,
                    title = "登录设备管理",
                    subtitle = "查看并下线其他登录设备",
                    onClick = onOpenSessions,
                    showDivider = true,
                )
                SettingsRow(
                    icon = Icons.Filled.VerifiedUser,
                    title = "两步验证",
                    subtitle = "使用验证器或验证码二次确认",
                    onClick = onOpenTwoFactor,
                    showDivider = true,
                )
                SettingsRow(
                    icon = Icons.Filled.Email,
                    title = "邮箱验证",
                    subtitle = "绑定并验证邮箱地址",
                    onClick = onOpenEmailVerification,
                    showDivider = true,
                )
            }

            SettingsSection(title = "关于") {
                SettingsRow(
                    icon = Icons.Filled.Info,
                    title = "关于与更新",
                    subtitle = "当前版本 v${updateState.currentVersion.ifBlank { "未知" }}",
                    onClick = onOpenAbout,
                )
            }

            OutlinedButton(
                onClick = { confirmLogout = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("退出登录")
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("退出登录") },
            text = { Text("确定要退出当前账号吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogout = false
                        viewModel.logout()
                    },
                    enabled = !state.loggingOut,
                ) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp),
        )
        Card(Modifier.fillMaxWidth()) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    showDivider: Boolean = false,
) {
    if (showDivider) {
        HorizontalDivider(Modifier.padding(start = 56.dp))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
