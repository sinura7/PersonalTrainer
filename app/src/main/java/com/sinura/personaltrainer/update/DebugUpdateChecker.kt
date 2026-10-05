package com.sinura.personaltrainer.update

import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.runCatchingCancellable
import java.io.IOException
import java.net.UnknownHostException

private const val TAG = "PT/DebugUpdate"

/** Re-check while Temper Debug is in use (was 6 h; too stale for live drops). */
internal const val FOREGROUND_TTL_MS = 30L * 60 * 1000
internal const val SETTINGS_TTL_MS = 5L * 60 * 1000
/** First resume after process start always hits GitHub. */
internal const val COLD_START_TTL_MS = 0L

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
        val release = fetchReleaseMetadata() ?: return null
        return releaseToOffer(release)
    }

    private fun fetchReleaseMetadata(): GitHubDebugRelease? {
        val viaApi = runCatching {
            GitHubDebugReleases.newestDebugRelease(http.get(GitHubDebugReleases.RELEASES_URL))
        }.getOrElse { error ->
            if (error.shouldTryAtomFallback()) {
                AppLog.w(TAG, "GitHub API check failed (${error.message}); trying releases.atom")
                null
            } else {
                throw error
            }
        }
        if (viaApi != null) return viaApi
        return GitHubDebugReleases.newestDebugReleaseFromAtom(
            http.get(GitHubDebugReleases.RELEASES_ATOM_URL),
        )
    }

    private fun releaseToOffer(release: GitHubDebugRelease): DebugUpdateOffer? {
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

    private fun Throwable.shouldTryAtomFallback(): Boolean = when (this) {
        is UnknownHostException -> true
        is IOException -> message?.contains("api.github.com", ignoreCase = true) == true ||
            message?.contains("Unable to resolve host", ignoreCase = true) == true ||
            message?.contains("failed to connect", ignoreCase = true) == true
        else -> false
    }
}
