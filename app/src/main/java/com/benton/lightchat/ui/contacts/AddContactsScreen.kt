package com.benton.lightchat.ui.contacts

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.benton.lightchat.data.remote.ChannelDto
import com.benton.lightchat.data.remote.UserDto
import com.benton.lightchat.ui.components.EmptyState
import com.benton.lightchat.ui.components.LoadingBox
import com.benton.lightchat.ui.components.UserAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContactsScreen(
    baseUrl: String?,
    onBack: () -> Unit,
    onOpenPrivate: (Long, Long, String) -> Unit,
    viewModel: AddContactsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var section by rememberSaveable { mutableIntStateOf(0) }
    var showCreateChannel by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.start() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AddContactsEvent.OpenPrivateChat ->
                    onOpenPrivate(event.chatId, event.userId, event.title)

                is AddContactsEvent.ShowMessage ->
                    snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("添加好友/群聊") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                SegmentedButton(
                    selected = section == 0,
                    onClick = { section = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text("好友") },
                )
                SegmentedButton(
                    selected = section == 1,
                    onClick = { section = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text("群聊") },
                )
            }

            if (section == 0) {
                FriendsSection(
                    state = state,
                    baseUrl = baseUrl,
                    onQueryChange = viewModel::setSearchQuery,
                    onStartPrivate = viewModel::startPrivateChat,
                    onAddFriend = viewModel::sendFriendRequest,
                )
            } else {
                GroupsSection(
                    state = state,
                    onCreateChannel = { showCreateChannel = true },
                    onJoin = viewModel::joinChannel,
                )
            }
        }
    }

    if (showCreateChannel) {
        CreateChannelDialog(
            busy = state.actionInProgress,
            onDismiss = { showCreateChannel = false },
            onCreate = { name, displayName, type, description ->
                viewModel.createChannel(name, displayName, type, description) {
                    showCreateChannel = false
                }
            },
        )
    }
}

@Composable
private fun FriendsSection(
    state: AddContactsUiState,
    baseUrl: String?,
    onQueryChange: (String) -> Unit,
    onStartPrivate: (UserDto) -> Unit,
    onAddFriend: (UserDto) -> Unit,
) {
    val users = if (state.searchQuery.isBlank()) state.users else state.searchResults

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("搜索用户名或昵称") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.searchQuery.isNotBlank()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "清空")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )

        when {
            state.loading || state.searching -> LoadingBox()
            users.isEmpty() -> EmptyState(
                icon = Icons.Filled.People,
                title = if (state.searchQuery.isBlank()) "暂无用户" else "没有找到相关用户",
                description = if (state.searchQuery.isBlank()) "点击右上角刷新试试" else "换个关键词试试",
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                items(users, key = { it.id }) { user ->
                    val displayName = user.nickname?.takeIf { it.isNotBlank() } ?: user.username
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UserAvatar(
                            name = displayName,
                            avatarUrl = user.avatar,
                            baseUrl = baseUrl,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = buildString {
                                    append("@${user.username}")
                                    user.signature?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        if (user.id in state.friendUserIds || user.id in state.requestedUserIds) {
                            Text(
                                text = if (user.id in state.friendUserIds) "已是好友" else "已申请",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        } else {
                            IconButton(
                                onClick = { onAddFriend(user) },
                                enabled = !state.actionInProgress,
                            ) {
                                Icon(Icons.Filled.PersonAdd, contentDescription = "加好友")
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                        FilledTonalButton(
                            onClick = { onStartPrivate(user) },
                            enabled = !state.actionInProgress,
                        ) {
                            Text("发消息")
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 76.dp))
                }
            }
        }
    }
}

@Composable
private fun GroupsSection(
    state: AddContactsUiState,
    onCreateChannel: () -> Unit,
    onJoin: (ChannelDto) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "可加入的群聊",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCreateChannel) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("创建群聊")
            }
        }

        when {
            state.loading -> LoadingBox()
            state.channels.isEmpty() -> EmptyState(
                icon = Icons.Filled.Groups,
                title = "暂无可加入的群聊",
                description = "可以创建一个新群聊，或稍后刷新",
                actionLabel = "创建群聊",
                onAction = onCreateChannel,
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                items(state.channels, key = { it.id }) { channel ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = (channel.displayName.firstOrNull() ?: '#')
                                    .uppercaseChar().toString(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (channel.type == "private") {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = "私密频道",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(
                                    text = channel.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = buildString {
                                    append("${channel.memberCount} 位成员")
                                    if (channel.description.isNotBlank()) {
                                        append(" · ${channel.description}")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = { onJoin(channel) },
                            enabled = !state.actionInProgress,
                        ) {
                            Text("加入")
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 76.dp))
                }
            }
        }
    }
}

@Composable
private fun CreateChannelDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("public") }

    val nameValid = name.matches(Regex("^[A-Za-z0-9_]{2,50}$"))
    val canSubmit = nameValid && displayName.isNotBlank() && !busy

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("创建群聊") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("群聊标识") },
                    placeholder = { Text("tech_chat") },
                    singleLine = true,
                    isError = name.isNotBlank() && !nameValid,
                    supportingText = { Text("2-50 位字母、数字或下划线") },
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("显示名称") },
                    placeholder = { Text("技术交流") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述（可选）") },
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
                onClick = { onCreate(name.trim(), displayName.trim(), type, description.trim()) },
                enabled = canSubmit,
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
        },
    )
}
