package com.sinura.personaltrainer.update

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * The GitHub Releases payload Temper Debug understands.
 *
 * Newest **pre-release** that carries `PersonalTrainer-*-debug.apk`. Gym-floor
 * `v*` releases and drafts are ignored. versionCode is not in that JSON —
 * current drops name the APK `PersonalTrainer-1.0.0-debug.apk` — so the checker
 * reads `debugLiveCode` from the tagged tree when the asset name does not
 * carry `+debug.N`.
 */
internal data class GitHubDebugRelease(
    val tag: String,
    val releaseUrl: String,
    val apkUrl: String,
    val assetVersionCode: Int?,
)

internal object GitHubDebugReleases {
    const val RELEASES_URL =
        "https://api.github.com/repos/sinura7/PersonalTrainer/releases?per_page=30"

    private val DEBUG_APK = Regex("""^PersonalTrainer-.+-debug\.apk$""", RegexOption.IGNORE_CASE)
    private val ASSET_CODE = Regex("""\+debug\.(\d+)""")
    private val GRADLE_CODE = Regex("""^val debugLiveCode = (\d+)\s*$""", RegexOption.MULTILINE)
    private val SAFE_TAG = Regex("""^[A-Za-z0-9._-]+$""")

    fun gradleUrl(tag: String): String =
        "https://raw.githubusercontent.com/sinura7/PersonalTrainer/$tag/app/build.gradle.kts"

    fun isSafeTag(tag: String): Boolean = SAFE_TAG.matches(tag)

    fun versionCodeFromAssetName(name: String): Int? =
        ASSET_CODE.find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()

    fun debugLiveCode(gradle: String): Int? =
        GRADLE_CODE.find(gradle)?.groupValues?.getOrNull(1)?.toIntOrNull()

    fun newestDebugRelease(body: String): GitHubDebugRelease? {
        val array = try {
            val parsed = JsonParser.parseString(body)
            if (!parsed.isJsonArray) return null
            parsed.asJsonArray
        } catch (_: Exception) {
            return null
        }
        var best: Pair<String, GitHubDebugRelease>? = null
        for (element in array) {
            if (!element.isJsonObject) continue
            val release = debugRelease(element.asJsonObject) ?: continue
            val published = element.asJsonObject.string("published_at").orEmpty()
            val current = best
            if (current == null || published > current.first) {
                best = published to release
            }
        }
        return best?.second
    }

    private fun debugRelease(obj: JsonObject): GitHubDebugRelease? {
        if (obj.bool("draft")) return null
        if (!obj.bool("prerelease")) return null
        val tag = obj.string("tag_name") ?: return null
        if (!isSafeTag(tag)) return null
        val releaseUrl = obj.string("html_url") ?: return null
        val assets = obj.get("assets") ?: return null
        if (!assets.isJsonArray) return null
        for (assetEl in assets.asJsonArray) {
            if (!assetEl.isJsonObject) continue
            val asset = assetEl.asJsonObject
            val name = asset.string("name") ?: continue
            if (!DEBUG_APK.matches(name)) continue
            val apkUrl = asset.string("browser_download_url") ?: continue
            return GitHubDebugRelease(
                tag = tag,
                releaseUrl = releaseUrl,
                apkUrl = apkUrl,
                assetVersionCode = versionCodeFromAssetName(name),
            )
        }
        return null
    }

    private fun JsonObject.bool(name: String): Boolean {
        val el = get(name) ?: return false
        if (!el.isJsonPrimitive) return false
        return try {
            el.asBoolean
        } catch (_: Exception) {
            false
        }
    }

    private fun JsonObject.string(name: String): String? {
        val el = get(name) ?: return null
        if (!el.isJsonPrimitive) return null
        return el.asString.takeIf { it.isNotBlank() }
    }
}
