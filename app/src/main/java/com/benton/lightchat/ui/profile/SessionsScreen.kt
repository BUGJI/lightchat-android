package com.benton.lightchat.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.benton.lightchat.data.remote.SessionDto
import com.benton.lightchat.ui.components.EmptyState
import com.benton.lightchat.ui.components.InlineSyncBar
import com.benton.lightchat.ui.components.ListSkeleton
import com.benton.lightchat.util.TimeFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    onBack: () -> Unit,
    viewModel: SessionsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmRevokeOthers by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(state.info, state.error) {
        val text = state.error ?: state.info
        if (!text.isNullOrBlank()) {
            snackbarHostState.showSnackbar(text)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("登录设备") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::load) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.loading -> ListSkeleton(Modifier.padding(padding))

            state.sessions.isEmpty() -> EmptyState(
                icon = Icons.Filled.Devices,
                title = "暂无登录设备",
                description = "下拉刷新试试",
                modifier = Modifier.padding(padding),
            )

            else -> Column(Modifier.padding(padding)) {
                InlineSyncBar(visible = state.refreshing)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.sessions, key = { it.id }) { session ->
                        SessionCard(
                            session = session,
                            busy = state.actionInProgress,
                            onRevoke = { viewModel.revoke(session) },
                        )
                    }
                    item(key = "revoke_others") {
                        OutlinedButton(
                            onClick = { confirmRevokeOthers = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.actionInProgress &&
                                state.sessions.any { !it.current },
                        ) {
                            Text("退出其他所有设备")
                        }
                    }
                }
            }
        }
    }

    if (confirmRevokeOthers) {
        AlertDialog(
            onDismissRequest = { confirmRevokeOthers = false },
            title = { Text("退出其他设备") },
            text = { Text("将退出除当前设备外的所有登录会话，确定继续？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRevokeOthers = false
                        viewModel.revokeOthers()
                    },
                    enabled = !state.actionInProgress,
                ) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRevokeOthers = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SessionCard(
    session: SessionDto,
    busy: Boolean,
    onRevoke: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Devices,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = session.userAgent.ifBlank { "未知设备" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (session.current) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "当前设备",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else if (session.expired) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "已过期",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (session.ip.isNotBlank()) {
                Text(
                    text = "IP：${session.ip}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val created = TimeFormat.chatTimestamp(session.createdAt)
            if (created.isNotBlank()) {
                Text(
                    text = "登录时间：$created",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val expires = TimeFormat.chatTimestamp(session.absoluteExpiresAt)
            if (expires.isNotBlank()) {
                Text(
                    text = "有效期至：$expires",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!session.current) {
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onRevoke, enabled = !busy) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("退出该设备", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
