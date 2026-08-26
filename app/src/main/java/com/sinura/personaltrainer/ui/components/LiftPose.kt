package com.sinura.personaltrainer.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.DefaultExercises
import com.sinura.personaltrainer.domain.EquipmentType
import kotlin.math.min

/**
 * One posed silhouette for a lift family.
 *
 * A person is an athletic plate figure: a torso (wide shoulders, nipped
 * waist, hips), tapered limbs that grow out of its corners, inner muscle
 * plates, then kit in the hands. Heat recolors those inner plates — pecs,
 * abs, delts, quad teardrops — it does not replace the body. Families
 * share a pose so 101 lifts stay one person.
 *
 * Body heat stays on the standing [drawTemperFigure]. Thumbs are identity:
 * the pose plus a fixed Heat3 on the working plates.
 */
internal enum class LiftPose {
    SQUAT,
    HINGE,
    LUNGE,
    HORIZONTAL_PRESS,
    VERTICAL_PRESS,
    FLY,
    VERTICAL_PULL,
    HORIZONTAL_PULL,
    ARM_CURL,
    ARM_EXT,
    HIP,
    CORE_FLOOR,
    SEATED_MACHINE,
    CARRY,
    ANATOMY,
}

internal fun poseFor(movementKey: String?): LiftPose = when (movementKey) {
    "squat" -> LiftPose.SQUAT
    "lunge", "step-up" -> LiftPose.LUNGE
    "leg-press", "leg-extension", "leg-curl", "calf-raise" -> LiftPose.SEATED_MACHINE
    "deadlift", "romanian-deadlift", "good-morning", "kettlebell-swing",
    "pull-through", "back-extension", "nordic-curl",
    -> LiftPose.HINGE
    "hip-thrust", "hip-abduction", "glute-kickback" -> LiftPose.HIP
    "bench-press", "push-up", "dip" -> LiftPose.HORIZONTAL_PRESS
    "overhead-press" -> LiftPose.VERTICAL_PRESS
    "chest-fly", "lateral-raise", "rear-delt" -> LiftPose.FLY
    "row", "pullover", "shrug" -> LiftPose.HORIZONTAL_PULL
    "pulldown", "pull-up" -> LiftPose.VERTICAL_PULL
    "curl" -> LiftPose.ARM_CURL
    "triceps-extension" -> LiftPose.ARM_EXT
    "plank", "crunch", "sit-up", "leg-raise", "rollout", "dead-bug", "twist" ->
        LiftPose.CORE_FLOOR
    "carry" -> LiftPose.CARRY
    else -> LiftPose.ANATOMY
}

internal fun DrawScope.drawLiftPose(
    pose: LiftPose,
    equipment: EquipmentType,
    fill: (CanonicalMuscle?) -> Color,
    kit: Color,
) {
    personInk(pose).forEach { paintInk(it, fill(it.muscle)) }
    kitInk(pose, equipment).forEach { paintInk(it, kit) }
}

internal data class PosePlate(
    val muscle: CanonicalMuscle?,
    val points: List<Pair<Float, Float>>,
)

/**
 * One mark of a posed figure. Canvas and the SVG board paint this list so the
 * geometry the tests dump is the geometry the thumb draws.
 *
 * [Limb] is the kit language (bar shafts). The person is [Fill] (torso,
 * pecs) plus [Taper] (limbs that thin toward the joint).
 */
internal sealed class PoseInk {
    abstract val muscle: CanonicalMuscle?

    data class Limb(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val width: Float,
        override val muscle: CanonicalMuscle?,
    ) : PoseInk()

    data class Taper(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val w1: Float,
        val w2: Float,
        override val muscle: CanonicalMuscle?,
    ) : PoseInk()

    data class Fill(
        val points: List<Pair<Float, Float>>,
        override val muscle: CanonicalMuscle?,
    ) : PoseInk()

    data class Dot(
        val x: Float,
        val y: Float,
        val r: Float,
        override val muscle: CanonicalMuscle?,
    ) : PoseInk()

    data class Oval(
        val x: Float,
        val y: Float,
        val rx: Float,
        val ry: Float,
        override val muscle: CanonicalMuscle?,
    ) : PoseInk()

    data class Rect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        override val muscle: CanonicalMuscle?,
    ) : PoseInk()
}

internal fun personInk(pose: LiftPose): List<PoseInk> {
    if (pose == LiftPose.ANATOMY) return emptyList()
    val figure = figureFor(pose)
    return figure.structure() + anatomyFor(pose, figure)
}

internal fun kitInk(pose: LiftPose, equipment: EquipmentType): List<PoseInk> =
    when (pose) {
        LiftPose.ANATOMY -> emptyList()
        LiftPose.SQUAT -> squatKit(equipment)
        LiftPose.HINGE -> hingeKit(equipment)
        LiftPose.LUNGE -> handBells(equipment, 0.24f, 0.56f, 0.76f, 0.56f)
        LiftPose.HORIZONTAL_PRESS -> pressKit(equipment)
        LiftPose.VERTICAL_PRESS -> overheadKit(equipment)
        LiftPose.FLY -> flyKit(equipment)
        LiftPose.VERTICAL_PULL -> pullKit(equipment)
        LiftPose.HORIZONTAL_PULL -> rowKit(equipment)
        LiftPose.ARM_CURL -> handBells(equipment, 0.26f, 0.28f, 0.74f, 0.28f)
        LiftPose.ARM_EXT -> extensionKit(equipment)
        LiftPose.HIP -> hipKit(equipment)
        LiftPose.CORE_FLOOR -> coreKit(equipment)
        LiftPose.SEATED_MACHINE -> seatedKit(equipment)
        LiftPose.CARRY -> handBells(equipment, 0.26f, 0.62f, 0.74f, 0.62f)
    }

internal fun platesForPose(pose: LiftPose): List<PosePlate> =
    personInk(pose).map { it.toPlate() }

internal fun kitPlates(pose: LiftPose, equipment: EquipmentType): List<List<Pair<Float, Float>>> =
    kitInk(pose, equipment).map { it.toPlate().points }

/** Every shipped family has a pose, so a new catalog row cannot silently stand still. */
internal fun poseCoversTheCatalog(): Boolean =
    DefaultExercises.MOVEMENT_FAMILIES.all { poseFor(it) != LiftPose.ANATOMY }

internal fun poseAndKitPoints(
    pose: LiftPose,
    equipment: EquipmentType,
): List<Pair<Float, Float>> =
    platesForPose(pose).flatMap { it.points } + kitPlates(pose, equipment).flatten()

private const val NECK_W1 = 0.050f
private const val NECK_W2 = 0.056f
private const val THIGH_W1 = 0.118f
private const val THIGH_W2 = 0.078f
private const val CALF_W1 = 0.078f
private const val CALF_W2 = 0.048f
private const val ARM_W1 = 0.100f
private const val ARM_W2 = 0.070f
private const val FORE_W1 = 0.062f
private const val FORE_W2 = 0.046f

private data class Figure(
    val head: Pair<Float, Float>,
    val sL: Pair<Float, Float>,
    val sR: Pair<Float, Float>,
    val eL: Pair<Float, Float>,
    val eR: Pair<Float, Float>,
    val wL: Pair<Float, Float>,
    val wR: Pair<Float, Float>,
    val hL: Pair<Float, Float>,
    val hR: Pair<Float, Float>,
    val kL: Pair<Float, Float>,
    val kR: Pair<Float, Float>,
    val aL: Pair<Float, Float>,
    val aR: Pair<Float, Float>,
    val side: Boolean = false,
    val depth: Float = 0.10f,
    val headRx: Float = 0.046f,
    val headRy: Float = 0.052f,
    val handRx: Float = 0.028f,
    val handRy: Float = 0.022f,
    val footRx: Float = 0.055f,
    val footRy: Float = 0.018f,
) {
    val midS: Pair<Float, Float> get() = lerp(sL, sR, 0.5f)
    val midH: Pair<Float, Float> get() = lerp(hL, hR, 0.5f)
    // Plant limbs inside the torso so quadratic smoothing of the
    // shoulder/hip corners cannot open a pit-coloured gap.
    val armRootL: Pair<Float, Float> get() = lerp(sL, midS, 0.10f).let { it.first to (it.second + 0.008f) }
    val armRootR: Pair<Float, Float> get() = lerp(sR, midS, 0.10f).let { it.first to (it.second + 0.008f) }
    val legRootL: Pair<Float, Float> get() = lerp(hL, midH, 0.08f)
    val legRootR: Pair<Float, Float> get() = lerp(hR, midH, 0.08f)
}

private fun figureFor(pose: LiftPose): Figure = when (pose) {
    LiftPose.SQUAT -> Figure(
        head = 0.50f to 0.145f,
        sL = 0.34f to 0.255f, sR = 0.66f to 0.255f,
        eL = 0.24f to 0.195f, eR = 0.76f to 0.195f,
        wL = 0.18f to 0.160f, wR = 0.82f to 0.160f,
        hL = 0.36f to 0.50f, hR = 0.64f to 0.50f,
        kL = 0.26f to 0.68f, kR = 0.74f to 0.68f,
        aL = 0.30f to 0.90f, aR = 0.70f to 0.90f,
        footRx = 0.058f,
    )
    LiftPose.HINGE -> Figure(
        head = 0.74f to 0.22f,
        sL = 0.56f to 0.33f, sR = 0.53f to 0.36f,
        eL = 0.62f to 0.50f, eR = 0.58f to 0.52f,
        wL = 0.70f to 0.64f, wR = 0.66f to 0.66f,
        hL = 0.40f to 0.50f, hR = 0.37f to 0.53f,
        kL = 0.44f to 0.70f, kR = 0.38f to 0.72f,
        aL = 0.42f to 0.90f, aR = 0.36f to 0.90f,
        side = true, depth = 0.095f, headRx = 0.042f, headRy = 0.048f, footRx = 0.062f,
    )
    LiftPose.LUNGE -> Figure(
        head = 0.50f to 0.100f,
        sL = 0.35f to 0.225f, sR = 0.65f to 0.225f,
        eL = 0.26f to 0.40f, eR = 0.74f to 0.40f,
        wL = 0.22f to 0.56f, wR = 0.78f to 0.56f,
        hL = 0.42f to 0.44f, hR = 0.58f to 0.44f,
        kL = 0.30f to 0.60f, kR = 0.60f to 0.68f,
        aL = 0.26f to 0.76f, aR = 0.58f to 0.90f,
    )
    LiftPose.HORIZONTAL_PRESS -> Figure(
        head = 0.13f to 0.39f,
        sL = 0.30f to 0.39f, sR = 0.30f to 0.45f,
        eL = 0.30f to 0.22f, eR = 0.33f to 0.26f,
        wL = 0.30f to 0.09f, wR = 0.33f to 0.13f,
        hL = 0.56f to 0.42f, hR = 0.56f to 0.48f,
        kL = 0.72f to 0.56f, kR = 0.74f to 0.60f,
        aL = 0.78f to 0.84f, aR = 0.80f to 0.84f,
        side = true, depth = 0.092f, headRx = 0.042f, headRy = 0.048f, footRx = 0.062f,
    )
    LiftPose.VERTICAL_PRESS -> Figure(
        head = 0.50f to 0.210f,
        sL = 0.35f to 0.335f, sR = 0.65f to 0.335f,
        eL = 0.28f to 0.170f, eR = 0.72f to 0.170f,
        wL = 0.30f to 0.065f, wR = 0.70f to 0.065f,
        hL = 0.38f to 0.52f, hR = 0.62f to 0.52f,
        kL = 0.39f to 0.72f, kR = 0.61f to 0.72f,
        aL = 0.39f to 0.92f, aR = 0.61f to 0.92f,
    )
    LiftPose.FLY -> Figure(
        head = 0.50f to 0.110f,
        sL = 0.35f to 0.250f, sR = 0.65f to 0.250f,
        eL = 0.18f to 0.32f, eR = 0.82f to 0.32f,
        wL = 0.07f to 0.38f, wR = 0.93f to 0.38f,
        hL = 0.38f to 0.50f, hR = 0.62f to 0.50f,
        kL = 0.39f to 0.72f, kR = 0.61f to 0.72f,
        aL = 0.39f to 0.92f, aR = 0.61f to 0.92f,
    )
    LiftPose.VERTICAL_PULL -> Figure(
        head = 0.50f to 0.270f,
        sL = 0.35f to 0.375f, sR = 0.65f to 0.375f,
        eL = 0.22f to 0.170f, eR = 0.78f to 0.170f,
        wL = 0.16f to 0.072f, wR = 0.84f to 0.072f,
        hL = 0.38f to 0.56f, hR = 0.62f to 0.56f,
        kL = 0.39f to 0.74f, kR = 0.61f to 0.74f,
        aL = 0.39f to 0.92f, aR = 0.61f to 0.92f,
    )
    LiftPose.HORIZONTAL_PULL -> Figure(
        head = 0.72f to 0.16f,
        sL = 0.54f to 0.27f, sR = 0.51f to 0.30f,
        eL = 0.38f to 0.30f, eR = 0.42f to 0.34f,
        wL = 0.26f to 0.34f, wR = 0.30f to 0.38f,
        hL = 0.42f to 0.50f, hR = 0.39f to 0.53f,
        kL = 0.46f to 0.70f, kR = 0.40f to 0.72f,
        aL = 0.44f to 0.90f, aR = 0.38f to 0.90f,
        side = true, depth = 0.094f, headRx = 0.042f, headRy = 0.048f, footRx = 0.062f,
    )
    LiftPose.ARM_CURL -> Figure(
        head = 0.50f to 0.092f,
        sL = 0.35f to 0.230f, sR = 0.65f to 0.230f,
        eL = 0.28f to 0.46f, eR = 0.72f to 0.46f,
        wL = 0.24f to 0.27f, wR = 0.76f to 0.27f,
        hL = 0.38f to 0.50f, hR = 0.62f to 0.50f,
        kL = 0.39f to 0.72f, kR = 0.61f to 0.72f,
        aL = 0.39f to 0.92f, aR = 0.61f to 0.92f,
    )
    LiftPose.ARM_EXT -> Figure(
        head = 0.50f to 0.210f,
        sL = 0.35f to 0.335f, sR = 0.65f to 0.335f,
        eL = 0.40f to 0.110f, eR = 0.60f to 0.110f,
        wL = 0.46f to 0.250f, wR = 0.54f to 0.250f,
        hL = 0.38f to 0.52f, hR = 0.62f to 0.52f,
        kL = 0.39f to 0.72f, kR = 0.61f to 0.72f,
        aL = 0.39f to 0.92f, aR = 0.61f to 0.92f,
    )
    LiftPose.HIP -> Figure(
        head = 0.13f to 0.60f,
        sL = 0.28f to 0.55f, sR = 0.28f to 0.60f,
        eL = 0.20f to 0.70f, eR = 0.22f to 0.74f,
        wL = 0.14f to 0.82f, wR = 0.16f to 0.84f,
        hL = 0.50f to 0.33f, hR = 0.50f to 0.38f,
        kL = 0.68f to 0.48f, kR = 0.70f to 0.52f,
        aL = 0.80f to 0.70f, aR = 0.82f to 0.72f,
        side = true, depth = 0.092f, headRx = 0.042f, headRy = 0.048f, footRx = 0.062f,
    )
    LiftPose.CORE_FLOOR -> Figure(
        head = 0.22f to 0.26f,
        sL = 0.34f to 0.37f, sR = 0.36f to 0.41f,
        eL = 0.42f to 0.22f, eR = 0.44f to 0.26f,
        wL = 0.54f to 0.18f, wR = 0.56f to 0.22f,
        hL = 0.48f to 0.62f, hR = 0.50f to 0.66f,
        kL = 0.66f to 0.46f, kR = 0.68f to 0.50f,
        aL = 0.80f to 0.58f, aR = 0.82f to 0.62f,
        side = true, depth = 0.094f, headRx = 0.042f, headRy = 0.048f, footRx = 0.062f,
    )
    LiftPose.SEATED_MACHINE -> Figure(
        head = 0.38f to 0.125f,
        sL = 0.36f to 0.255f, sR = 0.42f to 0.275f,
        eL = 0.52f to 0.30f, eR = 0.54f to 0.34f,
        wL = 0.64f to 0.28f, wR = 0.66f to 0.32f,
        hL = 0.40f to 0.50f, hR = 0.44f to 0.54f,
        kL = 0.62f to 0.50f, kR = 0.64f to 0.54f,
        aL = 0.64f to 0.76f, aR = 0.66f to 0.78f,
        side = true, depth = 0.092f, headRx = 0.042f, headRy = 0.048f, footRx = 0.062f,
    )
    LiftPose.CARRY -> Figure(
        head = 0.50f to 0.085f,
        sL = 0.34f to 0.215f, sR = 0.66f to 0.215f,
        eL = 0.26f to 0.42f, eR = 0.74f to 0.42f,
        wL = 0.24f to 0.62f, wR = 0.76f to 0.62f,
        hL = 0.38f to 0.48f, hR = 0.62f to 0.48f,
        kL = 0.39f to 0.70f, kR = 0.61f to 0.70f,
        aL = 0.39f to 0.92f, aR = 0.61f to 0.92f,
    )
    LiftPose.ANATOMY -> error("standing anatomy is drawTemperFigure")
}

private fun Figure.structure(): List<PoseInk> {
    val ink = ArrayList<PoseInk>(28)
    ink += fill(if (side) torsoSide(sL, hL, depth) else torsoFront(sL, sR, hL, hR), null)
    ink += taper(head, midS, NECK_W1, NECK_W2, null)
    ink += oval(head, headRx, headRy, null)
    ink += taper(legRootL, kL, THIGH_W1, THIGH_W2, null)
    ink += taper(legRootR, kR, THIGH_W1, THIGH_W2, null)
    ink += taper(kL, aL, CALF_W1, CALF_W2, null)
    ink += taper(kR, aR, CALF_W1, CALF_W2, null)
    ink += oval(hL, 0.058f, 0.050f, null)
    ink += oval(hR, 0.058f, 0.050f, null)
    ink += oval(kL, 0.046f, 0.040f, null)
    ink += oval(kR, 0.046f, 0.040f, null)
    ink += taper(armRootL, eL, ARM_W1, ARM_W2, null)
    ink += taper(eL, wL, FORE_W1, FORE_W2, null)
    if (!side) {
        ink += taper(armRootR, eR, ARM_W1, ARM_W2, null)
        ink += taper(eR, wR, FORE_W1, FORE_W2, null)
    }
    ink += oval(eL, 0.036f, 0.034f, null)
    if (!side) ink += oval(eR, 0.036f, 0.034f, null)
    ink += oval(wL, handRx, handRy, null)
    if (!side) ink += oval(wR, handRx, handRy, null)
    ink += oval(aL, footRx, footRy, null)
    ink += oval(aR, footRx, footRy, null)
    ink += oval(sL, 0.055f, 0.048f, null)
    if (!side) ink += oval(sR, 0.055f, 0.048f, null)
    return ink
}

private fun anatomyFor(pose: LiftPose, f: Figure): List<PoseInk> {
    val base = if (f.side) f.sideMap() else f.frontMap()
    val extras = when (pose) {
        LiftPose.VERTICAL_PULL -> f.backWings()
        LiftPose.LUNGE -> listOf(
            taper(lerp(f.hL, f.kL, 0.08f), lerp(f.hL, f.kL, 0.90f), 0.060f, 0.042f, CanonicalMuscle.HAMSTRINGS),
        )
        else -> emptyList()
    }
    return (base + extras).heatOnTop(pose)
}

private fun List<PoseInk>.heatOnTop(pose: LiftPose): List<PoseInk> {
    val muscle = when (pose) {
        LiftPose.SQUAT, LiftPose.LUNGE, LiftPose.SEATED_MACHINE -> CanonicalMuscle.QUADRICEPS
        LiftPose.HINGE, LiftPose.HIP -> CanonicalMuscle.GLUTES
        LiftPose.HORIZONTAL_PRESS, LiftPose.FLY -> CanonicalMuscle.CHEST
        LiftPose.VERTICAL_PRESS -> CanonicalMuscle.SHOULDERS
        LiftPose.VERTICAL_PULL, LiftPose.HORIZONTAL_PULL -> CanonicalMuscle.BACK
        LiftPose.ARM_CURL -> CanonicalMuscle.BICEPS
        LiftPose.ARM_EXT -> CanonicalMuscle.TRICEPS
        LiftPose.CORE_FLOOR, LiftPose.CARRY -> CanonicalMuscle.CORE
        LiftPose.ANATOMY -> return this
    }
    return filter { it.muscle != muscle } + filter { it.muscle == muscle }
}

private fun Figure.frontMap(): List<PoseInk> {
    val chest = lerp(midS, midH, 0.20f)
    val abs1 = lerp(midS, midH, 0.40f)
    val abs2 = lerp(midS, midH, 0.54f)
    val abs3 = lerp(midS, midH, 0.68f)
    val waistY = lerp(midS, midH, 0.38f).second
    return listOf(
        oval(sL, 0.038f, 0.034f, CanonicalMuscle.SHOULDERS),
        oval(sR, 0.038f, 0.034f, CanonicalMuscle.SHOULDERS),
        taper(lerp(sL, eL, 0.18f), lerp(sL, eL, 0.92f), 0.046f, 0.036f, CanonicalMuscle.TRICEPS),
        taper(lerp(sR, eR, 0.18f), lerp(sR, eR, 0.92f), 0.046f, 0.036f, CanonicalMuscle.TRICEPS),
        taper(lerp(sL, eL, 0.12f), lerp(sL, eL, 0.82f), 0.064f, 0.048f, CanonicalMuscle.BICEPS),
        oval(lerp(sL, eL, 0.42f), 0.028f, 0.022f, CanonicalMuscle.BICEPS),
        taper(lerp(sR, eR, 0.12f), lerp(sR, eR, 0.82f), 0.064f, 0.048f, CanonicalMuscle.BICEPS),
        oval(lerp(sR, eR, 0.42f), 0.028f, 0.022f, CanonicalMuscle.BICEPS),
        fill(pec(chest.first - 0.004f, chest.second, -1f), CanonicalMuscle.CHEST),
        fill(pec(chest.first + 0.004f, chest.second, 1f), CanonicalMuscle.CHEST),
        oval(abs1.first - 0.022f to abs1.second, 0.020f, 0.016f, CanonicalMuscle.CORE),
        oval(abs1.first + 0.022f to abs1.second, 0.020f, 0.016f, CanonicalMuscle.CORE),
        oval(abs2.first - 0.021f to abs2.second, 0.019f, 0.015f, CanonicalMuscle.CORE),
        oval(abs2.first + 0.021f to abs2.second, 0.019f, 0.015f, CanonicalMuscle.CORE),
        oval(abs3.first - 0.018f to abs3.second, 0.017f, 0.014f, CanonicalMuscle.CORE),
        oval(abs3.first + 0.018f to abs3.second, 0.017f, 0.014f, CanonicalMuscle.CORE),
        taper(
            midS.first - 0.072f to waistY,
            hL.first + 0.012f to (hL.second - 0.02f),
            0.028f, 0.022f, CanonicalMuscle.CORE,
        ),
        taper(
            midS.first + 0.072f to waistY,
            hR.first - 0.012f to (hR.second - 0.02f),
            0.028f, 0.022f, CanonicalMuscle.CORE,
        ),
        oval(midH.first - 0.038f to (midH.second - 0.012f), 0.042f, 0.032f, CanonicalMuscle.GLUTES),
        oval(midH.first + 0.038f to (midH.second - 0.012f), 0.042f, 0.032f, CanonicalMuscle.GLUTES),
        taper(lerp(kL, aL, 0.10f), lerp(kL, aL, 0.62f), 0.058f, 0.034f, CanonicalMuscle.CALVES),
        taper(lerp(kR, aR, 0.10f), lerp(kR, aR, 0.62f), 0.058f, 0.034f, CanonicalMuscle.CALVES),
        taper(lerp(hL, kL, 0.06f), lerp(hL, kL, 0.86f), 0.078f, 0.054f, CanonicalMuscle.QUADRICEPS),
        taper(lerp(hL, kL, 0.18f), lerp(hL, kL, 0.72f), 0.042f, 0.032f, CanonicalMuscle.QUADRICEPS),
        taper(lerp(hR, kR, 0.06f), lerp(hR, kR, 0.86f), 0.078f, 0.054f, CanonicalMuscle.QUADRICEPS),
        taper(lerp(hR, kR, 0.18f), lerp(hR, kR, 0.72f), 0.042f, 0.032f, CanonicalMuscle.QUADRICEPS),
    )
}

private fun Figure.sideMap(): List<PoseInk> {
    val chest = lerp(sL, hL, 0.20f)
    val glute = lerp(sL, hL, 0.86f)
    val glute2 = lerp(sL, hL, 0.90f)
    return listOf(
        oval(sL, 0.038f, 0.034f, CanonicalMuscle.SHOULDERS),
        taper(lerp(sL, hL, 0.04f), lerp(sL, hL, 0.38f), 0.072f, 0.060f, CanonicalMuscle.BACK),
        taper(lerp(sL, hL, 0.36f), lerp(sL, hL, 0.72f), 0.056f, 0.044f, CanonicalMuscle.BACK),
        fill(
            listOf(
                (chest.first + 0.012f) to (chest.second - 0.034f),
                (chest.first + 0.058f) to (chest.second - 0.008f),
                (chest.first + 0.048f) to (chest.second + 0.028f),
                (chest.first + 0.008f) to (chest.second + 0.018f),
                (chest.first - 0.006f) to (chest.second - 0.008f),
            ),
            CanonicalMuscle.CHEST,
        ),
        oval(lerp(sL, hL, 0.48f), 0.032f, 0.042f, CanonicalMuscle.CORE),
        oval(lerp(sL, hL, 0.62f), 0.028f, 0.032f, CanonicalMuscle.CORE),
        taper(lerp(sL, eL, 0.18f), lerp(sL, eL, 0.92f), 0.044f, 0.034f, CanonicalMuscle.TRICEPS),
        taper(lerp(sL, eL, 0.12f), lerp(sL, eL, 0.82f), 0.056f, 0.042f, CanonicalMuscle.BICEPS),
        oval(lerp(sL, eL, 0.42f), 0.024f, 0.020f, CanonicalMuscle.BICEPS),
        taper(lerp(hR, kR, 0.06f), lerp(hR, kR, 0.86f), 0.068f, 0.046f, CanonicalMuscle.QUADRICEPS),
        taper(lerp(kL, aL, 0.10f), lerp(kL, aL, 0.62f), 0.054f, 0.032f, CanonicalMuscle.CALVES),
        taper(lerp(hL, kL, 0.06f), lerp(hL, kL, 0.86f), 0.076f, 0.050f, CanonicalMuscle.HAMSTRINGS),
        taper(lerp(hL, kL, 0.16f), lerp(hL, kL, 0.70f), 0.044f, 0.034f, CanonicalMuscle.HAMSTRINGS),
        oval(glute, 0.058f, 0.050f, CanonicalMuscle.GLUTES),
        oval((glute2.first - 0.01f) to (glute2.second + 0.012f), 0.040f, 0.032f, CanonicalMuscle.GLUTES),
    )
}

private fun Figure.backWings(): List<PoseInk> = listOf(
    oval(lerp(midS, midH, 0.18f), 0.070f, 0.036f, CanonicalMuscle.BACK),
    taper(0.36f to 0.40f, 0.42f to 0.54f, 0.058f, 0.038f, CanonicalMuscle.BACK),
    taper(0.64f to 0.40f, 0.58f to 0.54f, 0.058f, 0.038f, CanonicalMuscle.BACK),
    taper(0.40f to 0.46f, 0.46f to 0.56f, 0.040f, 0.028f, CanonicalMuscle.BACK),
    taper(0.60f to 0.46f, 0.54f to 0.56f, 0.040f, 0.028f, CanonicalMuscle.BACK),
)

private fun DrawScope.paintInk(ink: PoseInk, color: Color) {
    val w = size.width
    val h = size.height
    val m = min(w, h)
    when (ink) {
        is PoseInk.Limb -> drawLine(
            color = color,
            start = Offset(ink.x1 * w, ink.y1 * h),
            end = Offset(ink.x2 * w, ink.y2 * h),
            strokeWidth = ink.width * m,
            cap = StrokeCap.Round,
        )
        is PoseInk.Taper -> drawPath(
            path = smoothPlatePath(
                capsule(ink.x1, ink.y1, ink.x2, ink.y2, ink.w1 / 2f, ink.w2 / 2f),
                w,
                h,
            ),
            color = color,
        )
        is PoseInk.Fill -> drawPath(
            path = smoothPlatePath(ink.points, w, h),
            color = color,
        )
        is PoseInk.Dot -> drawCircle(
            color = color,
            radius = ink.r * m,
            center = Offset(ink.x * w, ink.y * h),
        )
        is PoseInk.Oval -> drawOval(
            color = color,
            topLeft = Offset((ink.x - ink.rx) * w, (ink.y - ink.ry) * h),
            size = Size(ink.rx * 2f * w, ink.ry * 2f * h),
        )
        is PoseInk.Rect -> drawRoundRect(
            color = color,
            topLeft = Offset(ink.left * w, ink.top * h),
            size = Size((ink.right - ink.left) * w, (ink.bottom - ink.top) * h),
            cornerRadius = CornerRadius(0.012f * m, 0.012f * m),
        )
    }
}

internal fun PoseInk.toPlate(): PosePlate = when (this) {
    is PoseInk.Limb -> PosePlate(muscle, capsule(x1, y1, x2, y2, width / 2f, width / 2f))
    is PoseInk.Taper -> PosePlate(muscle, capsule(x1, y1, x2, y2, w1 / 2f, w2 / 2f))
    is PoseInk.Fill -> PosePlate(muscle, points.map { clamp(it.first, it.second) })
    is PoseInk.Dot -> PosePlate(muscle, ovalPoints(x, y, r, r))
    is PoseInk.Oval -> PosePlate(muscle, ovalPoints(x, y, rx, ry))
    is PoseInk.Rect -> PosePlate(
        muscle,
        listOf(clamp(left, top), clamp(right, top), clamp(right, bottom), clamp(left, bottom)),
    )
}

private fun taper(
    a: Pair<Float, Float>,
    b: Pair<Float, Float>,
    w1: Float,
    w2: Float,
    muscle: CanonicalMuscle?,
) = PoseInk.Taper(a.first, a.second, b.first, b.second, w1, w2, muscle)

private fun fill(points: List<Pair<Float, Float>>, muscle: CanonicalMuscle?) =
    PoseInk.Fill(points.map { clamp(it.first, it.second) }, muscle)

private fun oval(p: Pair<Float, Float>, rx: Float, ry: Float, muscle: CanonicalMuscle?) =
    PoseInk.Oval(p.first, p.second, rx, ry, muscle)

private fun torsoFront(
    sL: Pair<Float, Float>,
    sR: Pair<Float, Float>,
    hL: Pair<Float, Float>,
    hR: Pair<Float, Float>,
): List<Pair<Float, Float>> {
    val cx = (sL.first + sR.first) * 0.5f
    val top = (sL.second + sR.second) * 0.5f
    val bot = (hL.second + hR.second) * 0.5f
    val sw = (sR.first - sL.first) * 0.5f
    val hw = (hR.first - hL.first) * 0.5f
    val chest = top + (bot - top) * 0.28f
    val waist = top + (bot - top) * 0.58f
    return listOf(
        sL,
        (sL.first - 0.018f) to (sL.second + 0.024f),
        (cx - sw * 0.35f) to (top - 0.010f),
        (cx + sw * 0.35f) to (top - 0.010f),
        (sR.first + 0.018f) to (sR.second + 0.024f),
        sR,
        (cx + sw * 0.92f) to chest,
        (cx + hw * 0.78f) to waist,
        (hR.first + 0.012f) to (hR.second - 0.006f),
        hR,
        hL,
        (hL.first - 0.012f) to (hL.second - 0.006f),
        (cx - hw * 0.78f) to waist,
        (cx - sw * 0.92f) to chest,
    )
}

private fun torsoSide(
    s: Pair<Float, Float>,
    h: Pair<Float, Float>,
    depth: Float,
): List<Pair<Float, Float>> {
    val mid = lerp(s, h, 0.30f)
    val waist = lerp(s, h, 0.58f)
    val glute = lerp(s, h, 0.90f)
    return listOf(
        (s.first - depth * 0.40f) to (s.second - 0.016f),
        (s.first + depth * 0.55f) to (s.second - 0.004f),
        (mid.first + depth * 1.20f) to mid.second,
        (waist.first + depth * 0.55f) to waist.second,
        (h.first + depth * 0.42f) to (h.second + 0.004f),
        (h.first - depth * 0.20f) to (h.second + 0.022f),
        (glute.first - depth * 1.10f) to glute.second,
        (waist.first - depth * 0.90f) to waist.second,
        (mid.first - depth * 0.95f) to mid.second,
        (s.first - depth * 1.00f) to (s.second + 0.018f),
    )
}

private fun pec(cx: Float, cy: Float, side: Float): List<Pair<Float, Float>> {
    val s = side
    return listOf(
        (cx + s * 0.004f) to (cy - 0.036f),
        (cx + s * 0.050f) to (cy - 0.022f),
        (cx + s * 0.058f) to (cy + 0.006f),
        (cx + s * 0.038f) to (cy + 0.036f),
        (cx + s * 0.008f) to (cy + 0.024f),
        (cx + s * 0.006f) to (cy - 0.006f),
    )
}

private fun lerp(a: Pair<Float, Float>, b: Pair<Float, Float>, t: Float): Pair<Float, Float> =
    (a.first + (b.first - a.first) * t) to (a.second + (b.second - a.second) * t)

private fun clamp(x: Float, y: Float): Pair<Float, Float> =
    x.coerceIn(0f, 1f) to y.coerceIn(0f, 1f)

private fun ovalPoints(
    cx: Float,
    cy: Float,
    rx: Float,
    ry: Float,
    n: Int = 12,
): List<Pair<Float, Float>> {
    val pts = ArrayList<Pair<Float, Float>>(n)
    var i = 0
    while (i < n) {
        val a = (Math.PI * 2.0 * i / n) - Math.PI / 2.0
        pts.add(
            clamp(
                (cx + rx * kotlin.math.cos(a)).toFloat(),
                (cy + ry * kotlin.math.sin(a)).toFloat(),
            ),
        )
        i++
    }
    return pts
}

private fun capsule(
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
    r1: Float,
    r2: Float,
    cap: Int = 8,
): List<Pair<Float, Float>> {
    val dx = x2 - x1
    val dy = y2 - y1
    val len = kotlin.math.hypot(dx, dy).coerceAtLeast(1e-4f)
    val nx = -dy / len
    val ny = dx / len
    val aLeft = kotlin.math.atan2(ny, nx)
    val pts = ArrayList<Pair<Float, Float>>(cap * 2 + 4)
    var i = 0
    while (i <= cap) {
        val t = i / cap.toFloat()
        val ang = aLeft - t * Math.PI.toFloat()
        pts.add(clamp(x2 + r2 * kotlin.math.cos(ang), y2 + r2 * kotlin.math.sin(ang)))
        i++
    }
    i = 0
    while (i <= cap) {
        val t = i / cap.toFloat()
        val ang = aLeft + Math.PI.toFloat() - t * Math.PI.toFloat()
        pts.add(clamp(x1 + r1 * kotlin.math.cos(ang), y1 + r1 * kotlin.math.sin(ang)))
        i++
    }
    return pts
}

private fun bar(y: Float, left: Float = 0.10f, right: Float = 0.90f): List<PoseInk> {
    val cy = y.coerceIn(0.05f, 0.95f)
    val l = left.coerceIn(0f, 1f)
    val r = right.coerceIn(0f, 1f)
    return listOf(
        PoseInk.Limb(l, cy, r, cy, 0.014f, null),
        PoseInk.Oval(l + 0.020f, cy, 0.011f, 0.046f, null),
        PoseInk.Oval(l + 0.044f, cy, 0.011f, 0.036f, null),
        PoseInk.Oval(r - 0.044f, cy, 0.011f, 0.036f, null),
        PoseInk.Oval(r - 0.020f, cy, 0.011f, 0.046f, null),
    )
}

private fun dumbbell(cx: Float, cy: Float): List<PoseInk> = listOf(
    PoseInk.Limb(cx - 0.034f, cy, cx + 0.034f, cy, 0.012f, null),
    PoseInk.Oval(cx - 0.042f, cy, 0.013f, 0.028f, null),
    PoseInk.Oval(cx + 0.042f, cy, 0.013f, 0.028f, null),
)

private fun kettle(cx: Float, cy: Float): List<PoseInk> = listOf(
    PoseInk.Dot(cx, cy + 0.018f, 0.042f, null),
    PoseInk.Limb(cx - 0.022f, cy - 0.038f, cx + 0.022f, cy - 0.038f, 0.014f, null),
)

private fun machineFrame(): List<PoseInk> = listOf(
    PoseInk.Rect(0.80f, 0.10f, 0.90f, 0.90f, null),
    PoseInk.Rect(0.64f, 0.24f, 0.90f, 0.34f, null),
    PoseInk.Rect(0.68f, 0.50f, 0.90f, 0.58f, null),
)

private fun squatKit(equipment: EquipmentType) = when (equipment) {
    EquipmentType.BARBELL, EquipmentType.SMITH -> {
        val kit = bar(0.16f).toMutableList()
        if (equipment == EquipmentType.SMITH) {
            kit += PoseInk.Rect(0.10f, 0.06f, 0.16f, 0.94f, null)
            kit += PoseInk.Rect(0.84f, 0.06f, 0.90f, 0.94f, null)
        }
        kit
    }
    EquipmentType.DUMBBELL -> dumbbell(0.50f, 0.32f)
    EquipmentType.KETTLEBELL -> kettle(0.50f, 0.32f)
    EquipmentType.MACHINE -> listOf(
        PoseInk.Rect(0.18f, 0.08f, 0.26f, 0.94f, null),
        PoseInk.Rect(0.74f, 0.08f, 0.82f, 0.94f, null),
        PoseInk.Rect(0.26f, 0.14f, 0.74f, 0.22f, null),
    )
    else -> emptyList()
}

private fun hingeKit(equipment: EquipmentType) = when (equipment) {
    EquipmentType.BARBELL, EquipmentType.SMITH -> bar(0.64f, left = 0.52f, right = 0.94f)
    EquipmentType.KETTLEBELL -> kettle(0.70f, 0.64f)
    EquipmentType.DUMBBELL -> dumbbell(0.68f, 0.64f)
    EquipmentType.CABLE -> listOf(
        PoseInk.Rect(0.84f, 0.08f, 0.94f, 0.70f, null),
        PoseInk.Limb(0.70f, 0.58f, 0.84f, 0.58f, 0.018f, null),
    )
    EquipmentType.MACHINE -> machineFrame()
    else -> emptyList()
}

private fun pressKit(equipment: EquipmentType) = when (equipment) {
    EquipmentType.BARBELL, EquipmentType.SMITH ->
        listOf(PoseInk.Rect(0.22f, 0.46f, 0.62f, 0.54f, null)) + bar(0.10f, left = 0.10f, right = 0.50f)
    EquipmentType.DUMBBELL -> dumbbell(0.30f, 0.10f)
    EquipmentType.MACHINE -> listOf(
        PoseInk.Rect(0.22f, 0.46f, 0.62f, 0.54f, null),
        PoseInk.Rect(0.06f, 0.16f, 0.14f, 0.84f, null),
    )
    EquipmentType.BODYWEIGHT -> listOf(PoseInk.Rect(0.22f, 0.46f, 0.62f, 0.54f, null))
    else -> listOf(PoseInk.Rect(0.22f, 0.46f, 0.62f, 0.54f, null)) + bar(0.10f, left = 0.10f, right = 0.50f)
}

private fun overheadKit(equipment: EquipmentType) = when (equipment) {
    EquipmentType.BARBELL, EquipmentType.SMITH -> bar(0.06f)
    EquipmentType.DUMBBELL, EquipmentType.KETTLEBELL ->
        dumbbell(0.32f, 0.068f) + dumbbell(0.68f, 0.068f)
    EquipmentType.MACHINE -> machineFrame() + bar(0.06f, left = 0.22f, right = 0.78f)
    else -> bar(0.06f)
}

private fun flyKit(equipment: EquipmentType) = when (equipment) {
    EquipmentType.DUMBBELL, EquipmentType.KETTLEBELL ->
        dumbbell(0.08f, 0.38f) + dumbbell(0.92f, 0.38f)
    EquipmentType.CABLE, EquipmentType.MACHINE -> listOf(
        PoseInk.Rect(0.02f, 0.08f, 0.10f, 0.50f, null),
        PoseInk.Rect(0.90f, 0.08f, 0.98f, 0.50f, null),
    )
    else -> dumbbell(0.08f, 0.38f) + dumbbell(0.92f, 0.38f)
}

private fun pullKit(equipment: EquipmentType) = when (equipment) {
    EquipmentType.CABLE, EquipmentType.MACHINE -> listOf(
        PoseInk.Limb(0.12f, 0.068f, 0.88f, 0.068f, 0.016f, null),
        PoseInk.Rect(0.86f, 0.08f, 0.94f, 0.70f, null),
    )
    else -> bar(0.068f, left = 0.12f, right = 0.88f)
}

private fun rowKit(equipment: EquipmentType) = when (equipment) {
    EquipmentType.BARBELL -> bar(0.34f, left = 0.08f, right = 0.40f)
    EquipmentType.DUMBBELL -> dumbbell(0.28f, 0.34f)
    EquipmentType.KETTLEBELL -> kettle(0.28f, 0.34f)
    EquipmentType.CABLE, EquipmentType.MACHINE -> listOf(
        PoseInk.Rect(0.04f, 0.20f, 0.14f, 0.80f, null),
        PoseInk.Limb(0.14f, 0.36f, 0.28f, 0.36f, 0.018f, null),
    )
    EquipmentType.BODYWEIGHT -> emptyList()
    else -> bar(0.34f, left = 0.08f, right = 0.40f)
}

private fun extensionKit(equipment: EquipmentType) = when (equipment) {
    EquipmentType.BARBELL -> bar(0.08f, left = 0.22f, right = 0.78f)
    EquipmentType.CABLE, EquipmentType.MACHINE -> listOf(
        PoseInk.Rect(0.84f, 0.08f, 0.93f, 0.70f, null),
        PoseInk.Limb(0.47f, 0.255f, 0.53f, 0.255f, 0.016f, null),
    )
    else -> dumbbell(0.50f, 0.24f)
}

private fun hipKit(equipment: EquipmentType) = when (equipment) {
    EquipmentType.BARBELL -> bar(0.34f, left = 0.40f, right = 0.92f)
    EquipmentType.MACHINE -> machineFrame()
    EquipmentType.CABLE -> listOf(PoseInk.Rect(0.86f, 0.08f, 0.96f, 0.80f, null))
    else -> emptyList()
}

private fun coreKit(equipment: EquipmentType) = when (equipment) {
    EquipmentType.DUMBBELL -> dumbbell(0.50f, 0.22f)
    EquipmentType.MACHINE, EquipmentType.CABLE -> machineFrame()
    else -> emptyList()
}

private fun seatedKit(equipment: EquipmentType) = when (equipment) {
    EquipmentType.BODYWEIGHT, EquipmentType.BAND -> emptyList()
    else -> machineFrame()
}

private fun handBells(
    equipment: EquipmentType,
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
): List<PoseInk> = when (equipment) {
    EquipmentType.DUMBBELL -> dumbbell(x1, y1) + dumbbell(x2, y2)
    EquipmentType.KETTLEBELL -> kettle(x1, y1) + kettle(x2, y2)
    EquipmentType.BARBELL -> bar((y1 + y2) / 2f)
    EquipmentType.MACHINE, EquipmentType.CABLE, EquipmentType.SMITH -> machineFrame()
    else -> emptyList()
}
