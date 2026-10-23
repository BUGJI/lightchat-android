package com.benton.lightchat.ui.contacts

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.benton.lightchat.data.remote.BlockedUserDto
import com.benton.lightchat.data.remote.FriendDto
import com.benton.lightchat.data.remote.FriendRequestDto
import com.benton.lightchat.ui.components.EmptyState
import com.benton.lightchat.ui.components.SyncBarContent
import com.benton.lightchat.ui.components.ListSkeleton
import com.benton.lightchat.ui.components.UserAvatar
import com.benton.lightchat.util.TimeFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    baseUrl: String?,
    onBack: () -> Unit,
    onOpenAdd: () -> Unit,
    onOpenUserProfile: (Long) -> Unit,
    onOpenPrivate: (Long, Long, String) -> Unit,
    viewModel: FriendsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var section by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { viewModel.start() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is FriendsEvent.OpenPrivateChat ->
                    onOpenPrivate(event.chatId, event.userId, event.title)

                is FriendsEvent.ShowMessage -> snackbarHostState.showSnackbar(event.text)
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
                title = { Text("好友管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenAdd) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = "添加好友")
                    }
                    IconButton(onClick = viewModel::load) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        SyncBarContent(
            visible = state.refreshing,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            columnModifier = Modifier.fillMaxSize(),
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                SegmentedButton(
                    selected = section == 0,
                    onClick = { section = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    label = { Text("好友 ${state.friends.size}") },
                )
                SegmentedButton(
                    selected = section == 1,
                    onClick = { section = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    label = { Text("申请 ${state.requests.size}") },
                )
                SegmentedButton(
                    selected = section == 2,
                    onClick = { section = 2 },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    label = { Text("拉黑 ${state.blocked.size}") },
                )
            }

            when (section) {
                0 -> FriendsList(
                    state = state,
                    baseUrl = baseUrl,
                    onOpenAdd = onOpenAdd,
                    onOpenUserProfile = onOpenUserProfile,
                    onOpenChat = { friend ->
                        viewModel.openChat(
                            friend.userId,
                            friend.nickname.takeIf { it.isNotBlank() } ?: friend.username,
                        )
                    },
                    onRemove = viewModel::remove,
                    onBlock = viewModel::block,
                )

                1 -> RequestsList(
                    state = state,
                    baseUrl = baseUrl,
                    onRespond = viewModel::respond,
                )

                else -> BlockedList(
                    state = state,
                    baseUrl = baseUrl,
                    onUnblock = viewModel::unblock,
                )
            }
        }
    }
}

@Composable
private fun FriendsList(
    state: FriendsUiState,
    baseUrl: String?,
    onOpenAdd: () -> Unit,
    onOpenUserProfile: (Long) -> Unit,
    onOpenChat: (FriendDto) -> Unit,
    onRemove: (FriendDto) -> Unit,
    onBlock: (FriendDto) -> Unit,
) {
    when {
        state.loading -> ListSkeleton()
        state.friends.isEmpty() -> EmptyState(
            icon = Icons.Filled.Group,
            title = "还没有好友",
            description = "去添加好友，开始聊天吧",
            actionLabel = "添加好友",
            onAction = onOpenAdd,
        )

        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            items(state.friends, key = { it.userId }) { friend ->
                val displayName = friend.nickname.takeIf { it.isNotBlank() } ?: friend.username
                var menuExpanded by remember { mutableStateOf(false) }
                var pendingAction by remember { mutableStateOf<FriendAction?>(null) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenUserProfile(friend.userId) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UserAvatar(
                        name = displayName,
                        avatarUrl = friend.avatar,
                        baseUrl = baseUrl,
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
                            text = "@${friend.username}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = { onOpenChat(friend) },
                        enabled = !state.actionInProgress,
                    ) {
                        Text("发消息")
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("删除好友") },
                                onClick = {
                                    menuExpanded = false
                                    pendingAction = FriendAction.Remove
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text("拉黑", color = MaterialTheme.colorScheme.error)
                                },
                                onClick = {
                                    menuExpanded = false
                                    pendingAction = FriendAction.Block
                                },
                            )
                        }
                    }
                }

                pendingAction?.let { action ->
                    val remove = action == FriendAction.Remove
                    AlertDialog(
                        onDismissRequest = { pendingAction = null },
                        title = { Text(if (remove) "删除好友" else "拉黑用户") },
                        text = {
                            Text(
                                if (remove) {
                                    "删除后你们将不再是好友，确定删除「$displayName」？"
                                } else {
                                    "拉黑后对方将无法与你聊天，确定拉黑「$displayName」？"
                                },
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    pendingAction = null
                                    if (remove) onRemove(friend) else onBlock(friend)
                                },
                            ) {
                                Text(
                                    text = if (remove) "删除" else "拉黑",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingAction = null }) { Text("取消") }
                        },
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(start = 76.dp))
            }
        }
    }
}

@Composable
private fun RequestsList(
    state: FriendsUiState,
    baseUrl: String?,
    onRespond: (FriendRequestDto, Boolean) -> Unit,
) {
    when {
        state.loading -> ListSkeleton()
        state.requests.isEmpty() -> EmptyState(
            icon = Icons.Filled.PersonAdd,
            title = "暂无好友申请",
            description = "收到的好友申请会显示在这里",
        )

        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            items(state.requests, key = { it.id }) { request ->
                val displayName = request.nickname.takeIf { it.isNotBlank() } ?: request.username
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(
                            name = displayName,
                            avatarUrl = request.avatar,
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
                                    append("@${request.username}")
                                    val time = TimeFormat.chatTimestamp(request.createdAt)
                                    if (time.isNotBlank()) append(" · $time")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (request.message.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = request.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = { onRespond(request, false) },
                            enabled = !state.actionInProgress,
                        ) {
                            Text("拒绝")
                        }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = { onRespond(request, true) },
                            enabled = !state.actionInProgress,
                        ) {
                            if (state.actionInProgress) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text("接受")
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(start = 76.dp))
            }
        }
    }
}

@Composable
private fun BlockedList(
    state: FriendsUiState,
    baseUrl: String?,
    onUnblock: (BlockedUserDto) -> Unit,
) {
    when {
        state.loading -> ListSkeleton()
        state.blocked.isEmpty() -> EmptyState(
            icon = Icons.Filled.Block,
            title = "黑名单为空",
            description = "被你拉黑的用户会显示在这里",
        )

        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            items(state.blocked, key = { it.userId }) { user ->
                val displayName = user.nickname.takeIf { it.isNotBlank() } ?: user.username
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
                            text = "@${user.username}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalButton(
                        onClick = { onUnblock(user) },
                        enabled = !state.actionInProgress,
                    ) {
                        Text("解除拉黑")
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(start = 76.dp))
            }
        }
    }
}

private enum class FriendAction { Remove, Block }
