package com.benton.lightchat.data.local

object AccountKey {

    fun of(baseUrl: String?, userId: Long): String? {
        val normalized = baseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: return null
        return "$normalized#$userId"
    }
}
