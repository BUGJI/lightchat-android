package com.benton.lightchat.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateModelsTest {

    @Test
    fun mapsReleaseAndSelectsApkAsset() {
        val dto = GitHubReleaseDto(
            tagName = "v1.2.3",
            body = "release notes",
            htmlUrl = "https://github.com/BUGJI/lightchat-android/releases/tag/v1.2.3",
            assets = listOf(
                GitHubAssetDto(
                    name = "checksums.txt",
                    downloadUrl = "https://example.com/checksums.txt",
                    size = 10,
                ),
                GitHubAssetDto(
                    name = "lightchat-1.2.3.apk",
                    downloadUrl = "https://example.com/lightchat-1.2.3.apk",
                    size = 100,
                ),
            ),
        )

        val release = dto.toLatestRelease()

        assertEquals("1.2.3", release?.version)
        assertEquals("release notes", release?.notes)
        assertEquals("https://example.com/lightchat-1.2.3.apk", release?.apkUrl)
        assertEquals(100L, release?.apkSize)
    }

    @Test
    fun rejectsInsecureAssetUrl() {
        val dto = GitHubReleaseDto(
            tagName = "1.0.1",
            htmlUrl = "https://github.com/BUGJI/lightchat-android/releases/tag/v1.0.1",
            assets = listOf(
                GitHubAssetDto(
                    name = "lightchat-1.0.1.apk",
                    downloadUrl = "http://example.com/lightchat-1.0.1.apk",
                ),
            ),
        )

        assertNull(dto.toLatestRelease()?.apkUrl)
    }

    @Test
    fun rejectsReleaseWithoutTagOrUrl() {
        assertNull(GitHubReleaseDto(htmlUrl = "https://example.com").toLatestRelease())
        assertNull(GitHubReleaseDto(tagName = "v1.0.0").toLatestRelease())
    }
}
