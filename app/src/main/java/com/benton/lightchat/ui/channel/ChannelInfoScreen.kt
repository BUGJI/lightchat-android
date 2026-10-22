package com.benton.lightchat.ui.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.benton.lightchat.data.remote.ChannelInfoDto
import com.benton.lightchat.data.remote.ChannelMemberDto
import com.benton.lightchat.ui.components.LoadingBox
import com.benton.lightchat.ui.components.UserAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelInfoScreen(
    channelId: Long,
    initialTitle: String,
    baseUrl: String?,
    currentUserId: Long,
    onBack: () -> Unit,
    onOpenUserProfile: (Long) -> Unit,
    onClosed: () -> Unit,
    viewModel: ChannelInfoViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showEdit by rememberSaveable { mutableStateOf(false) }
    var showInvite by rememberSaveable { mutableStateOf(false) }
    var confirmDissolve by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var muteTarget by remember { mutableStateOf<ChannelMemberDto?>(null) }
    var kickTarget by remember { mutableStateOf<ChannelMemberDto?>(null) }
    var transferTarget by remember { mutableStateOf<ChannelMemberDto?>(null) }

    LaunchedEffect(channelId) { viewModel.load(channelId) }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val channel = state.channel
    val isOwner = channel != null &&
        (channel.ownerId == currentUserId || channel.viewerRole == "owner")
    val isAdmin = channel?.viewerRole == "admin"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("群聊信息") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = (channel?.displayName ?: initialTitle)
                                .firstOrNull()?.uppercaseChar()?.toString() ?: "#",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (channel?.type == "private") {
                                Icon(
                                    Icons.Filled.Lock,
                                    contentDescription = "私密频道",
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                text = channel?.displayName ?: initialTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = "${channel?.memberCount ?: state.members.size} 位成员",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isOwner) {
                        FilledTonalButton(onClick = { showEdit = true }) {
                            Text("编辑群资料")
                        }
                    }
                    if (channel != null && channel.type == "private" && channel.viewerRole != null) {
                        OutlinedButton(onClick = { showInvite = true }) {
                            Icon(
                                Icons.Filled.PersonAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("邀请成员")
                        }
                    }
                }
            }

            HorizontalDivider()
            Text(
                text = "成员",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                items(state.members, key = { it.userId }) { member ->
                    MemberRow(
                        member = member,
                        baseUrl = baseUrl,
                        showMenu = member.userId != currentUserId &&
                            (isOwner || (isAdmin && member.role == "member")),
                        isOwner = isOwner,
                        busy = state.actionInProgress,
                        onClick = { onOpenUserProfile(member.userId) },
                        onMute = { muteTarget = member },
                        onUnmute = { viewModel.memberAction(member.userId, "unmute") },
                        onToggleAdmin = {
                            viewModel.memberAction(
                                member.userId,
                                "set_role",
                                role = if (member.role == "admin") "member" else "admin",
                            )
                        },
                        onKick = { kickTarget = member },
                        onTransfer = { transferTarget = member },
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                }
            }

            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isOwner) {
                    OutlinedButton(
                        onClick = { confirmDissolve = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("解散群聊", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    OutlinedButton(
                        onClick = { confirmLeave = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("退出群聊")
                    }
                }
            }
        }
    }

    if (showEdit && channel != null) {
        EditChannelDialog(
            channel = channel,
            busy = state.actionInProgress,
            onDismiss = { showEdit = false },
            onSave = { name, type, description ->
                viewModel.updateChannel(name, type, description) { showEdit = false }
            },
        )
    }

    if (showInvite) {
        InviteDialog(
            busy = state.actionInProgress,
            onDismiss = { showInvite = false },
            onInvite = { username ->
                viewModel.invite(username) { showInvite = false }
            },
        )
    }

    if (confirmDissolve) {
        AlertDialog(
            onDismissRequest = { confirmDissolve = false },
            title = { Text("解散群聊") },
            text = { Text("解散后所有成员将收到通知，聊天记录归档保留。确定解散？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDissolve = false
                        viewModel.dissolve(onClosed)
                    },
                ) {
                    Text("解散", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDissolve = false }) { Text("取消") }
            },
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("退出群聊") },
            text = { Text("退出后将不再接收该群消息，确定退出？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLeave = false
                        viewModel.leave(onClosed)
                    },
                ) {
                    Text("退出")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text("取消") }
            },
        )
    }

    muteTarget?.let { target ->
        MemberMuteDialog(
            member = target,
            busy = state.actionInProgress,
            onDismiss = { muteTarget = null },
            onConfirm = { minutes ->
                viewModel.memberAction(
                    userId = target.userId,
                    action = "mute",
                    durationMinutes = minutes,
                    onDone = { muteTarget = null },
                )
            },
        )
    }

    kickTarget?.let { target ->
        val name = target.nickname.takeIf { it.isNotBlank() } ?: target.username
        AlertDialog(
            onDismissRequest = { kickTarget = null },
            title = { Text("踢出成员") },
            text = { Text("确定将 $name 移出群聊？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.memberAction(
                            userId = target.userId,
                            action = "kick",
                            onDone = { kickTarget = null },
                        )
                    },
                    enabled = !state.actionInProgress,
                ) {
                    Text("踢出", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { kickTarget = null }) { Text("取消") }
            },
        )
    }

    transferTarget?.let { target ->
        val name = target.nickname.takeIf { it.isNotBlank() } ?: target.username
        AlertDialog(
            onDismissRequest = { transferTarget = null },
            title = { Text("转让群主") },
            text = { Text("确定把群主转让给 $name？转让后你将成为管理员。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.memberAction(
                            userId = target.userId,
                            action = "transfer_owner",
                            onDone = { transferTarget = null },
                        )
                    },
                    enabled = !state.actionInProgress,
                ) {
                    Text("转让")
                }
            },
            dismissButton = {
                TextButton(onClick = { transferTarget = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun MemberRow(
    member: ChannelMemberDto,
    baseUrl: String?,
    showMenu: Boolean,
    isOwner: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    onMute: () -> Unit,
    onUnmute: () -> Unit,
    onToggleAdmin: () -> Unit,
    onKick: () -> Unit,
    onTransfer: () -> Unit,
) {
    val displayName = member.nickname.takeIf { it.isNotBlank() } ?: member.username
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(
            name = displayName,
            avatarUrl = member.avatar.takeIf { it.isNotBlank() },
            baseUrl = baseUrl,
            size = 40.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "@${member.username}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val roleLabel = when (member.role) {
            "owner" -> "群主"
            "admin" -> "管理员"
            else -> null
        }
        if (roleLabel != null) {
            Text(
                text = roleLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (member.muted) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "禁言",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (showMenu) {
            Box {
                IconButton(onClick = { menuExpanded = true }, enabled = !busy) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "管理成员")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(if (member.muted) "解除禁言" else "禁言") },
                        onClick = {
                            menuExpanded = false
                            if (member.muted) onUnmute() else onMute()
                        },
                    )
                    if (isOwner) {
                        DropdownMenuItem(
                            text = {
                                Text(if (member.role == "admin") "取消管理员" else "设为管理员")
                            },
                            onClick = {
                                menuExpanded = false
                                onToggleAdmin()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("踢出群聊", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuExpanded = false
                            onKick()
                        },
                    )
                    if (isOwner) {
                        DropdownMenuItem(
                            text = { Text("转让群主") },
                            onClick = {
                                menuExpanded = false
                                onTransfer()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberMuteDialog(
    member: ChannelMemberDto,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val options = listOf(
        10 to "10 分钟",
        60 to "1 小时",
        1440 to "1 天",
        10080 to "7 天",
        0 to "永久",
    )
    var selected by rememberSaveable { mutableIntStateOf(10) }
    val displayName = member.nickname.takeIf { it.isNotBlank() } ?: member.username

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("禁言") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "选择对 $displayName 的禁言时长",
                    style = MaterialTheme.typography.bodyMedium,
                )
                options.forEach { (minutes, label) ->
                    FilterChip(
                        selected = selected == minutes,
                        onClick = { selected = minutes },
                        label = { Text(label) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }, enabled = !busy) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
        },
    )
}

@Composable
private fun EditChannelDialog(
    channel: ChannelInfoDto,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var displayName by rememberSaveable { mutableStateOf(channel.displayName) }
    var type by rememberSaveable { mutableStateOf(channel.type) }
    var description by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("编辑群资料") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { if (it.length <= 50) displayName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("群名称") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 200) description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("群描述（留空保持不变）") },
                    maxLines = 3,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == "public",
                        onClick = { type = "public" },
                        label = { Text("公开") },
                    )
                    FilterChip(
                        selected = type == "private",
                        onClick = { type = "private" },
                        label = { Text("私密") },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(displayName, type, description) },
                enabled = !busy && displayName.isNotBlank(),
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
        },
    )
}

@Composable
private fun InviteDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onInvite: (String) -> Unit,
) {
    var username by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("邀请成员") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "输入对方的用户名，邀请后对方会直接加入本群。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.removePrefix("@") },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("用户名") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onInvite(username) },
                enabled = !busy && username.isNotBlank(),
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text("邀请")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
        },
    )
}
