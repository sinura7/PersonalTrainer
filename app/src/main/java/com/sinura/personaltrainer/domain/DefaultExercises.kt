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
    const val CATALOG_VERSION = 2

    /**
     * The closed family vocabulary for batch 1. Later catalog batches extend this set with
     * their own families; they never re-key a row that already shipped.
     */
    val MOVEMENT_FAMILIES: Set<String> = setOf(
        "squat", "lunge", "leg-press", "leg-extension", "deadlift", "romanian-deadlift",
        "hip-thrust", "leg-curl", "calf-raise", "bench-press", "push-up", "chest-fly",
        "overhead-press", "lateral-raise", "rear-delt", "row", "pulldown", "pull-up",
        "curl", "triceps-extension", "plank", "leg-raise", "crunch",
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
