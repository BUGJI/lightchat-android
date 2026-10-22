package com.benton.lightchat.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionUtilsTest {

    @Test
    fun comparesNumericSegments() {
        assertTrue(VersionUtils.isNewer("1.2.4", "1.2.3"))
        assertFalse(VersionUtils.isNewer("1.2.3", "1.2.3"))
        assertFalse(VersionUtils.isNewer("1.2.2", "1.2.3"))
    }

    @Test
    fun handlesTagPrefixAndMissingSegments() {
        assertTrue(VersionUtils.isNewer("v1.0.1", "1.0"))
        assertFalse(VersionUtils.isNewer("v1.0", "1.0.0"))
        assertTrue(VersionUtils.isNewer("V2.0", "1.9.9"))
    }

    @Test
    fun comparesMultiDigitSegmentsNumerically() {
        assertTrue(VersionUtils.isNewer("1.10.0", "1.9.0"))
        assertTrue(VersionUtils.isNewer("10.0.0", "9.9.9"))
    }

    @Test
    fun ignoresSuffixesAndSpaces() {
        assertEquals(0, VersionUtils.compare(" 1.2.3 ", "v1.2.3"))
        assertEquals(0, VersionUtils.compare("1.2.3-beta", "1.2.3"))
        assertTrue(VersionUtils.isNewer("1.2.3.1", "1.2.3"))
    }

    @Test
    fun treatsInvalidPartsAsZero() {
        assertEquals(0, VersionUtils.compare("abc", "0"))
        assertFalse(VersionUtils.isNewer("abc", "0.0.1"))
    }
}
