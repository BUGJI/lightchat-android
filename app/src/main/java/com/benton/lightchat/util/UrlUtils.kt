package com.benton.lightchat.util

object UrlUtils {

    private val basePattern =
        Regex("^https?://[^/\\s?#]+(?::\\d+)?(?:/[^\\s?#]*)?/api$", RegexOption.IGNORE_CASE)

    /**
     * Normalizes user input into a server API base URL that always ends with /api.
     * Returns null when the input cannot be interpreted as an http(s) URL.
     */
    fun normalize(input: String): String? {
        var value = input.trim()
        if (value.isEmpty()) return null
        if (!value.startsWith("http://", true) && !value.startsWith("https://", true)) {
            value = "http://$value"
        }
        value = value.replace(Regex("(?<!:)/{2,}"), "/")
        value = value.trimEnd('/')
        if (value.contains('?') || value.contains('#') || value.contains(' ')) return null
        if (!value.endsWith("/api")) value += "/api"
        if (!basePattern.matches(value)) return null
        return value
    }

    fun isValid(input: String): Boolean = basePattern.matches(input.trim())

    fun origin(baseUrl: String): String = baseUrl.removeSuffix("/api").trimEnd('/')

    fun serverRoot(baseUrl: String): String =
        Regex("^(https?://[^/]+)", RegexOption.IGNORE_CASE).find(baseUrl)?.value ?: origin(baseUrl)

    fun resolve(baseUrl: String?, path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://", true) ||
            path.startsWith("https://", true) ||
            path.startsWith("content://", true) ||
            path.startsWith("file://", true)
        ) {
            return path
        }
        val base = baseUrl ?: return path
        return if (path.startsWith("/")) {
            serverRoot(base) + path
        } else {
            origin(base) + "/" + path.trimStart('/')
        }
    }
}
