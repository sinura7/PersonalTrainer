package com.sinura.personaltrainer.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubDebugReleasesTest {
    @Test
    fun newestPreReleaseWithDebugApkWinsAndGymFloorIsIgnored() {
        val body = """
            [
              {
                "tag_name": "v1.0.0",
                "prerelease": false,
                "draft": false,
                "published_at": "2026-09-13T03:00:00Z",
                "html_url": "https://github.com/sinura7/PersonalTrainer/releases/tag/v1.0.0",
                "assets": [
                  {
                    "name": "PersonalTrainer-1.0.0.apk",
                    "browser_download_url": "https://github.com/sinura7/PersonalTrainer/releases/download/v1.0.0/PersonalTrainer-1.0.0.apk"
                  }
                ]
              },
              {
                "tag_name": "debug-live-2026-09-13-2",
                "prerelease": true,
                "draft": false,
                "published_at": "2026-09-13T02:55:11Z",
                "html_url": "https://github.com/sinura7/PersonalTrainer/releases/tag/debug-live-2026-09-13-2",
                "assets": [
                  {
                    "name": "PersonalTrainer-1.0.0-debug.apk",
                    "browser_download_url": "https://github.com/sinura7/PersonalTrainer/releases/download/debug-live-2026-09-13-2/PersonalTrainer-1.0.0-debug.apk"
                  }
                ]
              },
              {
                "tag_name": "debug-live-2026-09-12-3",
                "prerelease": true,
                "draft": false,
                "published_at": "2026-09-12T20:00:00Z",
                "html_url": "https://github.com/sinura7/PersonalTrainer/releases/tag/debug-live-2026-09-12-3",
                "assets": [
                  {
                    "name": "PersonalTrainer-1.0.0+debug.49-debug.apk",
                    "browser_download_url": "https://github.com/sinura7/PersonalTrainer/releases/download/debug-live-2026-09-12-3/PersonalTrainer-1.0.0+debug.49-debug.apk"
                  }
                ]
              }
            ]
        """.trimIndent()
        val release = GitHubDebugReleases.newestDebugRelease(body)!!
        assertEquals("debug-live-2026-09-13-2", release.tag)
        assertEquals(
            "https://github.com/sinura7/PersonalTrainer/releases/download/debug-live-2026-09-13-2/PersonalTrainer-1.0.0-debug.apk",
            release.apkUrl,
        )
        assertNull(release.assetVersionCode)
    }

    @Test
    fun assetNameCanCarryTheLiveNumber() {
        assertEquals(
            51,
            GitHubDebugReleases.versionCodeFromAssetName("PersonalTrainer-1.0.0+debug.51-debug.apk"),
        )
        assertNull(GitHubDebugReleases.versionCodeFromAssetName("PersonalTrainer-1.0.0-debug.apk"))
    }

    @Test
    fun gradleTreeCarriesDebugLiveCode() {
        assertEquals(50, GitHubDebugReleases.debugLiveCode("val debugLiveCode = 50\n"))
        assertNull(GitHubDebugReleases.debugLiveCode("val appVersionCode = 1\n"))
    }

    @Test
    fun draftsAndMalformedJsonAreNoOffer() {
        val draft = """
            [{
              "tag_name": "debug-live-2026-09-13-9",
              "prerelease": true,
              "draft": true,
              "published_at": "2026-09-13T04:00:00Z",
              "html_url": "https://example.test/draft",
              "assets": [{
                "name": "PersonalTrainer-1.0.0-debug.apk",
                "browser_download_url": "https://github.com/sinura7/PersonalTrainer/releases/download/debug-live-2026-09-13-9/PersonalTrainer-1.0.0-debug.apk"
              }]
            }]
        """.trimIndent()
        assertNull(GitHubDebugReleases.newestDebugRelease(draft))
        assertNull(GitHubDebugReleases.newestDebugRelease("{not an array}"))
        assertNull(GitHubDebugReleases.newestDebugRelease("not json"))
        assertNull(GitHubDebugReleases.newestDebugRelease(""))
    }

    @Test
    fun onlyGithubDebugApkDownloadUrlsAreAllowed() {
        val ok = "https://github.com/sinura7/PersonalTrainer/releases/download/debug-live-2026-09-14-4/PersonalTrainer-1.0.0-debug.apk"
        assertEquals(true, GitHubDebugReleases.isAllowedApkUrl(ok))
        assertEquals(
            true,
            GitHubDebugReleases.isAllowedApkUrl(
                "https://github.com/sinura7/PersonalTrainer/releases/download/debug-live-2026-09-13-3/PersonalTrainer-1.0.0+debug.52-debug.apk",
            ),
        )
        assertEquals(
            false,
            GitHubDebugReleases.isAllowedApkUrl("https://example.test/PersonalTrainer-1.0.0-debug.apk"),
        )
        assertEquals(
            false,
            GitHubDebugReleases.isAllowedApkUrl(
                "https://github.com/sinura7/PersonalTrainer/releases/download/v1.0.0/PersonalTrainer-1.0.0.apk",
            ),
        )
        assertEquals(
            false,
            GitHubDebugReleases.isAllowedApkUrl("http://github.com/sinura7/PersonalTrainer/releases/download/t/PersonalTrainer-1.0.0-debug.apk"),
        )
    }

    @Test
    fun aDebugApkOnAnotherHostIsNotAnOffer() {
        val body = """
            [{
              "tag_name": "debug-live-2026-09-13-2",
              "prerelease": true,
              "draft": false,
              "published_at": "2026-09-13T02:55:11Z",
              "html_url": "https://github.com/sinura7/PersonalTrainer/releases/tag/debug-live-2026-09-13-2",
              "assets": [{
                "name": "PersonalTrainer-1.0.0-debug.apk",
                "browser_download_url": "https://evil.example/PersonalTrainer-1.0.0-debug.apk"
              }]
            }]
        """.trimIndent()
        assertNull(GitHubDebugReleases.newestDebugRelease(body))
    }
}
