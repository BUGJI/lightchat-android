package com.benton.lightchat.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class ApiEnvelope<T>(
    val success: Boolean = false,
    val data: T? = null,
    val error: ApiError? = null,
)

@Serializable
data class ApiError(
    val code: String = "",
    val message: String = "",
)

@Serializable
data class EmptyData(
    val message: String? = null,
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class RegisterRequest(
    val username: String,
    val password: String,
    val email: String,
    val contact: String? = null,
)

@Serializable
data class AccountData(
    val userId: Long = 0,
    val username: String = "",
    val role: String? = null,
    val token: String? = null,
    val expiresAt: String? = null,
    val absoluteExpiresAt: String? = null,
    val apiKey: String? = null,
    val creatorId: Long? = null,
    val hint: String? = null,
)

@Serializable
data class RefreshData(
    val token: String = "",
    val expiresAt: String? = null,
    val absoluteExpiresAt: String? = null,
)

@Serializable
data class ChannelDto(
    val id: Long = 0,
    val name: String = "",
    val displayName: String = "",
    val type: String = "public",
    val description: String = "",
    val announcement: String? = null,
    val ownerId: Long = 0,
    val memberCount: Int = 0,
    val isJoined: Boolean = false,
    val unreadCount: Int = 0,
    val lastMessage: String? = null,
    val lastMessageAt: String? = null,
    val createdAt: String = "",
)

@Serializable
data class ChannelsData(
    val channels: List<ChannelDto> = emptyList(),
)

@Serializable
data class PrivateChatDto(
    val id: Long = 0,
    val otherUserId: Long = 0,
    val otherUsername: String = "",
    val otherNickname: String? = null,
    val otherAvatar: String? = null,
    val otherRole: String? = null,
    val lastMessage: String? = null,
    val lastMessageAt: String? = null,
    val unreadCount: Int = 0,
    val dnd: Int = 0,
    val createdAt: String = "",
)

@Serializable
data class PrivateChatsData(
    val chats: List<PrivateChatDto> = emptyList(),
)

@Serializable
data class RecalledByDto(
    val id: Long = 0,
    val username: String = "",
)

@Serializable
data class MessageDto(
    val id: Long = 0,
    val channelId: Long? = null,
    val chatId: Long? = null,
    val privateChatId: Long? = null,
    val userId: Long? = null,
    val fromUserId: Long? = null,
    val toUserId: Long? = null,
    val username: String? = null,
    val avatar: String? = null,
    val role: String? = null,
    val parentId: Long = 0,
    val type: String = "text",
    val content: String = "",
    val fileUrl: String? = null,
    val fileSize: Long? = null,
    val mentionedUsers: String? = null,
    val recalledBy: RecalledByDto? = null,
    val isRead: Int = 0,
    val createdAt: String = "",
) {
    val isPrivate: Boolean get() = privateChatId != null || chatId != null

    val conversationId: Long
        get() = channelId ?: privateChatId ?: chatId ?: 0
}

@Serializable
data class HistoryData(
    val messages: List<MessageDto> = emptyList(),
    val hasMore: Boolean = false,
    val recallEnabled: Boolean = true,
    val recallWindow: Int = 0,
    val recallLatestId: Long = 0,
)

@Serializable
data class RecallEventDto(
    val recallId: Long = 0,
    val action: String = "recall",
    val messageType: String = "channel",
    val messageId: Long = 0,
    val channelId: Long? = null,
    val privateChatId: Long? = null,
    val userId: Long = 0,
    val createdAt: String = "",
)

@Serializable
data class SyncData(
    val messages: List<MessageDto> = emptyList(),
    val latestId: Long = 0,
    val privateLatestId: Long = 0,
    val recalls: List<RecallEventDto> = emptyList(),
    val recallLatestId: Long = 0,
    val channels: List<ChannelDto> = emptyList(),
    val privateChats: List<PrivateChatDto> = emptyList(),
)

@Serializable
data class SendMessageRequest(
    val channelId: Long? = null,
    val toUserId: Long? = null,
    val content: String,
    val type: String = "text",
    val parentId: Long? = null,
    val mentionedUsers: List<Long>? = null,
    val fileId: Long? = null,
)

@Serializable
data class SendResultData(
    val messageId: Long = 0,
    val chatId: Long? = null,
    val content: String = "",
)

@Serializable
data class RecallRequest(
    val messageId: Long,
    val scope: String = "channel",
)

@Serializable
data class RecallResultData(
    val messageId: Long = 0,
    val scope: String = "channel",
    val recallId: Long = 0,
    val already: Boolean = false,
)

@Serializable
data class DeleteMessageRequest(
    val messageId: Long,
)

@Serializable
data class CreateChannelRequest(
    val name: String,
    val displayName: String,
    val type: String = "public",
    val description: String? = null,
)

@Serializable
data class ChannelIdRequest(
    val channelId: Long,
)

@Serializable
data class ChannelCreateData(
    val channelId: Long = 0,
)

@Serializable
data class PrivateContactData(
    val chatId: Long = 0,
    val nickname: String = "",
    val dnd: Int = 0,
    val hidden: Int = 0,
)

@Serializable
data class UpdatePrivateContactRequest(
    val chatId: Long,
    val action: String = "update",
    val nickname: String? = null,
    val dnd: Int? = null,
)

@Serializable
data class ChannelMemberDto(
    val userId: Long = 0,
    val username: String = "",
    val nickname: String = "",
    val avatar: String = "",
    val role: String = "member",
    val muted: Boolean = false,
    val muteUntil: String? = null,
)

@Serializable
data class ChannelInfoDto(
    val id: Long = 0,
    val displayName: String = "",
    val type: String = "public",
    val memberCount: Int = 0,
    val ownerId: Long = 0,
    val viewerRole: String? = null,
)

@Serializable
data class ChannelMembersData(
    val channel: ChannelInfoDto = ChannelInfoDto(),
    val members: List<ChannelMemberDto> = emptyList(),
)

@Serializable
data class InviteChannelRequest(
    val channelId: Long,
    val username: String? = null,
    val userId: Long? = null,
)

@Serializable
data class UpdateChannelRequest(
    val channelId: Long,
    val displayName: String? = null,
    val type: String? = null,
    val description: String? = null,
)

@Serializable
data class DeleteChannelRequest(
    val channelId: Long,
)

@Serializable
data class CreatePrivateRequest(
    val toUserId: Long,
)

@Serializable
data class CreatePrivateData(
    val chatId: Long = 0,
    val created: Boolean = false,
)

@Serializable
data class NotificationEmailTemplate(
    val subject: String = "",
    val body: String = "",
)

@Serializable
data class NotificationMessageTemplate(
    val title: String = "",
    val content: String = "",
)

@Serializable
data class NotificationTemplateDto(
    val email: NotificationEmailTemplate? = null,
    val pushplus: NotificationMessageTemplate? = null,
    val webhook: NotificationMessageTemplate? = null,
)

@Serializable
data class NotificationSettingsDto(
    val mode: String = "none",
    val email: String = "",
    val pushplusKey: String = "",
    val webhookUrl: String = "",
    val webhookSecret: String = "",
    val groupEnabled: Boolean = true,
    val privateEnabled: Boolean = true,
    val template: NotificationTemplateDto? = null,
    val availableMethods: List<String> = emptyList(),
)

@Serializable
data class TestNotificationData(
    val method: String = "",
    val message: String = "",
)

@Serializable
data class UserDto(
    val id: Long = 0,
    val username: String = "",
    val nickname: String? = null,
    val avatar: String? = null,
    val role: String? = null,
    val status: Int = 1,
    val email: String? = null,
    val bio: String? = null,
    val signature: String? = null,
    val createdAt: String? = null,
    val joinDate: String? = null,
    val lastActiveAt: String? = null,
    val emailVerified: Boolean = false,
    val twoFactorEnabled: Boolean = false,
    val notification: NotificationSettingsDto? = null,
)

@Serializable
data class ProfileData(
    val user: UserDto? = null,
)

@Serializable
data class UsersData(
    val users: List<UserDto> = emptyList(),
)

@Serializable
data class UpdateProfileRequest(
    val nickname: String? = null,
    val avatar: String? = null,
    val bio: String? = null,
    val signature: String? = null,
    val email: String? = null,
    val username: String? = null,
    val newPassword: String? = null,
    val oldPassword: String? = null,
    val notificationMode: String? = null,
    val notificationEmail: String? = null,
    val notificationPushplusKey: String? = null,
    val notificationWebhookUrl: String? = null,
    val notificationWebhookSecret: String? = null,
    val notificationGroupEnabled: Boolean? = null,
    val notificationPrivateEnabled: Boolean? = null,
    val notificationTemplate: NotificationTemplateDto? = null,
)

@Serializable
data class UploadData(
    val fileId: Long = 0,
    val fileUrl: String = "",
    val fileName: String = "",
    val fileSize: Long = 0,
    val fileType: String = "file",
    val mimeType: String = "",
    val duplicate: Boolean = false,
)

@Serializable
data class HealthData(
    val allOk: Boolean = false,
    val checks: JsonObject = JsonObject(emptyMap()),
    val hint: String? = null,
)

@Serializable
data class QuotaDto(
    val monthlyNetworkFlowMb: Long = 0,
    val diskSpaceMb: Long = 0,
    val maxConnections: Int = 0,
    val maxProcesses: Int = 0,
    val maxUploadMbps: Int = 0,
    val maxDownloadMbps: Int = 0,
)

@Serializable
data class DiskDto(
    val appUsedMb: Double = 0.0,
    val dataKb: Double = 0.0,
    val uploadsKb: Double = 0.0,
    val freeMb: Double = 0.0,
    val totalMb: Double = 0.0,
    val usedPct: Double = 0.0,
)

@Serializable
data class UsageDto(
    val networkFlowMb: Double = 0.0,
    val diskUsedMb: Double = 0.0,
    val activeSessions: Int = 0,
    val totalRequests: Long = 0,
    val month: String = "",
    val lastResetDate: String = "",
)

@Serializable
data class PhpInfoDto(
    val version: String = "",
    val memoryLimit: String = "",
    val postMaxSize: String = "",
    val uploadMaxFilesize: String = "",
    val maxExecutionTime: Int = 0,
)

@Serializable
data class ServerStatusData(
    val quota: QuotaDto = QuotaDto(),
    val disk: DiskDto = DiskDto(),
    val usage: UsageDto = UsageDto(),
    val php: PhpInfoDto = PhpInfoDto(),
)

@Serializable
data class FriendDto(
    val userId: Long = 0,
    val username: String = "",
    val nickname: String = "",
    val avatar: String? = null,
    val role: String = "member",
    val createdAt: String = "",
)

@Serializable
data class FriendRequestDto(
    val id: Long = 0,
    val fromUserId: Long = 0,
    val username: String = "",
    val nickname: String = "",
    val avatar: String? = null,
    val message: String = "",
    val createdAt: String = "",
)

@Serializable
data class BlockedUserDto(
    val userId: Long = 0,
    val username: String = "",
    val nickname: String = "",
    val avatar: String? = null,
    val createdAt: String = "",
)

@Serializable
data class FriendsData(
    val friends: List<FriendDto> = emptyList(),
    val requests: List<FriendRequestDto> = emptyList(),
    val blocked: List<BlockedUserDto> = emptyList(),
    val pendingCount: Int = 0,
)

@Serializable
data class FriendRequestBody(
    val userId: Long? = null,
    val username: String? = null,
    val message: String? = null,
)

@Serializable
data class FriendRequestData(
    val requestId: Long = 0,
    val userId: Long = 0,
    val username: String = "",
    val autoAccepted: Boolean = false,
)

@Serializable
data class RespondFriendRequest(
    val requestId: Long,
    val action: String,
)

@Serializable
data class RespondFriendData(
    val requestId: Long = 0,
    val userId: Long = 0,
    val accepted: Boolean = false,
)

@Serializable
data class RemoveFriendRequest(
    val userId: Long,
)

@Serializable
data class RemoveFriendData(
    val removed: Boolean = false,
    val userId: Long = 0,
)

@Serializable
data class BlockUserRequest(
    val userId: Long,
    val action: String,
)

@Serializable
data class BlockUserData(
    val blocked: Boolean = false,
    val userId: Long = 0,
    val username: String = "",
)

@Serializable
data class SearchMessagesData(
    val messages: List<MessageDto> = emptyList(),
    val hasMore: Boolean = false,
    val query: String = "",
)

@Serializable
data class ForgotPasswordRequest(
    val account: String,
)

@Serializable
data class ForgotPasswordData(
    val sent: Boolean = false,
)

@Serializable
data class ResetPasswordRequest(
    val token: String,
    val password: String,
)

@Serializable
data class ResetPasswordData(
    val reset: Boolean = false,
)

@Serializable
data class VerifyEmailRequest(
    val token: String,
)

@Serializable
data class VerifyEmailData(
    val emailVerified: Boolean = false,
)

@Serializable
data class ResendVerificationData(
    val sent: Boolean = false,
)

@Serializable
data class SessionDto(
    val id: Long = 0,
    val ip: String = "",
    val userAgent: String = "",
    val createdAt: String = "",
    val expiresAt: String = "",
    val absoluteExpiresAt: String = "",
    val current: Boolean = false,
    val expired: Boolean = false,
)

@Serializable
data class SessionsData(
    val sessions: List<SessionDto> = emptyList(),
)

@Serializable
data class SessionActionRequest(
    val action: String,
    val sessionId: Long? = null,
)

@Serializable
data class SessionActionData(
    val revoked: JsonElement? = null,
    val current: Boolean? = null,
)

@Serializable
data class TwoFactorData(
    val enabled: Boolean = false,
    val configured: Boolean = false,
    val secret: String? = null,
    val otpauthUrl: String? = null,
    val issuer: String? = null,
    val account: String? = null,
)

@Serializable
data class TwoFactorActionRequest(
    val action: String,
    val code: String? = null,
    val password: String? = null,
)

@Serializable
data class MemberActionRequest(
    val channelId: Long,
    val userId: Long,
    val action: String,
    val role: String? = null,
    val durationMinutes: Int? = null,
)

@Serializable
data class MemberActionResult(
    val channelId: Long = 0,
    val userId: Long = 0,
    val action: String = "",
    val muted: Boolean? = null,
    val muteUntil: String? = null,
    val role: String? = null,
)
