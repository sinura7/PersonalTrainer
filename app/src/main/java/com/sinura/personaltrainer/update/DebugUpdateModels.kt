package com.sinura.personaltrainer.update

/**
 * A newer Temper Debug drop on GitHub, ready to open — not to silent-install.
 */
data class DebugUpdateOffer(
    val versionCode: Int,
    val tag: String,
    val releaseUrl: String,
    val apkUrl: String,
)

data class DebugUpdateUi(
    val offer: DebugUpdateOffer? = null,
    val showBanner: Boolean = false,
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
