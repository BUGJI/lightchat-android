package com.benton.lightchat.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeFormat {

    private const val SERVER_PATTERN = "yyyy-MM-dd HH:mm:ss"

    private val formats: ThreadLocal<HashMap<String, SimpleDateFormat>> =
        ThreadLocal.withInitial { HashMap() }

    private fun formatter(pattern: String, locale: Locale = Locale.getDefault()): SimpleDateFormat {
        val key = "${locale.toLanguageTag()}|$pattern"
        val cache = formats.get() ?: HashMap<String, SimpleDateFormat>().also { formats.set(it) }
        return cache.getOrPut(key) { SimpleDateFormat(pattern, locale) }
    }

    fun parse(raw: String?): Date? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            formatter(SERVER_PATTERN, Locale.US).parse(raw)
        }.getOrNull()
    }

    fun chatTimestamp(raw: String?, now: Date = Date()): String {
        val date = parse(raw) ?: return raw.orEmpty()
        val today = Calendar.getInstance().apply { time = now }
        val target = Calendar.getInstance().apply { time = date }
        val sameDay = today.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            today.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
        val pattern = when {
            sameDay -> "HH:mm"
            today.get(Calendar.YEAR) == target.get(Calendar.YEAR) -> "MM-dd HH:mm"
            else -> "yyyy-MM-dd"
        }
        return formatter(pattern).format(date)
    }

    fun nowServerString(): String =
        formatter(SERVER_PATTERN, Locale.US).format(Date())

    fun dayLabel(raw: String?, now: Date = Date()): String {
        val date = parse(raw) ?: return raw?.take(10).orEmpty()
        val today = Calendar.getInstance().apply { time = now }
        val target = Calendar.getInstance().apply { time = date }
        val sameYear = today.get(Calendar.YEAR) == target.get(Calendar.YEAR)
        val yesterday = Calendar.getInstance().apply {
            time = now
            add(Calendar.DAY_OF_YEAR, -1)
        }
        return when {
            sameYear && today.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR) -> "今天"
            sameYear && yesterday.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                yesterday.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR) -> "昨天"

            else -> formatter(if (sameYear) "MM-dd" else "yyyy-MM-dd").format(date)
        }
    }

    fun relativeActive(raw: String?, now: Date = Date()): String {
        val date = parse(raw) ?: return ""
        val diffMinutes = ((now.time - date.time) / 60_000L).coerceAtLeast(0)
        return when {
            diffMinutes < 1 -> "刚刚活跃"
            diffMinutes < 60 -> "${diffMinutes} 分钟前活跃"
            diffMinutes < 60 * 24 -> "${diffMinutes / 60} 小时前活跃"
            else -> formatter("yyyy-MM-dd").format(date) + " 活跃"
        }
    }
}
