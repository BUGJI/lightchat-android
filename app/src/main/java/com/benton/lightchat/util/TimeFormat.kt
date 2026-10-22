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
}
