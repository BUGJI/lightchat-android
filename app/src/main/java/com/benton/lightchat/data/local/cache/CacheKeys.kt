package com.benton.lightchat.data.local.cache

object CacheKeys {
    const val PROFILE_SELF = "profile:self"
    const val FRIENDS = "friends"
    const val SESSIONS = "sessions"
    const val TWO_FACTOR = "two_factor"
    const val SERVER_STATUS = "server_status"
    const val DISCOVER_USERS = "discover_users"
    const val DISCOVER_CHANNELS = "discover_channels"

    fun profile(userId: Long): String = "profile:$userId"
    fun channelInfo(channelId: Long): String = "channel_info:$channelId"
}
