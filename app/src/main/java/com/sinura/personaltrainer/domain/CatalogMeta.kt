package com.sinura.personaltrainer.domain

/**
 * Catalog metadata that is deliberately NOT in the database.
 *
 * `sortRank` and `searchTerms` describe how the built-in catalog should be *presented* and
 * *found*. Neither is a property of the lift the way its equipment or its muscles are, and
 * neither is ever edited by the user — so putting them in `exercises` would mean a schema
 * migration every time a lift moves up the list or gains a nickname, and would leave two
 * copies of the same judgment call: one in the seed source, one in whatever the last seeder
 * run happened to write. Here there is one copy, in code, versioned with the app.
 *
 * The cost is that this table and [DefaultExercises] can drift apart. That is paid for by an
 * invariant test asserting every built-in id has exactly one entry here and every rank is
 * unique, so drift is a build failure rather than a lift that silently sorts last.
 *
 * Customs are absent by design: [sortRank] returns [Int.MAX_VALUE] so they sort after every
 * built-in, and [searchTerms] returns nothing so they match on their name alone.
 */
object CatalogMeta {
    private data class Entry(val sortRank: Int, val searchTerms: Set<String>)

    private fun meta(id: String, sortRank: Int, searchTerms: Set<String>) =
        id to Entry(sortRank, searchTerms)

    private val ROWS: Map<String, Entry> = mapOf(
        meta("ex-barbell-back-squat", 100, setOf("squat")),   // Barbell Back Squat
        meta("ex-conventional-deadlift", 105, setOf("deadlift", "dl")),   // Conventional Deadlift
        meta("ex-barbell-bench-press", 110, setOf("bench", "bp")),   // Barbell Bench Press
        meta("ex-overhead-press", 115, setOf("ohp", "military press", "strict press")),   // Overhead Press
        meta("ex-barbell-row", 120, emptySet()),   // Barbell Row
        meta("ex-pull-up", 125, emptySet()),   // Pull-Up
        // Directly behind the lift it leads to, and one rank apart rather than the usual
        // five: the assisted machines were added after the ladder was laid out, and moving
        // ninety-eight ranks to keep the spacing tidy would change the order of every
        // screen that reads it for no gain the owner can see.
        meta("ex-assisted-pull-up", 126, setOf("assisted", "machine pull-up")),   // Assisted Pull-Up
        meta("ex-romanian-deadlift", 130, setOf("rdl")),   // Romanian Deadlift
        meta("ex-front-squat", 135, emptySet()),   // Front Squat
        meta("ex-incline-bench-press", 140, emptySet()),   // Incline Bench Press
        meta("ex-dumbbell-bench-press", 145, emptySet()),   // Dumbbell Bench Press
        meta("ex-lat-pulldown", 150, setOf("pulldown")),   // Lat Pulldown
        meta("ex-seated-cable-row", 155, emptySet()),   // Seated Cable Row
        meta("ex-chin-up", 160, emptySet()),   // Chin-Up
        meta("ex-assisted-chin-up", 161, setOf("assisted", "machine chin-up")),   // Assisted Chin-Up
        meta("ex-hip-thrust", 165, emptySet()),   // Hip Thrust
        meta("ex-leg-press", 170, emptySet()),   // Leg Press
        meta("ex-bulgarian-split-squat", 175, emptySet()),   // Bulgarian Split Squat
        meta("ex-walking-lunge", 180, emptySet()),   // Walking Lunge
        meta("ex-goblet-squat", 185, emptySet()),   // Goblet Squat
        meta("ex-trap-bar-deadlift", 190, emptySet()),   // Trap Bar Deadlift
        meta("ex-seated-dumbbell-press", 195, emptySet()),   // Seated Dumbbell Press
        meta("ex-lateral-raise", 200, emptySet()),   // Lateral Raise
        meta("ex-face-pull", 205, emptySet()),   // Face Pull
        meta("ex-pendlay-row", 210, emptySet()),   // Pendlay Row
        meta("ex-one-arm-dumbbell-row", 215, emptySet()),   // One-Arm Dumbbell Row
        meta("ex-leg-curl", 220, emptySet()),   // Leg Curl
        meta("ex-leg-extension", 225, emptySet()),   // Leg Extension
        meta("ex-standing-calf-raise", 230, emptySet()),   // Standing Calf Raise
        meta("ex-barbell-curl", 235, emptySet()),   // Barbell Curl
        meta("ex-dumbbell-curl", 240, emptySet()),   // Dumbbell Curl
        meta("ex-tricep-pushdown", 245, setOf("rope pushdown", "cable pushdown")),   // Tricep Pushdown
        meta("ex-skull-crusher", 250, setOf("lying triceps extension", "french press")),   // Skull Crusher
        meta("ex-close-grip-bench-press", 255, emptySet()),   // Close-Grip Bench Press
        meta("ex-chest-fly", 260, emptySet()),   // Chest Fly
        meta("ex-push-up", 265, emptySet()),   // Push-Up
        meta("ex-plank", 270, emptySet()),   // Plank
        meta("ex-hanging-leg-raise", 275, setOf("hlr")),   // Hanging Leg Raise
        meta("ex-cable-crunch", 280, emptySet()),   // Cable Crunch
        meta("ex-incline-dumbbell-bench-press", 300, setOf("incline db press")),   // Incline Dumbbell Bench Press
        meta("ex-machine-chest-press", 305, setOf("chest press machine")),   // Machine Chest Press
        meta("ex-dip", 310, setOf("chest dip", "weighted dip")),   // Dip
        meta("ex-assisted-dip", 311, setOf("assisted", "machine dip")),   // Assisted Dip
        meta("ex-cable-fly", 315, setOf("cable crossover")),   // Cable Fly
        meta("ex-pec-deck", 320, setOf("seated fly", "butterfly")),   // Pec Deck
        meta("ex-decline-bench-press", 325, emptySet()),   // Decline Bench Press
        meta("ex-smith-machine-bench-press", 330, setOf("smith bench")),   // Smith Machine Bench Press
        meta("ex-t-bar-row", 335, setOf("tbar")),   // T-Bar Row
        meta("ex-machine-seated-row", 340, setOf("row machine")),   // Machine Seated Row
        meta("ex-chest-supported-dumbbell-row", 345, setOf("seal row")),   // Chest-Supported Dumbbell Row
        meta("ex-inverted-row", 350, setOf("bodyweight row")),   // Inverted Row
        meta("ex-close-grip-lat-pulldown", 355, setOf("neutral grip pulldown")),   // Close-Grip Lat Pulldown
        meta("ex-straight-arm-pulldown", 360, setOf("lat prayer")),   // Straight-Arm Pulldown
        meta("ex-barbell-shrug", 365, setOf("traps")),   // Barbell Shrug
        meta("ex-dumbbell-shrug", 370, emptySet()),   // Dumbbell Shrug
        meta("ex-push-press", 375, emptySet()),   // Push Press
        meta("ex-arnold-press", 380, emptySet()),   // Arnold Press
        meta("ex-machine-shoulder-press", 385, setOf("shoulder press machine")),   // Machine Shoulder Press
        meta("ex-cable-lateral-raise", 390, setOf("side raise")),   // Cable Lateral Raise
        meta("ex-machine-lateral-raise", 395, emptySet()),   // Machine Lateral Raise
        meta("ex-reverse-pec-deck", 400, setOf("reverse fly machine")),   // Reverse Pec Deck
        meta("ex-dumbbell-rear-delt-fly", 405, setOf("reverse fly", "bent over fly")),   // Dumbbell Rear-Delt Fly
        meta("ex-ez-bar-curl", 410, setOf("ez curl")),   // EZ-Bar Curl
        meta("ex-hammer-curl", 415, emptySet()),   // Hammer Curl
        meta("ex-preacher-curl", 420, emptySet()),   // Preacher Curl
        meta("ex-incline-dumbbell-curl", 425, emptySet()),   // Incline Dumbbell Curl
        meta("ex-cable-curl", 430, emptySet()),   // Cable Curl
        meta("ex-machine-bicep-curl", 435, setOf("preacher machine")),   // Machine Bicep Curl
        meta("ex-overhead-cable-triceps-extension", 440, setOf("overhead extension")),   // Overhead Cable Triceps Extension
        meta("ex-overhead-dumbbell-triceps-extension", 445, setOf("french press")),   // Overhead Dumbbell Triceps Extension
        meta("ex-machine-triceps-extension", 450, emptySet()),   // Machine Triceps Extension
        meta("ex-diamond-push-up", 455, setOf("close grip pushup")),   // Diamond Push-Up
        meta("ex-bench-dip", 460, emptySet()),   // Bench Dip
        meta("ex-hack-squat", 500, emptySet()),   // Hack Squat
        meta("ex-smith-machine-squat", 505, setOf("smith squat")),   // Smith Machine Squat
        meta("ex-reverse-lunge", 510, emptySet()),   // Reverse Lunge
        meta("ex-dumbbell-step-up", 515, setOf("step up")),   // Dumbbell Step-Up
        meta("ex-bodyweight-squat", 520, setOf("air squat")),   // Bodyweight Squat
        meta("ex-sumo-deadlift", 525, setOf("sumo")),   // Sumo Deadlift
        meta("ex-kettlebell-swing", 530, setOf("kb swing")),   // Kettlebell Swing
        meta("ex-back-extension", 535, setOf("hyperextension")),   // Back Extension
        meta("ex-seated-leg-curl", 540, emptySet()),   // Seated Leg Curl
        meta("ex-dumbbell-romanian-deadlift", 545, setOf("db rdl")),   // Dumbbell Romanian Deadlift
        meta("ex-single-leg-romanian-deadlift", 550, setOf("single leg rdl")),   // Single-Leg Romanian Deadlift
        meta("ex-good-morning", 555, emptySet()),   // Good Morning
        meta("ex-nordic-ham-curl", 560, setOf("nordic curl")),   // Nordic Ham Curl
        meta("ex-barbell-glute-bridge", 565, setOf("glute bridge")),   // Barbell Glute Bridge
        meta("ex-machine-hip-thrust", 570, emptySet()),   // Machine Hip Thrust
        meta("ex-hip-abduction-machine", 575, setOf("abductor")),   // Hip Abduction Machine
        meta("ex-cable-kickback", 580, setOf("glute kickback")),   // Cable Kickback
        meta("ex-cable-pull-through", 585, setOf("pull through")),   // Cable Pull-Through
        meta("ex-seated-calf-raise", 590, emptySet()),   // Seated Calf Raise
        meta("ex-leg-press-calf-raise", 595, setOf("calf press")),   // Leg Press Calf Raise
        meta("ex-single-leg-calf-raise", 600, emptySet()),   // Single-Leg Calf Raise
        meta("ex-machine-crunch", 605, setOf("ab machine")),   // Machine Crunch
        meta("ex-decline-sit-up", 610, setOf("situp")),   // Decline Sit-Up
        meta("ex-side-plank", 615, emptySet()),   // Side Plank
        meta("ex-ab-wheel-rollout", 620, setOf("ab rollout")),   // Ab Wheel Rollout
        meta("ex-dead-bug", 625, setOf("deadbug")),   // Dead Bug
        meta("ex-russian-twist", 630, emptySet()),   // Russian Twist
        meta("ex-farmer-s-carry", 635, setOf("farmers walk", "farmer walk")),   // Farmer's Carry
    )

    /** Display order among built-ins; customs sort last. */
    fun sortRank(id: String): Int = ROWS[id]?.sortRank ?: Int.MAX_VALUE

    /** Nicknames and abbreviations the catalog name does not contain. */
    fun searchTerms(id: String): Set<String> = ROWS[id]?.searchTerms.orEmpty()

    /**
     * Whether a typed query matches one of this lift's nicknames.
     *
     * Substring rather than equality, and in this direction: the user typing `rdl` must find
     * "db rdl" and "single leg rdl" as well as the plain one, so the query is looked for
     * *inside* each term. A blank query matches nothing here — an empty needle is inside every
     * string, and search with no query is handled by the caller showing everything anyway.
     */
    fun matchesSearchTerms(query: String, id: String): Boolean {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return false
        return searchTerms(id).any { it.contains(needle) }
    }

    /** Every id this table knows about — the invariant test's other half. */
    internal fun knownIds(): Set<String> = ROWS.keys
}
