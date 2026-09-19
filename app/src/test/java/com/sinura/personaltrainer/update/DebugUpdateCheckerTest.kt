package com.sinura.personaltrainer.update

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugUpdateCheckerTest {
    @Test
    fun higherVersionCodePrompts() = runBlocking {
        val http = ScriptedHttp(
            GitHubDebugReleases.RELEASES_URL to { debugReleasesJson(tag = "debug-live-2026-09-13-2") },
            GitHubDebugReleases.gradleUrl("debug-live-2026-09-13-2") to { "val debugLiveCode = 51\n" },
        )
        val offer = checker(http, installed = 50).check(minIntervalMs = 0)
        assertEquals(51, offer!!.versionCode)
        assertEquals("debug-live-2026-09-13-2", offer.tag)
        assertEquals(2, http.urls.size)
    }

    @Test
    fun sameVersionDoesNotPrompt() = runBlocking {
        val http = ScriptedHttp(
            GitHubDebugReleases.RELEASES_URL to { debugReleasesJson(tag = "debug-live-2026-09-13-2") },
            GitHubDebugReleases.gradleUrl("debug-live-2026-09-13-2") to { "val debugLiveCode = 50\n" },
        )
        assertNull(checker(http, installed = 50).check(minIntervalMs = 0))
    }

    @Test
    fun gymFloorNeverHitsGitHub() = runBlocking {
        val http = ScriptedHttp(
            GitHubDebugReleases.RELEASES_URL to { error("gym-floor must not fetch") },
        )
        assertNull(
            checker(http, installed = 1, enabled = false).check(minIntervalMs = 0),
        )
        assertTrue(http.urls.isEmpty())
    }

    @Test
    fun httpErrorAndMalformedJsonDoNotCrash() = runBlocking {
        val failing = ScriptedHttp(
            GitHubDebugReleases.RELEASES_URL to { throw IOException("GitHub GET 500") },
        )
        assertNull(checker(failing, installed = 50).check(minIntervalMs = 0))

        val malformed = ScriptedHttp(
            GitHubDebugReleases.RELEASES_URL to { "{not-json" },
        )
        assertNull(checker(malformed, installed = 50).check(minIntervalMs = 0))
    }

    @Test
    fun cacheSkipsASecondFetchInsideTheTtl() = runBlocking {
        val http = ScriptedHttp(
            GitHubDebugReleases.RELEASES_URL to { debugReleasesJson(tag = "debug-live-2026-09-13-2") },
            GitHubDebugReleases.gradleUrl("debug-live-2026-09-13-2") to { "val debugLiveCode = 51\n" },
        )
        var now = 1_000L
        val cache = MemoryDebugUpdateCache()
        val unit = DebugUpdateChecker(
            http = http,
            cache = cache,
            enabled = true,
            installedVersionCode = 50,
            nowMillis = { now },
            isOnline = { true },
        )
        assertEquals(51, unit.check(minIntervalMs = FOREGROUND_TTL_MS)!!.versionCode)
        val afterFirst = http.urls.size
        now += 60_000L
        assertEquals(51, unit.check(minIntervalMs = FOREGROUND_TTL_MS)!!.versionCode)
        assertEquals(afterFirst, http.urls.size)
    }

    @Test
    fun assetNameVersionSkipsTheGradleFetch() = runBlocking {
        val http = ScriptedHttp(
            GitHubDebugReleases.RELEASES_URL to {
                debugReleasesJson(
                    tag = "debug-live-2026-09-13-3",
                    apkName = "PersonalTrainer-1.0.0+debug.52-debug.apk",
                )
            },
        )
        val offer = checker(http, installed = 50).check(minIntervalMs = 0)
        assertEquals(52, offer!!.versionCode)
        assertEquals(1, http.urls.size)
    }

    @Test
    fun offlineUsesTheCachedOfferAndDoesNotFetch() = runBlocking {
        val cache = MemoryDebugUpdateCache()
        cache.save(
            CachedDebugCheck(
                checkedAtMillis = 0L,
                offer = DebugUpdateOffer(
                    versionCode = 51,
                    tag = "debug-live-2026-09-13-2",
                    releaseUrl = "https://github.com/sinura7/PersonalTrainer/releases/tag/debug-live-2026-09-13-2",
                    apkUrl = "https://github.com/sinura7/PersonalTrainer/releases/download/debug-live-2026-09-13-2/PersonalTrainer-1.0.0-debug.apk",
                ),
            ),
        )
        val http = ScriptedHttp(
            GitHubDebugReleases.RELEASES_URL to { error("offline must not fetch") },
        )
        val offer = DebugUpdateChecker(
            http = http,
            cache = cache,
            enabled = true,
            installedVersionCode = 50,
            nowMillis = { FOREGROUND_TTL_MS + 1 },
            isOnline = { false },
        ).check(minIntervalMs = 0)
        assertEquals(51, offer!!.versionCode)
        assertTrue(http.urls.isEmpty())
    }

    private fun checker(
        http: DebugUpdateHttp,
        installed: Int,
        enabled: Boolean = true,
    ): DebugUpdateChecker = DebugUpdateChecker(
        http = http,
        cache = MemoryDebugUpdateCache(),
        enabled = enabled,
        installedVersionCode = installed,
        nowMillis = { 0L },
        isOnline = { true },
    )

    private fun debugReleasesJson(
        tag: String,
        apkName: String = "PersonalTrainer-1.0.0-debug.apk",
    ): String = """
        [{
          "tag_name": "$tag",
          "prerelease": true,
          "draft": false,
          "published_at": "2026-09-13T02:55:11Z",
          "html_url": "https://github.com/sinura7/PersonalTrainer/releases/tag/$tag",
          "assets": [{
            "name": "$apkName",
            "browser_download_url": "https://github.com/sinura7/PersonalTrainer/releases/download/$tag/$apkName"
          }]
        }]
    """.trimIndent()

    private class ScriptedHttp(
        vararg routes: Pair<String, () -> String>,
    ) : DebugUpdateHttp {
        val urls = mutableListOf<String>()
        private val map = routes.toMap()

        override fun get(url: String): String {
            urls += url
            val handler = map[url] ?: throw IOException("unexpected $url")
            return handler()
        }
    }
}
