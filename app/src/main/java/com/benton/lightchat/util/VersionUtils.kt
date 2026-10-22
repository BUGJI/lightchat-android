package com.benton.lightchat.util

import kotlin.math.max

object VersionUtils {

    fun normalize(version: String?): String =
        version.orEmpty().trim().removePrefix("v").removePrefix("V")

    fun compare(left: String?, right: String?): Int {
        val leftParts = numericParts(left)
        val rightParts = numericParts(right)
        val size = max(leftParts.size, rightParts.size)
        for (index in 0 until size) {
            val result = leftParts.getOrElse(index) { 0 }.compareTo(rightParts.getOrElse(index) { 0 })
            if (result != 0) return result
        }
        return 0
    }

    fun isNewer(latest: String?, current: String?): Boolean = compare(latest, current) > 0

    private fun numericParts(version: String?): List<Int> =
        normalize(version)
            .substringBefore('-')
            .substringBefore('+')
            .split('.')
            .map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
}
