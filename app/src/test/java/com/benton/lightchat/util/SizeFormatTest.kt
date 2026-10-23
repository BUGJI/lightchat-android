package com.benton.lightchat.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SizeFormatTest {

    @Test
    fun formatsBytes() {
        assertEquals("0 B", SizeFormat.format(0))
        assertEquals("512 B", SizeFormat.format(512))
        assertTrue(SizeFormat.format(1024).endsWith("KB"))
        assertTrue(SizeFormat.format(1024L * 1024).endsWith("MB"))
        assertTrue(SizeFormat.format(1024L * 1024 * 1024).endsWith("GB"))
    }
}
