package com.sinura.personaltrainer.domain

/**
 * The page×state golden names the P1.2 harness will record.
 *
 * H3 replaces the 108-name AccessibilityMatrix fan-out with six gym-floor
 * pages, a component gallery, and the three ThemeGallery previews. Recording
 * those PNGs stays an owner emulator gate on `temper-tests-api29`.
 *
 * Asset path: `app/src/androidTest/assets/goldens/{name}.png`.
 */
object GoldenPageCatalog {
    const val PROFILE_SUFFIX = "api29"
    const val SUBSTRATE_GALLERY = "foundation-state-gallery-api29"
    const val COMPONENT_GALLERY = "component-state-gallery-api29"
    const val THEME_COLOUR_ROLES = "theme-colour-roles-api29"
    const val THEME_INSTRUMENT_TOKENS = "theme-instrument-tokens-api29"
    const val THEME_TYPE_RAMP = "theme-type-ramp-api29"

    val gymFloorPageIds: List<String> = listOf(
        "home",
        "body",
        "plan",
        "history",
        "settings",
        "active-strength",
    )

    fun assetName(pageId: String, state: String): String = "$pageId-$state-$PROFILE_SUFFIX"

    val requiredPageGoldens: List<String> =
        gymFloorPageIds.map { assetName(it, "populated") }

    val requiredSupportingGoldens: List<String> = listOf(
        COMPONENT_GALLERY,
        THEME_COLOUR_ROLES,
        THEME_INSTRUMENT_TOKENS,
        THEME_TYPE_RAMP,
    )

    /**
     * Goldens that are committed today. The substrate gallery is not a page
     * golden — it proves the harness, not Home or Settings.
     */
    val committed: Set<String> = setOf(SUBSTRATE_GALLERY)

    val missingPageGoldens: List<String>
        get() = requiredPageGoldens.filterNot { it in committed }

    val missingSupportingGoldens: List<String>
        get() = requiredSupportingGoldens.filterNot { it in committed }

    fun isCommitted(name: String): Boolean = name in committed
}
