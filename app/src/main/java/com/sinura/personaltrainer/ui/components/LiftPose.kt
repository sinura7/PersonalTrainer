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
 * A person is a pictogram: one trunk, limbs that grow out of its end-caps,
 * a head. Heat recolors the working limb, it does not replace the body with
 * a second pile of plates. Kit sits in the hands. Families share a pose so
 * 101 lifts stay one person.
 *
 * Body heat stays on the standing [drawTemperFigure]. Thumbs are identity:
 * the pose plus a fixed Heat3 on the working parts.
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
 * One mark of a pictogram. Canvas and the SVG board paint this list so the
 * geometry the tests dump is the geometry the thumb draws.
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
    return figure.structure() + heatFor(pose, figure)
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

private const val HEAD_R = 0.052f
private const val NECK_W = 0.044f
private const val TRUNK_W = 0.145f
private const val THIGH_W = 0.088f
private const val CALF_W = 0.070f
private const val ARM_W = 0.070f
private const val FORE_W = 0.054f
private const val HAND_R = 0.024f
private const val FOOT_RX = 0.046f
private const val FOOT_RY = 0.016f

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
    val headR: Float = HEAD_R,
    val trunkW: Float = TRUNK_W,
    val footRx: Float = FOOT_RX,
    val footRy: Float = FOOT_RY,
) {
    val midS: Pair<Float, Float> get() = lerp(sL, sR, 0.5f)
    val midH: Pair<Float, Float> get() = lerp(hL, hR, 0.5f)
}

private fun figureFor(pose: LiftPose): Figure = when (pose) {
    LiftPose.SQUAT -> Figure(
        head = 0.50f to 0.150f,
        sL = 0.405f to 0.255f, sR = 0.595f to 0.255f,
        eL = 0.28f to 0.195f, eR = 0.72f to 0.195f,
        wL = 0.20f to 0.165f, wR = 0.80f to 0.165f,
        hL = 0.405f to 0.50f, hR = 0.595f to 0.50f,
        kL = 0.28f to 0.68f, kR = 0.72f to 0.68f,
        aL = 0.32f to 0.90f, aR = 0.68f to 0.90f,
    )
    LiftPose.HINGE -> Figure(
        head = 0.72f to 0.24f,
        sL = 0.56f to 0.34f, sR = 0.54f to 0.37f,
        eL = 0.62f to 0.50f, eR = 0.58f to 0.52f,
        wL = 0.68f to 0.64f, wR = 0.64f to 0.66f,
        hL = 0.40f to 0.50f, hR = 0.38f to 0.53f,
        kL = 0.44f to 0.70f, kR = 0.38f to 0.72f,
        aL = 0.42f to 0.90f, aR = 0.36f to 0.90f,
        side = true, trunkW = 0.125f, footRx = 0.058f,
    )
    LiftPose.LUNGE -> Figure(
        head = 0.50f to 0.105f,
        sL = 0.405f to 0.225f, sR = 0.595f to 0.225f,
        eL = 0.28f to 0.40f, eR = 0.72f to 0.40f,
        wL = 0.24f to 0.56f, wR = 0.76f to 0.56f,
        hL = 0.44f to 0.44f, hR = 0.56f to 0.44f,
        kL = 0.32f to 0.60f, kR = 0.60f to 0.68f,
        aL = 0.28f to 0.76f, aR = 0.58f to 0.90f,
    )
    LiftPose.HORIZONTAL_PRESS -> Figure(
        head = 0.14f to 0.40f,
        sL = 0.30f to 0.40f, sR = 0.30f to 0.455f,
        eL = 0.30f to 0.24f, eR = 0.32f to 0.28f,
        wL = 0.30f to 0.10f, wR = 0.32f to 0.14f,
        hL = 0.56f to 0.42f, hR = 0.56f to 0.475f,
        kL = 0.72f to 0.56f, kR = 0.74f to 0.60f,
        aL = 0.78f to 0.84f, aR = 0.80f to 0.84f,
        side = true, headR = 0.048f, trunkW = 0.118f, footRx = 0.058f,
    )
    LiftPose.VERTICAL_PRESS -> Figure(
        head = 0.50f to 0.215f,
        sL = 0.405f to 0.335f, sR = 0.595f to 0.335f,
        eL = 0.30f to 0.175f, eR = 0.70f to 0.175f,
        wL = 0.32f to 0.068f, wR = 0.68f to 0.068f,
        hL = 0.405f to 0.52f, hR = 0.595f to 0.52f,
        kL = 0.40f to 0.72f, kR = 0.60f to 0.72f,
        aL = 0.40f to 0.92f, aR = 0.60f to 0.92f,
    )
    LiftPose.FLY -> Figure(
        head = 0.50f to 0.115f,
        sL = 0.405f to 0.255f, sR = 0.595f to 0.255f,
        eL = 0.20f to 0.32f, eR = 0.80f to 0.32f,
        wL = 0.08f to 0.38f, wR = 0.92f to 0.38f,
        hL = 0.405f to 0.50f, hR = 0.595f to 0.50f,
        kL = 0.40f to 0.72f, kR = 0.60f to 0.72f,
        aL = 0.40f to 0.92f, aR = 0.60f to 0.92f,
    )
    LiftPose.VERTICAL_PULL -> Figure(
        head = 0.50f to 0.275f,
        sL = 0.405f to 0.375f, sR = 0.595f to 0.375f,
        eL = 0.24f to 0.175f, eR = 0.76f to 0.175f,
        wL = 0.18f to 0.075f, wR = 0.82f to 0.075f,
        hL = 0.405f to 0.56f, hR = 0.595f to 0.56f,
        kL = 0.40f to 0.74f, kR = 0.60f to 0.74f,
        aL = 0.40f to 0.92f, aR = 0.60f to 0.92f,
    )
    LiftPose.HORIZONTAL_PULL -> Figure(
        head = 0.70f to 0.17f,
        sL = 0.54f to 0.28f, sR = 0.52f to 0.31f,
        eL = 0.40f to 0.30f, eR = 0.44f to 0.34f,
        wL = 0.28f to 0.34f, wR = 0.32f to 0.38f,
        hL = 0.42f to 0.50f, hR = 0.40f to 0.53f,
        kL = 0.46f to 0.70f, kR = 0.40f to 0.72f,
        aL = 0.44f to 0.90f, aR = 0.38f to 0.90f,
        side = true, trunkW = 0.125f, footRx = 0.058f,
    )
    LiftPose.ARM_CURL -> Figure(
        head = 0.50f to 0.095f,
        sL = 0.405f to 0.235f, sR = 0.595f to 0.235f,
        eL = 0.30f to 0.46f, eR = 0.70f to 0.46f,
        wL = 0.26f to 0.28f, wR = 0.74f to 0.28f,
        hL = 0.405f to 0.50f, hR = 0.595f to 0.50f,
        kL = 0.40f to 0.72f, kR = 0.60f to 0.72f,
        aL = 0.40f to 0.92f, aR = 0.60f to 0.92f,
    )
    LiftPose.ARM_EXT -> Figure(
        head = 0.50f to 0.215f,
        sL = 0.405f to 0.335f, sR = 0.595f to 0.335f,
        eL = 0.42f to 0.115f, eR = 0.58f to 0.115f,
        wL = 0.47f to 0.255f, wR = 0.53f to 0.255f,
        hL = 0.405f to 0.52f, hR = 0.595f to 0.52f,
        kL = 0.40f to 0.72f, kR = 0.60f to 0.72f,
        aL = 0.40f to 0.92f, aR = 0.60f to 0.92f,
    )
    LiftPose.HIP -> Figure(
        head = 0.14f to 0.60f,
        sL = 0.28f to 0.56f, sR = 0.28f to 0.61f,
        eL = 0.20f to 0.70f, eR = 0.22f to 0.74f,
        wL = 0.14f to 0.82f, wR = 0.16f to 0.84f,
        hL = 0.50f to 0.34f, hR = 0.50f to 0.39f,
        kL = 0.68f to 0.48f, kR = 0.70f to 0.52f,
        aL = 0.80f to 0.70f, aR = 0.82f to 0.72f,
        side = true, headR = 0.048f, trunkW = 0.118f, footRx = 0.058f,
    )
    LiftPose.CORE_FLOOR -> Figure(
        head = 0.24f to 0.27f,
        sL = 0.34f to 0.38f, sR = 0.36f to 0.42f,
        eL = 0.42f to 0.24f, eR = 0.44f to 0.28f,
        wL = 0.52f to 0.20f, wR = 0.54f to 0.24f,
        hL = 0.48f to 0.62f, hR = 0.50f to 0.66f,
        kL = 0.66f to 0.46f, kR = 0.68f to 0.50f,
        aL = 0.80f to 0.58f, aR = 0.82f to 0.62f,
        side = true, headR = 0.048f, trunkW = 0.125f, footRx = 0.058f,
    )
    LiftPose.SEATED_MACHINE -> Figure(
        head = 0.38f to 0.13f,
        sL = 0.36f to 0.26f, sR = 0.42f to 0.28f,
        eL = 0.52f to 0.30f, eR = 0.54f to 0.34f,
        wL = 0.64f to 0.28f, wR = 0.66f to 0.32f,
        hL = 0.40f to 0.50f, hR = 0.44f to 0.54f,
        kL = 0.62f to 0.50f, kR = 0.64f to 0.54f,
        aL = 0.64f to 0.76f, aR = 0.66f to 0.78f,
        side = true, trunkW = 0.125f, footRx = 0.058f,
    )
    LiftPose.CARRY -> Figure(
        head = 0.50f to 0.088f,
        sL = 0.395f to 0.215f, sR = 0.605f to 0.215f,
        eL = 0.28f to 0.42f, eR = 0.72f to 0.42f,
        wL = 0.26f to 0.62f, wR = 0.74f to 0.62f,
        hL = 0.405f to 0.48f, hR = 0.595f to 0.48f,
        kL = 0.40f to 0.70f, kR = 0.60f to 0.70f,
        aL = 0.40f to 0.92f, aR = 0.60f to 0.92f,
    )
    LiftPose.ANATOMY -> error("standing anatomy is drawTemperFigure")
}

private fun Figure.structure(): List<PoseInk> {
    val ink = ArrayList<PoseInk>(24)
    ink += limb(midS, midH, trunkW, null)
    ink += limb(head, midS, NECK_W, null)
    ink += limb(hL, kL, THIGH_W, null)
    ink += limb(hR, kR, THIGH_W, null)
    ink += limb(kL, aL, CALF_W, null)
    ink += limb(kR, aR, CALF_W, null)
    ink += limb(sL, eL, ARM_W, null)
    ink += limb(eL, wL, FORE_W, null)
    if (!side) {
        ink += limb(sR, eR, ARM_W, null)
        ink += limb(eR, wR, FORE_W, null)
    }
    // Joint caps so thigh/calf and arm/forearm read as one limb, not two pills.
    ink += dot(kL, THIGH_W / 2f, null)
    ink += dot(kR, THIGH_W / 2f, null)
    ink += dot(eL, ARM_W / 2f, null)
    if (!side) ink += dot(eR, ARM_W / 2f, null)
    ink += dot(hL, THIGH_W / 2f, null)
    ink += dot(hR, THIGH_W / 2f, null)
    ink += dot(sL, ARM_W / 2f, null)
    if (!side) ink += dot(sR, ARM_W / 2f, null)
    ink += dot(head, headR, null)
    ink += dot(wL, HAND_R, null)
    if (!side) ink += dot(wR, HAND_R, null)
    ink += oval(aL, footRx, footRy, null)
    ink += oval(aR, footRx, footRy, null)
    return ink
}

private fun heatFor(pose: LiftPose, f: Figure): List<PoseInk> {
    val midS = f.midS
    val midH = f.midH
    fun trunk(from: Float, to: Float, w: Float, m: CanonicalMuscle) =
        limb(lerp(midS, midH, from), lerp(midS, midH, to), w, m)
    return when (pose) {
        LiftPose.SQUAT -> listOf(
            limb(f.hL, f.kL, THIGH_W, CanonicalMuscle.QUADRICEPS),
            limb(f.hR, f.kR, THIGH_W, CanonicalMuscle.QUADRICEPS),
            trunk(0.78f, 1f, f.trunkW * 0.85f, CanonicalMuscle.GLUTES),
            trunk(0.25f, 0.65f, f.trunkW * 0.62f, CanonicalMuscle.CORE),
            limb(f.sL, f.eL, ARM_W, CanonicalMuscle.SHOULDERS),
            limb(f.sR, f.eR, ARM_W, CanonicalMuscle.SHOULDERS),
            limb(f.kL, f.aL, CALF_W, CanonicalMuscle.CALVES),
            limb(f.kR, f.aR, CALF_W, CanonicalMuscle.CALVES),
            limb(f.eL, f.wL, FORE_W, CanonicalMuscle.BICEPS),
            limb(f.eR, f.wR, FORE_W, CanonicalMuscle.BICEPS),
        )
        LiftPose.HINGE -> listOf(
            trunk(0.75f, 1f, f.trunkW, CanonicalMuscle.GLUTES),
            limb(f.hL, f.kL, THIGH_W, CanonicalMuscle.HAMSTRINGS),
            trunk(0.10f, 0.55f, 0.090f, CanonicalMuscle.BACK),
            trunk(0.40f, 0.75f, 0.070f, CanonicalMuscle.CORE),
            limb(f.hR, f.kR, THIGH_W * 0.9f, CanonicalMuscle.QUADRICEPS),
            limb(f.kL, f.aL, CALF_W, CanonicalMuscle.CALVES),
            limb(f.eL, f.wL, FORE_W, CanonicalMuscle.BICEPS),
            limb(f.sL, f.eL, ARM_W, CanonicalMuscle.SHOULDERS),
        )
        LiftPose.LUNGE -> listOf(
            limb(f.hR, f.kR, THIGH_W, CanonicalMuscle.QUADRICEPS),
            limb(f.hL, f.kL, THIGH_W, CanonicalMuscle.HAMSTRINGS),
            trunk(0.80f, 1f, f.trunkW * 0.85f, CanonicalMuscle.GLUTES),
            trunk(0.20f, 0.60f, f.trunkW * 0.62f, CanonicalMuscle.CORE),
            limb(f.sL, f.eL, ARM_W, CanonicalMuscle.SHOULDERS),
            limb(f.sR, f.eR, ARM_W, CanonicalMuscle.SHOULDERS),
            limb(f.kR, f.aR, CALF_W, CanonicalMuscle.CALVES),
            limb(f.kL, f.aL, CALF_W, CanonicalMuscle.CALVES),
        )
        LiftPose.HORIZONTAL_PRESS -> listOf(
            trunk(0.05f, 0.40f, 0.100f, CanonicalMuscle.CHEST),
            limb(f.sL, f.eL, ARM_W, CanonicalMuscle.SHOULDERS),
            limb(f.eL, f.wL, FORE_W, CanonicalMuscle.TRICEPS),
            trunk(0.40f, 0.75f, 0.075f, CanonicalMuscle.CORE),
            trunk(0.80f, 1f, 0.100f, CanonicalMuscle.GLUTES),
            limb(f.hL, f.kL, THIGH_W, CanonicalMuscle.QUADRICEPS),
        )
        LiftPose.VERTICAL_PRESS -> listOf(
            limb(f.sL, f.eL, ARM_W, CanonicalMuscle.SHOULDERS),
            limb(f.sR, f.eR, ARM_W, CanonicalMuscle.SHOULDERS),
            limb(f.eL, f.wL, FORE_W, CanonicalMuscle.TRICEPS),
            limb(f.eR, f.wR, FORE_W, CanonicalMuscle.TRICEPS),
            trunk(0.20f, 0.65f, f.trunkW * 0.62f, CanonicalMuscle.CORE),
            trunk(0.80f, 1f, f.trunkW * 0.85f, CanonicalMuscle.GLUTES),
            limb(f.hL, f.kL, THIGH_W, CanonicalMuscle.QUADRICEPS),
            limb(f.hR, f.kR, THIGH_W, CanonicalMuscle.QUADRICEPS),
        )
        LiftPose.FLY -> listOf(
            trunk(0.00f, 0.40f, f.trunkW * 0.78f, CanonicalMuscle.CHEST),
            limb(f.sL, f.eL, ARM_W, CanonicalMuscle.SHOULDERS),
            limb(f.sR, f.eR, ARM_W, CanonicalMuscle.SHOULDERS),
            trunk(0.40f, 0.70f, f.trunkW * 0.58f, CanonicalMuscle.CORE),
            limb(f.hL, f.kL, THIGH_W, CanonicalMuscle.QUADRICEPS),
            limb(f.hR, f.kR, THIGH_W, CanonicalMuscle.QUADRICEPS),
            limb(f.eL, f.wL, FORE_W, CanonicalMuscle.BICEPS),
            limb(f.eR, f.wR, FORE_W, CanonicalMuscle.BICEPS),
        )
        LiftPose.VERTICAL_PULL -> listOf(
            trunk(0.00f, 0.50f, f.trunkW * 0.80f, CanonicalMuscle.BACK),
            limb(f.eL, f.wL, FORE_W, CanonicalMuscle.BICEPS),
            limb(f.eR, f.wR, FORE_W, CanonicalMuscle.BICEPS),
            limb(f.sL, f.eL, ARM_W, CanonicalMuscle.SHOULDERS),
            limb(f.sR, f.eR, ARM_W, CanonicalMuscle.SHOULDERS),
            trunk(0.45f, 0.75f, f.trunkW * 0.58f, CanonicalMuscle.CORE),
            trunk(0.80f, 1f, f.trunkW * 0.85f, CanonicalMuscle.GLUTES),
            limb(f.hL, f.kL, THIGH_W, CanonicalMuscle.QUADRICEPS),
            limb(f.hR, f.kR, THIGH_W, CanonicalMuscle.QUADRICEPS),
        )
        LiftPose.HORIZONTAL_PULL -> listOf(
            trunk(0.08f, 0.50f, 0.095f, CanonicalMuscle.BACK),
            limb(f.sL, f.eL, ARM_W, CanonicalMuscle.SHOULDERS),
            limb(f.eL, f.wL, FORE_W, CanonicalMuscle.BICEPS),
            trunk(0.45f, 0.75f, 0.075f, CanonicalMuscle.CORE),
            trunk(0.80f, 1f, 0.110f, CanonicalMuscle.GLUTES),
            limb(f.hL, f.kL, THIGH_W, CanonicalMuscle.HAMSTRINGS),
        )
        LiftPose.ARM_CURL -> listOf(
            limb(f.sL, f.eL, ARM_W, CanonicalMuscle.BICEPS),
            limb(f.sR, f.eR, ARM_W, CanonicalMuscle.BICEPS),
            dot(f.sL, 0.036f, CanonicalMuscle.SHOULDERS),
            dot(f.sR, 0.036f, CanonicalMuscle.SHOULDERS),
            trunk(0.20f, 0.65f, f.trunkW * 0.62f, CanonicalMuscle.CORE),
            limb(f.hL, f.kL, THIGH_W, CanonicalMuscle.QUADRICEPS),
            limb(f.hR, f.kR, THIGH_W, CanonicalMuscle.QUADRICEPS),
        )
        LiftPose.ARM_EXT -> listOf(
            limb(f.sL, f.eL, ARM_W, CanonicalMuscle.TRICEPS),
            limb(f.sR, f.eR, ARM_W, CanonicalMuscle.TRICEPS),
            dot(f.sL, 0.036f, CanonicalMuscle.SHOULDERS),
            dot(f.sR, 0.036f, CanonicalMuscle.SHOULDERS),
            trunk(0.20f, 0.65f, f.trunkW * 0.62f, CanonicalMuscle.CORE),
            limb(f.hL, f.kL, THIGH_W, CanonicalMuscle.QUADRICEPS),
            limb(f.hR, f.kR, THIGH_W, CanonicalMuscle.QUADRICEPS),
        )
        LiftPose.HIP -> listOf(
            trunk(0.80f, 1f, f.trunkW, CanonicalMuscle.GLUTES),
            limb(f.hL, f.kL, THIGH_W, CanonicalMuscle.HAMSTRINGS),
            trunk(0.25f, 0.65f, 0.080f, CanonicalMuscle.CORE),
            limb(f.sL, f.eL, ARM_W, CanonicalMuscle.SHOULDERS),
            limb(f.hR, f.kR, THIGH_W * 0.9f, CanonicalMuscle.QUADRICEPS),
        )
        LiftPose.CORE_FLOOR -> listOf(
            trunk(0.15f, 0.70f, 0.095f, CanonicalMuscle.CORE),
            limb(f.sL, f.eL, ARM_W, CanonicalMuscle.SHOULDERS),
            trunk(0.80f, 1f, 0.110f, CanonicalMuscle.GLUTES),
            limb(f.hL, f.kL, THIGH_W, CanonicalMuscle.QUADRICEPS),
        )
        LiftPose.SEATED_MACHINE -> listOf(
            limb(f.hL, f.kL, THIGH_W, CanonicalMuscle.QUADRICEPS),
            limb(f.kL, f.aL, CALF_W, CanonicalMuscle.HAMSTRINGS),
            trunk(0.80f, 1f, 0.110f, CanonicalMuscle.GLUTES),
            trunk(0.20f, 0.60f, 0.080f, CanonicalMuscle.CORE),
            limb(f.sL, f.eL, ARM_W, CanonicalMuscle.SHOULDERS),
            limb(f.kR, f.aR, CALF_W, CanonicalMuscle.CALVES),
        )
        LiftPose.CARRY -> listOf(
            trunk(0.20f, 0.65f, f.trunkW * 0.62f, CanonicalMuscle.CORE),
            limb(f.sL, f.eL, ARM_W, CanonicalMuscle.SHOULDERS),
            limb(f.sR, f.eR, ARM_W, CanonicalMuscle.SHOULDERS),
            limb(f.hL, f.kL, THIGH_W, CanonicalMuscle.QUADRICEPS),
            limb(f.hR, f.kR, THIGH_W, CanonicalMuscle.QUADRICEPS),
            limb(f.eL, f.wL, FORE_W, CanonicalMuscle.BICEPS),
            limb(f.eR, f.wR, FORE_W, CanonicalMuscle.BICEPS),
        )
        LiftPose.ANATOMY -> emptyList()
    }
}

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

private fun PoseInk.toPlate(): PosePlate = when (this) {
    is PoseInk.Limb -> PosePlate(muscle, capsule(x1, y1, x2, y2, width / 2f, width / 2f))
    is PoseInk.Dot -> PosePlate(muscle, ovalPoints(x, y, r, r))
    is PoseInk.Oval -> PosePlate(muscle, ovalPoints(x, y, rx, ry))
    is PoseInk.Rect -> PosePlate(
        muscle,
        listOf(clamp(left, top), clamp(right, top), clamp(right, bottom), clamp(left, bottom)),
    )
}

private fun limb(
    a: Pair<Float, Float>,
    b: Pair<Float, Float>,
    width: Float,
    muscle: CanonicalMuscle?,
) = PoseInk.Limb(a.first, a.second, b.first, b.second, width, muscle)

private fun dot(p: Pair<Float, Float>, r: Float, muscle: CanonicalMuscle?) =
    PoseInk.Dot(p.first, p.second, r, muscle)

private fun oval(p: Pair<Float, Float>, rx: Float, ry: Float, muscle: CanonicalMuscle?) =
    PoseInk.Oval(p.first, p.second, rx, ry, muscle)

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
    cap: Int = 6,
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
