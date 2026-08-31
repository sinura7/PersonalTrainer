package com.sinura.personaltrainer.domain

/**
 * Short extra blocks minted from catalog rows that already exist.
 *
 * Not a seed expansion. Missing ids are skipped at attach time so an
 * older catalog still yields a shorter pack rather than a crash.
 *
 * Warm-ups prepare a session. Mobility packs are the longevity extras
 * (stretch, holds, core). Each pack is its own day block — not spliced
 * into the pinned workout.
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
)

object AuxiliaryPacks {
    val Golf = AuxiliaryPack(
        id = "golf",
        title = "Golf warm-up",
        caption = "Hips, rotation, shoulders. Exercises only — not a round.",
        kind = AuxiliaryKind.WARMUP,
        lifts = listOf(
            AuxiliaryLift("ex-hyper-pro-elephant-walk", 1, 10, 20),
            AuxiliaryLift("ex-hyper-pro-woodchop", 2, 8, 20),
            AuxiliaryLift("ex-hyper-pro-external-rotator", 2, 8, 20),
            AuxiliaryLift("ex-hyper-pro-face-pull", 2, 10, 20),
        ),
    )

    val LowerBody = AuxiliaryPack(
        id = "lower-body",
        title = "Lower-body warm-up",
        caption = "Squats, lunges, glutes. Before a lower-body session.",
        kind = AuxiliaryKind.WARMUP,
        lifts = listOf(
            AuxiliaryLift("ex-bodyweight-squat", 2, 10, 20),
            AuxiliaryLift("ex-walking-lunge", 1, 8, 20),
            AuxiliaryLift("ex-barbell-glute-bridge", 1, 8, 20),
            AuxiliaryLift("ex-hyper-pro-elephant-walk", 1, 10, 20),
        ),
    )

    val UpperBody = AuxiliaryPack(
        id = "upper-body",
        title = "Upper-body warm-up",
        caption = "Push-ups, rows, face pulls. Before an upper-body session.",
        kind = AuxiliaryKind.WARMUP,
        lifts = listOf(
            AuxiliaryLift("ex-push-up", 2, 8, 20),
            AuxiliaryLift("ex-face-pull", 2, 12, 20),
            AuxiliaryLift("ex-inverted-row", 1, 8, 20),
            AuxiliaryLift("ex-hyper-pro-external-rotator", 1, 10, 20),
        ),
    )

    val Shoulder = AuxiliaryPack(
        id = "shoulder",
        title = "Shoulder warm-up",
        caption = "Rotators, face pulls, raises. Before pressing.",
        kind = AuxiliaryKind.WARMUP,
        lifts = listOf(
            AuxiliaryLift("ex-hyper-pro-external-rotator", 2, 10, 20),
            AuxiliaryLift("ex-face-pull", 2, 12, 20),
            AuxiliaryLift("ex-lateral-raise", 2, 12, 20),
            AuxiliaryLift("ex-dumbbell-rear-delt-fly", 2, 12, 20),
        ),
    )

    val Stretch = AuxiliaryPack(
        id = "stretch",
        title = "Stretch",
        caption = "About eight minutes. Hips, calves, walk-outs. Any time of day.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            AuxiliaryLift("ex-hyper-pro-calf-stretch", 1, 8, 20),
            AuxiliaryLift("ex-hyper-pro-couch-stretch", 1, 8, 20),
            AuxiliaryLift("ex-hyper-pro-elephant-walk", 1, 10, 20),
            AuxiliaryLift("ex-hyper-pro-incline-pigeon", 1, 8, 20),
        ),
    )

    val LowerBack = AuxiliaryPack(
        id = "lower-back",
        title = "Lower back",
        caption = "About eight minutes. Extensions and a hold.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            AuxiliaryLift("ex-back-extension", 2, 10, 30),
            AuxiliaryLift("ex-dead-bug", 2, 8, 20),
            AuxiliaryLift("ex-plank", 1, 40, 20),
        ),
    )

    val Hips = AuxiliaryPack(
        id = "hips",
        title = "Hips",
        caption = "About eight minutes. Groin and hip openers.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            AuxiliaryLift("ex-hyper-pro-couch-stretch", 1, 8, 20),
            AuxiliaryLift("ex-hyper-pro-incline-pigeon", 1, 8, 20),
            AuxiliaryLift("ex-hyper-pro-elephant-walk", 1, 10, 20),
        ),
    )

    val Holds = AuxiliaryPack(
        id = "holds",
        title = "Holds",
        caption = "About six minutes. Static holds before bed or any time.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            AuxiliaryLift("ex-plank", 1, 40, 30),
            AuxiliaryLift("ex-side-plank", 1, 30, 20),
            AuxiliaryLift("ex-dead-bug", 2, 8, 20),
        ),
    )

    val Core = AuxiliaryPack(
        id = "core",
        title = "Core",
        caption = "About eight minutes. Crunches and leg raises. Not a static hold.",
        kind = AuxiliaryKind.MOBILITY,
        lifts = listOf(
            AuxiliaryLift("ex-hanging-leg-raise", 2, 8, 30),
            AuxiliaryLift("ex-machine-crunch", 2, 12, 30),
            AuxiliaryLift("ex-cable-crunch", 2, 12, 30),
        ),
    )

    val all: List<AuxiliaryPack> = listOf(
        Golf, LowerBody, UpperBody, Shoulder,
        Stretch, LowerBack, Hips, Holds, Core,
    )

    val warmups: List<AuxiliaryPack> = all.filter { it.kind == AuxiliaryKind.WARMUP }

    val mobility: List<AuxiliaryPack> = all.filter { it.kind == AuxiliaryKind.MOBILITY }

    fun byId(id: String): AuxiliaryPack? = all.firstOrNull { it.id == id }
}
