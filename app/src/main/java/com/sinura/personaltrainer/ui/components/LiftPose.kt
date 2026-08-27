package com.sinura.personaltrainer.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.DefaultExercises
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.ui.theme.HairlineStrong
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.SteelDim
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * One posed silhouette for a lift family.
 *
 * Library thumbs show the locked still for that family. These plates stay
 * the Body tab's standing map: live weekly heat on the same person, not a
 * PNG pack of 101 lifts.
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
    val hair = Metrics.hairline.toPx()
    val person = personInk(pose)
    // Underlay, then fills, then hairlines on top so seams survive overlap.
    person.forEach { paintInk(it, SteelDim) }
    person.forEach { paintInk(it, fill(it.muscle)) }
    person.forEach { paintInk(it, fill(it.muscle), edge = HairlineStrong, hair = hair, drawFill = false) }
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
 * The person is [Fill] — Temper plates, rigidly posed. [Limb] / [Oval] / [Rect]
 * / [Dot] are kit. [Taper] is unused on the person now that limbs are plates.
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
    val posed = figureFor(pose)
    return sourcePlates(pose).map { plate ->
        val bone = boneFor(plate)
        val (ra, rb) = REST.ends(bone)
        val (pa, pb) = posed.ends(bone)
        fill(rigidXform(plate.points, ra, rb, pa, pb), plate.muscle)
    }
}

internal fun kitInk(pose: LiftPose, equipment: EquipmentType): List<PoseInk> {
    if (pose == LiftPose.ANATOMY) return emptyList()
    val f = figureFor(pose)
    return when (pose) {
        LiftPose.ANATOMY -> emptyList()
        LiftPose.SQUAT -> squatKit(equipment, f)
        LiftPose.HINGE -> hingeKit(equipment, f)
        LiftPose.LUNGE -> handBells(equipment, f.wL, f.wR)
        LiftPose.HORIZONTAL_PRESS -> pressKit(equipment, f)
        LiftPose.VERTICAL_PRESS -> overheadKit(equipment, f)
        LiftPose.FLY -> flyKit(equipment, f)
        LiftPose.VERTICAL_PULL -> pullKit(equipment)
        LiftPose.HORIZONTAL_PULL -> rowKit(equipment, f)
        LiftPose.ARM_CURL -> handBells(equipment, f.wL, f.wR)
        LiftPose.ARM_EXT -> extensionKit(equipment, f)
        LiftPose.HIP -> hipKit(equipment, f)
        LiftPose.CORE_FLOOR -> coreKit(equipment, f)
        LiftPose.SEATED_MACHINE -> seatedKit(equipment)
        LiftPose.CARRY -> handBells(equipment, f.wL, f.wR)
    }
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

/**
 * Standing Temper joints in figure-box fractions. Library thumbs draw that
 * box into a square on purpose — the generated squat fills the frame, it
 * does not sit in a skinny 0.52 gutter.
 */
private data class Figure(
    val head: Pair<Float, Float>,
    val neck: Pair<Float, Float>,
    val chest: Pair<Float, Float>,
    val navel: Pair<Float, Float>,
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
) {
    val midS: Pair<Float, Float> get() = lerp(sL, sR, 0.5f)
    val midH: Pair<Float, Float> get() = lerp(hL, hR, 0.5f)

    fun ends(bone: PlateBone): Pair<Pair<Float, Float>, Pair<Float, Float>> = when (bone) {
        PlateBone.HEAD -> head to neck
        PlateBone.NECK -> neck to chest
        PlateBone.TORSO -> chest to navel
        PlateBone.PELVIS -> navel to midH
        PlateBone.ARM_L -> sL to eL
        PlateBone.ARM_R -> sR to eR
        PlateBone.FORE_L -> eL to wL
        PlateBone.FORE_R -> eR to wR
        PlateBone.THIGH_L -> hL to kL
        PlateBone.THIGH_R -> hR to kR
        PlateBone.CALF_L -> kL to aL
        PlateBone.CALF_R -> kR to aR
        PlateBone.FOOT_L -> aL to (aL.first + 0.04f to aL.second + 0.01f)
        PlateBone.FOOT_R -> aR to (aR.first - 0.04f to aR.second + 0.01f)
    }
}

private val REST = Figure(
    head = 0.50f to 0.048f,
    neck = 0.50f to 0.118f,
    chest = 0.50f to 0.22f,
    navel = 0.50f to 0.42f,
    sL = 0.16f to 0.195f, sR = 0.84f to 0.195f,
    eL = 0.14f to 0.338f, eR = 0.86f to 0.338f,
    wL = 0.11f to 0.585f, wR = 0.89f to 0.585f,
    hL = 0.38f to 0.515f, hR = 0.62f to 0.515f,
    kL = 0.37f to 0.745f, kR = 0.63f to 0.745f,
    aL = 0.36f to 0.978f, aR = 0.64f to 0.978f,
)

private fun figureFor(pose: LiftPose): Figure = when (pose) {
    // Locked stills 03–16. Joints are the Temper skeleton in the thumb
    // square, not a second person. Cameras match the bible: front, rear,
    // or 3/4. Profile collapse is gone — those stills show both limbs.
    LiftPose.SQUAT -> Figure(
        head = 0.50f to 0.20f, neck = 0.50f to 0.28f,
        chest = 0.50f to 0.38f, navel = 0.50f to 0.50f,
        sL = 0.24f to 0.32f, sR = 0.76f to 0.32f,
        eL = 0.18f to 0.44f, eR = 0.82f to 0.44f,
        wL = 0.16f to 0.34f, wR = 0.84f to 0.34f,
        hL = 0.32f to 0.52f, hR = 0.68f to 0.52f,
        kL = 0.18f to 0.68f, kR = 0.82f to 0.68f,
        aL = 0.28f to 0.90f, aR = 0.72f to 0.90f,
    )
    LiftPose.HINGE -> Figure(
        head = 0.62f to 0.16f, neck = 0.56f to 0.24f,
        chest = 0.50f to 0.34f, navel = 0.48f to 0.46f,
        sL = 0.38f to 0.30f, sR = 0.62f to 0.32f,
        eL = 0.40f to 0.50f, eR = 0.64f to 0.52f,
        wL = 0.42f to 0.68f, wR = 0.66f to 0.70f,
        hL = 0.40f to 0.48f, hR = 0.60f to 0.50f,
        kL = 0.42f to 0.70f, kR = 0.62f to 0.72f,
        aL = 0.40f to 0.90f, aR = 0.60f to 0.90f,
    )
    LiftPose.LUNGE -> Figure(
        head = 0.46f to 0.08f, neck = 0.46f to 0.16f,
        chest = 0.46f to 0.28f, navel = 0.46f to 0.40f,
        sL = 0.26f to 0.26f, sR = 0.66f to 0.26f,
        eL = 0.22f to 0.46f, eR = 0.70f to 0.46f,
        wL = 0.20f to 0.62f, wR = 0.72f to 0.62f,
        hL = 0.38f to 0.46f, hR = 0.56f to 0.48f,
        kL = 0.30f to 0.64f, kR = 0.62f to 0.70f,
        aL = 0.28f to 0.80f, aR = 0.58f to 0.90f,
    )
    LiftPose.HORIZONTAL_PRESS -> Figure(
        head = 0.20f to 0.28f, neck = 0.30f to 0.30f,
        chest = 0.48f to 0.32f, navel = 0.64f to 0.38f,
        sL = 0.38f to 0.20f, sR = 0.52f to 0.40f,
        eL = 0.28f to 0.10f, eR = 0.64f to 0.16f,
        wL = 0.30f to 0.04f, wR = 0.66f to 0.08f,
        hL = 0.70f to 0.42f, hR = 0.80f to 0.50f,
        kL = 0.72f to 0.62f, kR = 0.82f to 0.68f,
        aL = 0.70f to 0.84f, aR = 0.80f to 0.88f,
    )
    LiftPose.VERTICAL_PRESS -> Figure(
        head = 0.50f to 0.28f, neck = 0.50f to 0.36f,
        chest = 0.50f to 0.44f, navel = 0.50f to 0.56f,
        sL = 0.28f to 0.40f, sR = 0.72f to 0.40f,
        eL = 0.30f to 0.16f, eR = 0.70f to 0.16f,
        wL = 0.32f to 0.05f, wR = 0.68f to 0.05f,
        hL = 0.38f to 0.58f, hR = 0.62f to 0.58f,
        kL = 0.38f to 0.74f, kR = 0.62f to 0.74f,
        aL = 0.38f to 0.92f, aR = 0.62f to 0.92f,
    )
    LiftPose.FLY -> Figure(
        head = 0.50f to 0.08f, neck = 0.50f to 0.16f,
        chest = 0.50f to 0.26f, navel = 0.50f to 0.40f,
        sL = 0.22f to 0.22f, sR = 0.78f to 0.22f,
        eL = 0.08f to 0.24f, eR = 0.92f to 0.24f,
        wL = 0.04f to 0.26f, wR = 0.96f to 0.26f,
        hL = 0.38f to 0.50f, hR = 0.62f to 0.50f,
        kL = 0.38f to 0.72f, kR = 0.62f to 0.72f,
        aL = 0.38f to 0.92f, aR = 0.62f to 0.92f,
    )
    LiftPose.VERTICAL_PULL -> Figure(
        head = 0.50f to 0.28f, neck = 0.50f to 0.36f,
        chest = 0.50f to 0.46f, navel = 0.50f to 0.58f,
        sL = 0.22f to 0.38f, sR = 0.78f to 0.38f,
        eL = 0.12f to 0.16f, eR = 0.88f to 0.16f,
        wL = 0.10f to 0.06f, wR = 0.90f to 0.06f,
        hL = 0.38f to 0.60f, hR = 0.62f to 0.60f,
        kL = 0.38f to 0.76f, kR = 0.62f to 0.76f,
        aL = 0.38f to 0.92f, aR = 0.62f to 0.92f,
    )
    LiftPose.HORIZONTAL_PULL -> Figure(
        head = 0.58f to 0.14f, neck = 0.54f to 0.22f,
        chest = 0.48f to 0.34f, navel = 0.46f to 0.46f,
        sL = 0.36f to 0.28f, sR = 0.60f to 0.30f,
        eL = 0.30f to 0.40f, eR = 0.54f to 0.42f,
        wL = 0.38f to 0.50f, wR = 0.58f to 0.52f,
        hL = 0.40f to 0.50f, hR = 0.58f to 0.52f,
        kL = 0.42f to 0.70f, kR = 0.60f to 0.72f,
        aL = 0.40f to 0.90f, aR = 0.58f to 0.90f,
    )
    LiftPose.ARM_CURL -> Figure(
        head = 0.50f to 0.06f, neck = 0.50f to 0.16f,
        chest = 0.50f to 0.26f, navel = 0.50f to 0.40f,
        sL = 0.22f to 0.22f, sR = 0.78f to 0.22f,
        eL = 0.18f to 0.42f, eR = 0.82f to 0.42f,
        wL = 0.28f to 0.28f, wR = 0.72f to 0.28f,
        hL = 0.38f to 0.50f, hR = 0.62f to 0.50f,
        kL = 0.38f to 0.72f, kR = 0.62f to 0.72f,
        aL = 0.38f to 0.92f, aR = 0.62f to 0.92f,
    )
    LiftPose.ARM_EXT -> Figure(
        head = 0.50f to 0.20f, neck = 0.50f to 0.28f,
        chest = 0.50f to 0.38f, navel = 0.50f to 0.50f,
        sL = 0.24f to 0.32f, sR = 0.76f to 0.32f,
        eL = 0.32f to 0.12f, eR = 0.68f to 0.12f,
        wL = 0.42f to 0.28f, wR = 0.58f to 0.28f,
        hL = 0.38f to 0.54f, hR = 0.62f to 0.54f,
        kL = 0.38f to 0.74f, kR = 0.62f to 0.74f,
        aL = 0.38f to 0.92f, aR = 0.62f to 0.92f,
    )
    LiftPose.HIP -> Figure(
        head = 0.16f to 0.38f, neck = 0.26f to 0.36f,
        chest = 0.42f to 0.34f, navel = 0.58f to 0.34f,
        sL = 0.34f to 0.26f, sR = 0.40f to 0.42f,
        eL = 0.50f to 0.30f, eR = 0.54f to 0.44f,
        wL = 0.58f to 0.32f, wR = 0.62f to 0.38f,
        hL = 0.58f to 0.32f, hR = 0.64f to 0.38f,
        kL = 0.62f to 0.56f, kR = 0.68f to 0.60f,
        aL = 0.56f to 0.82f, aR = 0.62f to 0.86f,
    )
    LiftPose.CORE_FLOOR -> Figure(
        head = 0.50f to 0.16f, neck = 0.50f to 0.26f,
        chest = 0.50f to 0.38f, navel = 0.50f to 0.50f,
        sL = 0.28f to 0.34f, sR = 0.72f to 0.34f,
        eL = 0.34f to 0.48f, eR = 0.66f to 0.48f,
        wL = 0.40f to 0.56f, wR = 0.60f to 0.56f,
        hL = 0.40f to 0.56f, hR = 0.60f to 0.56f,
        kL = 0.32f to 0.70f, kR = 0.68f to 0.70f,
        aL = 0.36f to 0.88f, aR = 0.64f to 0.88f,
    )
    LiftPose.SEATED_MACHINE -> Figure(
        head = 0.46f to 0.10f, neck = 0.46f to 0.18f,
        chest = 0.46f to 0.30f, navel = 0.46f to 0.44f,
        sL = 0.28f to 0.28f, sR = 0.66f to 0.30f,
        eL = 0.24f to 0.46f, eR = 0.70f to 0.48f,
        wL = 0.22f to 0.62f, wR = 0.72f to 0.64f,
        hL = 0.38f to 0.52f, hR = 0.56f to 0.54f,
        kL = 0.36f to 0.70f, kR = 0.58f to 0.72f,
        aL = 0.34f to 0.88f, aR = 0.56f to 0.90f,
    )
    LiftPose.CARRY -> Figure(
        head = 0.50f to 0.06f, neck = 0.50f to 0.16f,
        chest = 0.50f to 0.26f, navel = 0.50f to 0.40f,
        sL = 0.20f to 0.22f, sR = 0.80f to 0.22f,
        eL = 0.16f to 0.42f, eR = 0.84f to 0.42f,
        wL = 0.14f to 0.60f, wR = 0.86f to 0.60f,
        hL = 0.38f to 0.50f, hR = 0.62f to 0.50f,
        kL = 0.38f to 0.72f, kR = 0.62f to 0.72f,
        aL = 0.38f to 0.92f, aR = 0.62f to 0.92f,
    )
    LiftPose.ANATOMY -> error("standing anatomy is drawTemperFigure")
}

private fun sourcePlates(pose: LiftPose): List<BodyPlate> {
    fun extra(view: BodyView, vararg muscles: CanonicalMuscle): List<BodyPlate> {
        val wanted = muscles.toSet()
        return platesFor(view).filter { it.muscle in wanted }
    }
    val plates = when (pose) {
        LiftPose.ANATOMY -> emptyList()
        LiftPose.HINGE, LiftPose.ARM_EXT ->
            platesFor(BodyView.BACK)
        LiftPose.HIP ->
            platesFor(BodyView.BACK) + extra(BodyView.FRONT, CanonicalMuscle.CORE)
        LiftPose.VERTICAL_PULL, LiftPose.HORIZONTAL_PULL ->
            platesFor(BodyView.BACK) + extra(BodyView.FRONT, CanonicalMuscle.BICEPS)
        LiftPose.HORIZONTAL_PRESS ->
            platesFor(BodyView.FRONT) + extra(
                BodyView.BACK,
                CanonicalMuscle.TRICEPS,
                CanonicalMuscle.GLUTES,
            )
        LiftPose.VERTICAL_PRESS, LiftPose.FLY ->
            platesFor(BodyView.FRONT) + extra(
                BodyView.BACK,
                CanonicalMuscle.TRICEPS,
                CanonicalMuscle.GLUTES,
            )
        LiftPose.SQUAT, LiftPose.LUNGE, LiftPose.SEATED_MACHINE ->
            platesFor(BodyView.FRONT) + extra(
                BodyView.BACK,
                CanonicalMuscle.GLUTES,
                CanonicalMuscle.HAMSTRINGS,
            )
        LiftPose.ARM_CURL, LiftPose.CORE_FLOOR, LiftPose.CARRY ->
            platesFor(BodyView.FRONT) + extra(BodyView.BACK, CanonicalMuscle.GLUTES)
    }
    return plates
}

private enum class PlateBone {
    HEAD, NECK, TORSO, PELVIS,
    ARM_L, ARM_R, FORE_L, FORE_R,
    THIGH_L, THIGH_R, CALF_L, CALF_R,
    FOOT_L, FOOT_R,
}

private fun boneFor(plate: BodyPlate): PlateBone {
    val c = plate.centroid
    val left = c.first < 0.5f
    val muscle = plate.muscle
    if (muscle == null) {
        return when {
            c.second < 0.10f -> PlateBone.HEAD
            c.second < 0.16f -> PlateBone.NECK
            c.second < 0.64f -> if (left) PlateBone.FORE_L else PlateBone.FORE_R
            else -> if (left) PlateBone.FOOT_L else PlateBone.FOOT_R
        }
    }
    return when (muscle) {
        CanonicalMuscle.CHEST, CanonicalMuscle.CORE, CanonicalMuscle.BACK -> PlateBone.TORSO
        CanonicalMuscle.GLUTES -> PlateBone.PELVIS
        CanonicalMuscle.SHOULDERS, CanonicalMuscle.BICEPS, CanonicalMuscle.TRICEPS ->
            if (left) PlateBone.ARM_L else PlateBone.ARM_R
        CanonicalMuscle.QUADRICEPS, CanonicalMuscle.HAMSTRINGS ->
            if (left) PlateBone.THIGH_L else PlateBone.THIGH_R
        CanonicalMuscle.CALVES -> if (left) PlateBone.CALF_L else PlateBone.CALF_R
        CanonicalMuscle.OTHER -> PlateBone.TORSO
    }
}

private val BodyPlate.centroid: Pair<Float, Float>
    get() = (points.sumOf { it.first.toDouble() } / points.size).toFloat() to
        (points.sumOf { it.second.toDouble() } / points.size).toFloat()

private fun rigidXform(
    pts: List<Pair<Float, Float>>,
    restA: Pair<Float, Float>,
    restB: Pair<Float, Float>,
    poseA: Pair<Float, Float>,
    poseB: Pair<Float, Float>,
): List<Pair<Float, Float>> {
    val rx = restB.first - restA.first
    val ry = restB.second - restA.second
    val px = poseB.first - poseA.first
    val py = poseB.second - poseA.second
    val rlen = hypot(rx, ry).coerceAtLeast(1e-4f)
    val plen = hypot(px, py).coerceAtLeast(1e-4f)
    val da = atan2(py, px) - atan2(ry, rx)
    // Armor plates keep standing size. Stretching with bone length turned
    // curls into balloons and side views into overlapping capsules.
    val scale = (plen / rlen).coerceIn(0.80f, 1.08f)
    val c = cos(da)
    val s = sin(da)
    return pts.map { (x, y) ->
        val lx = x - restA.first
        val ly = y - restA.second
        clamp(
            poseA.first + (lx * c - ly * s) * scale,
            poseA.second + (lx * s + ly * c) * scale,
        )
    }
}

private fun DrawScope.paintInk(
    ink: PoseInk,
    color: Color,
    edge: Color? = null,
    hair: Float = 0f,
    drawFill: Boolean = true,
) {
    val w = size.width
    val h = size.height
    val m = min(w, h)
    fun fillPath(points: List<Pair<Float, Float>>) {
        val path = instrumentPlatePath(points, w, h)
        if (drawFill) {
            drawPath(path = path, color = color)
        }
        if (edge != null && hair > 0f) {
            drawPath(path = path, color = edge, style = Stroke(width = hair))
        }
    }
    when (ink) {
        is PoseInk.Limb -> if (drawFill) drawLine(
            color = color,
            start = Offset(ink.x1 * w, ink.y1 * h),
            end = Offset(ink.x2 * w, ink.y2 * h),
            strokeWidth = ink.width * m,
            cap = StrokeCap.Round,
        )
        is PoseInk.Taper -> fillPath(
            capsule(ink.x1, ink.y1, ink.x2, ink.y2, ink.w1 / 2f, ink.w2 / 2f),
        )
        is PoseInk.Fill -> fillPath(ink.points)
        is PoseInk.Dot -> if (drawFill) drawCircle(
            color = color,
            radius = ink.r * m,
            center = Offset(ink.x * w, ink.y * h),
        )
        is PoseInk.Oval -> if (drawFill) drawOval(
            color = color,
            topLeft = Offset((ink.x - ink.rx) * w, (ink.y - ink.ry) * h),
            size = Size(ink.rx * 2f * w, ink.ry * 2f * h),
        )
        is PoseInk.Rect -> if (drawFill) drawRoundRect(
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

private fun fill(points: List<Pair<Float, Float>>, muscle: CanonicalMuscle?) =
    PoseInk.Fill(points.map { clamp(it.first, it.second) }, muscle)

private fun lerp(a: Pair<Float, Float>, b: Pair<Float, Float>, t: Float): Pair<Float, Float> =
    (a.first + (b.first - a.first) * t) to (a.second + (b.second - a.second) * t)

private fun clamp(x: Float, y: Float): Pair<Float, Float> =
    x.coerceIn(0f, 1f) to y.coerceIn(0f, 1f)

/**
 * Polygonal plate with a short chamfer. Full midpoint rounding turned posed
 * plates into pills; the generated squat is armor, not a pictogram.
 */
private const val PLATE_CHAMFER = 0.16f

private fun instrumentPlatePath(
    points: List<Pair<Float, Float>>,
    width: Float,
    height: Float,
): Path {
    val n = points.size
    val path = Path()
    if (n < 3) return path
    val t = PLATE_CHAMFER
    fun px(i: Int) = Offset(points[i].first * width, points[i].second * height)
    fun lerp(a: Offset, b: Offset, u: Float) =
        Offset(a.x + (b.x - a.x) * u, a.y + (b.y - a.y) * u)
    val p0 = px(0)
    path.moveTo(lerp(p0, px(n - 1), t).x, lerp(p0, px(n - 1), t).y)
    var i = 0
    while (i < n) {
        val curr = px(i)
        val prev = px(if (i == 0) n - 1 else i - 1)
        val next = px((i + 1) % n)
        val arrive = lerp(curr, prev, t)
        val leave = lerp(curr, next, t)
        if (i != 0) path.lineTo(arrive.x, arrive.y)
        path.quadraticTo(curr.x, curr.y, leave.x, leave.y)
        i++
    }
    path.close()
    return path
}

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
    val len = hypot(dx, dy).coerceAtLeast(1e-4f)
    val nx = -dy / len
    val ny = dx / len
    val aLeft = atan2(ny, nx)
    val pts = ArrayList<Pair<Float, Float>>(cap * 2 + 4)
    var i = 0
    while (i <= cap) {
        val t = i / cap.toFloat()
        val ang = aLeft - t * Math.PI.toFloat()
        pts.add(clamp(x2 + r2 * cos(ang), y2 + r2 * sin(ang)))
        i++
    }
    i = 0
    while (i <= cap) {
        val t = i / cap.toFloat()
        val ang = aLeft + Math.PI.toFloat() - t * Math.PI.toFloat()
        pts.add(clamp(x1 + r1 * cos(ang), y1 + r1 * sin(ang)))
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

private fun squatKit(equipment: EquipmentType, f: Figure) = when (equipment) {
    EquipmentType.BARBELL, EquipmentType.SMITH -> {
        val y = (f.sL.second + f.sR.second) * 0.5f - 0.055f
        val kit = bar(y, left = f.sL.first - 0.10f, right = f.sR.first + 0.10f).toMutableList()
        if (equipment == EquipmentType.SMITH) {
            kit += PoseInk.Rect(0.08f, 0.06f, 0.14f, 0.94f, null)
            kit += PoseInk.Rect(0.86f, 0.06f, 0.92f, 0.94f, null)
        }
        kit
    }
    EquipmentType.DUMBBELL -> dumbbell(f.midS.first, f.chest.second)
    EquipmentType.KETTLEBELL -> kettle(f.midS.first, f.chest.second)
    EquipmentType.MACHINE -> listOf(
        PoseInk.Rect(0.10f, 0.08f, 0.18f, 0.94f, null),
        PoseInk.Rect(0.82f, 0.08f, 0.90f, 0.94f, null),
        PoseInk.Rect(0.18f, 0.22f, 0.82f, 0.30f, null),
    )
    else -> emptyList()
}

private fun hingeKit(equipment: EquipmentType, f: Figure) = when (equipment) {
    EquipmentType.BARBELL, EquipmentType.SMITH ->
        bar(f.wL.second, left = (f.wL.first - 0.06f).coerceAtLeast(0.02f), right = 0.94f)
    EquipmentType.KETTLEBELL -> kettle(f.wL.first, f.wL.second)
    EquipmentType.DUMBBELL -> dumbbell(f.wL.first, f.wL.second)
    EquipmentType.CABLE -> listOf(
        PoseInk.Rect(0.84f, 0.08f, 0.94f, 0.70f, null),
        PoseInk.Limb(f.wL.first, f.wL.second, 0.84f, f.wL.second, 0.018f, null),
    )
    EquipmentType.MACHINE -> machineFrame()
    else -> emptyList()
}

private fun pressKit(equipment: EquipmentType, f: Figure): List<PoseInk> {
    val padTop = (f.chest.second + 0.04f).coerceIn(0.28f, 0.50f)
    val bench = listOf(PoseInk.Rect(0.18f, padTop, 0.78f, (padTop + 0.10f).coerceAtMost(0.62f), null))
    return when (equipment) {
        EquipmentType.BARBELL, EquipmentType.SMITH ->
            bench + bar(
                f.wL.second,
                left = (f.wL.first - 0.16f).coerceAtLeast(0.02f),
                right = (f.wR.first + 0.16f).coerceAtMost(0.98f),
            )
        EquipmentType.DUMBBELL ->
            bench + dumbbell(f.wL.first, f.wL.second) + dumbbell(f.wR.first, f.wR.second)
        EquipmentType.MACHINE -> bench + listOf(PoseInk.Rect(0.06f, 0.16f, 0.14f, 0.84f, null))
        EquipmentType.BODYWEIGHT -> bench
        else -> bench + bar(f.wL.second, left = 0.10f, right = 0.78f)
    }
}

private fun overheadKit(equipment: EquipmentType, f: Figure) = when (equipment) {
    EquipmentType.BARBELL, EquipmentType.SMITH ->
        bar(f.wL.second, left = f.wL.first - 0.10f, right = f.wR.first + 0.10f)
    EquipmentType.DUMBBELL, EquipmentType.KETTLEBELL ->
        dumbbell(f.wL.first, f.wL.second) + dumbbell(f.wR.first, f.wR.second)
    EquipmentType.MACHINE -> machineFrame() + bar(f.wL.second, left = 0.22f, right = 0.78f)
    else -> bar(f.wL.second)
}

private fun flyKit(equipment: EquipmentType, f: Figure) = when (equipment) {
    EquipmentType.DUMBBELL, EquipmentType.KETTLEBELL ->
        dumbbell(f.wL.first, f.wL.second) + dumbbell(f.wR.first, f.wR.second)
    EquipmentType.CABLE, EquipmentType.MACHINE -> listOf(
        PoseInk.Rect(0.02f, 0.08f, 0.10f, 0.50f, null),
        PoseInk.Rect(0.90f, 0.08f, 0.98f, 0.50f, null),
    )
    else -> dumbbell(f.wL.first, f.wL.second) + dumbbell(f.wR.first, f.wR.second)
}

private fun pullKit(equipment: EquipmentType) = when (equipment) {
    EquipmentType.CABLE, EquipmentType.MACHINE -> listOf(
        PoseInk.Limb(0.12f, 0.068f, 0.88f, 0.068f, 0.016f, null),
        PoseInk.Rect(0.86f, 0.08f, 0.94f, 0.70f, null),
    )
    else -> bar(0.068f, left = 0.12f, right = 0.88f)
}

private fun rowKit(equipment: EquipmentType, f: Figure) = when (equipment) {
    EquipmentType.BARBELL ->
        bar(f.wL.second, left = (f.wL.first - 0.10f).coerceAtLeast(0.02f), right = 0.40f)
    EquipmentType.DUMBBELL -> dumbbell(f.wL.first, f.wL.second)
    EquipmentType.KETTLEBELL -> kettle(f.wL.first, f.wL.second)
    EquipmentType.CABLE, EquipmentType.MACHINE -> listOf(
        PoseInk.Rect(0.04f, 0.20f, 0.14f, 0.80f, null),
        PoseInk.Limb(0.14f, f.wL.second, f.wL.first, f.wL.second, 0.018f, null),
    )
    EquipmentType.BODYWEIGHT -> emptyList()
    else -> bar(f.wL.second, left = 0.08f, right = 0.40f)
}

private fun extensionKit(equipment: EquipmentType, f: Figure) = when (equipment) {
    EquipmentType.BARBELL -> bar(0.08f, left = 0.22f, right = 0.78f)
    EquipmentType.CABLE, EquipmentType.MACHINE -> listOf(
        PoseInk.Rect(0.84f, 0.08f, 0.93f, 0.70f, null),
        PoseInk.Limb(f.wL.first, f.wL.second, f.wR.first, f.wR.second, 0.016f, null),
    )
    else -> dumbbell((f.wL.first + f.wR.first) / 2f, (f.wL.second + f.wR.second) / 2f)
}

private fun hipKit(equipment: EquipmentType, f: Figure): List<PoseInk> {
    val bench = listOf(
        PoseInk.Rect(0.08f, 0.34f, 0.48f, 0.46f, null),
        PoseInk.Rect(0.22f, 0.46f, 0.32f, 0.86f, null),
    )
    val load = when (equipment) {
        EquipmentType.BARBELL ->
            bar(f.hL.second, left = (f.hL.first - 0.06f).coerceAtLeast(0.02f), right = 0.92f)
        EquipmentType.MACHINE -> machineFrame()
        EquipmentType.CABLE -> listOf(PoseInk.Rect(0.86f, 0.08f, 0.96f, 0.80f, null))
        else -> emptyList()
    }
    return bench + load
}

private fun coreKit(equipment: EquipmentType, f: Figure) = when (equipment) {
    EquipmentType.DUMBBELL -> dumbbell(f.midS.first, f.chest.second)
    EquipmentType.MACHINE, EquipmentType.CABLE -> machineFrame()
    else -> emptyList()
}

private fun seatedKit(@Suppress("UNUSED_PARAMETER") equipment: EquipmentType) = listOf(
    PoseInk.Rect(0.30f, 0.48f, 0.78f, 0.56f, null),
    PoseInk.Rect(0.70f, 0.18f, 0.80f, 0.56f, null),
    PoseInk.Rect(0.32f, 0.56f, 0.40f, 0.88f, null),
    PoseInk.Rect(0.68f, 0.56f, 0.76f, 0.88f, null),
)

private fun handBells(
    equipment: EquipmentType,
    left: Pair<Float, Float>,
    right: Pair<Float, Float>,
): List<PoseInk> = when (equipment) {
    EquipmentType.DUMBBELL -> dumbbell(left.first, left.second) + dumbbell(right.first, right.second)
    EquipmentType.KETTLEBELL -> kettle(left.first, left.second) + kettle(right.first, right.second)
    EquipmentType.BARBELL -> bar((left.second + right.second) / 2f)
    EquipmentType.MACHINE, EquipmentType.CABLE, EquipmentType.SMITH -> machineFrame()
    else -> emptyList()
}
