package com.sinura.personaltrainer.domain

/**
 * One built-in lift, with everything v2 knows about it.
 *
 * The v1 catalog was a name and a muscle-group string, and its ids were derived from the name
 * by slugging. Those ids are now frozen literals: every routine, every session and every set in
 * the owner's history points at them by foreign key, so re-slugging "Push-Up" would not rename
 * an exercise — it would orphan a year of training. The private slug helper survives only as an
 * assertion aid for the invariant test.
 *
 * The property names [name] and [muscleGroup] are load-bearing beyond this file:
 * `MuscleNormalizerTest` iterates the catalog reading both.
 */
data class SeedExercise(
    val id: String,
    val name: String,
    val muscleGroup: String,
    val equipment: EquipmentType,
    val loadType: LoadType,
    val movementKey: String?,
    val credits: List<MuscleCredit>,
)

/**
 * The built-in catalog, and the version stamp that lets it be improved.
 *
 * v1 seeded only when the exercise table was empty. That is a rule that fires exactly once per
 * install, so every catalog correction after a phone's first launch was unreachable on that
 * phone forever. [CATALOG_VERSION] replaces it: the seeder upserts by id whenever the stored
 * version is behind, updates built-ins in place, inserts what is new, and never deletes.
 *
 * [movementKey] names the lift FAMILY, not the biomechanical pattern — Barbell Bench Press,
 * Incline Bench Press, Dumbbell Bench Press and Close-Grip Bench Press are all `bench-press`.
 * There is one movementKey vocabulary in this app and this is it; the library's family grouping
 * and sibling-swap ride on these exact strings, so they are shipped correct here rather than
 * re-keyed later on data that has already migrated.
 */
object DefaultExercises {
    const val CATALOG_VERSION = 5

    /**
     * The family vocabulary. Batch 1 shipped 23 families and batch 2 adds three; a later batch
     * may add more, but no batch ever re-keys a row that already shipped — the library's family
     * grouping and the sibling swap both ride on these exact strings, and re-keying would
     * silently move a lift out of the family the user found it in.
     */
    val MOVEMENT_FAMILIES: Set<String> = setOf(
        "squat", "lunge", "leg-press", "leg-extension", "deadlift", "romanian-deadlift",
        "hip-thrust", "leg-curl", "calf-raise", "bench-press", "push-up", "chest-fly",
        "overhead-press", "lateral-raise", "rear-delt", "row", "pulldown", "pull-up",
        "curl", "triceps-extension", "plank", "leg-raise", "crunch",
        // Batch 2 (v3).
        "dip", "pullover", "shrug",
        // Batch 3 (v4): the lower-body and core families batch 1 had no room for.
        "step-up", "good-morning", "back-extension", "kettlebell-swing", "hip-abduction",
        "glute-kickback", "pull-through", "nordic-curl", "sit-up", "rollout", "twist",
        "dead-bug", "carry",
    )

    fun catalog(): List<SeedExercise> = CATALOG

    private val CATALOG: List<SeedExercise> = listOf(
        seed(
            id = "ex-barbell-back-squat", name = "Barbell Back Squat", muscleGroup = "Quads",
            equipment = EquipmentType.BARBELL, loadType = LoadType.EXTERNAL, movementKey = "squat",
            primary = "quadriceps", secondaries = listOf("glutes" to 0.50, "hamstrings" to 0.25, "core" to 0.25),
        ),
        seed(
            id = "ex-front-squat", name = "Front Squat", muscleGroup = "Quads",
            equipment = EquipmentType.BARBELL, loadType = LoadType.EXTERNAL, movementKey = "squat",
            primary = "quadriceps", secondaries = listOf("glutes" to 0.50, "core" to 0.25),
        ),
        seed(
            id = "ex-goblet-squat", name = "Goblet Squat", muscleGroup = "Quads",
            equipment = EquipmentType.DUMBBELL, loadType = LoadType.EXTERNAL, movementKey = "squat",
            primary = "quadriceps", secondaries = listOf("glutes" to 0.50, "core" to 0.25),
        ),
        seed(
            id = "ex-bulgarian-split-squat", name = "Bulgarian Split Squat", muscleGroup = "Quads",
            equipment = EquipmentType.DUMBBELL, loadType = LoadType.EXTERNAL, movementKey = "lunge",
            primary = "quadriceps", secondaries = listOf("glutes" to 0.50, "hamstrings" to 0.25),
        ),
        seed(
            id = "ex-walking-lunge", name = "Walking Lunge", muscleGroup = "Quads",
            equipment = EquipmentType.DUMBBELL, loadType = LoadType.EXTERNAL, movementKey = "lunge",
            primary = "quadriceps", secondaries = listOf("glutes" to 0.50, "hamstrings" to 0.25),
        ),
        seed(
            id = "ex-leg-press", name = "Leg Press", muscleGroup = "Quads",
            equipment = EquipmentType.MACHINE, loadType = LoadType.EXTERNAL, movementKey = "leg-press",
            primary = "quadriceps", secondaries = listOf("glutes" to 0.50),
        ),
        seed(
            id = "ex-leg-extension", name = "Leg Extension", muscleGroup = "Quads",
            equipment = EquipmentType.MACHINE, loadType = LoadType.STACK, movementKey = "leg-extension",
            primary = "quadriceps", secondaries = emptyList(),
        ),
        seed(
            id = "ex-conventional-deadlift", name = "Conventional Deadlift", muscleGroup = "Posterior chain",
            equipment = EquipmentType.BARBELL, loadType = LoadType.EXTERNAL, movementKey = "deadlift",
            primary = "glutes", secondaries = listOf("hamstrings" to 0.50, "back" to 0.50),
        ),
        seed(
            id = "ex-romanian-deadlift", name = "Romanian Deadlift", muscleGroup = "Hamstrings",
            equipment = EquipmentType.BARBELL, loadType = LoadType.EXTERNAL, movementKey = "romanian-deadlift",
            primary = "hamstrings", secondaries = listOf("glutes" to 0.50, "back" to 0.25),
        ),
        seed(
            id = "ex-trap-bar-deadlift", name = "Trap Bar Deadlift", muscleGroup = "Posterior chain",
            equipment = EquipmentType.BARBELL, loadType = LoadType.EXTERNAL, movementKey = "deadlift",
            primary = "glutes",
            secondaries = listOf("hamstrings" to 0.50, "quadriceps" to 0.25, "back" to 0.25),
        ),
        seed(
            id = "ex-hip-thrust", name = "Hip Thrust", muscleGroup = "Glutes",
            equipment = EquipmentType.BARBELL, loadType = LoadType.EXTERNAL, movementKey = "hip-thrust",
            primary = "glutes", secondaries = listOf("hamstrings" to 0.25),
        ),
        seed(
            id = "ex-leg-curl", name = "Leg Curl", muscleGroup = "Hamstrings",
            equipment = EquipmentType.MACHINE, loadType = LoadType.STACK, movementKey = "leg-curl",
            primary = "hamstrings", secondaries = emptyList(),
        ),
        seed(
            id = "ex-standing-calf-raise", name = "Standing Calf Raise", muscleGroup = "Calves",
            equipment = EquipmentType.MACHINE, loadType = LoadType.STACK, movementKey = "calf-raise",
            primary = "calves", secondaries = emptyList(),
        ),
        seed(
            id = "ex-barbell-bench-press", name = "Barbell Bench Press", muscleGroup = "Chest",
            equipment = EquipmentType.BARBELL, loadType = LoadType.EXTERNAL, movementKey = "bench-press",
            primary = "chest", secondaries = listOf("triceps" to 0.50, "shoulders" to 0.25),
        ),
        seed(
            id = "ex-incline-bench-press", name = "Incline Bench Press", muscleGroup = "Chest",
            equipment = EquipmentType.BARBELL, loadType = LoadType.EXTERNAL, movementKey = "bench-press",
            primary = "chest", secondaries = listOf("shoulders" to 0.50, "triceps" to 0.25),
        ),
        seed(
            id = "ex-dumbbell-bench-press", name = "Dumbbell Bench Press", muscleGroup = "Chest",
            equipment = EquipmentType.DUMBBELL, loadType = LoadType.EXTERNAL, movementKey = "bench-press",
            primary = "chest", secondaries = listOf("triceps" to 0.50, "shoulders" to 0.25),
        ),
        seed(
            id = "ex-push-up", name = "Push-Up", muscleGroup = "Chest",
            equipment = EquipmentType.BODYWEIGHT, loadType = LoadType.BODYWEIGHT, movementKey = "push-up",
            primary = "chest",
            secondaries = listOf("triceps" to 0.50, "shoulders" to 0.25, "core" to 0.25),
        ),
        seed(
            id = "ex-chest-fly", name = "Chest Fly", muscleGroup = "Chest",
            equipment = EquipmentType.DUMBBELL, loadType = LoadType.EXTERNAL, movementKey = "chest-fly",
            primary = "chest", secondaries = listOf("shoulders" to 0.25),
        ),
        seed(
            id = "ex-overhead-press", name = "Overhead Press", muscleGroup = "Shoulders",
            equipment = EquipmentType.BARBELL, loadType = LoadType.EXTERNAL, movementKey = "overhead-press",
            primary = "shoulders", secondaries = listOf("triceps" to 0.50, "core" to 0.25),
        ),
        seed(
            id = "ex-seated-dumbbell-press", name = "Seated Dumbbell Press", muscleGroup = "Shoulders",
            equipment = EquipmentType.DUMBBELL, loadType = LoadType.EXTERNAL, movementKey = "overhead-press",
            primary = "shoulders", secondaries = listOf("triceps" to 0.50),
        ),
        seed(
            id = "ex-lateral-raise", name = "Lateral Raise", muscleGroup = "Shoulders",
            equipment = EquipmentType.DUMBBELL, loadType = LoadType.EXTERNAL, movementKey = "lateral-raise",
            primary = "shoulders", secondaries = emptyList(),
        ),
        seed(
            id = "ex-face-pull", name = "Face Pull", muscleGroup = "Rear delts",
            equipment = EquipmentType.CABLE, loadType = LoadType.STACK, movementKey = "rear-delt",
            primary = "shoulders", secondaries = listOf("back" to 0.50),
        ),
        seed(
            id = "ex-barbell-row", name = "Barbell Row", muscleGroup = "Back",
            equipment = EquipmentType.BARBELL, loadType = LoadType.EXTERNAL, movementKey = "row",
            primary = "back", secondaries = listOf("biceps" to 0.50, "shoulders" to 0.25),
        ),
        seed(
            id = "ex-pendlay-row", name = "Pendlay Row", muscleGroup = "Back",
            equipment = EquipmentType.BARBELL, loadType = LoadType.EXTERNAL, movementKey = "row",
            primary = "back", secondaries = listOf("biceps" to 0.50),
        ),
        seed(
            id = "ex-one-arm-dumbbell-row", name = "One-Arm Dumbbell Row", muscleGroup = "Back",
            equipment = EquipmentType.DUMBBELL, loadType = LoadType.EXTERNAL, movementKey = "row",
            primary = "back", secondaries = listOf("biceps" to 0.50),
        ),
        seed(
            id = "ex-lat-pulldown", name = "Lat Pulldown", muscleGroup = "Back",
            equipment = EquipmentType.CABLE, loadType = LoadType.STACK, movementKey = "pulldown",
            primary = "back", secondaries = listOf("biceps" to 0.50),
        ),
        seed(
            id = "ex-pull-up", name = "Pull-Up", muscleGroup = "Back",
            equipment = EquipmentType.BODYWEIGHT, loadType = LoadType.BODYWEIGHT_PLUS, movementKey = "pull-up",
            primary = "back", secondaries = listOf("biceps" to 0.50, "core" to 0.25),
        ),
        seed(
            id = "ex-chin-up", name = "Chin-Up", muscleGroup = "Back",
            equipment = EquipmentType.BODYWEIGHT, loadType = LoadType.BODYWEIGHT_PLUS, movementKey = "pull-up",
            primary = "back", secondaries = listOf("biceps" to 0.50, "core" to 0.25),
        ),
        seed(
            id = "ex-seated-cable-row", name = "Seated Cable Row", muscleGroup = "Back",
            equipment = EquipmentType.CABLE, loadType = LoadType.STACK, movementKey = "row",
            primary = "back", secondaries = listOf("biceps" to 0.50),
        ),
        seed(
            id = "ex-barbell-curl", name = "Barbell Curl", muscleGroup = "Biceps",
            equipment = EquipmentType.BARBELL, loadType = LoadType.EXTERNAL, movementKey = "curl",
            primary = "biceps", secondaries = emptyList(),
        ),
        seed(
            id = "ex-dumbbell-curl", name = "Dumbbell Curl", muscleGroup = "Biceps",
            equipment = EquipmentType.DUMBBELL, loadType = LoadType.EXTERNAL, movementKey = "curl",
            primary = "biceps", secondaries = emptyList(),
        ),
        seed(
            id = "ex-tricep-pushdown", name = "Tricep Pushdown", muscleGroup = "Triceps",
            equipment = EquipmentType.CABLE, loadType = LoadType.STACK, movementKey = "triceps-extension",
            primary = "triceps", secondaries = emptyList(),
        ),
        seed(
            id = "ex-skull-crusher", name = "Skull Crusher", muscleGroup = "Triceps",
            equipment = EquipmentType.BARBELL, loadType = LoadType.EXTERNAL, movementKey = "triceps-extension",
            primary = "triceps", secondaries = emptyList(),
        ),
        seed(
            id = "ex-close-grip-bench-press", name = "Close-Grip Bench Press", muscleGroup = "Triceps",
            equipment = EquipmentType.BARBELL, loadType = LoadType.EXTERNAL, movementKey = "bench-press",
            primary = "triceps", secondaries = listOf("chest" to 0.50, "shoulders" to 0.25),
        ),
        seed(
            id = "ex-plank", name = "Plank", muscleGroup = "Core",
            equipment = EquipmentType.BODYWEIGHT, loadType = LoadType.BODYWEIGHT, movementKey = "plank",
            primary = "core", secondaries = emptyList(),
        ),
        seed(
            id = "ex-hanging-leg-raise", name = "Hanging Leg Raise", muscleGroup = "Core",
            equipment = EquipmentType.BODYWEIGHT, loadType = LoadType.BODYWEIGHT, movementKey = "leg-raise",
            primary = "core", secondaries = emptyList(),
        ),
        seed(
            id = "ex-cable-crunch", name = "Cable Crunch", muscleGroup = "Core",
            equipment = EquipmentType.CABLE, loadType = LoadType.STACK, movementKey = "crunch",
            primary = "core", secondaries = emptyList(),
        ),

        // --- Batch 2 (v3): upper body. 33 rows, appended never reordered — the list order is
        // not the display order (that is CatalogMeta.sortRank), so appending keeps every diff
        // against the previous version readable.
        seed(
            id = "ex-incline-dumbbell-bench-press", name = "Incline Dumbbell Bench Press", muscleGroup = "Chest",
            equipment = EquipmentType.DUMBBELL, loadType = LoadType.EXTERNAL, movementKey = "bench-press",
            primary = "chest", secondaries = listOf("shoulders" to 0.50, "triceps" to 0.50),
        ),
        seed(
            id = "ex-machine-chest-press", name = "Machine Chest Press", muscleGroup = "Chest",
            equipment = EquipmentType.MACHINE, loadType = LoadType.STACK, movementKey = "bench-press",
            primary = "chest", secondaries = listOf("triceps" to 0.50, "shoulders" to 0.25),
        ),
        seed(
            id = "ex-dip", name = "Dip", muscleGroup = "Chest",
            equipment = EquipmentType.BODYWEIGHT, loadType = LoadType.BODYWEIGHT_PLUS, movementKey = "dip",
            primary = "chest", secondaries = listOf("triceps" to 0.50, "shoulders" to 0.25),
        ),
        seed(
            id = "ex-cable-fly", name = "Cable Fly", muscleGroup = "Chest",
            equipment = EquipmentType.CABLE, loadType = LoadType.STACK, movementKey = "chest-fly",
            primary = "chest", secondaries = listOf("shoulders" to 0.25),
        ),
        seed(
            id = "ex-pec-deck", name = "Pec Deck", muscleGroup = "Chest",
            equipment = EquipmentType.MACHINE, loadType = LoadType.STACK, movementKey = "chest-fly",
            primary = "chest", secondaries = emptyList(),
        ),
        seed(
            id = "ex-decline-bench-press", name = "Decline Bench Press", muscleGroup = "Chest",
            equipment = EquipmentType.BARBELL, loadType = LoadType.EXTERNAL, movementKey = "bench-press",
            primary = "chest", secondaries = listOf("triceps" to 0.50),
        ),
        seed(
            id = "ex-smith-machine-bench-press", name = "Smith Machine Bench Press", muscleGroup = "Chest",
            equipment = EquipmentType.SMITH, loadType = LoadType.EXTERNAL, movementKey = "bench-press",
            primary = "chest", secondaries = listOf("triceps" to 0.50, "shoulders" to 0.25),
        ),
        seed(
            id = "ex-t-bar-row", name = "T-Bar Row", muscleGroup = "Back",
            equipment = EquipmentType.BARBELL, loadType = LoadType.EXTERNAL, movementKey = "row",
            primary = "back", secondaries = listOf("biceps" to 0.50),
        ),
        seed(
            id = "ex-machine-seated-row", name = "Machine Seated Row", muscleGroup = "Back",
            equipment = EquipmentType.MACHINE, loadType = LoadType.STACK, movementKey = "row",
            primary = "back", secondaries = listOf("biceps" to 0.50),
        ),
        seed(
            id = "ex-chest-supported-dumbbell-row", name = "Chest-Supported Dumbbell Row", muscleGroup = "Back",
            equipment = EquipmentType.DUMBBELL, loadType = LoadType.EXTERNAL, movementKey = "row",
            primary = "back", secondaries = listOf("biceps" to 0.50),
        ),
        seed(
            id = "ex-inverted-row", name = "Inverted Row", muscleGroup = "Back",
            equipment = EquipmentType.BODYWEIGHT, loadType = LoadType.BODYWEIGHT, movementKey = "row",
            primary = "back", secondaries = listOf("biceps" to 0.50, "core" to 0.25),
        ),
        seed(
            id = "ex-close-grip-lat-pulldown", name = "Close-Grip Lat Pulldown", muscleGroup = "Back",
            equipment = EquipmentType.CABLE, loadType = LoadType.STACK, movementKey = "pulldown",
            primary = "back", secondaries = listOf("biceps" to 0.50),
        ),
        seed(
            id = "ex-straight-arm-pulldown", name = "Straight-Arm Pulldown", muscleGroup = "Back",
            equipment = EquipmentType.CABLE, loadType = LoadType.STACK, movementKey = "pullover",
            primary = "back", secondaries = listOf("triceps" to 0.25),
        ),
        seed(
            id = "ex-barbell-shrug", name = "Barbell Shrug", muscleGroup = "Back",
            equipment = EquipmentType.BARBELL, loadType = LoadType.EXTERNAL, movementKey = "shrug",
            primary = "back", secondaries = emptyList(),
        ),
        seed(
            id = "ex-dumbbell-shrug", name = "Dumbbell Shrug", muscleGroup = "Back",
            equipment = EquipmentType.DUMBBELL, loadType = LoadType.EXTERNAL, movementKey = "shrug",
            primary = "back", secondaries = emptyList(),
        ),
        seed(
            id = "ex-push-press", name = "Push Press", muscleGroup = "Shoulders",
            equipment = EquipmentType.BARBELL, loadType = LoadType.EXTERNAL, movementKey = "overhead-press",
            primary = "shoulders", secondaries = listOf("triceps" to 0.50, "quadriceps" to 0.25),
        ),
        seed(
            id = "ex-arnold-press", name = "Arnold Press", muscleGroup = "Shoulders",
            equipment = EquipmentType.DUMBBELL, loadType = LoadType.EXTERNAL, movementKey = "overhead-press",
            primary = "shoulders", secondaries = listOf("triceps" to 0.50),
        ),
        seed(
            id = "ex-machine-shoulder-press", name = "Machine Shoulder Press", muscleGroup = "Shoulders",
            equipment = EquipmentType.MACHINE, loadType = LoadType.STACK, movementKey = "overhead-press",
            primary = "shoulders", secondaries = listOf("triceps" to 0.50),
        ),
        seed(
            id = "ex-cable-lateral-raise", name = "Cable Lateral Raise", muscleGroup = "Shoulders",
            equipment = EquipmentType.CABLE, loadType = LoadType.STACK, movementKey = "lateral-raise",
            primary = "shoulders", secondaries = emptyList(),
        ),
        seed(
            id = "ex-machine-lateral-raise", name = "Machine Lateral Raise", muscleGroup = "Shoulders",
            equipment = EquipmentType.MACHINE, loadType = LoadType.STACK, movementKey = "lateral-raise",
            primary = "shoulders", secondaries = emptyList(),
        ),
        seed(
            id = "ex-reverse-pec-deck", name = "Reverse Pec Deck", muscleGroup = "Shoulders",
            equipment = EquipmentType.MACHINE, loadType = LoadType.STACK, movementKey = "rear-delt",
            primary = "shoulders", secondaries = listOf("back" to 0.25),
        ),
        seed(
            id = "ex-dumbbell-rear-delt-fly", name = "Dumbbell Rear-Delt Fly", muscleGroup = "Shoulders",
            equipment = EquipmentType.DUMBBELL, loadType = LoadType.EXTERNAL, movementKey = "rear-delt",
            primary = "shoulders", secondaries = listOf("back" to 0.25),
        ),
        seed(
            id = "ex-ez-bar-curl", name = "EZ-Bar Curl", muscleGroup = "Biceps",
            equipment = EquipmentType.BARBELL, loadType = LoadType.EXTERNAL, movementKey = "curl",
            primary = "biceps", secondaries = emptyList(),
        ),
        seed(
            id = "ex-hammer-curl", name = "Hammer Curl", muscleGroup = "Biceps",
            equipment = EquipmentType.DUMBBELL, loadType = LoadType.EXTERNAL, movementKey = "curl",
            primary = "biceps", secondaries = emptyList(),
        ),
        seed(
            id = "ex-preacher-curl", name = "Preacher Curl", muscleGroup = "Biceps",
            equipment = EquipmentType.BARBELL, loadType = LoadType.EXTERNAL, movementKey = "curl",
            primary = "biceps", secondaries = emptyList(),
        ),
        seed(
            id = "ex-incline-dumbbell-curl", name = "Incline Dumbbell Curl", muscleGroup = "Biceps",
            equipment = EquipmentType.DUMBBELL, loadType = LoadType.EXTERNAL, movementKey = "curl",
            primary = "biceps", secondaries = emptyList(),
        ),
        seed(
            id = "ex-cable-curl", name = "Cable Curl", muscleGroup = "Biceps",
            equipment = EquipmentType.CABLE, loadType = LoadType.STACK, movementKey = "curl",
            primary = "biceps", secondaries = emptyList(),
        ),
        seed(
            id = "ex-machine-bicep-curl", name = "Machine Bicep Curl", muscleGroup = "Biceps",
            equipment = EquipmentType.MACHINE, loadType = LoadType.STACK, movementKey = "curl",
            primary = "biceps", secondaries = emptyList(),
        ),
        seed(
            id = "ex-overhead-cable-triceps-extension", name = "Overhead Cable Triceps Extension", muscleGroup = "Triceps",
            equipment = EquipmentType.CABLE, loadType = LoadType.STACK, movementKey = "triceps-extension",
            primary = "triceps", secondaries = emptyList(),
        ),
        seed(
            id = "ex-overhead-dumbbell-triceps-extension", name = "Overhead Dumbbell Triceps Extension", muscleGroup = "Triceps",
            equipment = EquipmentType.DUMBBELL, loadType = LoadType.EXTERNAL, movementKey = "triceps-extension",
            primary = "triceps", secondaries = emptyList(),
        ),
        seed(
            id = "ex-machine-triceps-extension", name = "Machine Triceps Extension", muscleGroup = "Triceps",
            equipment = EquipmentType.MACHINE, loadType = LoadType.STACK, movementKey = "triceps-extension",
            primary = "triceps", secondaries = emptyList(),
        ),
        seed(
            id = "ex-diamond-push-up", name = "Diamond Push-Up", muscleGroup = "Triceps",
            equipment = EquipmentType.BODYWEIGHT, loadType = LoadType.BODYWEIGHT, movementKey = "push-up",
            primary = "triceps", secondaries = listOf("chest" to 0.50),
        ),
        seed(
            id = "ex-bench-dip", name = "Bench Dip", muscleGroup = "Triceps",
            equipment = EquipmentType.BODYWEIGHT, loadType = LoadType.BODYWEIGHT, movementKey = "dip",
            primary = "triceps", secondaries = listOf("chest" to 0.50, "shoulders" to 0.25),
        ),

        // --- Batch 3 (v4): lower body and core. 28 rows, completing the curated 98.
        seed(
            id = "ex-hack-squat", name = "Hack Squat", muscleGroup = "Quads",
            equipment = EquipmentType.MACHINE, loadType = LoadType.EXTERNAL, movementKey = "squat",
            primary = "quadriceps", secondaries = listOf("glutes" to 0.50),
        ),
        seed(
            id = "ex-smith-machine-squat", name = "Smith Machine Squat", muscleGroup = "Quads",
            equipment = EquipmentType.SMITH, loadType = LoadType.EXTERNAL, movementKey = "squat",
            primary = "quadriceps", secondaries = listOf("glutes" to 0.50),
        ),
        seed(
            id = "ex-reverse-lunge", name = "Reverse Lunge", muscleGroup = "Quads",
            equipment = EquipmentType.DUMBBELL, loadType = LoadType.EXTERNAL, movementKey = "lunge",
            primary = "quadriceps", secondaries = listOf("glutes" to 0.50, "hamstrings" to 0.25),
        ),
        seed(
            id = "ex-dumbbell-step-up", name = "Dumbbell Step-Up", muscleGroup = "Quads",
            equipment = EquipmentType.DUMBBELL, loadType = LoadType.EXTERNAL, movementKey = "step-up",
            primary = "quadriceps", secondaries = listOf("glutes" to 0.50),
        ),
        seed(
            id = "ex-bodyweight-squat", name = "Bodyweight Squat", muscleGroup = "Quads",
            equipment = EquipmentType.BODYWEIGHT, loadType = LoadType.BODYWEIGHT, movementKey = "squat",
            primary = "quadriceps", secondaries = listOf("glutes" to 0.50),
        ),
        seed(
            id = "ex-sumo-deadlift", name = "Sumo Deadlift", muscleGroup = "Glutes",
            equipment = EquipmentType.BARBELL, loadType = LoadType.EXTERNAL, movementKey = "deadlift",
            primary = "glutes", secondaries = listOf("back" to 0.50, "quadriceps" to 0.50),
        ),
        seed(
            id = "ex-kettlebell-swing", name = "Kettlebell Swing", muscleGroup = "Glutes",
            equipment = EquipmentType.KETTLEBELL, loadType = LoadType.EXTERNAL, movementKey = "kettlebell-swing",
            primary = "glutes", secondaries = listOf("hamstrings" to 0.50, "back" to 0.25),
        ),
        seed(
            id = "ex-back-extension", name = "Back Extension", muscleGroup = "Back",
            equipment = EquipmentType.BODYWEIGHT, loadType = LoadType.BODYWEIGHT_PLUS, movementKey = "back-extension",
            primary = "back", secondaries = listOf("glutes" to 0.50, "hamstrings" to 0.50),
        ),
        seed(
            id = "ex-seated-leg-curl", name = "Seated Leg Curl", muscleGroup = "Hamstrings",
            equipment = EquipmentType.MACHINE, loadType = LoadType.STACK, movementKey = "leg-curl",
            primary = "hamstrings", secondaries = emptyList(),
        ),
        seed(
            id = "ex-dumbbell-romanian-deadlift", name = "Dumbbell Romanian Deadlift", muscleGroup = "Hamstrings",
            equipment = EquipmentType.DUMBBELL, loadType = LoadType.EXTERNAL, movementKey = "romanian-deadlift",
            primary = "hamstrings", secondaries = listOf("glutes" to 0.50, "back" to 0.25),
        ),
        seed(
            id = "ex-single-leg-romanian-deadlift", name = "Single-Leg Romanian Deadlift", muscleGroup = "Hamstrings",
            equipment = EquipmentType.DUMBBELL, loadType = LoadType.EXTERNAL, movementKey = "romanian-deadlift",
            primary = "hamstrings", secondaries = listOf("glutes" to 0.50, "core" to 0.25),
        ),
        seed(
            id = "ex-good-morning", name = "Good Morning", muscleGroup = "Hamstrings",
            equipment = EquipmentType.BARBELL, loadType = LoadType.EXTERNAL, movementKey = "good-morning",
            primary = "hamstrings", secondaries = listOf("glutes" to 0.50, "back" to 0.50),
        ),
        seed(
            id = "ex-nordic-ham-curl", name = "Nordic Ham Curl", muscleGroup = "Hamstrings",
            equipment = EquipmentType.BODYWEIGHT, loadType = LoadType.BODYWEIGHT, movementKey = "nordic-curl",
            primary = "hamstrings", secondaries = emptyList(),
        ),
        seed(
            id = "ex-barbell-glute-bridge", name = "Barbell Glute Bridge", muscleGroup = "Glutes",
            equipment = EquipmentType.BARBELL, loadType = LoadType.EXTERNAL, movementKey = "hip-thrust",
            primary = "glutes", secondaries = listOf("hamstrings" to 0.25),
        ),
        seed(
            id = "ex-machine-hip-thrust", name = "Machine Hip Thrust", muscleGroup = "Glutes",
            equipment = EquipmentType.MACHINE, loadType = LoadType.STACK, movementKey = "hip-thrust",
            primary = "glutes", secondaries = listOf("hamstrings" to 0.25),
        ),
        seed(
            id = "ex-hip-abduction-machine", name = "Hip Abduction Machine", muscleGroup = "Glutes",
            equipment = EquipmentType.MACHINE, loadType = LoadType.STACK, movementKey = "hip-abduction",
            primary = "glutes", secondaries = emptyList(),
        ),
        seed(
            id = "ex-cable-kickback", name = "Cable Kickback", muscleGroup = "Glutes",
            equipment = EquipmentType.CABLE, loadType = LoadType.STACK, movementKey = "glute-kickback",
            primary = "glutes", secondaries = listOf("hamstrings" to 0.25),
        ),
        seed(
            id = "ex-cable-pull-through", name = "Cable Pull-Through", muscleGroup = "Glutes",
            equipment = EquipmentType.CABLE, loadType = LoadType.STACK, movementKey = "pull-through",
            primary = "glutes", secondaries = listOf("hamstrings" to 0.50),
        ),
        seed(
            id = "ex-seated-calf-raise", name = "Seated Calf Raise", muscleGroup = "Calves",
            equipment = EquipmentType.MACHINE, loadType = LoadType.STACK, movementKey = "calf-raise",
            primary = "calves", secondaries = emptyList(),
        ),
        seed(
            id = "ex-leg-press-calf-raise", name = "Leg Press Calf Raise", muscleGroup = "Calves",
            equipment = EquipmentType.MACHINE, loadType = LoadType.EXTERNAL, movementKey = "calf-raise",
            primary = "calves", secondaries = emptyList(),
        ),
        seed(
            id = "ex-single-leg-calf-raise", name = "Single-Leg Calf Raise", muscleGroup = "Calves",
            equipment = EquipmentType.BODYWEIGHT, loadType = LoadType.BODYWEIGHT_PLUS, movementKey = "calf-raise",
            primary = "calves", secondaries = emptyList(),
        ),
        seed(
            id = "ex-machine-crunch", name = "Machine Crunch", muscleGroup = "Core",
            equipment = EquipmentType.MACHINE, loadType = LoadType.STACK, movementKey = "crunch",
            primary = "core", secondaries = emptyList(),
        ),
        seed(
            id = "ex-decline-sit-up", name = "Decline Sit-Up", muscleGroup = "Core",
            equipment = EquipmentType.BODYWEIGHT, loadType = LoadType.BODYWEIGHT_PLUS, movementKey = "sit-up",
            primary = "core", secondaries = emptyList(),
        ),
        seed(
            id = "ex-side-plank", name = "Side Plank", muscleGroup = "Core",
            equipment = EquipmentType.BODYWEIGHT, loadType = LoadType.BODYWEIGHT, movementKey = "plank",
            primary = "core", secondaries = emptyList(),
        ),
        seed(
            id = "ex-ab-wheel-rollout", name = "Ab Wheel Rollout", muscleGroup = "Core",
            equipment = EquipmentType.OTHER, loadType = LoadType.BODYWEIGHT, movementKey = "rollout",
            primary = "core", secondaries = listOf("shoulders" to 0.25),
        ),
        seed(
            id = "ex-dead-bug", name = "Dead Bug", muscleGroup = "Core",
            equipment = EquipmentType.BODYWEIGHT, loadType = LoadType.BODYWEIGHT, movementKey = "dead-bug",
            primary = "core", secondaries = emptyList(),
        ),
        seed(
            id = "ex-russian-twist", name = "Russian Twist", muscleGroup = "Core",
            equipment = EquipmentType.DUMBBELL, loadType = LoadType.BODYWEIGHT_PLUS, movementKey = "twist",
            primary = "core", secondaries = emptyList(),
        ),
        seed(
            id = "ex-farmer-s-carry", name = "Farmer's Carry", muscleGroup = "Core",
            equipment = EquipmentType.DUMBBELL, loadType = LoadType.EXTERNAL, movementKey = "carry",
            primary = "core", secondaries = listOf("back" to 0.25),
        ),

        // --- Batch 4 (v5): the assisted machines. 3 rows.
        //
        // LoadType.ASSISTED shipped with the bodyweight work and no catalog row used it, which
        // made it a capability the app had and nobody could reach: the machine in every gym that
        // gets a beginner to their first pull-up had to be logged as a custom lift, with no
        // progression, because the built-in catalog only offered the unassisted version.
        //
        // Same family and same credits as the lift they lead to, deliberately. The assistance
        // machine is a pull-up you can currently do, not a different exercise, and keeping the
        // movementKey means the library offers the sibling swap in the direction that matters:
        // from assisted to free once the stack runs out.
        //
        // Appended rather than slotted beside their siblings, because the frozen-id test reads
        // batch 1 as a prefix of this list. Presentation order is CatalogMeta's job, and there
        // each one sits directly behind the lift it leads to.
        seed(
            id = "ex-assisted-pull-up", name = "Assisted Pull-Up", muscleGroup = "Back",
            equipment = EquipmentType.MACHINE, loadType = LoadType.ASSISTED, movementKey = "pull-up",
            primary = "back", secondaries = listOf("biceps" to 0.50, "core" to 0.25),
        ),
        seed(
            id = "ex-assisted-chin-up", name = "Assisted Chin-Up", muscleGroup = "Back",
            equipment = EquipmentType.MACHINE, loadType = LoadType.ASSISTED, movementKey = "pull-up",
            primary = "back", secondaries = listOf("biceps" to 0.50, "core" to 0.25),
        ),
        seed(
            id = "ex-assisted-dip", name = "Assisted Dip", muscleGroup = "Chest",
            equipment = EquipmentType.MACHINE, loadType = LoadType.ASSISTED, movementKey = "dip",
            primary = "chest", secondaries = listOf("triceps" to 0.50, "shoulders" to 0.25),
        ),
    )

    private fun seed(
        id: String,
        name: String,
        muscleGroup: String,
        equipment: EquipmentType,
        loadType: LoadType,
        movementKey: String?,
        primary: String,
        secondaries: List<Pair<String, Double>>,
    ): SeedExercise = SeedExercise(
        id = id,
        name = name,
        muscleGroup = muscleGroup,
        equipment = equipment,
        loadType = loadType,
        movementKey = movementKey,
        credits = buildList {
            add(MuscleCredit(muscleKey = primary, weight = 1.0))
            secondaries.forEach { (key, weight) -> add(MuscleCredit(muscleKey = key, weight = weight)) }
        },
    )

    /**
     * How v1 derived ids from names. Kept only so the invariant test can prove the frozen
     * literals above still match it — nothing in the app calls this to make an id any more.
     */
    internal fun slugOf(name: String): String = "ex-" + name.lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
}
