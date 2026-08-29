package com.sinura.personaltrainer.domain

/**
 * The page×state golden names the P1.2 harness will record.
 *
 * P1.2 closed the substrate: one API 29 gallery PNG and a comparator
 * ([docs/foundation-program/VISUAL_TESTING.md]). Page goldens were deferred.
 * This catalog is the fan-out contract so later UI packets can add a PNG
 * without inventing a second naming scheme, and so the JVM can see what is
 * still missing without an emulator.
 *
 * Asset path: `app/src/androidTest/assets/goldens/{name}.png`.
 * Recording stays on `temper-tests-api29` — an owner emulator gate.
 */
object GoldenPageCatalog {
    const val PROFILE_SUFFIX = "api29"
    const val SUBSTRATE_GALLERY = "foundation-state-gallery-api29"

    fun assetName(pageId: String, state: String): String = "$pageId-$state-$PROFILE_SUFFIX"

    val requiredPageGoldens: List<String> =
        AccessibilityMatrix.pages.flatMap { page ->
            page.states.map { assetName(page.id, it) }
        }

    /**
     * Goldens that are committed today. The substrate gallery is not a page
     * golden — it proves the harness, not Home or Settings.
     */
    val committed: Set<String> = setOf(SUBSTRATE_GALLERY)

    val missingPageGoldens: List<String>
        get() = requiredPageGoldens.filterNot { it in committed }

    fun isCommitted(name: String): Boolean = name in committed
}
