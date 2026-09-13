package com.sinura.personaltrainer.domain

/**
 * Short extra blocks minted from catalog rows that already exist.
 *
 * Not a seed expansion. Missing ids are skipped at attach time so an
 * older catalog still yields a shorter pack rather than a crash.
 *
 * Warm-ups prepare a session. Mobility packs are the longevity extras
 * (stretch, holds, core) and the golf cool-down. Each pack is its own day
 * block — not spliced into the pinned workout.
 *
 * [kit] / [shownFor] are the Extra second question: None, Free weights,
 * Machines, Mixed. Catalog + these packs — not an LLM writing sessions.
 */
data class AuxiliaryLift(
    val exerciseId: String,
    val sets: Int,
    val reps: Int,
    val restSeconds: Int,
)

enum class AuxiliaryKind {
    WARMUP,
    MOBILITY,
}

data class AuxiliaryPack(
    val id: String,
    val title: String,
    val caption: String,
    val kind: AuxiliaryKind,
    val lifts: List<AuxiliaryLift>,
    /**
     * Catalog still to show on Extra pickers. Hyphens in the lift id
     * become underscores — the same key [SeedExercise.imageKey] writes,
     * so extras reuse ADR-022 stills instead of a second pack.
     */
    val imageKey: String,
    /** Role this pack fills so Plan does not add floor Core and machine Core. */
    val family: String,
    val shownFor: Set<ExtraEquipment>,
)

object AuxiliaryPacks {
    val Golf = pack(
        id = "golf",
        title = "Golf warm-up",
        caption = "Hips, rotation, shoulders. Exercises only — not a round.",
        kind = AuxiliaryKind.WARMUP,
        lifts = listOf(
            lift("ex-hyper-pro-elephant-walk", 1, 10),
            lift("ex-hyper-pro-woodchop", 2, 8),
            lift("ex-hyper-pro-external-rotator", 2, 8),
            lift("ex-hyper-pro-face-pull", 2, 10),
        ),
        imageKey = "ex_hyper_pro_woodchop",
        shownFor = setOf(ExtraEquipment.MACHINES, ExtraEquipment.MIXED),
    )

    /**
     * After a round, not before one: the warm-up above readies rotation, this
     * unloads the hips and calves that carried eighteen holes and finishes with
     * a brace so the back is not left to settle on its own.
     */
    val GolfCooldown = pack(
        id = "golf-cooldown",
        title = "Golf cool-down",
        caption = "About ten minutes. Hips, calves, a walk-out and a brace. After a round.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            lift("ex-hyper-pro-couch-stretch"),
            lift("ex-hyper-pro-incline-pigeon"),
            lift("ex-hyper-pro-calf-stretch"),
            lift("ex-hyper-pro-elephant-walk", 1, 10),
            lift("ex-dead-bug", 2, 8),
        ),
        imageKey = "ex_hyper_pro_couch_stretch",
        shownFor = setOf(ExtraEquipment.MACHINES, ExtraEquipment.MIXED),
    )

    val LowerBody = pack(
        id = "lower-body",
        title = "Lower-body warm-up",
        caption = "Squats, lunges, glutes. Before a lower-body session.",
        kind = AuxiliaryKind.WARMUP,
        lifts = listOf(
            lift("ex-bodyweight-squat", 2, 10),
            lift("ex-walking-lunge"),
            lift("ex-barbell-glute-bridge"),
            lift("ex-hyper-pro-elephant-walk", 1, 10),
        ),
        imageKey = "ex_bodyweight_squat",
        shownFor = setOf(ExtraEquipment.MIXED),
    )

    val UpperBody = pack(
        id = "upper-body",
        title = "Upper-body warm-up",
        caption = "Push-ups, rows, face pulls. Before an upper-body session.",
        kind = AuxiliaryKind.WARMUP,
        lifts = listOf(
            lift("ex-push-up", 2, 8),
            lift("ex-face-pull", 2, 12),
            lift("ex-inverted-row"),
            lift("ex-hyper-pro-external-rotator", 1, 10),
        ),
        imageKey = "ex_push_up",
        shownFor = setOf(ExtraEquipment.MIXED),
    )

    val Shoulder = pack(
        id = "shoulder",
        title = "Shoulder warm-up",
        caption = "Rotators, face pulls, raises. Before pressing.",
        kind = AuxiliaryKind.WARMUP,
        lifts = listOf(
            lift("ex-hyper-pro-external-rotator", 2, 10),
            lift("ex-face-pull", 2, 12),
            lift("ex-lateral-raise", 2, 12),
            lift("ex-dumbbell-rear-delt-fly", 2, 12),
        ),
        imageKey = "ex_lateral_raise",
        shownFor = setOf(ExtraEquipment.MIXED),
    )

    val Stretch = pack(
        id = "stretch",
        title = "Stretch",
        caption = "About eight minutes. Hips, calves, walk-outs. Any time of day.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            lift("ex-hyper-pro-calf-stretch"),
            lift("ex-hyper-pro-couch-stretch"),
            lift("ex-hyper-pro-elephant-walk", 1, 10),
            lift("ex-hyper-pro-incline-pigeon"),
        ),
        imageKey = "ex_hyper_pro_calf_stretch",
        shownFor = setOf(ExtraEquipment.MACHINES, ExtraEquipment.MIXED),
    )

    val LowerBack = pack(
        id = "lower-back",
        title = "Lower back",
        caption = "About eight minutes. Extensions and a hold.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            lift("ex-back-extension", 2, 10, 30),
            lift("ex-dead-bug", 2, 8),
            lift("ex-plank", 1, 40),
        ),
        imageKey = "ex_back_extension",
        shownFor = setOf(ExtraEquipment.MIXED),
    )

    val Hips = pack(
        id = "hips",
        title = "Hips",
        caption = "About eight minutes. Groin and hip openers.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            lift("ex-hyper-pro-couch-stretch"),
            lift("ex-hyper-pro-incline-pigeon"),
            lift("ex-hyper-pro-elephant-walk", 1, 10),
        ),
        imageKey = "ex_hyper_pro_incline_pigeon",
        shownFor = setOf(ExtraEquipment.MACHINES, ExtraEquipment.MIXED),
    )

    val Holds = pack(
        id = "holds",
        title = "Holds",
        caption = "About six minutes. Static holds before bed or any time.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            lift("ex-plank", 1, 40, 30),
            lift("ex-side-plank", 1, 30),
            lift("ex-dead-bug", 2, 8),
        ),
        imageKey = "ex_plank",
        shownFor = ExtraEquipment.entries.toSet(),
    )

    val Core = pack(
        id = "core",
        title = "Core",
        caption = "About eight minutes. Crunches and leg raises. Not a static hold.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            lift("ex-hanging-leg-raise", 2, 8, 30),
            lift("ex-machine-crunch", 2, 12, 30),
            lift("ex-cable-crunch", 2, 12, 30),
        ),
        imageKey = "ex_hanging_leg_raise",
        shownFor = setOf(ExtraEquipment.MACHINES, ExtraEquipment.MIXED),
    )

    val GolfNone = pack(
        id = "golf-none",
        family = "golf",
        title = "Golf warm-up",
        caption = "Floor rotation and a brace. No bench, no cable.",
        kind = AuxiliaryKind.WARMUP,
        lifts = listOf(
            lift("ex-doorway-chest-stretch"),
            lift("ex-y-hold", 2, 8),
            lift("ex-push-up", 2, 8),
            lift("ex-dead-bug", 2, 8),
        ),
        imageKey = "ex_y_hold",
        shownFor = setOf(ExtraEquipment.NONE),
    )

    val LowerBodyNone = pack(
        id = "lower-body-none",
        family = "lower-body",
        title = "Lower-body warm-up",
        caption = "Floor squats, a wall sit, calves. No kit.",
        kind = AuxiliaryKind.WARMUP,
        lifts = listOf(
            lift("ex-bodyweight-squat", 2, 10),
            lift("ex-wall-sit", 1, 30),
            lift("ex-single-leg-calf-raise", 1, 10),
            lift("ex-deep-squat-hold", 1, 20),
        ),
        imageKey = "ex_wall_sit",
        shownFor = setOf(ExtraEquipment.NONE),
    )

    val UpperBodyNone = pack(
        id = "upper-body-none",
        family = "upper-body",
        title = "Upper-body warm-up",
        caption = "Push-ups, inverted rows, a diamond. Floor and a bar.",
        kind = AuxiliaryKind.WARMUP,
        lifts = listOf(
            lift("ex-push-up", 2, 8),
            lift("ex-inverted-row"),
            lift("ex-diamond-push-up", 2, 8),
            lift("ex-doorway-chest-stretch"),
        ),
        imageKey = "ex_diamond_push_up",
        shownFor = setOf(ExtraEquipment.NONE),
    )

    val ShoulderNone = pack(
        id = "shoulder-none",
        family = "shoulder",
        title = "Shoulder warm-up",
        caption = "Y-holds, a doorway stretch, inverted rows.",
        kind = AuxiliaryKind.WARMUP,
        lifts = listOf(
            lift("ex-y-hold", 2, 10),
            lift("ex-inverted-row"),
            lift("ex-doorway-chest-stretch"),
            lift("ex-diamond-push-up", 2, 8),
        ),
        imageKey = "ex_inverted_row",
        shownFor = setOf(ExtraEquipment.NONE),
    )

    val GolfCooldownNone = pack(
        id = "golf-cooldown-none",
        family = "golf-cooldown",
        title = "Golf cool-down",
        caption = "Floor hips and a brace. After a round with no bench.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            lift("ex-dead-bug", 2, 8),
            lift("ex-plank", 1, 40),
            lift("ex-side-plank", 1, 30),
            lift("ex-doorway-chest-stretch"),
        ),
        imageKey = "ex_side_plank",
        shownFor = setOf(ExtraEquipment.NONE),
    )

    val StretchNone = pack(
        id = "stretch-none",
        family = "stretch",
        title = "Stretch",
        caption = "Doorway, a deep squat, Y-holds. The floor.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            lift("ex-doorway-chest-stretch"),
            lift("ex-deep-squat-hold", 1, 20),
            lift("ex-y-hold", 2, 8),
            lift("ex-wall-sit", 1, 30),
        ),
        imageKey = "ex_doorway_chest_stretch",
        shownFor = setOf(ExtraEquipment.NONE),
    )

    val LowerBackNone = pack(
        id = "lower-back-none",
        family = "lower-back",
        title = "Lower back",
        caption = "Dead bugs and a plank. No hyperextension bench.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            lift("ex-dead-bug", 2, 8),
            lift("ex-plank", 1, 40),
            lift("ex-side-plank", 1, 30),
        ),
        imageKey = "ex_dead_bug",
        shownFor = setOf(ExtraEquipment.NONE),
    )

    val HipsNone = pack(
        id = "hips-none",
        family = "hips",
        title = "Hips",
        caption = "Deep squat, wall sit, a Nordic. Floor only.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            lift("ex-deep-squat-hold", 1, 20),
            lift("ex-wall-sit", 1, 30),
            lift("ex-nordic-ham-curl"),
            lift("ex-dead-bug", 2, 8),
        ),
        imageKey = "ex_nordic_ham_curl",
        shownFor = setOf(ExtraEquipment.NONE),
    )

    val CoreNone = pack(
        id = "core-none",
        family = "core",
        title = "Core",
        caption = "Floor brace. Dead bugs, planks. No machine.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            lift("ex-dead-bug", 2, 8),
            lift("ex-plank", 1, 40),
            lift("ex-side-plank", 1, 30),
            lift("ex-decline-sit-up", 2, 10),
        ),
        imageKey = "ex_decline_sit_up",
        shownFor = setOf(ExtraEquipment.NONE),
    )

    val GolfFree = pack(
        id = "golf-free",
        family = "golf",
        title = "Golf warm-up",
        caption = "Swings, a twist, rear delts. Dumbbells and a bell.",
        kind = AuxiliaryKind.WARMUP,
        lifts = listOf(
            lift("ex-kettlebell-swing", 2, 10),
            lift("ex-russian-twist", 2, 10),
            lift("ex-dumbbell-rear-delt-fly", 2, 12),
            lift("ex-goblet-squat", 2, 8),
        ),
        imageKey = "ex_kettlebell_swing",
        shownFor = setOf(ExtraEquipment.FREE_WEIGHTS),
    )

    val LowerBodyFree = pack(
        id = "lower-body-free",
        family = "lower-body",
        title = "Lower-body warm-up",
        caption = "Goblet squats, a DB hinge, a glute bridge.",
        kind = AuxiliaryKind.WARMUP,
        lifts = listOf(
            lift("ex-goblet-squat", 2, 8),
            lift("ex-dumbbell-romanian-deadlift", 2, 8),
            lift("ex-barbell-glute-bridge"),
            lift("ex-walking-lunge"),
        ),
        imageKey = "ex_goblet_squat",
        shownFor = setOf(ExtraEquipment.FREE_WEIGHTS),
    )

    val UpperBodyFree = pack(
        id = "upper-body-free",
        family = "upper-body",
        title = "Upper-body warm-up",
        caption = "Push-ups, a DB row, raises. Before pressing.",
        kind = AuxiliaryKind.WARMUP,
        lifts = listOf(
            lift("ex-push-up", 2, 8),
            lift("ex-one-arm-dumbbell-row", 2, 8),
            lift("ex-lateral-raise", 2, 12),
            lift("ex-dumbbell-rear-delt-fly", 2, 12),
        ),
        imageKey = "ex_one_arm_dumbbell_row",
        shownFor = setOf(ExtraEquipment.FREE_WEIGHTS),
    )

    val ShoulderFree = pack(
        id = "shoulder-free",
        family = "shoulder",
        title = "Shoulder warm-up",
        caption = "DB press, laterals, rear-delt flies.",
        kind = AuxiliaryKind.WARMUP,
        lifts = listOf(
            lift("ex-seated-dumbbell-press", 2, 8),
            lift("ex-lateral-raise", 2, 12),
            lift("ex-dumbbell-rear-delt-fly", 2, 12),
            lift("ex-arnold-press", 1, 8),
        ),
        imageKey = "ex_seated_dumbbell_press",
        shownFor = setOf(ExtraEquipment.FREE_WEIGHTS),
    )

    val GolfCooldownFree = pack(
        id = "golf-cooldown-free",
        family = "golf-cooldown",
        title = "Golf cool-down",
        caption = "A carry, a twist, a hinge. After a round with DBs.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            lift("ex-farmer-s-carry", 1, 8, 30),
            lift("ex-russian-twist", 2, 10),
            lift("ex-good-morning"),
            lift("ex-dead-bug", 2, 8),
        ),
        imageKey = "ex_farmer_s_carry",
        shownFor = setOf(ExtraEquipment.FREE_WEIGHTS),
    )

    val StretchFree = pack(
        id = "stretch-free",
        family = "stretch",
        title = "Stretch",
        caption = "A light good-morning, a hinge, a doorway.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            lift("ex-good-morning"),
            lift("ex-single-leg-romanian-deadlift"),
            lift("ex-doorway-chest-stretch"),
            lift("ex-deep-squat-hold", 1, 20),
        ),
        imageKey = "ex_good_morning",
        shownFor = setOf(ExtraEquipment.FREE_WEIGHTS),
    )

    val LowerBackFree = pack(
        id = "lower-back-free",
        family = "lower-back",
        title = "Lower back",
        caption = "A glute bridge, a hinge, a brace.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            lift("ex-barbell-glute-bridge", 2, 8),
            lift("ex-good-morning"),
            lift("ex-dead-bug", 2, 8),
            lift("ex-plank", 1, 40),
        ),
        imageKey = "ex_barbell_glute_bridge",
        shownFor = setOf(ExtraEquipment.FREE_WEIGHTS),
    )

    val HipsFree = pack(
        id = "hips-free",
        family = "hips",
        title = "Hips",
        caption = "Hip thrust, a split squat, a swing.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            lift("ex-hip-thrust", 2, 8),
            lift("ex-bulgarian-split-squat"),
            lift("ex-kettlebell-swing", 2, 10),
            lift("ex-goblet-squat", 2, 8),
        ),
        imageKey = "ex_hip_thrust",
        shownFor = setOf(ExtraEquipment.FREE_WEIGHTS),
    )

    val CoreFree = pack(
        id = "core-free",
        family = "core",
        title = "Core",
        caption = "Twists, a carry, a brace. Dumbbells, no stack.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            lift("ex-russian-twist", 2, 12),
            lift("ex-farmer-s-carry", 1, 8, 30),
            lift("ex-dead-bug", 2, 8),
            lift("ex-plank", 1, 40),
        ),
        imageKey = "ex_russian_twist",
        shownFor = setOf(ExtraEquipment.FREE_WEIGHTS),
    )

    val LowerBodyMachines = pack(
        id = "lower-body-machines",
        family = "lower-body",
        title = "Lower-body warm-up",
        caption = "Leg press, curl, hip machine, calves.",
        kind = AuxiliaryKind.WARMUP,
        lifts = listOf(
            lift("ex-leg-press", 2, 10),
            lift("ex-seated-leg-curl", 2, 10),
            lift("ex-hip-abduction-machine", 2, 12),
            lift("ex-seated-calf-raise", 1, 12),
        ),
        imageKey = "ex_leg_press",
        shownFor = setOf(ExtraEquipment.MACHINES),
    )

    val UpperBodyMachines = pack(
        id = "upper-body-machines",
        family = "upper-body",
        title = "Upper-body warm-up",
        caption = "Chest press, a seated row, reverse pec deck.",
        kind = AuxiliaryKind.WARMUP,
        lifts = listOf(
            lift("ex-machine-chest-press", 2, 8),
            lift("ex-machine-seated-row", 2, 10),
            lift("ex-reverse-pec-deck", 2, 12),
            lift("ex-lat-pulldown", 2, 10),
        ),
        imageKey = "ex_machine_chest_press",
        shownFor = setOf(ExtraEquipment.MACHINES),
    )

    val ShoulderMachines = pack(
        id = "shoulder-machines",
        family = "shoulder",
        title = "Shoulder warm-up",
        caption = "Machine laterals, reverse pec deck, face pulls.",
        kind = AuxiliaryKind.WARMUP,
        lifts = listOf(
            lift("ex-machine-lateral-raise", 2, 12),
            lift("ex-reverse-pec-deck", 2, 12),
            lift("ex-cable-lateral-raise", 2, 12),
            lift("ex-face-pull", 2, 12),
        ),
        imageKey = "ex_machine_lateral_raise",
        shownFor = setOf(ExtraEquipment.MACHINES),
    )

    val LowerBackMachines = pack(
        id = "lower-back-machines",
        family = "lower-back",
        title = "Lower back",
        caption = "A back extension, reverse hyper, a brace.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            lift("ex-hyper-pro-45-degree-back-extension", 2, 10, 30),
            lift("ex-hyper-pro-reverse-hyper", 2, 8, 30),
            lift("ex-dead-bug", 2, 8),
        ),
        imageKey = "ex_hyper_pro_45_degree_back_extension",
        shownFor = setOf(ExtraEquipment.MACHINES),
    )

    val all: List<AuxiliaryPack> = listOf(
        Golf, LowerBody, UpperBody, Shoulder,
        GolfCooldown, Stretch, LowerBack, Hips, Holds, Core,
        GolfNone, LowerBodyNone, UpperBodyNone, ShoulderNone,
        GolfCooldownNone, StretchNone, LowerBackNone, HipsNone, CoreNone,
        GolfFree, LowerBodyFree, UpperBodyFree, ShoulderFree,
        GolfCooldownFree, StretchFree, LowerBackFree, HipsFree, CoreFree,
        LowerBodyMachines, UpperBodyMachines, ShoulderMachines, LowerBackMachines,
    )

    val warmups: List<AuxiliaryPack> = all.filter { it.kind == AuxiliaryKind.WARMUP }

    val mobility: List<AuxiliaryPack> = all.filter { it.kind == AuxiliaryKind.MOBILITY }

    fun byId(id: String): AuxiliaryPack? = all.firstOrNull { it.id == id }

    fun forEquipment(kit: ExtraEquipment): List<AuxiliaryPack> =
        all.filter { kit in it.shownFor }

    fun visibleFor(kit: ExtraEquipment, usedPackIds: Set<String>): List<AuxiliaryPack> {
        val usedFamilies = usedPackIds.map { used -> byId(used)?.family ?: used }.toSet()
        return forEquipment(kit).filter { it.family !in usedFamilies }
    }

    fun usesGymStack(pack: AuxiliaryPack, catalog: Map<String, EquipmentType>): Boolean =
        pack.lifts.any { lift ->
            catalog[lift.exerciseId] in ExtraEquipment.GYM_STACK
        }

    private fun lift(
        exerciseId: String,
        sets: Int = 1,
        reps: Int = 8,
        restSeconds: Int = 20,
    ) = AuxiliaryLift(exerciseId, sets, reps, restSeconds)

    private fun pack(
        id: String,
        title: String,
        caption: String,
        kind: AuxiliaryKind,
        lifts: List<AuxiliaryLift>,
        imageKey: String,
        shownFor: Set<ExtraEquipment>,
        family: String = id,
    ) = AuxiliaryPack(
        id = id,
        title = title,
        caption = caption,
        kind = kind,
        lifts = lifts,
        imageKey = imageKey,
        family = family,
        shownFor = shownFor,
    )
}
