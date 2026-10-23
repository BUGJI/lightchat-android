package com.benton.lightchat.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalIdTest {

    @Test
    fun next_isNegativeAndUnique() {
        val ids = (1..1_000).map { LocalId.next() }
        assertTrue(ids.all { it < 0 })
        assertEquals(ids.size, ids.toSet().size)
    }
}
