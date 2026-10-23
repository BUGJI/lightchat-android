package com.benton.lightchat.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeFormat {

    private const val SERVER_PATTERN = "yyyy-MM-dd HH:mm:ss"

    fun parse(raw: String?): Date? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            SimpleDateFormat(SERVER_PATTERN, Locale.US).parse(raw)
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
        return SimpleDateFormat(pattern, Locale.getDefault()).format(date)
    }

    fun nowServerString(): String =
        SimpleDateFormat(SERVER_PATTERN, Locale.US).format(Date())

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

            else -> SimpleDateFormat(if (sameYear) "MM-dd" else "yyyy-MM-dd", Locale.getDefault())
                .format(date)
        }
    }

    fun relativeActive(raw: String?, now: Date = Date()): String {
        val date = parse(raw) ?: return ""
        val diffMinutes = ((now.time - date.time) / 60_000L).coerceAtLeast(0)
        return when {
            diffMinutes < 1 -> "刚刚活跃"
            diffMinutes < 60 -> "${diffMinutes} 分钟前活跃"
            diffMinutes < 60 * 24 -> "${diffMinutes / 60} 小时前活跃"
            else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date) + " 活跃"
        }
    }
}
