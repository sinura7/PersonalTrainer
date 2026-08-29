package com.sinura.personaltrainer.domain

/**
 * Short longevity blocks minted from catalog rows that already exist.
 *
 * Not a seed expansion. Missing ids are skipped at attach time so an
 * older catalog still yields a shorter pack rather than a crash.
 */
data class AuxiliaryLift(
    val exerciseId: String,
    val sets: Int,
    val reps: Int,
    val restSeconds: Int,
)

data class AuxiliaryPack(
    val id: String,
    val title: String,
    val caption: String,
    val lifts: List<AuxiliaryLift>,
)

object AuxiliaryPacks {
    val Stretch = AuxiliaryPack(
        id = "stretch",
        title = "Stretch",
        caption = "About eight minutes. Hips, calves, walk-outs. Any time of day.",
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
        lifts = listOf(
            AuxiliaryLift("ex-plank", 1, 40, 30),
            AuxiliaryLift("ex-side-plank", 1, 30, 20),
            AuxiliaryLift("ex-dead-bug", 2, 8, 20),
        ),
    )

    val all: List<AuxiliaryPack> = listOf(Stretch, LowerBack, Hips, Holds)

    fun byId(id: String): AuxiliaryPack? = all.firstOrNull { it.id == id }
}
