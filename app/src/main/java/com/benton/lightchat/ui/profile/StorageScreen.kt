package com.benton.lightchat.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.benton.lightchat.data.local.StorageSnapshot
import com.benton.lightchat.util.SizeFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(
    onBack: () -> Unit,
    viewModel: StorageViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingAction by remember { mutableStateOf<StorageAction?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("存储空间") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.busy) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val snapshot = state.snapshot
            if (state.loading && snapshot == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (snapshot != null) {
                TotalCard(snapshot)
                CategoriesCard(
                    snapshot = snapshot,
                    busy = state.busy,
                    onClearMessages = { pendingAction = StorageAction.Messages },
                    onClearImages = { pendingAction = StorageAction.Images },
                    onClearAttachments = { pendingAction = StorageAction.Attachments },
                )
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        text = "缓存按账号隔离保存，退出登录不会自动删除。清理聊天记录只影响当前账号，服务端记录不受影响。",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = { pendingAction = StorageAction.All },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.busy,
                ) {
                    Text(
                        text = "清理全部本地数据",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            state.error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { if (!state.busy) pendingAction = null },
            title = { Text(action.title) },
            text = { Text(action.description) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingAction = null
                        when (action) {
                            StorageAction.Messages -> viewModel.clearMessages()
                            StorageAction.Images -> viewModel.clearImages()
                            StorageAction.Attachments -> viewModel.clearAttachments()
                            StorageAction.All -> viewModel.clearAll()
                        }
                    },
                    enabled = !state.busy,
                ) {
                    Text(action.confirmLabel, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }, enabled = !state.busy) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun TotalCard(snapshot: StorageSnapshot) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text("本地缓存", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = SizeFormat.format(snapshot.totalBytes),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "设备可用 ${SizeFormat.format(snapshot.freeBytes)} / " +
                    SizeFormat.format(snapshot.diskTotalBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "当前账号：${snapshot.messageCount} 条消息 · " +
                    "${snapshot.conversationCount} 个会话",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CategoriesCard(
    snapshot: StorageSnapshot,
    busy: Boolean,
    onClearMessages: () -> Unit,
    onClearImages: () -> Unit,
    onClearAttachments: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            StorageCategoryRow(
                icon = Icons.AutoMirrored.Filled.Chat,
                title = "聊天记录",
                detail = "${SizeFormat.format(snapshot.databaseBytes)} · " +
                    "${snapshot.messageCount} 条消息",
                actionEnabled = snapshot.messageCount > 0 && !busy,
                onAction = onClearMessages,
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            StorageCategoryRow(
                icon = Icons.Filled.Image,
                title = "图片缓存",
                detail = "${SizeFormat.format(snapshot.imageBytes)} · 图片与头像",
                actionEnabled = snapshot.imageBytes > 0 && !busy,
                onAction = onClearImages,
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            StorageCategoryRow(
                icon = Icons.Filled.InsertDriveFile,
                title = "文件缓存",
                detail = "${SizeFormat.format(snapshot.attachmentBytes)} · 已下载附件",
                actionEnabled = snapshot.attachmentBytes > 0 && !busy,
                onAction = onClearAttachments,
            )
        }
    }
}

@Composable
private fun StorageCategoryRow(
    icon: ImageVector,
    title: String,
    detail: String,
    actionEnabled: Boolean,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onAction, enabled = actionEnabled) {
            Text("清理")
        }
    }
}

private enum class StorageAction(
    val title: String,
    val description: String,
    val confirmLabel: String,
) {
    Messages(
        title = "清理聊天记录",
        description = "将删除当前账号的本地消息与同步游标，服务端记录不受影响。",
        confirmLabel = "清理",
    ),
    Images(
        title = "清理图片缓存",
        description = "将删除本地缓存的图片和头像，之后需要重新加载。",
        confirmLabel = "清理",
    ),
    Attachments(
        title = "清理文件缓存",
        description = "将删除本地缓存的附件文件，之后需要重新下载。",
        confirmLabel = "清理",
    ),
    All(
        title = "清理全部本地数据",
        description = "将删除所有账号的本地聊天记录、图片与文件缓存。此操作不可恢复。",
        confirmLabel = "全部清理",
    ),
}
