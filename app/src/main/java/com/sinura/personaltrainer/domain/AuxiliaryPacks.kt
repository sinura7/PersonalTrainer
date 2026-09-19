package com.sinura.personaltrainer.domain

/**
 * Short extra blocks minted from catalog rows.
 *
 * Missing ids are skipped at attach time so an older catalog still
 * yields a shorter pack rather than a crash.
 *
 * Warm-ups prepare a session. Mobility packs are the longevity extras
 * (stretch, holds, core) and the golf cool-down. Each pack is its own day
 * block — not spliced into the pinned workout.
 *
 * Ten Extra types × four kits = forty packs. Mixed is free weights plus
 * machines, not a silent copy of the machine list. Floor Extra
 * ([ExtraEquipment.NONE]) never includes gym-stack lifts.
 *
 * [shownFor] is the Extra second question: Bodyweight (none), Free weights,
 * Machines, Mixed. Catalog + these packs — not an LLM writing sessions.
 *
 * Hold rows store seconds in [AuxiliaryLift.reps]; [AuxiliaryBlocks] writes
 * them as hold time.
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
        caption = "A swing, a chop, rear delts, a face pull.",
        kind = AuxiliaryKind.WARMUP,
        lifts = listOf(
            lift("ex-kettlebell-swing", 2, 10),
            lift("ex-hyper-pro-woodchop", 2, 8),
            lift("ex-dumbbell-rear-delt-fly", 2, 12),
            lift("ex-face-pull", 2, 12),
        ),
        imageKey = "ex_face_pull",
        shownFor = setOf(ExtraEquipment.MIXED),
    )

    val LowerBody = pack(
        id = "lower-body",
        title = "Lower-body warm-up",
        caption = "A goblet squat, lunges, a leg press, a walk-out.",
        kind = AuxiliaryKind.WARMUP,
        lifts = listOf(
            lift("ex-goblet-squat", 2, 8),
            lift("ex-walking-lunge"),
            lift("ex-leg-press", 2, 10),
            lift("ex-hyper-pro-elephant-walk", 1, 10),
        ),
        imageKey = "ex_walking_lunge",
        shownFor = setOf(ExtraEquipment.MIXED),
    )

    val UpperBody = pack(
        id = "upper-body",
        title = "Upper-body warm-up",
        caption = "Push-ups, a DB row, a face pull, a chest press.",
        kind = AuxiliaryKind.WARMUP,
        lifts = listOf(
            lift("ex-push-up", 2, 8),
            lift("ex-one-arm-dumbbell-row", 2, 8),
            lift("ex-face-pull", 2, 12),
            lift("ex-machine-chest-press", 2, 8),
        ),
        imageKey = "ex_push_up",
        shownFor = setOf(ExtraEquipment.MIXED),
    )

    val Shoulder = pack(
        id = "shoulder",
        title = "Shoulder warm-up",
        caption = "Laterals, a face pull, rear delts, a rotator.",
        kind = AuxiliaryKind.WARMUP,
        lifts = listOf(
            lift("ex-lateral-raise", 2, 12),
            lift("ex-face-pull", 2, 12),
            lift("ex-dumbbell-rear-delt-fly", 2, 12),
            lift("ex-hyper-pro-external-rotator", 2, 10),
        ),
        imageKey = "ex_lateral_raise",
        shownFor = setOf(ExtraEquipment.MIXED),
    )

    /**
     * After a round, not before one: the warm-up above readies rotation, this
     * unloads the hips and calves that carried eighteen holes.
     */
    val GolfCooldown = pack(
        id = "golf-cooldown",
        title = "Golf cool-down",
        caption = "A couch stretch, a light hinge, a walk-out, calves. After a round.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            hold("ex-couch-stretch"),
            lift("ex-good-morning"),
            lift("ex-hyper-pro-elephant-walk", 1, 10),
            hold("ex-hyper-pro-calf-stretch"),
        ),
        imageKey = "ex_good_morning",
        shownFor = setOf(ExtraEquipment.MIXED),
    )

    val Stretch = pack(
        id = "stretch",
        title = "Stretch",
        caption = "A light good-morning, a couch stretch, a walk-out, ankle rocks.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            lift("ex-good-morning"),
            hold("ex-couch-stretch"),
            lift("ex-hyper-pro-elephant-walk", 1, 10),
            lift("ex-ankle-rocks", 2, 10),
        ),
        imageKey = "ex_ankle_rocks",
        shownFor = setOf(ExtraEquipment.MIXED),
    )

    val LowerBack = pack(
        id = "lower-back",
        title = "Lower back",
        caption = "An extension, a hinge, a brace, a reverse hyper.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            lift("ex-back-extension", 2, 10, 30),
            lift("ex-good-morning"),
            lift("ex-dead-bug", 2, 8),
            lift("ex-hyper-pro-reverse-hyper", 2, 8, 30),
        ),
        imageKey = "ex_back_extension",
        shownFor = setOf(ExtraEquipment.MIXED),
    )

    val Hips = pack(
        id = "hips",
        title = "Hips",
        caption = "A goblet squat, a hip machine, a split squat, a walk-out.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            lift("ex-goblet-squat", 2, 8),
            lift("ex-hip-abduction-machine", 2, 12),
            lift("ex-bulgarian-split-squat"),
            lift("ex-hyper-pro-elephant-walk", 1, 10),
        ),
        imageKey = "ex_hip_abduction_machine",
        shownFor = setOf(ExtraEquipment.MIXED),
    )

    val Holds = pack(
        id = "holds",
        title = "Holds",
        caption = "A carry, a hang, a side plank, a couch stretch.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            lift("ex-farmer-s-carry", 1, 8, 30),
            hold("ex-dead-hang", seconds = 30),
            hold("ex-side-plank", seconds = 30),
            hold("ex-hyper-pro-couch-stretch"),
        ),
        imageKey = "ex_side_plank",
        shownFor = setOf(ExtraEquipment.MIXED),
    )

    val Core = pack(
        id = "core",
        title = "Core",
        caption = "A twist, a cable crunch, a carry, hanging raises.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            lift("ex-russian-twist", 2, 12),
            lift("ex-cable-crunch", 2, 12, 30),
            lift("ex-farmer-s-carry", 1, 8, 30),
            lift("ex-hanging-leg-raise", 2, 8, 30),
        ),
        imageKey = "ex_hanging_leg_raise",
        shownFor = setOf(ExtraEquipment.MIXED),
    )

    val GolfNone = pack(
        id = "golf-none",
        family = "golf",
        title = "Golf warm-up",
        caption = "Joint circles, a floor chop, Y-holds, squats. No kit.",
        kind = AuxiliaryKind.WARMUP,
        lifts = listOf(
            lift("ex-joint-circles", 2, 8),
            lift("ex-floor-woodchop", 2, 8),
            hold("ex-y-hold", sets = 2, seconds = 20),
            lift("ex-bodyweight-squat", 2, 10),
        ),
        imageKey = "ex_floor_woodchop",
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
            hold("ex-wall-sit", seconds = 30),
            lift("ex-single-leg-calf-raise", 1, 10),
            hold("ex-deep-squat-hold", seconds = 30),
        ),
        imageKey = "ex_wall_sit",
        shownFor = setOf(ExtraEquipment.NONE),
    )

    val UpperBodyNone = pack(
        id = "upper-body-none",
        family = "upper-body",
        title = "Upper-body warm-up",
        caption = "Push-ups, inverted rows, a diamond, a scap hang.",
        kind = AuxiliaryKind.WARMUP,
        lifts = listOf(
            lift("ex-push-up", 2, 8),
            lift("ex-inverted-row"),
            lift("ex-diamond-push-up", 2, 8),
            hold("ex-scapular-hang", seconds = 20),
        ),
        imageKey = "ex_diamond_push_up",
        shownFor = setOf(ExtraEquipment.NONE),
    )

    val ShoulderNone = pack(
        id = "shoulder-none",
        family = "shoulder",
        title = "Shoulder warm-up",
        caption = "Y-holds, inverted rows, a doorway, a scap hang.",
        kind = AuxiliaryKind.WARMUP,
        lifts = listOf(
            hold("ex-y-hold", sets = 2, seconds = 20),
            lift("ex-inverted-row"),
            hold("ex-doorway-chest-stretch"),
            hold("ex-scapular-hang", seconds = 20),
        ),
        imageKey = "ex_inverted_row",
        shownFor = setOf(ExtraEquipment.NONE),
    )

    val GolfCooldownNone = pack(
        id = "golf-cooldown-none",
        family = "golf-cooldown",
        title = "Golf cool-down",
        caption = "Couch, pigeon, calves, hamstrings. After a round on the floor.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            hold("ex-couch-stretch"),
            hold("ex-pigeon-stretch"),
            hold("ex-calf-stretch"),
            hold("ex-hamstring-stretch"),
        ),
        imageKey = "ex_pigeon_stretch",
        shownFor = setOf(ExtraEquipment.NONE),
    )

    val StretchNone = pack(
        id = "stretch-none",
        family = "stretch",
        title = "Stretch",
        caption = "Couch, 90/90, a doorway, ankle rocks. The floor.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            hold("ex-couch-stretch"),
            hold("ex-90-90-hips"),
            hold("ex-doorway-chest-stretch"),
            lift("ex-ankle-rocks", 2, 10),
        ),
        imageKey = "ex_couch_stretch",
        shownFor = setOf(ExtraEquipment.NONE),
    )

    val LowerBackNone = pack(
        id = "lower-back-none",
        family = "lower-back",
        title = "Lower back",
        caption = "Dead bugs, a plank, a side plank, a back extension.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            lift("ex-dead-bug", 2, 8),
            hold("ex-plank", seconds = 40),
            hold("ex-side-plank", seconds = 30),
            lift("ex-back-extension", 2, 10, 30),
        ),
        imageKey = "ex_dead_bug",
        shownFor = setOf(ExtraEquipment.NONE),
    )

    val HipsNone = pack(
        id = "hips-none",
        family = "hips",
        title = "Hips",
        caption = "A deep squat, a couch stretch, pigeon, 90/90.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            hold("ex-deep-squat-hold", seconds = 30),
            hold("ex-couch-stretch"),
            hold("ex-pigeon-stretch"),
            hold("ex-90-90-hips"),
        ),
        imageKey = "ex_90_90_hips",
        shownFor = setOf(ExtraEquipment.NONE),
    )

    val HoldsNone = pack(
        id = "holds-none",
        family = "holds",
        title = "Holds",
        caption = "Plank, side plank, a wall sit, a deep squat. Floor only.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            hold("ex-plank", seconds = 40),
            hold("ex-side-plank", seconds = 30),
            hold("ex-wall-sit", seconds = 30),
            hold("ex-deep-squat-hold", seconds = 30),
        ),
        imageKey = "ex_plank",
        shownFor = setOf(ExtraEquipment.NONE),
    )

    val CoreNone = pack(
        id = "core-none",
        family = "core",
        title = "Core",
        caption = "Dead bugs, planks, an ab wheel. No machine.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            lift("ex-dead-bug", 2, 8),
            hold("ex-plank", seconds = 40),
            hold("ex-side-plank", seconds = 30),
            lift("ex-ab-wheel-rollout", 2, 8),
        ),
        imageKey = "ex_ab_wheel_rollout",
        shownFor = setOf(ExtraEquipment.NONE),
    )

    val GolfFree = pack(
        id = "golf-free",
        family = "golf",
        title = "Golf warm-up",
        caption = "Swings, a twist, rear delts, a goblet squat.",
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
        caption = "Goblet squats, a DB hinge, lunges, a swing.",
        kind = AuxiliaryKind.WARMUP,
        lifts = listOf(
            lift("ex-goblet-squat", 2, 8),
            lift("ex-dumbbell-romanian-deadlift", 2, 8),
            lift("ex-walking-lunge"),
            lift("ex-kettlebell-swing", 2, 10),
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
        caption = "A light hinge, a single-leg RDL, a couch stretch, calves.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            lift("ex-good-morning"),
            lift("ex-single-leg-romanian-deadlift"),
            hold("ex-couch-stretch"),
            hold("ex-calf-stretch"),
        ),
        imageKey = "ex_single_leg_romanian_deadlift",
        shownFor = setOf(ExtraEquipment.FREE_WEIGHTS),
    )

    val StretchFree = pack(
        id = "stretch-free",
        family = "stretch",
        title = "Stretch",
        caption = "A couch stretch, hamstrings, a light good-morning, ankle rocks.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            hold("ex-couch-stretch"),
            hold("ex-hamstring-stretch"),
            lift("ex-good-morning"),
            lift("ex-ankle-rocks", 2, 10),
        ),
        imageKey = "ex_hamstring_stretch",
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
            hold("ex-plank", seconds = 40),
        ),
        imageKey = "ex_barbell_glute_bridge",
        shownFor = setOf(ExtraEquipment.FREE_WEIGHTS),
    )

    val HipsFree = pack(
        id = "hips-free",
        family = "hips",
        title = "Hips",
        caption = "Hip thrust, a split squat, a swing, a goblet squat.",
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

    val HoldsFree = pack(
        id = "holds-free",
        family = "holds",
        title = "Holds",
        caption = "A carry, a plank, Y-holds, a dead hang.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            lift("ex-farmer-s-carry", 1, 8, 30),
            hold("ex-plank", seconds = 40),
            hold("ex-y-hold", sets = 2, seconds = 20),
            hold("ex-dead-hang", seconds = 30),
        ),
        imageKey = "ex_dead_hang",
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
            hold("ex-plank", seconds = 40),
        ),
        imageKey = "ex_russian_twist",
        shownFor = setOf(ExtraEquipment.FREE_WEIGHTS),
    )

    val GolfMachines = pack(
        id = "golf-machines",
        family = "golf",
        title = "Golf warm-up",
        caption = "A walk-out, a chop, a rotator, a face pull.",
        kind = AuxiliaryKind.WARMUP,
        lifts = listOf(
            lift("ex-hyper-pro-elephant-walk", 1, 10),
            lift("ex-hyper-pro-woodchop", 2, 8),
            lift("ex-hyper-pro-external-rotator", 2, 8),
            lift("ex-face-pull", 2, 12),
        ),
        imageKey = "ex_hyper_pro_woodchop",
        shownFor = setOf(ExtraEquipment.MACHINES),
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

    val GolfCooldownMachines = pack(
        id = "golf-cooldown-machines",
        family = "golf-cooldown",
        title = "Golf cool-down",
        caption = "Couch, pigeon, calves, a walk-out. After a round on the bench.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            hold("ex-hyper-pro-couch-stretch"),
            hold("ex-hyper-pro-incline-pigeon"),
            hold("ex-hyper-pro-calf-stretch"),
            lift("ex-hyper-pro-elephant-walk", 1, 10),
        ),
        imageKey = "ex_hyper_pro_couch_stretch",
        shownFor = setOf(ExtraEquipment.MACHINES),
    )

    val StretchMachines = pack(
        id = "stretch-machines",
        family = "stretch",
        title = "Stretch",
        caption = "Calves, couch, walk-outs, pigeon. Hyper Pro.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            hold("ex-hyper-pro-calf-stretch"),
            hold("ex-hyper-pro-couch-stretch"),
            lift("ex-hyper-pro-elephant-walk", 1, 10),
            hold("ex-hyper-pro-incline-pigeon"),
        ),
        imageKey = "ex_hyper_pro_calf_stretch",
        shownFor = setOf(ExtraEquipment.MACHINES),
    )

    val LowerBackMachines = pack(
        id = "lower-back-machines",
        family = "lower-back",
        title = "Lower back",
        caption = "A back extension, reverse hyper, a QL raise.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            lift("ex-hyper-pro-45-degree-back-extension", 2, 10, 30),
            lift("ex-hyper-pro-reverse-hyper", 2, 8, 30),
            lift("ex-hyper-pro-ql-raise", 2, 8, 30),
        ),
        imageKey = "ex_hyper_pro_45_degree_back_extension",
        shownFor = setOf(ExtraEquipment.MACHINES),
    )

    val HipsMachines = pack(
        id = "hips-machines",
        family = "hips",
        title = "Hips",
        caption = "Couch, pigeon, a hip machine, a cable kickback.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            hold("ex-hyper-pro-couch-stretch"),
            hold("ex-hyper-pro-incline-pigeon"),
            lift("ex-hip-abduction-machine", 2, 12),
            lift("ex-cable-kickback", 2, 12),
        ),
        imageKey = "ex_hyper_pro_incline_pigeon",
        shownFor = setOf(ExtraEquipment.MACHINES),
    )

    val HoldsMachines = pack(
        id = "holds-machines",
        family = "holds",
        title = "Holds",
        caption = "A dead hang, a scap hang, a couch stretch, Y-holds.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            hold("ex-dead-hang", seconds = 30),
            hold("ex-scapular-hang", seconds = 20),
            hold("ex-hyper-pro-couch-stretch"),
            hold("ex-y-hold", sets = 2, seconds = 20),
        ),
        imageKey = "ex_scapular_hang",
        shownFor = setOf(ExtraEquipment.MACHINES),
    )

    val CoreMachines = pack(
        id = "core-machines",
        family = "core",
        title = "Core",
        caption = "A machine crunch, a cable crunch, Hyper Pro sit-up and raise.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            lift("ex-machine-crunch", 2, 12, 30),
            lift("ex-cable-crunch", 2, 12, 30),
            lift("ex-hyper-pro-sit-up", 2, 10, 30),
            lift("ex-hyper-pro-leg-raise", 2, 8, 30),
        ),
        imageKey = "ex_machine_crunch",
        shownFor = setOf(ExtraEquipment.MACHINES),
    )

    val all: List<AuxiliaryPack> = listOf(
        Golf, LowerBody, UpperBody, Shoulder,
        GolfCooldown, Stretch, LowerBack, Hips, Holds, Core,
        GolfNone, LowerBodyNone, UpperBodyNone, ShoulderNone,
        GolfCooldownNone, StretchNone, LowerBackNone, HipsNone, HoldsNone, CoreNone,
        GolfFree, LowerBodyFree, UpperBodyFree, ShoulderFree,
        GolfCooldownFree, StretchFree, LowerBackFree, HipsFree, HoldsFree, CoreFree,
        GolfMachines, LowerBodyMachines, UpperBodyMachines, ShoulderMachines,
        GolfCooldownMachines, StretchMachines, LowerBackMachines, HipsMachines,
        HoldsMachines, CoreMachines,
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

    private fun hold(
        exerciseId: String,
        sets: Int = 1,
        seconds: Int = 45,
        restSeconds: Int = 20,
    ) = AuxiliaryLift(exerciseId, sets, seconds, restSeconds)

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
