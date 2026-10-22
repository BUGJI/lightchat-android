package com.benton.lightchat.ui.chats

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.benton.lightchat.data.remote.MessageDto
import com.benton.lightchat.ui.components.EmptyState
import com.benton.lightchat.ui.components.LoadingBox
import com.benton.lightchat.ui.components.UserAvatar
import com.benton.lightchat.util.TimeFormat
import com.benton.lightchat.util.UrlUtils
import androidx.compose.material.icons.filled.Forum
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    target: ChatTarget,
    baseUrl: String?,
    currentUserId: Long,
    onBack: () -> Unit,
    onOpenUserProfile: (Long) -> Unit = {},
    onOpenChannelInfo: (Long, String) -> Unit = { _, _ -> },
    viewModel: ChatViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    var previewUrl by remember { mutableStateOf<String?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showContactSettings by remember { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val displayTitle = if (target is ChatTarget.Private && state.contactNickname.isNotBlank()) {
        state.contactNickname
    } else {
        target.title
    }

    LaunchedEffect(target) { viewModel.open(target) }

    LaunchedEffect(target, lifecycleOwner) {
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = when {
                                target is ChatTarget.Channel -> "频道"
                                state.contactDnd -> "私聊 · 消息免打扰"
                                else -> "私聊"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { searchOpen = true }) {
                        Icon(Icons.Filled.Search, contentDescription = "搜索消息")
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            if (target is ChatTarget.Private) {
                                DropdownMenuItem(
                                    text = { Text("聊天设置") },
                                    onClick = {
                                        menuExpanded = false
                                        showContactSettings = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("查看资料") },
                                    onClick = {
                                        menuExpanded = false
                                        onOpenUserProfile(target.otherUserId)
                                    },
                                )
                            } else if (target is ChatTarget.Channel) {
                                DropdownMenuItem(
                                    text = { Text("群聊信息") },
                                    onClick = {
                                        menuExpanded = false
                                        onOpenChannelInfo(target.id, displayTitle)
                                    },
                                )
                            }
                        }
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
                .imePadding(),
        ) {
            Box(Modifier.weight(1f)) {
                when {
                    state.loading -> LoadingBox()
                    state.messages.isEmpty() -> EmptyState(
                        icon = Icons.Filled.Forum,
                        title = "还没有消息",
                        description = "发送第一条消息吧",
                    )

                    else -> MessageList(
                        messages = state.messages,
                        target = target,
                        baseUrl = baseUrl,
                        currentUserId = currentUserId,
                        recallEnabled = state.recallEnabled,
                        hasMore = state.hasMore,
                        loadingOlder = state.loadingOlder,
                        onLoadOlder = viewModel::loadOlder,
                        onRecall = viewModel::recall,
                        onDelete = viewModel::delete,
                        focusMessageId = state.focusMessageId,
                        onFocusConsumed = viewModel::consumeFocus,
                        onPreviewImage = { previewUrl = it },
                        onOpenFile = { url ->
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }.onFailure { error ->
                                if (error is ActivityNotFoundException) {
                                    // No app can handle this file type.
                                }
                            }
                        },
                    )
                }
            }

            MessageInput(
                sending = state.sending,
                uploading = state.uploading,
                onSendText = viewModel::sendText,
                onPickImage = { uri -> viewModel.sendAttachment(uri, true) },
                onPickFile = { uri -> viewModel.sendAttachment(uri, false) },
            )
        }
    }

    previewUrl?.let { url ->
        Dialog(
            onDismissRequest = { previewUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .combinedClickable(
                        onClick = { previewUrl = null },
                        onLongClick = {},
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = "图片预览",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }

    if (showContactSettings && target is ChatTarget.Private) {
        ContactSettingsDialog(
            nickname = state.contactNickname,
            dnd = state.contactDnd,
            onSave = { nickname, dnd ->
                viewModel.saveContact(nickname, dnd) { showContactSettings = false }
            },
            onDelete = {
                viewModel.deleteContact {
                    showContactSettings = false
                    onBack()
                }
            },
            onDismiss = { showContactSettings = false },
        )
    }

    if (searchOpen) {
        SearchMessagesDialog(
            query = state.searchQuery,
            results = state.searchResults,
            searching = state.searching,
            loadingMore = state.searchLoadingMore,
            hasMore = state.searchHasMore,
            onQueryChange = viewModel::search,
            onLoadMore = viewModel::loadMoreSearch,
            onSelect = { message ->
                searchOpen = false
                if (state.messages.any { it.id == message.id }) {
                    viewModel.focusMessage(message.id)
                }
            },
            onDismiss = {
                searchOpen = false
                viewModel.clearSearch()
            },
        )
    }
}

@Composable
private fun SearchMessagesDialog(
    query: String,
    results: List<MessageDto>,
    searching: Boolean,
    loadingMore: Boolean,
    hasMore: Boolean,
    onQueryChange: (String) -> Unit,
    onLoadMore: () -> Unit,
    onSelect: (MessageDto) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "关闭搜索",
                        )
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("搜索消息内容") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    )
                }
                HorizontalDivider()

                when {
                    searching && results.isEmpty() -> LoadingBox()
                    query.isBlank() -> EmptyState(
                        icon = Icons.Filled.Search,
                        title = "搜索消息",
                        description = "输入关键字查找聊天记录",
                    )

                    results.isEmpty() -> EmptyState(
                        icon = Icons.Filled.Search,
                        title = "没有找到相关消息",
                        description = "换个关键词试试",
                    )

                    else -> LazyColumn(Modifier.weight(1f)) {
                        items(results, key = { it.id }) { message ->
                            val preview = when (message.type) {
                                "image" -> "[图片]"
                                "file" -> "[文件]"
                                else -> message.content
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(message) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = message.username.orEmpty(),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = TimeFormat.chatTimestamp(message.createdAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = preview,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            HorizontalDivider()
                        }
                        if (hasMore) {
                            item(key = "search_more") {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    TextButton(onClick = onLoadMore, enabled = !loadingMore) {
                                        if (loadingMore) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                            )
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text(if (loadingMore) "加载中…" else "加载更多")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactSettingsDialog(
    nickname: String,
    dnd: Boolean,
    onSave: (String, Boolean) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var draftNickname by remember(nickname) { mutableStateOf(nickname) }
    var draftDnd by remember(dnd) { mutableStateOf(dnd) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("聊天设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = draftNickname,
                    onValueChange = { if (it.length <= 30) draftNickname = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("备注名") },
                    placeholder = { Text("仅自己可见") },
                    singleLine = true,
                    supportingText = { Text("${draftNickname.length}/30") },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("消息免打扰", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "新消息不会提醒，但未读仍然计数",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = draftDnd, onCheckedChange = { draftDnd = it })
                }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("删除聊天", color = MaterialTheme.colorScheme.error)
                }
                Text(
                    text = "删除后对方再发消息会重新出现在会话列表。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draftNickname, draftDnd) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun MessageList(
    messages: List<MessageDto>,
    target: ChatTarget,
    baseUrl: String?,
    currentUserId: Long,
    recallEnabled: Boolean,
    hasMore: Boolean,
    loadingOlder: Boolean,
    onLoadOlder: () -> Unit,
    onRecall: (MessageDto) -> Unit,
    onDelete: (MessageDto) -> Unit,
    focusMessageId: Long?,
    onFocusConsumed: () -> Unit,
    onPreviewImage: (String) -> Unit,
    onOpenFile: (String) -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.lastOrNull()?.id) {
        if (messages.isNotEmpty() && !listState.canScrollForward) {
            listState.scrollToItem(messages.lastIndex)
        }
    }

    LaunchedEffect(focusMessageId) {
        val targetId = focusMessageId ?: return@LaunchedEffect
        val index = messages.indexOfFirst { it.id == targetId }
        if (index >= 0) {
            val offset = if (hasMore || loadingOlder) 1 else 0
            listState.animateScrollToItem(index + offset)
        }
        onFocusConsumed()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (hasMore || loadingOlder) {
            item(key = "load_older") {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    TextButton(onClick = onLoadOlder, enabled = !loadingOlder) {
                        if (loadingOlder) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(if (loadingOlder) "加载中…" else "加载更早消息")
                    }
                }
            }
        }
        items(messages, key = { it.id }) { message ->
            val isMine = message.userId == currentUserId || message.fromUserId == currentUserId
            MessageBubble(
                message = message,
                isMine = isMine,
                showSender = target is ChatTarget.Channel && !isMine,
                baseUrl = baseUrl,
                canRecall = isMine && recallEnabled && message.type != "recalled",
                canDelete = isMine && target is ChatTarget.Channel && message.type != "recalled",
                onRecall = { onRecall(message) },
                onDelete = { onDelete(message) },
                onPreviewImage = onPreviewImage,
                onOpenFile = onOpenFile,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: MessageDto,
    isMine: Boolean,
    showSender: Boolean,
    baseUrl: String?,
    canRecall: Boolean,
    canDelete: Boolean,
    onRecall: () -> Unit,
    onDelete: () -> Unit,
    onPreviewImage: (String) -> Unit,
    onOpenFile: (String) -> Unit,
) {
    val isRecalled = message.type == "recalled"
    val isSystem = message.type == "system"
    var menuExpanded by remember { mutableStateOf(false) }

    if (isSystem) {
        Text(
            text = message.content,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        return
    }

    if (isRecalled) {
        Text(
            text = "${if (isMine) "你" else (message.username ?: "对方")}撤回了一条消息",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        return
    }

    val imageUrl = UrlUtils.resolve(baseUrl, message.fileUrl)
    val alignment = if (isMine) Alignment.End else Alignment.Start

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!isMine) {
            UserAvatar(
                name = message.username,
                avatarUrl = message.avatar,
                baseUrl = baseUrl,
                size = 32.dp,
            )
            Spacer(Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            if (showSender) {
                Text(
                    text = message.username.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                )
            }

            Box {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isMine) 16.dp else 4.dp,
                        bottomEnd = if (isMine) 4.dp else 16.dp,
                    ),
                    color = if (isMine) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ) {
                    Box(
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                when (message.type) {
                                    "image" -> imageUrl?.let(onPreviewImage)
                                    "file" -> imageUrl?.let(onOpenFile)
                                }
                            },
                            onLongClick = {
                                if (canRecall || canDelete) menuExpanded = true
                            },
                        ),
                    ) {
                        when (message.type) {
                            "image" -> {
                                if (imageUrl != null) {
                                    AsyncImage(
                                        model = imageUrl,
                                        contentDescription = "图片消息",
                                        modifier = Modifier
                                            .widthIn(max = 240.dp)
                                            .heightIn(max = 280.dp)
                                            .clip(RoundedCornerShape(16.dp)),
                                        contentScale = ContentScale.Fit,
                                    )
                                } else {
                                    Text(
                                        text = "[图片]",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    )
                                }
                            }

                            "file" -> {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Filled.InsertDriveFile,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f, fill = false)) {
                                        Text(
                                            text = fileName(imageUrl),
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        message.fileSize?.let {
                                            Text(
                                                text = formatSize(it),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }

                            else -> {
                                Text(
                                    text = message.content,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    if (canRecall) {
                        DropdownMenuItem(
                            text = { Text("撤回") },
                            onClick = {
                                menuExpanded = false
                                onRecall()
                            },
                        )
                    }
                    if (canDelete) {
                        DropdownMenuItem(
                            text = { Text("删除") },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }

            Text(
                text = TimeFormat.chatTimestamp(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp),
            )
        }
    }
}

@Composable
private fun MessageInput(
    sending: Boolean,
    uploading: Boolean,
    onSendText: (String) -> Unit,
    onPickImage: (Uri) -> Unit,
    onPickFile: (Uri) -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var attachMenu by remember { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri -> if (uri != null) onPickImage(uri) }
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri -> if (uri != null) onPickFile(uri) }

    Column {
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box {
                IconButton(onClick = { attachMenu = true }, enabled = !sending && !uploading) {
                    Icon(Icons.Filled.AttachFile, contentDescription = "附件")
                }
                DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("图片") },
                        leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
                        onClick = {
                            attachMenu = false
                            imagePicker.launch("image/*")
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("文件") },
                        leadingIcon = { Icon(Icons.Filled.InsertDriveFile, contentDescription = null) },
                        onClick = {
                            attachMenu = false
                            filePicker.launch("*/*")
                        },
                    )
                }
            }

            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= 500) draft = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                placeholder = { Text("输入消息…") },
                maxLines = 5,
                shape = RoundedCornerShape(22.dp),
            )

            IconButton(
                onClick = {
                    if (draft.isNotBlank()) {
                        onSendText(draft)
                        draft = ""
                    }
                },
                enabled = draft.isNotBlank() && !sending && !uploading,
            ) {
                if (sending || uploading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (uploading) {
            Text(
                text = "正在上传附件…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, bottom = 6.dp),
            )
        }
    }
}

private fun fileName(url: String?): String {
    if (url.isNullOrBlank()) return "文件"
    val last = Uri.parse(url).lastPathSegment ?: return "文件"
    return Uri.decode(last)
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
