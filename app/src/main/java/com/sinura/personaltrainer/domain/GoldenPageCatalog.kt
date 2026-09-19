package com.sinura.personaltrainer.domain

/**
 * The page×state golden names the P1.2 harness will record.
 *
 * H3 replaces the 108-name AccessibilityMatrix fan-out with six gym-floor
 * pages, a component gallery, and the three ThemeGallery previews. Recording
 * those PNGs stays an owner emulator gate on `temper-tests-api29`.
 *
 * Packet H named six 360×800 floor states. The image-led redesign
 * records working, warmup, rest, hold, success, error, completion,
 * font-2.0, and reduced-motion on `temper-tests-api29`. F3 replaces the
 * old live-clock renders with frozen integration frames of shipping components,
 * measured at 360×800 dp. Native route/journey suites verify their wiring.
 * Those nine names are in [committed]. No caller may add a
 * `GoldenImageAssert` `assertMatches` on a name that is not.
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

    /** Recording profile for every floor state below: 360×800, populated. */
    const val FLOOR_WIDTH_DP = 360
    const val FLOOR_HEIGHT_DP = 800

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
     * Image-led floor states at 360×800: working, warmup, rest, hold,
     * success, error, completion, plus font-2.0 and reduced-motion
     * variants. F3 records compact identity and derived actions with pinned clocks.
     */
    val floorStateIds: List<String> = listOf(
        "working",
        "warmup",
        "rest",
        "hold",
        "success",
        "error",
        "completion",
        "font20",
        "reduced-motion",
    )

    fun floorAssetName(state: String): String = "active-strength-$state-$PROFILE_SUFFIX"

    val requiredFloorStateGoldens: List<String> =
        floorStateIds.map { floorAssetName(it) }

    /**
     * Goldens that are committed today. The substrate gallery is not a page
     * golden — it proves the harness, not Home or Settings. Floor states combine
     * shipping components with immutable fixture state; they are not route tests.
     */
    val committed: Set<String> = setOf(SUBSTRATE_GALLERY) + requiredFloorStateGoldens.toSet()

    val missingPageGoldens: List<String>
        get() = requiredPageGoldens.filterNot { it in committed }

    val missingSupportingGoldens: List<String>
        get() = requiredSupportingGoldens.filterNot { it in committed }

    val missingFloorStateGoldens: List<String>
        get() = requiredFloorStateGoldens.filterNot { it in committed }

    fun isCommitted(name: String): Boolean = name in committed
}
