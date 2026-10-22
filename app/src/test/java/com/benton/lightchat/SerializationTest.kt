package com.benton.lightchat

import com.benton.lightchat.data.remote.ApiEnvelope
import com.benton.lightchat.data.remote.NotificationMessageTemplate
import com.benton.lightchat.data.remote.NotificationTemplateDto
import com.benton.lightchat.data.remote.ProfileData
import com.benton.lightchat.data.remote.SendMessageRequest
import com.benton.lightchat.data.remote.SyncData
import com.benton.lightchat.data.remote.UpdateProfileRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    @Test
    fun serializesSnakeCaseAndOmitsNulls() {
        val encoded = json.encodeToString(
            SendMessageRequest(channelId = 7, content = "hello"),
        )
        assertTrue(encoded.contains("\"channel_id\":7"))
        assertTrue(encoded.contains("\"content\":\"hello\""))
        assertFalse(encoded.contains("to_user_id"))
        assertFalse(encoded.contains("file_id"))
    }

    @Test
    fun decodesSyncEnvelope() {
        val raw = """
            {
              "success": true,
              "data": {
                "messages": [
                  {
                    "id": 12,
                    "channel_id": 3,
                    "user_id": 5,
                    "username": "alice",
                    "type": "text",
                    "content": "hi",
                    "created_at": "2026-10-17 12:00:00"
                  },
                  {
                    "id": 13,
                    "private_chat_id": 9,
                    "from_user_id": 5,
                    "username": "alice",
                    "type": "text",
                    "content": "yo",
                    "is_read": 0,
                    "created_at": "2026-10-17 12:00:01"
                  }
                ],
                "latest_id": 12,
                "private_latest_id": 13,
                "recalls": [
                  {
                    "recall_id": 2,
                    "action": "recall",
                    "message_type": "channel",
                    "message_id": 10,
                    "channel_id": 3,
                    "user_id": 5,
                    "created_at": "2026-10-17 12:00:02"
                  }
                ],
                "recall_latest_id": 2,
                "channels": [
                  {
                    "id": 3,
                    "name": "general",
                    "display_name": "综合",
                    "type": "public",
                    "member_count": 3,
                    "is_joined": true,
                    "unread_count": 2
                  }
                ]
              },
              "error": null
            }
        """.trimIndent()

        val envelope = json.decodeFromString<ApiEnvelope<SyncData>>(raw)
        assertTrue(envelope.success)
        assertNull(envelope.error)
        val data = envelope.data!!
        assertEquals(12L, data.latestId)
        assertEquals(13L, data.privateLatestId)
        assertEquals(1, data.recalls.size)
        assertEquals(3L, data.channels.first().id)
        assertEquals(2, data.channels.first().unreadCount)
        assertTrue(data.channels.first().isJoined)
        assertEquals(3L, data.messages.first().channelId)
        assertEquals(9L, data.messages[1].privateChatId)
        assertEquals(5L, data.messages[1].fromUserId)
    }

    @Test
    fun decodesNotificationSettings() {
        val raw = """
            {
              "success": true,
              "data": {
                "user": {
                  "id": 1,
                  "username": "alice",
                  "email": "alice@example.com",
                  "notification": {
                    "mode": "pushplus",
                    "email": "alice@example.com",
                    "pushplus_key": "token",
                    "webhook_url": "",
                    "webhook_secret": "",
                    "group_enabled": true,
                    "private_enabled": false,
                    "template": {
                      "pushplus": { "title": "Hi", "content": "<p>Hello</p>" }
                    },
                    "available_methods": ["pushplus", "webhook"]
                  }
                }
              }
            }
        """.trimIndent()

        val envelope = json.decodeFromString<ApiEnvelope<ProfileData>>(raw)
        val notification = envelope.data?.user?.notification!!
        assertEquals("pushplus", notification.mode)
        assertEquals("token", notification.pushplusKey)
        assertTrue(notification.groupEnabled)
        assertFalse(notification.privateEnabled)
        assertEquals(listOf("pushplus", "webhook"), notification.availableMethods)
        assertEquals("Hi", notification.template?.pushplus?.title)
        assertEquals("<p>Hello</p>", notification.template?.pushplus?.content)
    }

    @Test
    fun serializesNotificationSettingsRequest() {
        val encoded = json.encodeToString(
            UpdateProfileRequest(
                notificationMode = "webhook",
                notificationWebhookUrl = "https://example.com/hook",
                notificationWebhookSecret = "secret",
                notificationGroupEnabled = false,
                notificationPrivateEnabled = true,
                notificationTemplate = NotificationTemplateDto(
                    webhook = NotificationMessageTemplate(title = "T", content = "C"),
                ),
            ),
        )
        assertTrue(encoded.contains("\"notification_mode\":\"webhook\""))
        assertTrue(encoded.contains("\"notification_webhook_url\":\"https://example.com/hook\""))
        assertTrue(encoded.contains("\"notification_webhook_secret\":\"secret\""))
        assertTrue(encoded.contains("\"notification_group_enabled\":false"))
        assertTrue(encoded.contains("\"notification_private_enabled\":true"))
        assertTrue(encoded.contains("\"webhook\":{\"title\":\"T\",\"content\":\"C\"}"))
        assertFalse(encoded.contains("\"pushplus\""))
    }

    @Test
    fun serializesEmptyTemplateForReset() {
        val encoded = json.encodeToString(
            UpdateProfileRequest(notificationTemplate = NotificationTemplateDto()),
        )
        assertTrue(encoded.contains("\"notification_template\":{}"))
    }
}
