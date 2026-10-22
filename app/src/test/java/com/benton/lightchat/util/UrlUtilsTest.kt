package com.benton.lightchat.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlUtilsTest {

    @Test
    fun addsSchemeAndApiSuffix() {
        assertEquals("http://192.168.1.10/api", UrlUtils.normalize("192.168.1.10"))
        assertEquals("http://192.168.1.10:8080/api", UrlUtils.normalize("192.168.1.10:8080/"))
    }

    @Test
    fun keepsHttpsAndTrimsTrailingSlash() {
        assertEquals("https://chat.example.com/api", UrlUtils.normalize(" https://chat.example.com/api/ "))
    }

    @Test
    fun appendsApiToExistingPath() {
        assertEquals("https://example.com/lightchat/api", UrlUtils.normalize("https://example.com/lightchat"))
    }

    @Test
    fun rejectsInvalidInput() {
        assertNull(UrlUtils.normalize(""))
        assertNull(UrlUtils.normalize("   "))
        assertNull(UrlUtils.normalize("http://example.com/api?x=1"))
        assertNull(UrlUtils.normalize("http://example.com/api#frag"))
        assertNull(UrlUtils.normalize("http://exa mple.com"))
    }

    @Test
    fun originRemovesApiSegment() {
        assertEquals("http://host:8080", UrlUtils.origin("http://host:8080/api"))
        assertEquals("http://host/lightchat", UrlUtils.origin("http://host/lightchat/api"))
    }

    @Test
    fun resolvesRelativeAndAbsoluteUrls() {
        assertEquals("http://host/uploads/a.jpg", UrlUtils.resolve("http://host/api", "/uploads/a.jpg"))
        assertEquals("http://host/lightchat/uploads/a.jpg", UrlUtils.resolve("http://host/lightchat/api", "uploads/a.jpg"))
        assertEquals("http://host/uploads/a.jpg", UrlUtils.resolve("http://host/lightchat/api", "/uploads/a.jpg"))
        assertEquals("https://cdn.example.com/a.jpg", UrlUtils.resolve("http://host/api", "https://cdn.example.com/a.jpg"))
        assertEquals("content://media/1", UrlUtils.resolve("http://host/api", "content://media/1"))
        assertNull(UrlUtils.resolve("http://host/api", null))
    }
}
