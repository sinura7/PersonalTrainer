package com.sinura.personaltrainer.update

/**
 * A newer Temper Debug drop on GitHub. The tap path downloads the APK and
 * hands it to Android's installer — it does not silent-install, and it does
 * not open the GitHub webpage.
 */
data class DebugUpdateOffer(
    val versionCode: Int,
    val tag: String,
    val releaseUrl: String,
    val apkUrl: String,
)

enum class DebugUpdateInstall {
    Idle,
    NeedsPermission,
    Downloading,
    Installing,
    Failed,
}

data class DebugUpdateUi(
    val offer: DebugUpdateOffer? = null,
    val showBanner: Boolean = false,
    val install: DebugUpdateInstall = DebugUpdateInstall.Idle,
    val downloadPercent: Int? = null,
)

internal data class CachedDebugCheck(
    val checkedAtMillis: Long,
    val offer: DebugUpdateOffer?,
)

internal interface DebugUpdateCache {
    suspend fun load(): CachedDebugCheck?
    suspend fun save(check: CachedDebugCheck)
    suspend fun dismissedVersionCode(): Int
    suspend fun dismiss(versionCode: Int)
}

internal class MemoryDebugUpdateCache : DebugUpdateCache {
    var stored: CachedDebugCheck? = null
    var dismissed: Int = 0

    override suspend fun load(): CachedDebugCheck? = stored

    override suspend fun save(check: CachedDebugCheck) {
        stored = check
    }

    override suspend fun dismissedVersionCode(): Int = dismissed

    override suspend fun dismiss(versionCode: Int) {
        dismissed = versionCode
    }
}
