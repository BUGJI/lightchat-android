package com.benton.lightchat.ui.chats

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.benton.lightchat.LightchatApplication
import com.benton.lightchat.data.remote.ApiResult
import com.benton.lightchat.data.remote.MessageDto
import com.benton.lightchat.data.remote.RecallEventDto
import com.benton.lightchat.util.ErrorMessages
import com.benton.lightchat.util.TimeFormat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

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
    val loadingOlder: Boolean = false,
    val hasMore: Boolean = false,
    val recallEnabled: Boolean = true,
    val contactNickname: String = "",
    val contactDnd: Boolean = false,
    val contactLoaded: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<MessageDto> = emptyList(),
    val searching: Boolean = false,
    val searchHasMore: Boolean = false,
    val searchLoadingMore: Boolean = false,
    val focusMessageId: Long? = null,
    val error: String? = null,
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as LightchatApplication).container
    private val chatRepository = container.chatRepository
    private val preferences = container.preferences

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var channelCursor = 0L
    private var privateCursor = 0L
    private var recallCursor = 0L
    private var currentUserId = 0L
    private var currentUsername = ""
    private var openedKey: String? = null
    private var searchJob: Job? = null
    private var searchCursor = 0L

    fun open(target: ChatTarget) {
        if (openedKey == target.key) return
        openedKey = target.key
        channelCursor = 0
        privateCursor = 0
        recallCursor = 0
        _state.value = ChatUiState(target = target, loading = true)
        viewModelScope.launch {
            val prefs = preferences.current()
            currentUserId = prefs.userId
            currentUsername = prefs.username
            loadHistory(target)
            if (target is ChatTarget.Private) {
                loadContact(target.chatId)
            }
        }
    }

    suspend fun pollLoop() {
        val target = _state.value.target ?: return
        while (coroutineContext.isActive) {
            pollOnce(target)
            delay(3000)
        }
    }

    fun sendText(text: String) {
        val target = _state.value.target ?: return
        val content = text.trim()
        if (content.isEmpty()) return
        if (_state.value.sending || _state.value.uploading) return

        viewModelScope.launch {
            _state.update { it.copy(sending = true, error = null) }
            val result = when (target) {
                is ChatTarget.Channel ->
                    chatRepository.sendChannelMessage(target.id, content)

                is ChatTarget.Private ->
                    chatRepository.sendPrivateMessage(target.otherUserId, content)
            }
            when (result) {
                is ApiResult.Success -> {
                    val data = result.data
                    val message = buildLocalMessage(
                        target = target,
                        messageId = data?.messageId ?: 0,
                        content = data?.content?.takeIf { it.isNotBlank() } ?: content,
                        type = "text",
                    )
                    appendMessage(message)
                    _state.update { it.copy(sending = false) }
                }

                is ApiResult.Failure -> {
                    _state.update { it.copy(sending = false, error = result.message) }
                }
            }
        }
    }

    fun sendAttachment(uri: Uri, preferImage: Boolean) {
        val target = _state.value.target ?: return
        if (_state.value.sending || _state.value.uploading) return

        viewModelScope.launch {
            _state.update { it.copy(uploading = true, error = null) }
            when (val uploadResult = chatRepository.upload(uri)) {
                is ApiResult.Failure -> {
                    _state.update { it.copy(uploading = false, error = uploadResult.message) }
                }

                is ApiResult.Success -> {
                    val upload = uploadResult.data
                    if (upload == null) {
                        _state.update {
                            it.copy(uploading = false, error = ErrorMessages.humanize("upload_failed"))
                        }
                        return@launch
                    }
                    val type = if (preferImage || upload.fileType == "image") "image" else "file"
                    _state.update { it.copy(uploading = false, sending = true) }
                    val result = when (target) {
                        is ChatTarget.Channel -> chatRepository.sendChannelMessage(
                            channelId = target.id,
                            content = "",
                            type = type,
                            fileId = upload.fileId,
                        )

                        is ChatTarget.Private -> chatRepository.sendPrivateMessage(
                            toUserId = target.otherUserId,
                            content = "",
                            type = type,
                            fileId = upload.fileId,
                        )
                    }
                    when (result) {
                        is ApiResult.Success -> {
                            val message = buildLocalMessage(
                                target = target,
                                messageId = result.data?.messageId ?: 0,
                                content = "",
                                type = type,
                            ).copy(
                                fileUrl = upload.fileUrl,
                                fileSize = upload.fileSize,
                            )
                            appendMessage(message)
                            _state.update { it.copy(sending = false) }
                        }

                        is ApiResult.Failure -> {
                            _state.update { it.copy(sending = false, error = result.message) }
                        }
                    }
                }
            }
        }
    }

    fun recall(message: MessageDto) {
        val target = _state.value.target ?: return
        viewModelScope.launch {
            val scope = if (target is ChatTarget.Private) "private" else "channel"
            when (val result = chatRepository.recall(message.id, scope)) {
                is ApiResult.Success -> {
                    _state.update { current ->
                        current.copy(
                            messages = current.messages.map {
                                if (it.id == message.id) {
                                    it.copy(type = "recalled", content = "", fileUrl = null)
                                } else {
                                    it
                                }
                            },
                        )
                    }
                }

                is ApiResult.Failure -> _state.update { it.copy(error = result.message) }
            }
        }
    }

    fun delete(message: MessageDto) {
        if (_state.value.target !is ChatTarget.Channel) return
        viewModelScope.launch {
            when (val result = chatRepository.deleteMessage(message.id)) {
                is ApiResult.Success -> {
                    _state.update { current ->
                        current.copy(messages = current.messages.filterNot { it.id == message.id })
                    }
                }

                is ApiResult.Failure -> _state.update { it.copy(error = result.message) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun search(query: String) {
        val target = _state.value.target ?: return
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
            val result = when (target) {
                is ChatTarget.Channel ->
                    chatRepository.searchChannelMessages(target.id, keyword)

                is ChatTarget.Private ->
                    chatRepository.searchPrivateMessages(target.chatId, keyword)
            }
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
        val target = _state.value.target ?: return
        val keyword = _state.value.searchQuery.trim()
        if (keyword.isEmpty() || searchCursor <= 0) return
        if (_state.value.searching || _state.value.searchLoadingMore || !_state.value.searchHasMore) return
        viewModelScope.launch {
            _state.update { it.copy(searchLoadingMore = true) }
            val result = when (target) {
                is ChatTarget.Channel ->
                    chatRepository.searchChannelMessages(
                        target.id,
                        keyword,
                        before = searchCursor,
                    )

                is ChatTarget.Private ->
                    chatRepository.searchPrivateMessages(
                        target.chatId,
                        keyword,
                        before = searchCursor,
                    )
            }
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
            )
        }
    }

    fun focusMessage(messageId: Long) = _state.update { it.copy(focusMessageId = messageId) }

    fun consumeFocus() = _state.update { it.copy(focusMessageId = null) }

    private suspend fun loadHistory(target: ChatTarget) {
        _state.update { it.copy(loading = true, error = null) }
        when (target) {
            is ChatTarget.Channel -> {
                when (val result = chatRepository.channelHistory(target.id, limit = 100)) {
                    is ApiResult.Success -> {
                        val history = result.data
                        val messages = (history?.messages.orEmpty()).sortedBy { it.id }
                        channelCursor = messages.maxOfOrNull { it.id } ?: 0
                        recallCursor = history?.recallLatestId ?: 0
                        _state.update {
                            it.copy(
                                loading = false,
                                messages = messages,
                                hasMore = history?.hasMore ?: false,
                                recallEnabled = history?.recallEnabled ?: true,
                            )
                        }
                        chatRepository.markChannelRead(target.id)
                    }

                    is ApiResult.Failure -> {
                        _state.update { it.copy(loading = false, error = result.message) }
                    }
                }
            }

            is ChatTarget.Private -> {
                when (val result = chatRepository.privateHistory(target.chatId, limit = 100)) {
                    is ApiResult.Success -> {
                        val history = result.data
                        val messages = (history?.messages.orEmpty()).sortedBy { it.id }
                        privateCursor = messages.maxOfOrNull { it.id } ?: 0
                        recallCursor = history?.recallLatestId ?: 0
                        _state.update {
                            it.copy(
                                loading = false,
                                messages = messages,
                                hasMore = history?.hasMore ?: false,
                                recallEnabled = history?.recallEnabled ?: true,
                            )
                        }
                    }

                    is ApiResult.Failure -> {
                        _state.update { it.copy(loading = false, error = result.message) }
                    }
                }
            }
        }
    }

    fun loadOlder() {
        val target = _state.value.target ?: return
        if (_state.value.loadingOlder || !_state.value.hasMore) return
        val oldestId = _state.value.messages.firstOrNull()?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(loadingOlder = true) }
            val result = when (target) {
                is ChatTarget.Channel ->
                    chatRepository.channelHistory(target.id, before = oldestId, limit = 100)

                is ChatTarget.Private ->
                    chatRepository.privateHistory(target.chatId, before = oldestId, limit = 100)
            }
            when (result) {
                is ApiResult.Success -> {
                    val history = result.data
                    val older = history?.messages.orEmpty()
                    _state.update { current ->
                        val existing = current.messages.mapTo(HashSet()) { it.id }
                        val merged = (older.filter { it.id !in existing } + current.messages)
                            .sortedBy { it.id }
                        current.copy(
                            messages = merged,
                            loadingOlder = false,
                            hasMore = history?.hasMore ?: false,
                        )
                    }
                }

                is ApiResult.Failure -> {
                    _state.update { it.copy(loadingOlder = false, error = result.message) }
                }
            }
        }
    }

    private suspend fun loadContact(chatId: Long) {
        when (val result = chatRepository.privateContact(chatId)) {
            is ApiResult.Success -> {
                val contact = result.data
                _state.update {
                    it.copy(
                        contactLoaded = true,
                        contactNickname = contact?.nickname.orEmpty(),
                        contactDnd = contact?.dnd == 1,
                    )
                }
            }

            is ApiResult.Failure -> _state.update { it.copy(contactLoaded = true) }
        }
    }

    fun saveContact(nickname: String, dnd: Boolean, onSaved: () -> Unit) {
        val target = _state.value.target as? ChatTarget.Private ?: return
        viewModelScope.launch {
            val trimmed = nickname.trim()
            when (val result = chatRepository.updatePrivateContact(target.chatId, trimmed, dnd)) {
                is ApiResult.Success -> {
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
        viewModelScope.launch {
            when (val result = chatRepository.deletePrivateContact(target.chatId)) {
                is ApiResult.Success -> onDeleted()
                is ApiResult.Failure -> _state.update { it.copy(error = result.message) }
            }
        }
    }

    private suspend fun pollOnce(target: ChatTarget) {
        val result = chatRepository.sync(
            channelId = if (target is ChatTarget.Channel) target.id else null,
            sinceId = if (target is ChatTarget.Channel) channelCursor else null,
            privateChatId = if (target is ChatTarget.Private) target.chatId else null,
            privateSinceId = if (target is ChatTarget.Private) privateCursor else null,
            recallSinceId = recallCursor,
            lists = false,
        )
        if (result !is ApiResult.Success) return
        val data = result.data ?: return

        val incoming = data.messages.filter { message ->
            when (target) {
                is ChatTarget.Channel -> message.channelId == target.id
                is ChatTarget.Private ->
                    message.privateChatId == target.chatId || message.chatId == target.chatId
            }
        }

        if (target is ChatTarget.Channel) {
            if (data.latestId > channelCursor) channelCursor = data.latestId
        } else {
            if (data.privateLatestId > privateCursor) privateCursor = data.privateLatestId
        }

        val recalls = data.recalls.filter { event ->
            when (target) {
                is ChatTarget.Channel ->
                    event.messageType == "channel" && event.channelId == target.id

                is ChatTarget.Private ->
                    event.messageType == "private" && event.privateChatId == target.chatId
            }
        }

        if (incoming.isEmpty() && recalls.isEmpty()) return

        _state.update { current ->
            val merged = current.messages.toMutableList()
            incoming.forEach { message ->
                val index = merged.indexOfFirst { it.id == message.id }
                if (index >= 0) merged[index] = message else merged.add(message)
            }
            recalls.forEach { event -> applyRecall(merged, event) }
            current.copy(messages = merged.sortedBy { it.id })
        }

        if (target is ChatTarget.Channel && incoming.isNotEmpty()) {
            chatRepository.markChannelRead(target.id)
        }

        if (data.recallLatestId > recallCursor) recallCursor = data.recallLatestId
    }

    private fun applyRecall(messages: MutableList<MessageDto>, event: RecallEventDto) {
        val index = messages.indexOfFirst { it.id == event.messageId }
        if (index < 0) return
        if (event.action == "delete") {
            messages.removeAt(index)
        } else {
            messages[index] = messages[index].copy(
                type = "recalled",
                content = "",
                fileUrl = null,
            )
        }
    }

    private fun appendMessage(message: MessageDto) {
        if (message.id <= 0) return
        _state.update { current ->
            val exists = current.messages.any { it.id == message.id }
            val messages = if (exists) {
                current.messages
            } else {
                (current.messages + message).sortedBy { it.id }
            }
            current.copy(messages = messages)
        }
    }

    private fun buildLocalMessage(
        target: ChatTarget,
        messageId: Long,
        content: String,
        type: String,
    ): MessageDto = when (target) {
        is ChatTarget.Channel -> MessageDto(
            id = messageId,
            channelId = target.id,
            userId = currentUserId,
            username = currentUsername,
            type = type,
            content = content,
            createdAt = TimeFormat.nowServerString(),
        )

        is ChatTarget.Private -> MessageDto(
            id = messageId,
            privateChatId = target.chatId,
            fromUserId = currentUserId,
            toUserId = target.otherUserId,
            userId = currentUserId,
            username = currentUsername,
            type = type,
            content = content,
            createdAt = TimeFormat.nowServerString(),
        )
    }
}
