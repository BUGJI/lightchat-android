package com.benton.lightchat.data.local

import com.benton.lightchat.data.remote.MessageDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageMappingTest {

    @Test
    fun mapsChannelMessageAndRoundTrips() {
        val dto = MessageDto(
            id = 7,
            channelId = 3,
            content = "hello",
            createdAt = "2026-01-02 03:04:05",
        )
        val entity = dto.toEntity("owner", ConversationRef.channel(3), 100L)

        requireNotNull(entity)
        assertEquals("channel", entity.conversationType)
        assertEquals(3L, entity.conversationId)
        assertEquals(3L, entity.channelId)

        val restored = entity.toDto()
        assertEquals(7L, restored.id)
        assertEquals(3L, restored.channelId)
        assertEquals("hello", restored.content)
        assertEquals("2026-01-02 03:04:05", restored.createdAt)
    }

    @Test
    fun mapsPrivateMessageUsingChatId() {
        val dto = MessageDto(
            id = 8,
            chatId = 9,
            content = "hi",
            createdAt = "2026-01-02 03:04:05",
        )
        val entity = dto.toEntity("owner", ConversationRef.private(9), 100L)

        requireNotNull(entity)
        assertEquals("private", entity.conversationType)
        assertEquals(9L, entity.conversationId)
        assertEquals(9L, entity.privateChatId)
    }

    @Test
    fun ignoresMessagesWithoutId() {
        val dto = MessageDto(id = 0, channelId = 3, content = "invalid")
        assertNull(dto.toEntity("owner", ConversationRef.channel(3), 100L))
    }

    @Test
    fun ignoresMessagesWithoutConversation() {
        val dto = MessageDto(id = 5, content = "orphan")
        assertNull(dto.toEntity("owner", null, 100L))
    }
}
