package com.benton.lightchat.data.remote

import com.benton.lightchat.util.VersionUtils
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val assets: List<GitHubAssetDto> = emptyList(),
)

@Serializable
data class GitHubAssetDto(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    val size: Long = 0,
)

data class LatestRelease(
    val version: String,
    val tagName: String,
    val releaseUrl: String,
    val notes: String,
    val apkUrl: String?,
    val apkSize: Long,
)

fun GitHubReleaseDto.toLatestRelease(): LatestRelease? {
    val version = VersionUtils.normalize(tagName)
    if (version.isBlank() || htmlUrl.isBlank()) return null
    val apk = assets
        .filter { it.name.endsWith(".apk", ignoreCase = true) }
        .filter { it.downloadUrl.startsWith("https://", ignoreCase = true) }
        .maxByOrNull { asset ->
            var score = 0
            if (asset.name.contains(version, ignoreCase = true)) score += 2
            if (asset.downloadUrl.isNotBlank()) score += 1
            score
        }
    return LatestRelease(
        version = version,
        tagName = tagName,
        releaseUrl = htmlUrl,
        notes = body.orEmpty(),
        apkUrl = apk?.downloadUrl,
        apkSize = apk?.size ?: 0L,
    )
}
