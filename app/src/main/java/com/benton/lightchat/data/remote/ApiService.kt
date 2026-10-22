package com.benton.lightchat.data.remote

import kotlinx.serialization.json.JsonElement
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface ApiService {

    @GET("health.php")
    suspend fun health(): Response<ApiEnvelope<HealthData>>

    @GET("server/status.php")
    suspend fun serverStatus(): Response<ApiEnvelope<ServerStatusData>>

    @POST("token/login.php")
    suspend fun login(@Body body: LoginRequest): Response<ApiEnvelope<AccountData>>

    @POST("token/register.php")
    suspend fun register(@Body body: RegisterRequest): Response<ApiEnvelope<AccountData>>

    @POST("token/refresh.php")
    suspend fun refresh(): Response<ApiEnvelope<RefreshData>>

    @POST("token/logout.php")
    suspend fun logout(): Response<ApiEnvelope<EmptyData>>

    @GET("sync.php")
    suspend fun sync(
        @Query("channels") channels: String? = null,
        @Query("since_id") sinceId: Long? = null,
        @Query("private_chat_id") privateChatId: Long? = null,
        @Query("private_since_id") privateSinceId: Long? = null,
        @Query("recall_since_id") recallSinceId: Long? = null,
        @Query("lists") lists: Int = 1,
    ): Response<ApiEnvelope<SyncData>>

    @GET("channels/list.php")
    suspend fun channels(): Response<ApiEnvelope<ChannelsData>>

    @POST("channels/create.php")
    suspend fun createChannel(@Body body: CreateChannelRequest): Response<ApiEnvelope<ChannelCreateData>>

    @POST("channels/join.php")
    suspend fun joinChannel(@Body body: ChannelIdRequest): Response<ApiEnvelope<EmptyData>>

    @POST("channels/leave.php")
    suspend fun leaveChannel(@Body body: ChannelIdRequest): Response<ApiEnvelope<EmptyData>>

    @POST("channels/read.php")
    suspend fun markChannelRead(@Body body: ChannelIdRequest): Response<ApiEnvelope<EmptyData>>

    @GET("messages/history.php")
    suspend fun channelHistory(
        @Query("channel_id") channelId: Long,
        @Query("before") before: Long? = null,
        @Query("limit") limit: Int = 100,
    ): Response<ApiEnvelope<HistoryData>>

    @POST("messages/send.php")
    suspend fun sendChannelMessage(@Body body: SendMessageRequest): Response<ApiEnvelope<SendResultData>>

    @POST("messages/recall.php")
    suspend fun recallMessage(@Body body: RecallRequest): Response<ApiEnvelope<RecallResultData>>

    @POST("messages/delete.php")
    suspend fun deleteMessage(@Body body: DeleteMessageRequest): Response<ApiEnvelope<EmptyData>>

    @GET("private/list.php")
    suspend fun privateChats(): Response<ApiEnvelope<PrivateChatsData>>

    @GET("private/history.php")
    suspend fun privateHistory(
        @Query("chat_id") chatId: Long,
        @Query("before") before: Long? = null,
        @Query("limit") limit: Int = 100,
    ): Response<ApiEnvelope<HistoryData>>

    @POST("private/send.php")
    suspend fun sendPrivateMessage(@Body body: SendMessageRequest): Response<ApiEnvelope<SendResultData>>

    @POST("private/create.php")
    suspend fun createPrivateChat(@Body body: CreatePrivateRequest): Response<ApiEnvelope<CreatePrivateData>>

    @GET("private/contact.php")
    suspend fun privateContact(@Query("chat_id") chatId: Long): Response<ApiEnvelope<PrivateContactData>>

    @POST("private/contact.php")
    suspend fun updatePrivateContact(
        @Body body: UpdatePrivateContactRequest,
    ): Response<ApiEnvelope<EmptyData>>

    @GET("channels/members.php")
    suspend fun channelMembers(
        @Query("channel_id") channelId: Long,
    ): Response<ApiEnvelope<ChannelMembersData>>

    @POST("channels/invite.php")
    suspend fun inviteChannel(@Body body: InviteChannelRequest): Response<ApiEnvelope<EmptyData>>

    @POST("channels/update.php")
    suspend fun updateChannel(@Body body: UpdateChannelRequest): Response<ApiEnvelope<EmptyData>>

    @POST("channels/delete.php")
    suspend fun deleteChannel(@Body body: DeleteChannelRequest): Response<ApiEnvelope<JsonElement>>

    @GET("users/profile.php")
    suspend fun profile(@Query("user_id") userId: Long? = null): Response<ApiEnvelope<ProfileData>>

    @POST("users/profile.php")
    suspend fun updateProfile(@Body body: UpdateProfileRequest): Response<ApiEnvelope<EmptyData>>

    @POST("users/test_notification.php")
    suspend fun testNotification(): Response<ApiEnvelope<TestNotificationData>>

    @GET("users/list.php")
    suspend fun users(): Response<ApiEnvelope<UsersData>>

    @GET("users/search.php")
    suspend fun searchUsers(
        @Query("q") query: String,
        @Query("limit") limit: Int = 30,
    ): Response<ApiEnvelope<UsersData>>

    @Multipart
    @POST("files/upload.php")
    suspend fun uploadFile(@Part file: MultipartBody.Part): Response<ApiEnvelope<UploadData>>

    @GET("friends/list.php")
    suspend fun friends(): Response<ApiEnvelope<FriendsData>>

    @POST("friends/request.php")
    suspend fun sendFriendRequest(
        @Body body: FriendRequestBody,
    ): Response<ApiEnvelope<FriendRequestData>>

    @POST("friends/respond.php")
    suspend fun respondFriendRequest(
        @Body body: RespondFriendRequest,
    ): Response<ApiEnvelope<RespondFriendData>>

    @POST("friends/remove.php")
    suspend fun removeFriend(
        @Body body: RemoveFriendRequest,
    ): Response<ApiEnvelope<RemoveFriendData>>

    @POST("friends/block.php")
    suspend fun blockUser(
        @Body body: BlockUserRequest,
    ): Response<ApiEnvelope<BlockUserData>>

    @GET("messages/search.php")
    suspend fun searchChannelMessages(
        @Query("channel_id") channelId: Long,
        @Query("q") query: String,
        @Query("before") before: Long? = null,
        @Query("limit") limit: Int = 30,
    ): Response<ApiEnvelope<SearchMessagesData>>

    @GET("private/search.php")
    suspend fun searchPrivateMessages(
        @Query("chat_id") chatId: Long,
        @Query("q") query: String,
        @Query("before") before: Long? = null,
        @Query("limit") limit: Int = 30,
    ): Response<ApiEnvelope<SearchMessagesData>>

    @POST("token/forgot.php")
    suspend fun forgotPassword(
        @Body body: ForgotPasswordRequest,
    ): Response<ApiEnvelope<ForgotPasswordData>>

    @POST("token/reset.php")
    suspend fun resetPassword(
        @Body body: ResetPasswordRequest,
    ): Response<ApiEnvelope<ResetPasswordData>>

    @POST("token/verify_email.php")
    suspend fun verifyEmail(
        @Body body: VerifyEmailRequest,
    ): Response<ApiEnvelope<VerifyEmailData>>

    @POST("token/resend_verification.php")
    suspend fun resendVerification(): Response<ApiEnvelope<ResendVerificationData>>

    @POST("channels/member_action.php")
    suspend fun memberAction(
        @Body body: MemberActionRequest,
    ): Response<ApiEnvelope<MemberActionResult>>

    @GET("users/sessions.php")
    suspend fun sessions(): Response<ApiEnvelope<SessionsData>>

    @POST("users/sessions.php")
    suspend fun sessionAction(
        @Body body: SessionActionRequest,
    ): Response<ApiEnvelope<SessionActionData>>

    @GET("users/two_factor.php")
    suspend fun twoFactorStatus(): Response<ApiEnvelope<TwoFactorData>>

    @POST("users/two_factor.php")
    suspend fun twoFactorAction(
        @Body body: TwoFactorActionRequest,
    ): Response<ApiEnvelope<TwoFactorData>>
}
