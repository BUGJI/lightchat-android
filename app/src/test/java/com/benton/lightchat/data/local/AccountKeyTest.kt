package com.benton.lightchat.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountKeyTest {

    @Test
    fun normalizesServerAndUser() {
        assertEquals(
            "http://example.com/api#42",
            AccountKey.of("  http://example.com/api/  ", 42),
        )
    }

    @Test
    fun returnsNullWithoutServer() {
        assertNull(AccountKey.of(null, 1))
        assertNull(AccountKey.of("   ", 1))
    }

    @Test
    fun separatesAccountsByServer() {
        val first = AccountKey.of("http://a.example/api", 1)
        val second = AccountKey.of("http://b.example/api", 1)
        assertEquals(false, first == second)
    }
}
