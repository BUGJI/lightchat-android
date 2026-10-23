package com.benton.lightchat.data.local

data class ConversationRef(val type: String, val id: Long) {

    val isChannel: Boolean get() = type == TYPE_CHANNEL

    companion object {
        const val TYPE_CHANNEL = "channel"
        const val TYPE_PRIVATE = "private"

        fun channel(id: Long): ConversationRef = ConversationRef(TYPE_CHANNEL, id)
        fun private(id: Long): ConversationRef = ConversationRef(TYPE_PRIVATE, id)
    }
}

data class ConversationCursor(
    val lastMessageId: Long,
    val recallId: Long,
    val historyComplete: Boolean,
    val recallEnabled: Boolean,
    val recallWindow: Int,
    val lastReadMessageId: Long = 0,
)
