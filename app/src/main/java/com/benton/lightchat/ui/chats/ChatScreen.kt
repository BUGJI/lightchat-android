package com.benton.lightchat.ui.chats

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.benton.lightchat.data.remote.ChannelMemberDto
import com.benton.lightchat.data.remote.MessageDto
import com.benton.lightchat.data.remote.ParentMessageDto
import com.benton.lightchat.ui.components.EmptyState
import com.benton.lightchat.ui.components.InlineSyncBar
import com.benton.lightchat.ui.components.ListSkeleton
import com.benton.lightchat.ui.components.LoadingBox
import com.benton.lightchat.ui.components.OfflineBanner
import com.benton.lightchat.ui.components.PredictiveBackOverlay
import com.benton.lightchat.ui.components.UserAvatar
import com.benton.lightchat.util.TimeFormat
import com.benton.lightchat.util.UrlUtils
import com.benton.lightchat.util.VoiceRecorder
import androidx.compose.material.icons.filled.Forum
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private val mentionRegex = Regex("@[\\w\\u4e00-\\u9fa5]{1,32}")
private val urlRegex = Regex("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+")
private val voiceNameRegex = Regex("voice_(\\d+)s")

private data class ImagePreviewTarget(
    val url: String,
    val bounds: Rect,
    val aspectRatio: Float,
)

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = remember(context) {
        context.getSystemService(ClipboardManager::class.java)
    }
    var previewTarget by remember { mutableStateOf<ImagePreviewTarget?>(null) }
    var pendingSaveBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val saveImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/*"),
    ) { uri ->
        val bitmap = pendingSaveBitmap
        pendingSaveBitmap = null
        if (uri != null && bitmap != null) {
            val saved = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                } ?: false
            }.getOrDefault(false)
            Toast.makeText(
                context,
                if (saved) "图片已保存" else "保存失败",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    var menuExpanded by remember { mutableStateOf(false) }
    var showContactSettings by remember { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var showAnnouncement by remember { mutableStateOf(false) }
    val displayTitle = if (target is ChatTarget.Private && state.contactNickname.isNotBlank()) {
        state.contactNickname
    } else {
        target.title
    }

    LaunchedEffect(target) { viewModel.open(target) }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    BackHandler(
        enabled = state.replyTo != null && previewTarget == null && !searchOpen,
    ) {
        viewModel.clearReply()
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
                                !state.contactLastActive.isNullOrBlank() -> buildString {
                                    append(TimeFormat.relativeActive(state.contactLastActive))
                                    if (state.contactDnd) append(" · 消息免打扰")
                                }

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
            if (state.offline) {
                OfflineBanner()
            }
            if (target is ChatTarget.Channel && !state.announcement.isNullOrBlank()) {
                AnnouncementBanner(
                    text = state.announcement.orEmpty(),
                    onClick = { showAnnouncement = true },
                )
            }
            Box(Modifier.weight(1f)) {
                when {
                    state.loading -> ListSkeleton(rows = 4, showAvatar = false)
                    state.messages.isEmpty() -> EmptyState(
                        icon = Icons.Filled.Forum,
                        title = "还没有消息",
                        description = if (state.offline) {
                            "本地暂无缓存，联网后自动加载"
                        } else {
                            "发送第一条消息吧"
                        },
                    )

                    else -> MessageList(
                        messages = state.messages,
                        target = target,
                        baseUrl = baseUrl,
                        currentUserId = currentUserId,
                        recallEnabled = state.recallEnabled,
                        hasMore = state.hasMore,
                        loadingOlder = state.loadingOlder,
                        unreadAnchorId = state.unreadAnchorId,
                        onLoadOlder = viewModel::loadOlder,
                        onRecall = viewModel::recall,
                        onDelete = viewModel::delete,
                        onRetry = viewModel::retry,
                        onDiscard = viewModel::discard,
                        onReply = viewModel::replyTo,
                        onOpenParent = viewModel::openParent,
                        onCopy = { text ->
                            clipboard?.setPrimaryClip(ClipData.newPlainText("消息", text))
                            scope.launch { snackbarHostState.showSnackbar("已复制") }
                        },
                        focusMessageId = state.focusMessageId,
                        onFocusConsumed = viewModel::consumeFocus,
                        onPreviewImage = { previewTarget = it },
                        onOpenFile = { message ->
                            viewModel.downloadAttachment(message) { file ->
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file,
                                )
                                val mime = MimeTypeMap.getSingleton()
                                    .getMimeTypeFromExtension(file.extension.lowercase())
                                    ?: "application/octet-stream"
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW)
                                            .setDataAndType(uri, mime)
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }.onFailure { error ->
                                    if (error is ActivityNotFoundException) {
                                        Toast.makeText(
                                            context,
                                            "没有可打开此文件的应用",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            }
                        },
                        downloadingFileId = state.downloadingFileId,
                        downloadProgress = state.downloadProgress,
                    )
                }
                if (state.locating) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "正在定位消息…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                InlineSyncBar(
                    visible = state.initialSyncing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }

            MessageInput(
                sending = state.sending,
                uploading = state.uploading,
                uploadProgress = state.uploadProgress,
                offline = state.offline,
                members = state.members,
                replyTo = state.replyTo,
                draft = state.draftText,
                onDraftChange = viewModel::onDraftChange,
                onCancelReply = viewModel::clearReply,
                onSendText = { text, mentions, done ->
                    viewModel.sendText(text, mentions, done)
                },
                onPickImages = { uris -> viewModel.sendAttachments(uris, true) },
                onPickFile = { uri -> viewModel.sendAttachment(uri, false) },
                onSendVoice = { file, duration -> viewModel.sendVoice(file, duration) },
                onCancelUpload = viewModel::cancelUpload,
            )
        }
    }

    previewTarget?.let { target ->
        ImagePreviewOverlay(
            target = target,
            onDismiss = { previewTarget = null },
            onShare = { imageUrl ->
                runCatching {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, imageUrl)
                    }
                    context.startActivity(Intent.createChooser(share, "分享图片"))
                }
            },
            onSave = { imageUrl ->
                scope.launch {
                    val request = ImageRequest.Builder(context).data(imageUrl).build()
                    val result = context.imageLoader.execute(request)
                    val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    if (bitmap == null) {
                        Toast.makeText(context, "无法保存图片", Toast.LENGTH_SHORT).show()
                    } else {
                        pendingSaveBitmap = bitmap
                        saveImageLauncher.launch("lightchat_${System.currentTimeMillis()}.jpg")
                    }
                }
            },
        )
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

    if (showAnnouncement) {
        AlertDialog(
            onDismissRequest = { showAnnouncement = false },
            title = { Text("频道公告") },
            text = { Text(state.announcement.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { showAnnouncement = false }) { Text("知道了") }
            },
        )
    }

    if (searchOpen) {
        SearchMessagesOverlay(
            query = state.searchQuery,
            results = state.searchResults,
            searching = state.searching,
            loadingMore = state.searchLoadingMore,
            hasMore = state.searchHasMore,
            filter = state.searchFilter,
            onFilterChange = viewModel::setSearchFilter,
            onQueryChange = viewModel::search,
            onLoadMore = viewModel::loadMoreSearch,
            onSelect = { message ->
                searchOpen = false
                viewModel.clearSearch()
                viewModel.openSearchResult(message)
            },
            onDismiss = {
                searchOpen = false
                viewModel.clearSearch()
            },
        )
    }
}

@Composable
private fun AnnouncementBanner(text: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Campaign,
                contentDescription = "公告",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "公告：$text",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ImagePreviewOverlay(
    target: ImagePreviewTarget,
    onDismiss: () -> Unit,
    onShare: (String) -> Unit,
    onSave: (String) -> Unit,
) {
    var scale by remember(target.url) { mutableFloatStateOf(1f) }
    var offset by remember(target.url) { mutableStateOf(Offset.Zero) }
    val zoomed = scale > 1f
    val entry = remember(target.url) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val next = (scale * zoomChange).coerceIn(1f, 5f)
        scale = next
        offset = if (next <= 1f) Offset.Zero else offset + panChange
    }

    fun close() {
        scope.launch {
            entry.stop()
            entry.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
            onDismiss()
        }
    }

    LaunchedEffect(target.url) {
        entry.snapTo(0f)
        entry.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
    }

    BackHandler(enabled = zoomed) {
        scale = 1f
        offset = Offset.Zero
    }

    PredictiveBackOverlay(
        onDismiss = onDismiss,
        enabled = !zoomed,
    ) { backProgress ->
        val progress = (entry.value * (1f - backProgress)).coerceIn(0f, 1f)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = progress)),
        ) {
            val density = LocalDensity.current
            val screenWidth = with(density) { maxWidth.toPx() }
            val screenHeight = with(density) { maxHeight.toPx() }
            val aspectRatio = target.aspectRatio.takeIf { it > 0f } ?: 1f
            val fullWidth: Float
            val fullHeight: Float
            if (screenWidth / screenHeight > aspectRatio) {
                fullHeight = screenHeight
                fullWidth = screenHeight * aspectRatio
            } else {
                fullWidth = screenWidth
                fullHeight = screenWidth / aspectRatio
            }
            val fullRect = Rect(
                left = (screenWidth - fullWidth) / 2f,
                top = (screenHeight - fullHeight) / 2f,
                right = (screenWidth + fullWidth) / 2f,
                bottom = (screenHeight + fullHeight) / 2f,
            )
            val startRect = target.bounds.takeUnless { it.isEmpty } ?: fullRect
            val reveal = 1f - progress
            val visualRect = Rect(
                left = fullRect.left + (startRect.left - fullRect.left) * reveal,
                top = fullRect.top + (startRect.top - fullRect.top) * reveal,
                right = fullRect.right + (startRect.right - fullRect.right) * reveal,
                bottom = fullRect.bottom + (startRect.bottom - fullRect.bottom) * reveal,
            )
            val heroScale = if (fullRect.width > 0f) {
                visualRect.width / fullRect.width
            } else {
                1f
            }
            val translationX = visualRect.center.x - fullRect.center.x
            val translationY = visualRect.center.y - fullRect.center.y
            val cornerRadius = (16.dp * reveal / heroScale.coerceAtLeast(0.01f))
                .coerceAtLeast(0.dp)

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(
                        width = with(density) { fullRect.width.toDp() },
                        height = with(density) { fullRect.height.toDp() },
                    )
                    .graphicsLayer {
                        scaleX = heroScale
                        scaleY = heroScale
                        this.translationX = translationX
                        this.translationY = translationY
                    }
                    .clip(RoundedCornerShape(cornerRadius)),
            ) {
                AsyncImage(
                    model = target.url,
                    contentDescription = "图片预览",
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(target.url) {
                            detectTapGestures(
                                onTap = {
                                    if (scale > 1f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        close()
                                    }
                                },
                                onDoubleTap = {
                                    if (scale > 1f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = 2.5f
                                    }
                                },
                            )
                        }
                        .transformable(transformState)
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                        ),
                    contentScale = ContentScale.Fit,
                )
            }

            IconButton(
                onClick = { close() },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .safeDrawingPadding()
                    .padding(8.dp)
                    .alpha(progress),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.White)
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(8.dp)
                    .alpha(progress),
            ) {
                IconButton(onClick = { onSave(target.url) }) {
                    Icon(Icons.Filled.Download, contentDescription = "保存", tint = Color.White)
                }
                IconButton(onClick = { onShare(target.url) }) {
                    Icon(Icons.Filled.Share, contentDescription = "分享", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun SearchMessagesOverlay(
    query: String,
    results: List<MessageDto>,
    searching: Boolean,
    loadingMore: Boolean,
    hasMore: Boolean,
    filter: SearchFilter,
    onFilterChange: (SearchFilter) -> Unit,
    onQueryChange: (String) -> Unit,
    onLoadMore: () -> Unit,
    onSelect: (MessageDto) -> Unit,
    onDismiss: () -> Unit,
) {
    PredictiveBackOverlay(onDismiss = onDismiss) { progress ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = 1f - progress
                    translationX = progress * size.width * 0.25f
                },
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding(),
            ) {
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
                if (query.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SearchFilter.entries.forEach { item ->
                            FilterChip(
                                selected = filter == item,
                                onClick = { onFilterChange(item) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }

                val visible = remember(results, filter) {
                    results.filter { message ->
                        when (filter) {
                            SearchFilter.ALL -> true
                            SearchFilter.TEXT -> message.type == "text"
                            SearchFilter.IMAGE -> message.type == "image"
                            SearchFilter.FILE -> message.type == "file"
                        }
                    }
                }

                when {
                    searching && results.isEmpty() -> LoadingBox()
                    query.isBlank() -> EmptyState(
                        icon = Icons.Filled.Search,
                        title = "搜索消息",
                        description = "输入关键字查找聊天记录",
                    )

                    visible.isEmpty() -> EmptyState(
                        icon = Icons.Filled.Search,
                        title = "没有找到相关消息",
                        description = if (results.isEmpty()) {
                            "换个关键词试试"
                        } else {
                            "当前类型下没有结果，试试「全部」"
                        },
                    )

                    else -> LazyColumn(Modifier.weight(1f)) {
                        items(visible, key = { it.id }) { message ->
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
                                    text = highlightKeyword(preview, query),
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
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除聊天") },
            text = { Text("删除后对方再发消息会重新出现在会话列表。确定删除？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
        return
    }

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
                    onClick = { confirmDelete = true },
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

private sealed interface ChatListEntry {
    val key: String

    data class Day(val label: String, override val key: String) : ChatListEntry

    data class Unread(override val key: String) : ChatListEntry

    data class Message(val message: MessageDto) : ChatListEntry {
        override val key: String get() = "message_${message.id}"
    }
}

private fun buildMessageEntries(
    messages: List<MessageDto>,
    unreadAnchorId: Long,
): List<ChatListEntry> {
    val result = ArrayList<ChatListEntry>(messages.size + 4)
    var lastDay: String? = null
    var unreadInserted = false
    messages.forEach { message ->
        val day = message.createdAt.take(10)
        if (day.isNotBlank() && day != lastDay) {
            lastDay = day
            result += ChatListEntry.Day(TimeFormat.dayLabel(message.createdAt), "day_$day")
        }
        if (!unreadInserted && unreadAnchorId > 0 && message.id > unreadAnchorId) {
            unreadInserted = true
            result += ChatListEntry.Unread("unread")
        }
        result += ChatListEntry.Message(message)
    }
    return result
}

@Composable
private fun DaySeparator(label: String) {
    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun UnreadSeparator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(Modifier.weight(1f))
        Text(
            text = "以下是新消息",
            modifier = Modifier.padding(horizontal = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        HorizontalDivider(Modifier.weight(1f))
    }
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
    unreadAnchorId: Long,
    onLoadOlder: () -> Unit,
    onRecall: (MessageDto) -> Unit,
    onDelete: (MessageDto) -> Unit,
    onRetry: (MessageDto) -> Unit,
    onDiscard: (MessageDto) -> Unit,
    onReply: (MessageDto) -> Unit,
    onOpenParent: (Long) -> Unit,
    onCopy: (String) -> Unit,
    focusMessageId: Long?,
    onFocusConsumed: () -> Unit,
    onPreviewImage: (ImagePreviewTarget) -> Unit,
    onOpenFile: (MessageDto) -> Unit,
    downloadingFileId: Long?,
    downloadProgress: Float?,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val isAtBottom by remember { derivedStateOf { !listState.canScrollForward } }
    var newMessageCount by remember { mutableIntStateOf(0) }
    val entries = remember(messages, unreadAnchorId) {
        buildMessageEntries(messages, unreadAnchorId)
    }
    val headerOffset = if (hasMore || loadingOlder) 1 else 0
    val lastItemIndex = entries.lastIndex + headerOffset

    LaunchedEffect(messages.lastOrNull()?.id) {
        val last = messages.lastOrNull() ?: return@LaunchedEffect
        if (focusMessageId != null) return@LaunchedEffect
        val isMine = last.userId == currentUserId || last.fromUserId == currentUserId
        if (isMine || !listState.canScrollForward) {
            listState.scrollToItem(lastItemIndex)
            newMessageCount = 0
        } else {
            newMessageCount += 1
        }
    }

    LaunchedEffect(isAtBottom) {
        if (isAtBottom) newMessageCount = 0
    }

    LaunchedEffect(focusMessageId) {
        val targetId = focusMessageId ?: return@LaunchedEffect
        val index = entries.indexOfFirst {
            it is ChatListEntry.Message && it.message.id == targetId
        }
        if (index >= 0) {
            listState.animateScrollToItem(index + headerOffset)
        }
        onFocusConsumed()
    }

    var hasScrolled by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect {
            if (it) hasScrolled = true
        }
    }
    LaunchedEffect(listState, hasMore, loadingOlder, hasScrolled) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index ->
                if (hasScrolled && index <= 1 && hasMore && !loadingOlder) onLoadOlder()
            }
    }

    Box(Modifier.fillMaxSize()) {
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
        items(entries, key = { it.key }) { entry ->
            when (entry) {
                is ChatListEntry.Day -> DaySeparator(entry.label)
                is ChatListEntry.Unread -> UnreadSeparator()
                is ChatListEntry.Message -> {
                    val message = entry.message
                    val isMine = message.userId == currentUserId ||
                        message.fromUserId == currentUserId
                    MessageBubble(
                        message = message,
                        isMine = isMine,
                        showSender = target is ChatTarget.Channel && !isMine,
                        baseUrl = baseUrl,
                        canRecall = isMine && recallEnabled && message.id > 0 &&
                            message.status == "sent" && message.type != "recalled",
                        canDelete = isMine && target is ChatTarget.Channel && message.id > 0 &&
                            message.status == "sent" && message.type != "recalled",
                        canCopy = message.type !in setOf("image", "file") &&
                            message.content.isNotBlank(),
                        onRecall = { onRecall(message) },
                        onDelete = { onDelete(message) },
                        onRetry = { onRetry(message) },
                        onDiscard = { onDiscard(message) },
                        onReply = { onReply(message) },
                        onOpenParent = { parentId -> onOpenParent(parentId) },
                        mentionsMe = message.mentionedUsers
                            ?.split(',')
                            ?.any { it.trim().toLongOrNull() == currentUserId } == true,
                        canReply = message.type !in setOf("system", "recalled"),
                        onCopy = { onCopy(message.content) },
                        onPreviewImage = onPreviewImage,
                        onOpenFile = onOpenFile,
                        downloadingFileId = downloadingFileId,
                        downloadProgress = downloadProgress,
                    )
                }
            }
        }
    }
        if (!isAtBottom || newMessageCount > 0) {
            FilledTonalButton(
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem(lastItemIndex)
                        newMessageCount = 0
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(if (newMessageCount > 0) "新消息" else "回到底部")
            }
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
    canCopy: Boolean,
    onRecall: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
    onReply: () -> Unit,
    onOpenParent: (Long) -> Unit,
    mentionsMe: Boolean,
    canReply: Boolean,
    onCopy: () -> Unit,
    onPreviewImage: (ImagePreviewTarget) -> Unit,
    onOpenFile: (MessageDto) -> Unit,
    downloadingFileId: Long?,
    downloadProgress: Float?,
) {
    val isRecalled = message.type == "recalled"
    val isSystem = message.type == "system"
    var menuExpanded by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<MessageAction?>(null) }

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
    var imageBounds by remember(message.id) { mutableStateOf(Rect.Zero) }
    var imageAspectRatio by remember(message.id) { mutableFloatStateOf(0f) }

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
            if (mentionsMe) {
                Text(
                    text = "提到了你",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
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
                    Column(
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                when (message.type) {
                                    "image" -> imageUrl?.let { url ->
                                        onPreviewImage(
                                            ImagePreviewTarget(
                                                url = url,
                                                bounds = imageBounds,
                                                aspectRatio = imageAspectRatio
                                                    .takeIf { it > 0f } ?: 1f,
                                            ),
                                        )
                                    }

                                    "file" -> onOpenFile(message)
                                }
                            },
                            onLongClick = {
                                val localPending = isMine &&
                                    (message.status == "failed" || message.status == "pending")
                                if (canCopy || canRecall || canDelete || canReply || localPending) {
                                    menuExpanded = true
                                }
                            },
                        ),
                    ) {
                        message.parent?.let { parent ->
                            ParentQuote(parent = parent, onOpen = { onOpenParent(parent.id) })
                            Spacer(Modifier.height(4.dp))
                        }
                        when (message.type) {
                            "image" -> {
                                if (imageUrl != null) {
                                    AsyncImage(
                                        model = imageUrl,
                                        contentDescription = "图片消息",
                                        modifier = Modifier
                                            .widthIn(max = 240.dp)
                                            .heightIn(max = 280.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .onGloballyPositioned { coordinates ->
                                                val bounds = coordinates.boundsInWindow()
                                                if (!bounds.isEmpty) {
                                                    imageBounds = bounds
                                                    imageAspectRatio = bounds.width / bounds.height
                                                }
                                            },
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
                                if (isVoiceMessage(message)) {
                                    VoiceMessageRow(
                                        url = imageUrl,
                                        durationLabel = voiceDurationLabel(message),
                                    )
                                } else {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.InsertDriveFile,
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
                                            if (downloadingFileId == message.id) {
                                                LinearProgressIndicator(
                                                    progress = { downloadProgress ?: 0f },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = 6.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            else -> {
                                Text(
                                    text = highlightedContent(message.content),
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
                    if (canCopy) {
                        DropdownMenuItem(
                            text = { Text("复制") },
                            onClick = {
                                menuExpanded = false
                                onCopy()
                            },
                        )
                    }
                    if (canReply) {
                        DropdownMenuItem(
                            text = { Text("回复") },
                            onClick = {
                                menuExpanded = false
                                onReply()
                            },
                        )
                    }
                    if (canRecall) {
                        DropdownMenuItem(
                            text = { Text("撤回") },
                            onClick = {
                                menuExpanded = false
                                pendingAction = MessageAction.Recall
                            },
                        )
                    }
                    if (canDelete) {
                        DropdownMenuItem(
                            text = { Text("删除") },
                            onClick = {
                                menuExpanded = false
                                pendingAction = MessageAction.Delete
                            },
                        )
                    }
                    if (isMine && message.status == "failed") {
                        DropdownMenuItem(
                            text = { Text("重新发送") },
                            onClick = {
                                menuExpanded = false
                                onRetry()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text("删除本地消息", color = MaterialTheme.colorScheme.error)
                            },
                            onClick = {
                                menuExpanded = false
                                onDiscard()
                            },
                        )
                    }
                    if (isMine && message.status == "pending") {
                        DropdownMenuItem(
                            text = { Text("取消发送") },
                            onClick = {
                                menuExpanded = false
                                onDiscard()
                            },
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp),
            ) {
                Text(
                    text = TimeFormat.chatTimestamp(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isMine && message.status == "pending") {
                    Spacer(Modifier.width(4.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(10.dp),
                        strokeWidth = 1.5.dp,
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "发送中",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isMine && message.status == "failed") {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "发送失败，点按重试",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickable(onClick = onRetry),
                    )
                }
            }
        }
    }

    pendingAction?.let { action ->
        val recall = action == MessageAction.Recall
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(if (recall) "撤回消息" else "删除消息") },
            text = {
                Text(
                    if (recall) {
                        "撤回后对方将看不到这条消息。"
                    } else {
                        "删除后该消息将从群聊中移除，且不可恢复。"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingAction = null
                        if (recall) onRecall() else onDelete()
                    },
                ) {
                    Text(
                        text = if (recall) "撤回" else "删除",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) { Text("取消") }
            },
        )
    }
}

private enum class MessageAction { Recall, Delete }

@Composable
private fun MessageInput(
    sending: Boolean,
    uploading: Boolean,
    uploadProgress: Float?,
    offline: Boolean,
    members: List<ChannelMemberDto>,
    replyTo: MessageDto?,
    draft: String,
    onDraftChange: (String) -> Unit,
    onCancelReply: () -> Unit,
    onSendText: (String, List<Long>, () -> Unit) -> Unit,
    onPickImages: (List<Uri>) -> Unit,
    onPickFile: (Uri) -> Unit,
    onSendVoice: (File, Long) -> Unit,
    onCancelUpload: () -> Unit,
) {
    val context = LocalContext.current
    val recorder = remember { VoiceRecorder(context) }
    var attachMenu by remember { mutableStateOf(false) }
    var mentionMenu by remember { mutableStateOf(false) }
    var mentionedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var recording by remember { mutableStateOf(false) }
    var recordSeconds by remember { mutableIntStateOf(0) }
    var recordStart by remember { mutableLongStateOf(0L) }

    val startRecording = {
        recordStart = System.currentTimeMillis()
        recordSeconds = 0
        if (recorder.start()) {
            recording = true
        } else {
            Toast.makeText(context, "无法开始录音", Toast.LENGTH_SHORT).show()
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startRecording()
        else Toast.makeText(context, "需要麦克风权限才能发送语音", Toast.LENGTH_SHORT).show()
    }
    val requestRecording = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            startRecording()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val finishRecording = {
        if (recording) {
            val duration = System.currentTimeMillis() - recordStart
            val file = recorder.stop(duration)
            recording = false
            recordSeconds = 0
            if (file != null) onSendVoice(file, duration)
        }
    }
    val cancelRecording = {
        recorder.cancel()
        recording = false
        recordSeconds = 0
    }

    BackHandler(enabled = recording) {
        cancelRecording()
    }

    LaunchedEffect(recording) {
        if (!recording) return@LaunchedEffect
        while (recording) {
            delay(200)
            val seconds = ((System.currentTimeMillis() - recordStart) / 1000).toInt()
            recordSeconds = seconds
            if (seconds >= 60) {
                finishRecording()
                break
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { recorder.cancel() }
    }
    val sendDraft = {
        val content = draft
        if (content.isNotBlank() && !uploading) {
            val parsed = members
                .filter { content.contains("@${it.username}") }
                .map { it.userId }
            onSendText(content, (mentionedIds + parsed).distinct()) {
                mentionedIds = emptySet()
            }
        }
    }
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris -> if (uris.isNotEmpty()) onPickImages(uris) }
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) onPickFile(uri) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = cameraUri
        cameraUri = null
        if (success && uri != null) onPickImages(listOf(uri))
    }
    val launchCamera = {
        val directory = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(directory, "camera_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        cameraUri = uri
        cameraLauncher.launch(uri)
    }

    Column {
        HorizontalDivider()
        replyTo?.let { reply ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "回复 ${reply.username.orEmpty()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = messagePreview(reply),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onCancelReply) {
                        Icon(Icons.Filled.Close, contentDescription = "取消回复")
                    }
                }
            }
        }
        if (offline) {
            Text(
                text = "离线模式：文字消息会排队，联网后自动发送；附件需联网",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (recording) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "录音中 ${recordSeconds}s / 60s",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    TextButton(onClick = cancelRecording) { Text("取消") }
                    TextButton(onClick = finishRecording) { Text("发送") }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (members.isNotEmpty()) {
                Box {
                    IconButton(
                        onClick = { mentionMenu = true },
                        enabled = !uploading,
                    ) {
                        Icon(Icons.Filled.AlternateEmail, contentDescription = "提及成员")
                    }
                    DropdownMenu(
                        expanded = mentionMenu,
                        onDismissRequest = { mentionMenu = false },
                    ) {
                        members.take(50).forEach { member ->
                            val name = member.nickname.takeIf { it.isNotBlank() }
                                ?: member.username
                            DropdownMenuItem(
                                text = { Text("$name  @${member.username}") },
                                onClick = {
                                    mentionMenu = false
                                    val prefix = if (draft.isNotEmpty() && !draft.endsWith(" ")) {
                                        "$draft "
                                    } else {
                                        draft
                                    }
                                    onDraftChange("$prefix@${member.username} ")
                                    mentionedIds = mentionedIds + member.userId
                                },
                            )
                        }
                    }
                }
            }
            Box {
                IconButton(
                    onClick = { attachMenu = true },
                    enabled = !uploading && !offline,
                ) {
                    Icon(Icons.Filled.AttachFile, contentDescription = "附件")
                }
                DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("拍照") },
                        leadingIcon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                        onClick = {
                            attachMenu = false
                            launchCamera()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("图片（可多选）") },
                        leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
                        onClick = {
                            attachMenu = false
                            imagePicker.launch("image/*")
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("文件") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null) },
                        onClick = {
                            attachMenu = false
                            filePicker.launch(
                                arrayOf(
                                    "application/pdf",
                                    "application/msword",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "text/plain",
                                    "application/zip",
                                ),
                            )
                        },
                    )
                }
            }

            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= 500) onDraftChange(it) },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                placeholder = { Text("输入消息…") },
                maxLines = 5,
                enabled = !recording,
                shape = RoundedCornerShape(22.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { sendDraft() }),
            )

            IconButton(
                onClick = { if (recording) finishRecording() else requestRecording() },
                enabled = !uploading && !offline,
            ) {
                Icon(
                    imageVector = if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (recording) "停止录音" else "语音",
                    tint = if (recording) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            IconButton(
                onClick = sendDraft,
                enabled = draft.isNotBlank() && !uploading && !recording,
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    LinearProgressIndicator(
                        progress = { uploadProgress ?: 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "正在上传附件… ${((uploadProgress ?: 0f) * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onCancelUpload) { Text("取消") }
            }
        }
    }
}

@Composable
private fun VoiceMessageRow(url: String?, durationLabel: String) {
    var playing by remember(url) { mutableStateOf(false) }
    var preparing by remember(url) { mutableStateOf(false) }
    var player by remember(url) { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(url) {
        onDispose {
            runCatching { player?.release() }
            player = null
        }
    }

    Row(
        modifier = Modifier
            .padding(12.dp)
            .clickable(enabled = url != null) {
                if (playing) {
                    runCatching { player?.pause() }
                    playing = false
                    return@clickable
                }
                val existing = player
                if (existing != null) {
                    runCatching { existing.start() }
                    playing = true
                    return@clickable
                }
                if (url == null || preparing) return@clickable
                preparing = true
                val created = MediaPlayer()
                runCatching {
                    created.setDataSource(url)
                    created.setOnPreparedListener { mp ->
                        preparing = false
                        runCatching { mp.start() }
                        playing = true
                    }
                    created.setOnCompletionListener { mp ->
                        playing = false
                        runCatching { mp.seekTo(0) }
                    }
                    created.setOnErrorListener { mp, _, _ ->
                        preparing = false
                        playing = false
                        runCatching { mp.release() }
                        player = null
                        true
                    }
                    created.prepareAsync()
                    player = created
                }.onFailure {
                    preparing = false
                    runCatching { created.release() }
                    player = null
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (preparing) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "暂停" else "播放语音",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = durationLabel,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun isVoiceMessage(message: MessageDto): Boolean =
    message.type == "file" &&
        (message.content.startsWith("voice:") || isVoiceFile(message.fileUrl))

private fun isVoiceFile(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val extension = Uri.parse(url).lastPathSegment
        ?.substringAfterLast('.', "")
        ?.lowercase()
        .orEmpty()
    return extension in setOf("m4a", "aac", "mp3", "wav", "ogg", "amr")
}

private fun voiceDurationLabel(message: MessageDto): String {
    val fromContent = message.content.removePrefix("voice:").toIntOrNull()
    if (fromContent != null) return "${fromContent}s"
    val name = message.fileUrl?.let { Uri.parse(it).lastPathSegment } ?: return "语音"
    val seconds = voiceNameRegex.find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: return "语音"
    return "${seconds}s"
}

@Composable
private fun ParentQuote(parent: ParentMessageDto, onOpen: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 8.dp)
            .clickable(onClick = onOpen),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                text = parent.username,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = messagePreview(parent),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun highlightKeyword(text: String, keyword: String): androidx.compose.ui.text.AnnotatedString {
    val color = MaterialTheme.colorScheme.primary
    return remember(text, keyword, color) {
        if (keyword.isBlank()) {
            androidx.compose.ui.text.AnnotatedString(text)
        } else {
            val builder = androidx.compose.ui.text.AnnotatedString.Builder(text)
            var index = text.indexOf(keyword, ignoreCase = true)
            while (index >= 0) {
                builder.addStyle(
                    androidx.compose.ui.text.SpanStyle(
                        color = color,
                        fontWeight = FontWeight.SemiBold,
                        background = color.copy(alpha = 0.16f),
                    ),
                    index,
                    index + keyword.length,
                )
                index = text.indexOf(keyword, index + keyword.length, ignoreCase = true)
            }
            builder.toAnnotatedString()
        }
    }
}

@Composable
private fun highlightedContent(text: String): androidx.compose.ui.text.AnnotatedString {
    val color = MaterialTheme.colorScheme.primary
    return remember(text, color) {
        val builder = androidx.compose.ui.text.AnnotatedString.Builder(text)
        mentionRegex.findAll(text).forEach { match ->
            builder.addStyle(
                androidx.compose.ui.text.SpanStyle(
                    color = color,
                    fontWeight = FontWeight.Medium,
                ),
                match.range.first,
                match.range.last + 1,
            )
        }
        urlRegex.findAll(text).forEach { match ->
            val style = androidx.compose.ui.text.SpanStyle(
                color = color,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
            )
            builder.addStyle(style, match.range.first, match.range.last + 1)
            builder.addLink(
                androidx.compose.ui.text.LinkAnnotation.Url(url = match.value),
                match.range.first,
                match.range.last + 1,
            )
        }
        builder.toAnnotatedString()
    }
}

private fun messagePreview(parent: ParentMessageDto): String = when (parent.type) {
    "image" -> "[图片]"
    "file" -> if (parent.content.startsWith("voice:")) "[语音]" else "[文件]"
    else -> parent.content
}

private fun messagePreview(message: MessageDto?): String = when (message?.type) {
    "image" -> "[图片]"
    "file" -> if (
        message.content.startsWith("voice:") || isVoiceFile(message.fileUrl)
    ) {
        "[语音]"
    } else {
        "[文件]"
    }

    "recalled" -> "[已撤回]"
    else -> message?.content.orEmpty()
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
