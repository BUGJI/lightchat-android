package com.benton.lightchat.data.local.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CacheKeysTest {

    @Test
    fun namespacesProfilesPerUser() {
        assertNotEquals(CacheKeys.PROFILE_SELF, CacheKeys.profile(7))
        assertNotEquals(CacheKeys.profile(7), CacheKeys.profile(8))
    }

    @Test
    fun namespacesChannelInfoPerChannel() {
        assertEquals("channel_info:42", CacheKeys.channelInfo(42))
        assertNotEquals(CacheKeys.channelInfo(42), CacheKeys.channelInfo(43))
    }
}
