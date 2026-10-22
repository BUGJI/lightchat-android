package com.benton.lightchat.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.benton.lightchat.data.remote.ChannelDto
import com.benton.lightchat.data.remote.PrivateChatDto
import com.benton.lightchat.ui.components.EmptyState
import com.benton.lightchat.ui.components.LoadingBox
import com.benton.lightchat.ui.components.UserAvatar
import com.benton.lightchat.ui.profile.ProfileScreen
import com.benton.lightchat.util.TimeFormat

private data class ConversationEntry(
    val sortKey: Long,
    val channel: ChannelDto? = null,
    val chat: PrivateChatDto? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    baseUrl: String?,
    onOpenChannel: (Long, String) -> Unit,
    onOpenPrivate: (Long, Long, String) -> Unit,
    onOpenAdd: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditProfile: () -> Unit,
    viewModel: MainViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) { viewModel.start() }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.pollLoop()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val title = when (selectedTab) {
        0 -> "消息"
        1 -> "联系人"
        else -> "我的"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                actions = {
                    if (selectedTab == 0 || selectedTab == 1) {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                        }
                    }
                    if (selectedTab == 1) {
                        IconButton(onClick = onOpenFriends) {
                            Icon(Icons.Filled.Group, contentDescription = "好友管理")
                        }
                        IconButton(onClick = onOpenAdd) {
                            Icon(Icons.Filled.PersonAdd, contentDescription = "添加好友或群聊")
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) },
                    label = { Text("消息") },
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Filled.People, contentDescription = null) },
                    label = { Text("联系人") },
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    label = { Text("我的") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> MessagesTab(
                    state = state,
                    baseUrl = baseUrl,
                    onOpenChannel = onOpenChannel,
                    onOpenPrivate = onOpenPrivate,
                    onOpenAdd = onOpenAdd,
                    onLeaveChannel = viewModel::leaveChannel,
                )

                1 -> ContactsTab(
                    state = state,
                    baseUrl = baseUrl,
                    onOpenChannel = onOpenChannel,
                    onOpenPrivate = onOpenPrivate,
                    onOpenAdd = onOpenAdd,
                    onLeaveChannel = viewModel::leaveChannel,
                )

                else -> ProfileScreen(
                    baseUrl = baseUrl,
                    onEditProfile = onEditProfile,
                    onOpenSettings = onOpenSettings,
                )
            }
        }
    }
}

@Composable
private fun MessagesTab(
    state: MainUiState,
    baseUrl: String?,
    onOpenChannel: (Long, String) -> Unit,
    onOpenPrivate: (Long, Long, String) -> Unit,
    onOpenAdd: () -> Unit,
    onLeaveChannel: (ChannelDto) -> Unit,
) {
    if (state.loading) {
        LoadingBox()
        return
    }

    val conversations = remember(state.channels, state.privateChats) {
        buildList {
            state.privateChats.forEach { chat ->
                add(
                    ConversationEntry(
                        sortKey = timestampOf(chat.lastMessageAt, chat.createdAt),
                        chat = chat,
                    ),
                )
            }
            state.channels.filter { it.isJoined }.forEach { channel ->
                add(
                    ConversationEntry(
                        sortKey = timestampOf(channel.lastMessageAt, channel.createdAt),
                        channel = channel,
                    ),
                )
            }
        }.sortedByDescending { it.sortKey }
    }

    if (conversations.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Forum,
            title = "暂无会话",
            description = "添加好友或加入群聊，开始聊天吧",
            actionLabel = "添加好友/群聊",
            onAction = onOpenAdd,
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        items(
            conversations,
            key = { entry ->
                entry.channel?.let { "channel_${it.id}" } ?: "private_${entry.chat?.id}"
            },
        ) { entry ->
            val channel = entry.channel
            if (channel != null) {
                ChannelRow(
                    channel = channel,
                    baseUrl = baseUrl,
                    preview = channel.lastMessage.orEmpty(),
                    time = TimeFormat.chatTimestamp(channel.lastMessageAt ?: channel.createdAt),
                    onClick = { onOpenChannel(channel.id, channel.displayName) },
                    onLeave = { onLeaveChannel(channel) },
                )
            } else {
                val chat = entry.chat ?: return@items
                val displayName = chat.otherNickname?.takeIf { it.isNotBlank() } ?: chat.otherUsername
                PrivateChatRow(
                    name = displayName,
                    avatarUrl = chat.otherAvatar,
                    baseUrl = baseUrl,
                    preview = chat.lastMessage ?: "开始聊天吧",
                    time = TimeFormat.chatTimestamp(chat.lastMessageAt),
                    unread = chat.unreadCount,
                    muted = chat.dnd > 0,
                    onClick = { onOpenPrivate(chat.id, chat.otherUserId, displayName) },
                )
            }
            HorizontalDivider(modifier = Modifier.padding(start = 76.dp))
        }
    }
}

@Composable
private fun ContactsTab(
    state: MainUiState,
    baseUrl: String?,
    onOpenChannel: (Long, String) -> Unit,
    onOpenPrivate: (Long, Long, String) -> Unit,
    onOpenAdd: () -> Unit,
    onLeaveChannel: (ChannelDto) -> Unit,
) {
    var section by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SegmentedButton(
                selected = section == 0,
                onClick = { section = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                label = { Text("私聊") },
            )
            SegmentedButton(
                selected = section == 1,
                onClick = { section = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                label = { Text("群聊") },
            )
        }

        if (state.loading) {
            LoadingBox()
            return@Column
        }

        if (section == 0) {
            if (state.privateChats.isEmpty()) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    title = "还没有私聊",
                    description = "去添加好友，开始一段对话",
                    actionLabel = "添加好友",
                    onAction = onOpenAdd,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    items(state.privateChats, key = { it.id }) { chat ->
                        val displayName = chat.otherNickname?.takeIf { it.isNotBlank() }
                            ?: chat.otherUsername
                        PrivateChatRow(
                            name = displayName,
                            avatarUrl = chat.otherAvatar,
                            baseUrl = baseUrl,
                            preview = chat.lastMessage ?: "开始聊天吧",
                            time = TimeFormat.chatTimestamp(chat.lastMessageAt),
                            unread = chat.unreadCount,
                            muted = chat.dnd > 0,
                            onClick = { onOpenPrivate(chat.id, chat.otherUserId, displayName) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 76.dp))
                    }
                }
            }
        } else {
            val joinedChannels = state.channels.filter { it.isJoined }
            if (joinedChannels.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Groups,
                    title = "还没有群聊",
                    description = "添加或创建一个群聊，和更多人交流",
                    actionLabel = "添加群聊",
                    onAction = onOpenAdd,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    items(joinedChannels, key = { it.id }) { channel ->
                        ChannelRow(
                            channel = channel,
                            baseUrl = baseUrl,
                            preview = channel.lastMessage.orEmpty(),
                            time = TimeFormat.chatTimestamp(channel.lastMessageAt ?: channel.createdAt),
                            onClick = { onOpenChannel(channel.id, channel.displayName) },
                            onLeave = { onLeaveChannel(channel) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 76.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivateChatRow(
    name: String,
    avatarUrl: String?,
    baseUrl: String?,
    preview: String,
    time: String,
    unread: Int,
    muted: Boolean,
    onClick: () -> Unit,
) {
    ConversationRow(
        name = name,
        avatarUrl = avatarUrl,
        avatarName = name,
        baseUrl = baseUrl,
        preview = preview,
        time = time,
        unread = unread,
        muted = muted,
        onClick = onClick,
    )
}

@Composable
private fun ChannelRow(
    channel: ChannelDto,
    baseUrl: String?,
    preview: String,
    time: String,
    onClick: () -> Unit,
    onLeave: (() -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
                text = (channel.displayName.firstOrNull() ?: '#').uppercaseChar().toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Groups,
                    contentDescription = "群聊",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(4.dp))
                if (channel.type == "private") {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "私密频道",
                        modifier = Modifier.size(13.dp),
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
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                if (time.isNotBlank()) {
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = preview.ifBlank { "${channel.memberCount} 位成员" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (channel.unreadCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Badge {
                        Text(if (channel.unreadCount > 99) "99+" else channel.unreadCount.toString())
                    }
                }
            }
        }
        if (onLeave != null) {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("退出群聊") },
                        onClick = {
                            menuExpanded = false
                            onLeave()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    name: String,
    avatarName: String,
    avatarUrl: String?,
    baseUrl: String?,
    preview: String,
    time: String,
    unread: Int,
    muted: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(name = avatarName, avatarUrl = avatarUrl, baseUrl = baseUrl)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (muted) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.NotificationsOff,
                        contentDescription = "消息免打扰",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (time.isNotBlank()) {
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (unread > 0) {
                    Spacer(Modifier.width(8.dp))
                    Badge { Text(if (unread > 99) "99+" else unread.toString()) }
                }
            }
        }
    }
}

private fun timestampOf(raw: String?, fallback: String?): Long =
    TimeFormat.parse(raw)?.time ?: TimeFormat.parse(fallback)?.time ?: 0L
