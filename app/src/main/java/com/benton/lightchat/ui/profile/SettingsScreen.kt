package com.benton.lightchat.ui.profile

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.benton.lightchat.data.remote.ServerStatusData
import com.benton.lightchat.ui.theme.ThemeMode
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    serverUrl: String?,
    onBack: () -> Unit,
    onChangeServer: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenPassword: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenTwoFactor: () -> Unit,
    onOpenEmailVerification: () -> Unit,
    updateViewModel: UpdateViewModel,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    var confirmLogout by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }

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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("外观", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "主题模式",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = state.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ThemeMode.entries.size,
                                ),
                                label = { Text(mode.label) },
                            )
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("动态取色", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = "使用系统壁纸配色",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = state.dynamicColor,
                                onCheckedChange = viewModel::setDynamicColor,
                            )
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("离线通知", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "设置离线时的邮件、PushPlus 或 Webhook 提醒",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = onOpenNotifications,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("配置离线通知")
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Dns,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("服务器地址", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = serverUrl.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    OutlinedButton(onClick = onChangeServer, modifier = Modifier.fillMaxWidth()) {
                        Text("更换服务器")
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("服务器状态", style = MaterialTheme.typography.titleMedium)
                    when {
                        state.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("加载中…", style = MaterialTheme.typography.bodyMedium)
                        }

                        state.status == null -> {
                            Text(
                                text = state.error ?: "无法获取服务器状态",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(onClick = viewModel::load) { Text("重试") }
                        }

                        else -> ServerStatusContent(state.status!!)
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("账号安全", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "定期更换密码可以保护账号安全",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onOpenPassword, modifier = Modifier.fillMaxWidth()) {
                        Text("修改密码")
                    }
                    OutlinedButton(onClick = onOpenSessions, modifier = Modifier.fillMaxWidth()) {
                        Text("登录设备管理")
                    }
                    OutlinedButton(onClick = onOpenTwoFactor, modifier = Modifier.fillMaxWidth()) {
                        Text("两步验证")
                    }
                    OutlinedButton(
                        onClick = onOpenEmailVerification,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("邮箱验证")
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.SystemUpdate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("关于与更新", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "当前版本 v${updateState.currentVersion.ifBlank { "未知" }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("自动检测更新", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "启动时每天检查一次",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = updateState.autoCheck,
                            onCheckedChange = updateViewModel::setAutoCheckUpdates,
                        )
                    }
                    when (updateState.status) {
                        UpdateCheckStatus.CHECKING -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("正在检查更新…", style = MaterialTheme.typography.bodyMedium)
                        }

                        UpdateCheckStatus.UP_TO_DATE -> Text(
                            text = "已是最新版本",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )

                        UpdateCheckStatus.UPDATE_AVAILABLE -> Text(
                            text = updateState.message ?: "发现新版本",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )

                        UpdateCheckStatus.ERROR -> Text(
                            text = updateState.message ?: "检查更新失败",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )

                        UpdateCheckStatus.IDLE -> Unit
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = updateViewModel::checkNow,
                            modifier = Modifier.weight(1f),
                            enabled = updateState.status != UpdateCheckStatus.CHECKING,
                        ) {
                            Text("检查更新")
                        }
                        if (updateState.status == UpdateCheckStatus.UPDATE_AVAILABLE) {
                            OutlinedButton(
                                onClick = updateViewModel::showUpdateDialog,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("查看新版本")
                            }
                        }
                    }
                }
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
private fun ServerStatusContent(status: ServerStatusData) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusRow("PHP 版本", status.php.version.ifBlank { "-" })
        StatusRow("内存限制", status.php.memoryLimit.ifBlank { "-" })
        StatusRow(
            "上传限制",
            listOf(status.php.uploadMaxFilesize, status.php.postMaxSize)
                .filter { it.isNotBlank() }
                .joinToString(" / ")
                .ifBlank { "-" },
        )
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        StatusRow(
            "磁盘可用",
            if (status.disk.freeMb >= 0) "${formatMb(status.disk.freeMb)} / ${formatMb(status.disk.totalMb)}" else "未知",
        )
        StatusRow("应用占用", formatMb(status.disk.appUsedMb))
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        StatusRow(
            "本月流量",
            "${status.usage.networkFlowMb} MB · ${status.usage.totalRequests} 次请求",
        )
        StatusRow(
            "活跃会话",
            if (status.usage.activeSessions >= 0) status.usage.activeSessions.toString() else "未知",
        )
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun formatMb(value: Double): String = when {
    value >= 1024 -> String.format(Locale.getDefault(), "%.1f GB", value / 1024)
    else -> String.format(Locale.getDefault(), "%.1f MB", value)
}
