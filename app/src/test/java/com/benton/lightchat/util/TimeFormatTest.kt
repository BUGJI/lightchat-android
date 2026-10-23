package com.benton.lightchat.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class TimeFormatTest {

    private val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        .parse("2026-10-23 12:00:00")!!

    @Test
    fun relativeActive_reportsMinutesHoursAndDays() {
        assertEquals("刚刚活跃", TimeFormat.relativeActive("2026-10-23 11:59:30", now))
        assertEquals("5 分钟前活跃", TimeFormat.relativeActive("2026-10-23 11:55:00", now))
        assertEquals("2 小时前活跃", TimeFormat.relativeActive("2026-10-23 10:00:00", now))
        assertEquals("2026-10-20 活跃", TimeFormat.relativeActive("2026-10-20 10:00:00", now))
        assertEquals("", TimeFormat.relativeActive(null, now))
    }

    @Test
    fun dayLabel_recognizesTodayAndYesterday() {
        assertEquals("今天", TimeFormat.dayLabel("2026-10-23 10:00:00", now))
        assertEquals("昨天", TimeFormat.dayLabel("2026-10-22 23:00:00", now))
        assertEquals("10-20", TimeFormat.dayLabel("2026-10-20 10:00:00", now))
    }
}
