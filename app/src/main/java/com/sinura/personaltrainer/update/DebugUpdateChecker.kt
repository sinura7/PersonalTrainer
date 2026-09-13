package com.sinura.personaltrainer.update

import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.runCatchingCancellable

private const val TAG = "PT/DebugUpdate"

internal const val FOREGROUND_TTL_MS = 6L * 60 * 60 * 1000
internal const val SETTINGS_TTL_MS = 15L * 60 * 1000

/**
 * Fetches GitHub for a newer Temper Debug APK and decides whether to prompt.
 *
 * Fail-open: no network, a bad payload, or GitHub down means no prompt. Never
 * throws into the UI. [enabled] is false on gym-floor, so that path never
 * talks to GitHub.
 */
internal class DebugUpdateChecker(
    private val http: DebugUpdateHttp,
    private val cache: DebugUpdateCache,
    private val enabled: Boolean,
    private val installedVersionCode: Int,
    private val nowMillis: () -> Long,
    private val isOnline: () -> Boolean = { true },
) {
    suspend fun check(minIntervalMs: Long): DebugUpdateOffer? {
        if (!enabled) return null
        val now = nowMillis()
        val cached = runCatchingCancellable { cache.load() }.getOrNull()
        if (cached != null && now - cached.checkedAtMillis < minIntervalMs) {
            return newerThanInstalled(cached.offer)
        }
        if (!isOnline()) {
            return newerThanInstalled(cached?.offer)
        }
        val fetched = runCatchingCancellable { fetchLatest() }.getOrElse { error ->
            AppLog.w(TAG, "Debug update check failed", error)
            return newerThanInstalled(cached?.offer)
        }
        runCatchingCancellable {
            cache.save(CachedDebugCheck(checkedAtMillis = now, offer = fetched))
        }.onFailure { error ->
            AppLog.w(TAG, "Caching the debug update check failed", error)
        }
        return newerThanInstalled(fetched)
    }

    private fun newerThanInstalled(offer: DebugUpdateOffer?): DebugUpdateOffer? {
        if (offer == null) return null
        return if (offer.versionCode > installedVersionCode) offer else null
    }

    private fun fetchLatest(): DebugUpdateOffer? {
        val release = GitHubDebugReleases.newestDebugRelease(
            http.get(GitHubDebugReleases.RELEASES_URL),
        ) ?: return null
        val versionCode = release.assetVersionCode
            ?: if (GitHubDebugReleases.isSafeTag(release.tag)) {
                GitHubDebugReleases.debugLiveCode(http.get(GitHubDebugReleases.gradleUrl(release.tag)))
            } else {
                null
            }
            ?: return null
        return DebugUpdateOffer(
            versionCode = versionCode,
            tag = release.tag,
            releaseUrl = release.releaseUrl,
            apkUrl = release.apkUrl,
        )
    }
}
