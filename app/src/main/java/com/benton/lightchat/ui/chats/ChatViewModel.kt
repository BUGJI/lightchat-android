package com.benton.lightchat.ui.chats

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.benton.lightchat.LightchatApplication
import com.benton.lightchat.data.local.AccountKey
import com.benton.lightchat.data.local.ConversationRef
import com.benton.lightchat.data.remote.ApiResult
import com.benton.lightchat.data.remote.ChannelMemberDto
import com.benton.lightchat.data.remote.MessageDto
import com.benton.lightchat.data.remote.ParentMessageDto
import com.benton.lightchat.util.ErrorMessages
import com.benton.lightchat.util.LocalId
import com.benton.lightchat.util.TimeFormat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID

enum class SearchFilter(val label: String) {
    ALL("全部"),
    TEXT("文本"),
    IMAGE("图片"),
    FILE("文件"),
}

sealed interface ChatTarget {
    val key: String
    val title: String

    data class Channel(val id: Long, override val title: String) : ChatTarget {
        override val key: String get() = "channel:$id"
    }

    data class Private(
        val chatId: Long,
        val otherUserId: Long,
        override val title: String,
    ) : ChatTarget {
        override val key: String get() = "private:$chatId"
    }
}

data class ChatUiState(
    val target: ChatTarget? = null,
    val messages: List<MessageDto> = emptyList(),
    val loading: Boolean = true,
    val sending: Boolean = false,
    val uploading: Boolean = false,
    val uploadProgress: Float? = null,
    val downloadingFileId: Long? = null,
    val downloadProgress: Float? = null,
    val loadingOlder: Boolean = false,
    val hasMore: Boolean = false,
    val recallEnabled: Boolean = true,
    val contactNickname: String = "",
    val contactDnd: Boolean = false,
    val contactLoaded: Boolean = false,
    val contactLastActive: String? = null,
    val members: List<ChannelMemberDto> = emptyList(),
    val announcement: String? = null,
    val replyTo: MessageDto? = null,
    val draftText: String = "",
    val unreadAnchorId: Long = 0,
    val searchQuery: String = "",
    val searchResults: List<MessageDto> = emptyList(),
    val searching: Boolean = false,
    val searchHasMore: Boolean = false,
    val searchLoadingMore: Boolean = false,
    val searchFilter: SearchFilter = SearchFilter.ALL,
    val focusMessageId: Long? = null,
    val locating: Boolean = false,
    val offline: Boolean = false,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as LightchatApplication).container
    private val chatRepository = container.chatRepository
    private val sendQueue = container.sendQueue
    private val syncCoordinator = container.syncCoordinator
    private val userRepository = container.userRepository
    private val preferences = container.preferences

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            sendQueue.errors.collect { message ->
                _state.update { it.copy(error = message) }
            }
        }
        viewModelScope.launch {
            syncCoordinator.status.collect { status ->
                _state.update { it.copy(offline = !status.online) }
            }
        }
    }

    private var accountKey: String? = null
    private var conversationRef: ConversationRef? = null
    private val loadedCount = MutableStateFlow(PAGE_SIZE)
    private var messagesJob: Job? = null
    private var openJob: Job? = null
    private var currentUserId = 0L
    private var currentUsername = ""
    private var openedKey: String? = null
    private var searchJob: Job? = null
    private var uploadJob: Job? = null
    private var draftJob: Job? = null
    private var searchCursor = 0L

    fun open(target: ChatTarget) {
        if (openedKey == target.key && accountKey != null) return
        openedKey = target.key
        messagesJob?.cancel()
        loadedCount.value = PAGE_SIZE
        accountKey = null
        conversationRef = null
        _state.value = ChatUiState(target = target, loading = true)
        openJob = viewModelScope.launch {
            val prefs = preferences.current()
            currentUserId = prefs.userId
            currentUsername = prefs.username
            val key = AccountKey.of(prefs.baseUrl, prefs.userId)
            if (key == null) {
                _state.update { it.copy(loading = false, error = "无法读取本地账号信息") }
                return@launch
            }
            if (openedKey != target.key) return@launch
            accountKey = key
            val ref = target.toRef()
            conversationRef = ref
            val draft = chatRepository.draft(key, ref)
            _state.update { it.copy(draftText = draft?.text.orEmpty()) }
            if (draft != null && draft.parentId > 0) {
                chatRepository.message(key, ref, draft.parentId)?.let { parent ->
                    _state.update { it.copy(replyTo = parent) }
                }
            }

            val cursor = chatRepository.cursor(key, ref)
            _state.update {
                it.copy(
                    recallEnabled = cursor?.recallEnabled ?: true,
                    unreadAnchorId = cursor?.lastReadMessageId ?: 0,
                )
            }
            observeMessages(key, ref)
            if (target is ChatTarget.Private) {
                viewModelScope.launch { loadContact(key, target.chatId, target.otherUserId) }
            } else if (target is ChatTarget.Channel) {
                viewModelScope.launch { loadChannelExtras(key, target.id) }
            }

            val cached = chatRepository.countMessages(key, ref)
            val online = if (cached == 0L) {
                chatRepository.refreshHistory(key, ref)
            } else {
                syncCoordinator.status.value.online
            }
            val latestCursor = chatRepository.cursor(key, ref)
            _state.update {
                it.copy(
                    loading = false,
                    offline = !online,
                    recallEnabled = latestCursor?.recallEnabled
                        ?: it.recallEnabled,
                )
            }
            syncCoordinator.setActive(ref)
            syncCoordinator.requestSync()
            sendQueue.setOnline(online)
            if (online) sendQueue.drain()
            updateHasMore(key, ref)
            if (online && target is ChatTarget.Channel) {
                chatRepository.markChannelRead(key, target.id)
            }
        }
    }

    override fun onCleared() {
        conversationRef?.let { syncCoordinator.clearActive(it) }
    }

    fun sendText(
        text: String,
        mentionedUserIds: List<Long> = emptyList(),
        onSent: () -> Unit = {},
    ) {
        val target = _state.value.target ?: return
        val key = accountKey ?: return
        val ref = conversationRef ?: return
        val content = text.trim()
        if (content.isEmpty()) return

        val reply = _state.value.replyTo
        val mentions = mentionedUserIds
            .takeIf { target is ChatTarget.Channel && it.isNotEmpty() }
            ?.joinToString(",")
        val message = buildPendingMessage(
            target = target,
            content = content,
            type = "text",
            parent = reply,
            mentionedUsers = mentions,
        )
        viewModelScope.launch {
            _state.update { it.copy(error = null, replyTo = null, draftText = "") }
            chatRepository.clearDraft(key, ref)
            chatRepository.cacheOutgoing(key, ref, message)
            sendQueue.enqueue()
        }
        onSent()
    }

    fun onDraftChange(text: String) {
        _state.update { it.copy(draftText = text) }
        val key = accountKey ?: return
        val ref = conversationRef ?: return
        draftJob?.cancel()
        draftJob = viewModelScope.launch {
            delay(400)
            chatRepository.saveDraft(key, ref, text, _state.value.replyTo?.id ?: 0)
        }
    }

    fun sendAttachment(uri: Uri, preferImage: Boolean) {
        sendAttachments(listOf(uri), preferImage)
    }

    fun sendAttachments(uris: List<Uri>, preferImage: Boolean) {
        val target = _state.value.target ?: return
        val key = accountKey ?: return
        val ref = conversationRef ?: return
        if (uris.isEmpty() || _state.value.uploading) return
        if (_state.value.offline) {
            _state.update { it.copy(error = "离线时无法上传附件，请联网后重试") }
            return
        }

        val reply = _state.value.replyTo
        uploadJob = viewModelScope.launch {
            _state.update { it.copy(uploading = true, uploadProgress = 0f, error = null) }
            var firstError: String? = null
            var sentAny = false
            uris.forEachIndexed { index, uri ->
                val result = chatRepository.upload(uri) { progress ->
                    val overall = (index + progress.coerceIn(0f, 1f)) / uris.size
                    _state.update { it.copy(uploadProgress = overall) }
                }
                when (result) {
                    is ApiResult.Failure -> {
                        if (firstError == null) firstError = result.message
                    }

                    is ApiResult.Success -> {
                        val upload = result.data
                        if (upload == null) {
                            if (firstError == null) {
                                firstError = ErrorMessages.humanize("upload_failed")
                            }
                        } else {
                            chatRepository.cacheUploadedAttachment(
                                uri,
                                upload.fileUrl,
                                upload.fileName,
                            )
                            val type = if (preferImage || upload.fileType == "image") {
                                "image"
                            } else {
                                "file"
                            }
                            val message = buildPendingMessage(
                                target = target,
                                content = "",
                                type = type,
                                parent = reply,
                            ).copy(
                                fileId = upload.fileId,
                                fileUrl = upload.fileUrl,
                                fileSize = upload.fileSize,
                            )
                            chatRepository.cacheOutgoing(key, ref, message)
                            sentAny = true
                        }
                    }
                }
            }
            _state.update {
                it.copy(
                    uploading = false,
                    uploadProgress = null,
                    sending = false,
                    replyTo = if (sentAny) null else it.replyTo,
                    error = firstError ?: it.error,
                )
            }
            if (sentAny) sendQueue.enqueue()
        }
    }

    fun sendVoice(file: File, durationMs: Long) {
        val target = _state.value.target ?: return
        val key = accountKey ?: return
        val ref = conversationRef ?: return
        if (_state.value.uploading) {
            file.delete()
            return
        }
        uploadJob = viewModelScope.launch {
            _state.update { it.copy(uploading = true, uploadProgress = 0f, error = null) }
            when (
                val result = chatRepository.uploadVoice(file) { progress ->
                    _state.update { it.copy(uploadProgress = progress.coerceIn(0f, 1f)) }
                }
            ) {
                is ApiResult.Failure -> {
                    file.delete()
                    _state.update {
                        it.copy(uploading = false, uploadProgress = null, error = result.message)
                    }
                }

                is ApiResult.Success -> {
                    val upload = result.data
                    if (upload == null) {
                        file.delete()
                        _state.update {
                            it.copy(
                                uploading = false,
                                uploadProgress = null,
                                error = ErrorMessages.humanize("upload_failed"),
                            )
                        }
                        return@launch
                    }
                    chatRepository.cacheUploadedVoice(file, upload.fileUrl, upload.fileName)
                    val seconds = (durationMs / 1000).coerceAtLeast(1)
                    val message = buildPendingMessage(
                        target = target,
                        content = "voice:$seconds",
                        type = "file",
                        parent = _state.value.replyTo,
                    ).copy(
                        fileId = upload.fileId,
                        fileUrl = upload.fileUrl,
                        fileSize = upload.fileSize,
                    )
                    chatRepository.cacheOutgoing(key, ref, message)
                    _state.update {
                        it.copy(uploading = false, uploadProgress = null, replyTo = null)
                    }
                    sendQueue.enqueue()
                    file.delete()
                }
            }
        }
    }

    fun cancelUpload() {
        if (!_state.value.uploading) return
        uploadJob?.cancel()
        uploadJob = null
        _state.update { it.copy(uploading = false, uploadProgress = null) }
    }

    fun downloadAttachment(message: MessageDto, onReady: (File) -> Unit) {
        val url = message.fileUrl ?: return
        if (_state.value.downloadingFileId != null) return
        viewModelScope.launch {
            _state.update {
                it.copy(downloadingFileId = message.id, downloadProgress = 0f, error = null)
            }
            val hint = url.substringBefore('?').substringAfterLast('/', "")
            val result = chatRepository.downloadAttachment(url, hint.ifBlank { null }) { progress ->
                _state.update { it.copy(downloadProgress = progress.coerceIn(0f, 1f)) }
            }
            _state.update { it.copy(downloadingFileId = null, downloadProgress = null) }
            when (result) {
                is ApiResult.Success -> {
                    val file = result.data
                    if (file == null) {
                        _state.update { it.copy(error = "无法保存文件") }
                    } else {
                        onReady(file)
                    }
                }

                is ApiResult.Failure -> _state.update { it.copy(error = result.message) }
            }
        }
    }

    fun recall(message: MessageDto) {
        val target = _state.value.target ?: return
        val key = accountKey ?: return
        val ref = conversationRef ?: return
        viewModelScope.launch {
            val scope = if (target is ChatTarget.Private) "private" else "channel"
            when (val result = chatRepository.recall(message.id, scope)) {
                is ApiResult.Success -> {
                    chatRepository.markRecalled(key, ref, message.id)
                    chatRepository.deleteCachedAttachment(message.fileUrl)
                }

                is ApiResult.Failure -> _state.update { it.copy(error = result.message) }
            }
        }
    }

    fun delete(message: MessageDto) {
        if (_state.value.target !is ChatTarget.Channel) return
        val key = accountKey ?: return
        val ref = conversationRef ?: return
        viewModelScope.launch {
            when (val result = chatRepository.deleteMessage(message.id)) {
                is ApiResult.Success -> {
                    chatRepository.deleteCachedMessage(key, ref, message.id)
                    chatRepository.deleteCachedAttachment(message.fileUrl)
                }

                is ApiResult.Failure -> _state.update { it.copy(error = result.message) }
            }
        }
    }

    fun retry(message: MessageDto) {
        val key = accountKey ?: return
        val ref = conversationRef ?: return
        if (message.id >= 0) return
        sendQueue.retry(key, ref, message.id)
    }

    fun discard(message: MessageDto) {
        val key = accountKey ?: return
        val ref = conversationRef ?: return
        if (message.id >= 0) return
        sendQueue.discard(key, ref, message.id)
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun search(query: String) {
        val key = accountKey ?: return
        val ref = conversationRef ?: return
        searchJob?.cancel()
        searchCursor = 0
        _state.update {
            it.copy(
                searchQuery = query,
                searching = query.isNotBlank(),
                searchResults = emptyList(),
                searchHasMore = false,
                searchLoadingMore = false,
            )
        }
        if (query.isBlank()) return
        searchJob = viewModelScope.launch {
            delay(300)
            val keyword = query.trim()
            if (keyword.isEmpty()) return@launch
            _state.update { it.copy(searching = true) }
            val result = chatRepository.searchMessages(
                ownerKey = key,
                ref = ref,
                query = keyword,
                limit = 30,
                remote = !_state.value.offline,
            )
            when (result) {
                is ApiResult.Success -> {
                    val data = result.data
                    val messages = data?.messages.orEmpty()
                    searchCursor = messages.lastOrNull()?.id ?: 0
                    _state.update {
                        it.copy(
                            searching = false,
                            searchResults = messages,
                            searchHasMore = data?.hasMore ?: false,
                        )
                    }
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(searching = false, error = result.message)
                }
            }
        }
    }

    fun loadMoreSearch() {
        val key = accountKey ?: return
        val ref = conversationRef ?: return
        val keyword = _state.value.searchQuery.trim()
        if (keyword.isEmpty() || searchCursor <= 0) return
        if (_state.value.searching || _state.value.searchLoadingMore || !_state.value.searchHasMore) return
        viewModelScope.launch {
            _state.update { it.copy(searchLoadingMore = true) }
            val result = chatRepository.searchMessages(
                ownerKey = key,
                ref = ref,
                query = keyword,
                beforeId = searchCursor,
                limit = 30,
                remote = !_state.value.offline,
            )
            when (result) {
                is ApiResult.Success -> {
                    val data = result.data
                    val more = data?.messages.orEmpty()
                    more.lastOrNull()?.let { searchCursor = it.id }
                    _state.update { current ->
                        val existing = current.searchResults.mapTo(HashSet()) { it.id }
                        current.copy(
                            searchLoadingMore = false,
                            searchResults = current.searchResults +
                                more.filter { it.id !in existing },
                            searchHasMore = data?.hasMore ?: false,
                        )
                    }
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(searchLoadingMore = false, error = result.message)
                }
            }
        }
    }

    fun setSearchFilter(filter: SearchFilter) = _state.update {
        it.copy(searchFilter = filter)
    }

    fun clearSearch() {
        searchJob?.cancel()
        searchCursor = 0
        _state.update {
            it.copy(
                searchQuery = "",
                searchResults = emptyList(),
                searching = false,
                searchHasMore = false,
                searchLoadingMore = false,
                searchFilter = SearchFilter.ALL,
            )
        }
    }

    fun focusMessage(messageId: Long) = _state.update { it.copy(focusMessageId = messageId) }

    fun consumeFocus() = _state.update { it.copy(focusMessageId = null) }

    fun openSearchResult(message: MessageDto) = locateMessage(message.id)

    fun openParent(messageId: Long) = locateMessage(messageId)

    private fun locateMessage(messageId: Long) {
        if (messageId <= 0) return
        val key = accountKey ?: return
        val ref = conversationRef ?: return
        if (_state.value.locating) return
        if (_state.value.messages.any { it.id == messageId }) {
            focusMessage(messageId)
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(locating = true, error = null) }
            val available = chatRepository.ensureMessageAvailable(key, ref, messageId)
            if (!available) {
                _state.update {
                    it.copy(
                        locating = false,
                        error = "该消息位置较早，暂时无法定位",
                    )
                }
                return@launch
            }
            val fromCount = chatRepository.countMessagesFrom(key, ref, messageId)
            if (fromCount > 0) {
                loadedCount.update { current -> maxOf(current, fromCount.toInt()) }
            }
            withTimeoutOrNull(1_500) {
                _state.first { state ->
                    state.messages.any { it.id == messageId }
                }
            }
            _state.update { it.copy(locating = false) }
            focusMessage(messageId)
        }
    }

    fun replyTo(message: MessageDto) {
        if (message.type == "recalled" || message.type == "system") return
        _state.update { it.copy(replyTo = message) }
        onDraftChange(_state.value.draftText)
    }

    fun clearReply() {
        _state.update { it.copy(replyTo = null) }
        onDraftChange(_state.value.draftText)
    }

    fun loadOlder() {
        val key = accountKey ?: return
        val ref = conversationRef ?: return
        if (_state.value.loadingOlder || !_state.value.hasMore) return
        val oldestId = _state.value.messages.firstOrNull { it.id > 0 }?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(loadingOlder = true) }
            val localBefore = chatRepository.countMessagesBefore(key, ref, oldestId)
            var success = true
            if (localBefore == 0L) {
                val cursor = chatRepository.cursor(key, ref)
                if (cursor?.historyComplete == false) {
                    success = chatRepository.loadOlderHistory(key, ref, oldestId)
                }
            }
            if (success) {
                loadedCount.update { it + PAGE_SIZE }
            } else {
                _state.update { it.copy(error = "网络异常，无法加载更早消息") }
            }
            updateHasMore(key, ref)
            _state.update { it.copy(loadingOlder = false) }
        }
    }

    fun saveContact(nickname: String, dnd: Boolean, onSaved: () -> Unit) {
        val target = _state.value.target as? ChatTarget.Private ?: return
        val key = accountKey ?: return
        viewModelScope.launch {
            val trimmed = nickname.trim()
            when (val result = chatRepository.updatePrivateContact(target.chatId, trimmed, dnd)) {
                is ApiResult.Success -> {
                    chatRepository.savePrivateContactLocal(key, target.chatId, trimmed, dnd)
                    _state.update {
                        it.copy(contactNickname = trimmed, contactDnd = dnd, contactLoaded = true)
                    }
                    onSaved()
                }

                is ApiResult.Failure -> _state.update { it.copy(error = result.message) }
            }
        }
    }

    fun deleteContact(onDeleted: () -> Unit) {
        val target = _state.value.target as? ChatTarget.Private ?: return
        val key = accountKey ?: return
        viewModelScope.launch {
            when (val result = chatRepository.deletePrivateContact(target.chatId)) {
                is ApiResult.Success -> {
                    chatRepository.deletePrivateChatLocal(key, target.chatId)
                    onDeleted()
                }

                is ApiResult.Failure -> _state.update { it.copy(error = result.message) }
            }
        }
    }

    private fun observeMessages(key: String, ref: ConversationRef) {
        messagesJob = viewModelScope.launch {
            loadedCount
                .flatMapLatest { limit -> chatRepository.observeMessages(key, ref, limit) }
                .collect { messages ->
                    _state.update { current ->
                        current.copy(
                            messages = messages,
                            loading = current.loading && messages.isEmpty(),
                        )
                    }
                    updateHasMore(key, ref)
                }
        }
    }

    private suspend fun updateHasMore(key: String, ref: ConversationRef) {
        val total = chatRepository.countMessages(key, ref)
        val cursor = chatRepository.cursor(key, ref)
        _state.update {
            it.copy(hasMore = it.messages.size < total || cursor?.historyComplete == false)
        }
    }

    private suspend fun loadContact(key: String, chatId: Long, otherUserId: Long) {
        val local = chatRepository.localPrivateContact(key, chatId)
        if (local != null) {
            _state.update {
                it.copy(
                    contactLoaded = true,
                    contactNickname = local.nickname,
                    contactDnd = local.dnd == 1,
                )
            }
        }
        if (_state.value.offline) {
            _state.update { it.copy(contactLoaded = true) }
            return
        }
        when (val result = chatRepository.privateContact(chatId)) {
            is ApiResult.Success -> {
                val contact = result.data
                if (contact == null) {
                    _state.update { it.copy(contactLoaded = true) }
                } else {
                    val nickname = contact.nickname
                    val dnd = contact.dnd == 1
                    chatRepository.savePrivateContactLocal(key, chatId, nickname, dnd)
                    _state.update {
                        it.copy(
                            contactLoaded = true,
                            contactNickname = nickname,
                            contactDnd = dnd,
                        )
                    }
                }
            }

            is ApiResult.Failure -> _state.update { it.copy(contactLoaded = true) }
        }
        if (otherUserId > 0) {
            when (val profile = userRepository.profile(otherUserId)) {
                is ApiResult.Success -> _state.update {
                    it.copy(contactLastActive = profile.data?.lastActiveAt)
                }

                is ApiResult.Failure -> Unit
            }
        }
    }

    private suspend fun loadChannelExtras(key: String, channelId: Long) {
        chatRepository.channel(key, channelId)?.let { channel ->
            _state.update { it.copy(announcement = channel.announcement) }
        }
        when (val result = chatRepository.channelMembers(channelId)) {
            is ApiResult.Success -> {
                val members = result.data?.members.orEmpty()
                _state.update { it.copy(members = members) }
            }

            is ApiResult.Failure -> Unit
        }
    }

    private fun buildPendingMessage(
        target: ChatTarget,
        content: String,
        type: String,
        parent: MessageDto? = null,
        fileId: Long? = null,
        fileUrl: String? = null,
        fileSize: Long? = null,
        mentionedUsers: String? = null,
    ): MessageDto {
        val createdAt = TimeFormat.nowServerString()
        val parentPreview = parent?.let {
            ParentMessageDto(
                id = it.id,
                username = it.username.orEmpty(),
                type = it.type,
                content = it.content,
                fileUrl = it.fileUrl,
                deleted = it.type == "recalled",
                recalled = it.type == "recalled",
            )
        }
        return when (target) {
            is ChatTarget.Channel -> MessageDto(
                id = LocalId.next(),
                channelId = target.id,
                userId = currentUserId,
                username = currentUsername,
                type = type,
                content = content,
                fileId = fileId,
                fileUrl = fileUrl,
                fileSize = fileSize,
                parentId = parent?.id ?: 0,
                parent = parentPreview,
                mentionedUsers = mentionedUsers,
                createdAt = createdAt,
                status = "pending",
                clientMsgId = UUID.randomUUID().toString(),
            )

            is ChatTarget.Private -> MessageDto(
                id = LocalId.next(),
                privateChatId = target.chatId,
                chatId = target.chatId,
                fromUserId = currentUserId,
                toUserId = target.otherUserId,
                userId = currentUserId,
                username = currentUsername,
                type = type,
                content = content,
                fileId = fileId,
                fileUrl = fileUrl,
                fileSize = fileSize,
                parentId = parent?.id ?: 0,
                parent = parentPreview,
                createdAt = createdAt,
                status = "pending",
                clientMsgId = UUID.randomUUID().toString(),
            )
        }
    }

    private fun ChatTarget.toRef(): ConversationRef = when (this) {
        is ChatTarget.Channel -> ConversationRef.channel(id)
        is ChatTarget.Private -> ConversationRef.private(chatId)
    }

    companion object {
        private const val PAGE_SIZE = 100
    }
}
