package com.sinura.personaltrainer.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.DefaultExercises
import com.sinura.personaltrainer.domain.EquipmentType

/**
 * One posed silhouette for a lift family, drawn in the same plate language
 * as the Body figure.
 *
 * Library rows are 40dp. A standing anatomy with a corner badge cannot say
 * "this is a squat on a smith" at that size. A posed plate figure with the
 * kit in the silhouette can. Families share a pose so 101 lifts stay one
 * person, one seam, one density — not 101 pictures.
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
    val plates = platesForPose(pose)
    plates.forEach { plate ->
        drawPath(smoothPlatePath(plate.points, size.width, size.height), fill(plate.muscle))
    }
    kitPlates(pose, equipment).forEach { points ->
        drawPath(smoothPlatePath(points, size.width, size.height), kit)
    }
}

internal data class PosePlate(
    val muscle: CanonicalMuscle?,
    val points: List<Pair<Float, Float>>,
)

internal fun platesForPose(pose: LiftPose): List<PosePlate> = when (pose) {
    LiftPose.ANATOMY -> emptyList()
    LiftPose.SQUAT -> SQUAT
    LiftPose.HINGE -> HINGE
    LiftPose.LUNGE -> LUNGE
    LiftPose.HORIZONTAL_PRESS -> HORIZONTAL_PRESS
    LiftPose.VERTICAL_PRESS -> VERTICAL_PRESS
    LiftPose.FLY -> FLY
    LiftPose.VERTICAL_PULL -> VERTICAL_PULL
    LiftPose.HORIZONTAL_PULL -> HORIZONTAL_PULL
    LiftPose.ARM_CURL -> ARM_CURL
    LiftPose.ARM_EXT -> ARM_EXT
    LiftPose.HIP -> HIP
    LiftPose.CORE_FLOOR -> CORE_FLOOR
    LiftPose.SEATED_MACHINE -> SEATED_MACHINE
    LiftPose.CARRY -> CARRY
}

internal fun kitPlates(pose: LiftPose, equipment: EquipmentType): List<List<Pair<Float, Float>>> =
    when (pose) {
        LiftPose.ANATOMY -> emptyList()
        LiftPose.SQUAT -> squatKit(equipment)
        LiftPose.HINGE -> hingeKit(equipment)
        LiftPose.LUNGE -> handBells(equipment, 0.22f, 0.48f, 0.78f, 0.48f)
        LiftPose.HORIZONTAL_PRESS -> pressKit(equipment)
        LiftPose.VERTICAL_PRESS -> overheadKit(equipment)
        LiftPose.FLY -> flyKit(equipment)
        LiftPose.VERTICAL_PULL -> pullKit(equipment)
        LiftPose.HORIZONTAL_PULL -> rowKit(equipment)
        LiftPose.ARM_CURL -> handBells(equipment, 0.18f, 0.62f, 0.82f, 0.62f)
        LiftPose.ARM_EXT -> extensionKit(equipment)
        LiftPose.HIP -> hipKit(equipment)
        LiftPose.CORE_FLOOR -> coreKit(equipment)
        LiftPose.SEATED_MACHINE -> seatedKit(equipment)
        LiftPose.CARRY -> handBells(equipment, 0.16f, 0.72f, 0.84f, 0.72f)
    }

private fun skull(cx: Float, cy: Float, rx: Float = 0.06f, ry: Float = 0.05f): PosePlate {
    val n = 12
    val xy = FloatArray(n * 2)
    var i = 0
    while (i < n) {
        val a = (Math.PI * 2.0 * i / n) - Math.PI / 2.0
        xy[i * 2] = (cx + rx * kotlin.math.cos(a)).toFloat().coerceIn(0f, 1f)
        xy[i * 2 + 1] = (cy + ry * kotlin.math.sin(a)).toFloat().coerceIn(0f, 1f)
        i++
    }
    return p(null, *xy)
}

private fun p(muscle: CanonicalMuscle?, vararg xy: Float): PosePlate {
    require(xy.size >= 6 && xy.size % 2 == 0)
    val points = ArrayList<Pair<Float, Float>>(xy.size / 2)
    var i = 0
    while (i < xy.size) {
        points.add(xy[i] to xy[i + 1])
        i += 2
    }
    return PosePlate(muscle, points)
}

private fun poly(vararg xy: Float): List<Pair<Float, Float>> {
    val points = ArrayList<Pair<Float, Float>>(xy.size / 2)
    var i = 0
    while (i < xy.size) {
        points.add(xy[i] to xy[i + 1])
        i += 2
    }
    return points
}

private val SQUAT = listOf(
    skull(0.50f, 0.10f),
    p(CanonicalMuscle.SHOULDERS, 0.30f, 0.16f, 0.46f, 0.15f, 0.44f, 0.24f, 0.28f, 0.26f),
    p(CanonicalMuscle.SHOULDERS, 0.70f, 0.16f, 0.54f, 0.15f, 0.56f, 0.24f, 0.72f, 0.26f),
    p(CanonicalMuscle.CORE, 0.38f, 0.22f, 0.62f, 0.22f, 0.60f, 0.40f, 0.40f, 0.40f),
    p(CanonicalMuscle.GLUTES, 0.36f, 0.38f, 0.64f, 0.38f, 0.66f, 0.50f, 0.34f, 0.50f),
    p(CanonicalMuscle.QUADRICEPS, 0.22f, 0.48f, 0.48f, 0.46f, 0.46f, 0.70f, 0.20f, 0.68f),
    p(CanonicalMuscle.QUADRICEPS, 0.78f, 0.48f, 0.52f, 0.46f, 0.54f, 0.70f, 0.80f, 0.68f),
    p(CanonicalMuscle.CALVES, 0.24f, 0.70f, 0.44f, 0.70f, 0.40f, 0.92f, 0.26f, 0.94f),
    p(CanonicalMuscle.CALVES, 0.76f, 0.70f, 0.56f, 0.70f, 0.60f, 0.92f, 0.74f, 0.94f),
    p(CanonicalMuscle.BICEPS, 0.18f, 0.26f, 0.30f, 0.24f, 0.28f, 0.46f, 0.16f, 0.46f),
    p(CanonicalMuscle.BICEPS, 0.82f, 0.26f, 0.70f, 0.24f, 0.72f, 0.46f, 0.84f, 0.46f),
)

private val HINGE = listOf(
    skull(0.64f, 0.15f, rx = 0.055f, ry = 0.048f),
    p(CanonicalMuscle.BACK, 0.34f, 0.20f, 0.66f, 0.16f, 0.62f, 0.38f, 0.30f, 0.42f),
    p(CanonicalMuscle.SHOULDERS, 0.62f, 0.16f, 0.78f, 0.22f, 0.74f, 0.34f, 0.58f, 0.28f),
    p(CanonicalMuscle.CORE, 0.28f, 0.40f, 0.58f, 0.36f, 0.52f, 0.52f, 0.26f, 0.52f),
    p(CanonicalMuscle.GLUTES, 0.24f, 0.50f, 0.50f, 0.50f, 0.48f, 0.64f, 0.22f, 0.62f),
    p(CanonicalMuscle.HAMSTRINGS, 0.22f, 0.62f, 0.46f, 0.64f, 0.42f, 0.86f, 0.24f, 0.84f),
    p(CanonicalMuscle.QUADRICEPS, 0.46f, 0.52f, 0.62f, 0.50f, 0.58f, 0.78f, 0.44f, 0.76f),
    p(CanonicalMuscle.CALVES, 0.24f, 0.84f, 0.42f, 0.86f, 0.40f, 0.96f, 0.26f, 0.96f),
    p(CanonicalMuscle.BICEPS, 0.66f, 0.30f, 0.80f, 0.34f, 0.70f, 0.58f, 0.56f, 0.52f),
)

private val LUNGE = listOf(
    skull(0.52f, 0.08f),
    p(CanonicalMuscle.CORE, 0.42f, 0.12f, 0.62f, 0.12f, 0.60f, 0.36f, 0.42f, 0.36f),
    p(CanonicalMuscle.SHOULDERS, 0.32f, 0.14f, 0.46f, 0.14f, 0.44f, 0.26f, 0.30f, 0.26f),
    p(CanonicalMuscle.SHOULDERS, 0.68f, 0.14f, 0.54f, 0.14f, 0.56f, 0.26f, 0.70f, 0.26f),
    p(CanonicalMuscle.GLUTES, 0.40f, 0.34f, 0.62f, 0.34f, 0.60f, 0.46f, 0.38f, 0.46f),
    p(CanonicalMuscle.QUADRICEPS, 0.28f, 0.44f, 0.50f, 0.42f, 0.40f, 0.78f, 0.22f, 0.74f),
    p(CanonicalMuscle.HAMSTRINGS, 0.52f, 0.44f, 0.68f, 0.46f, 0.78f, 0.70f, 0.58f, 0.68f),
    p(CanonicalMuscle.CALVES, 0.22f, 0.74f, 0.40f, 0.78f, 0.36f, 0.96f, 0.20f, 0.94f),
    p(CanonicalMuscle.CALVES, 0.60f, 0.68f, 0.78f, 0.70f, 0.82f, 0.86f, 0.64f, 0.84f),
)

private val HORIZONTAL_PRESS = listOf(
    skull(0.15f, 0.41f, rx = 0.055f, ry = 0.048f),
    p(CanonicalMuscle.SHOULDERS, 0.20f, 0.36f, 0.34f, 0.32f, 0.34f, 0.46f, 0.20f, 0.50f),
    p(CanonicalMuscle.CHEST, 0.32f, 0.34f, 0.62f, 0.34f, 0.62f, 0.52f, 0.32f, 0.52f),
    p(CanonicalMuscle.CORE, 0.60f, 0.36f, 0.82f, 0.38f, 0.84f, 0.54f, 0.60f, 0.52f),
    p(CanonicalMuscle.GLUTES, 0.80f, 0.40f, 0.94f, 0.42f, 0.94f, 0.56f, 0.80f, 0.54f),
    p(CanonicalMuscle.QUADRICEPS, 0.78f, 0.54f, 0.96f, 0.56f, 0.90f, 0.86f, 0.74f, 0.80f),
    p(CanonicalMuscle.TRICEPS, 0.18f, 0.22f, 0.34f, 0.18f, 0.36f, 0.34f, 0.20f, 0.36f),
    p(CanonicalMuscle.TRICEPS, 0.18f, 0.50f, 0.34f, 0.52f, 0.32f, 0.70f, 0.16f, 0.66f),
)

private val VERTICAL_PRESS = listOf(
    skull(0.50f, 0.22f),
    p(CanonicalMuscle.SHOULDERS, 0.28f, 0.26f, 0.48f, 0.26f, 0.46f, 0.38f, 0.26f, 0.38f),
    p(CanonicalMuscle.SHOULDERS, 0.72f, 0.26f, 0.52f, 0.26f, 0.54f, 0.38f, 0.74f, 0.38f),
    p(CanonicalMuscle.CORE, 0.38f, 0.36f, 0.62f, 0.36f, 0.60f, 0.58f, 0.40f, 0.58f),
    p(CanonicalMuscle.GLUTES, 0.36f, 0.56f, 0.64f, 0.56f, 0.62f, 0.68f, 0.38f, 0.68f),
    p(CanonicalMuscle.QUADRICEPS, 0.34f, 0.66f, 0.50f, 0.66f, 0.48f, 0.94f, 0.34f, 0.94f),
    p(CanonicalMuscle.QUADRICEPS, 0.66f, 0.66f, 0.50f, 0.66f, 0.52f, 0.94f, 0.66f, 0.94f),
    p(CanonicalMuscle.TRICEPS, 0.22f, 0.08f, 0.36f, 0.10f, 0.34f, 0.28f, 0.20f, 0.26f),
    p(CanonicalMuscle.TRICEPS, 0.78f, 0.08f, 0.64f, 0.10f, 0.66f, 0.28f, 0.80f, 0.26f),
)

private val FLY = listOf(
    skull(0.50f, 0.12f),
    p(CanonicalMuscle.CHEST, 0.36f, 0.18f, 0.64f, 0.18f, 0.62f, 0.40f, 0.38f, 0.40f),
    p(CanonicalMuscle.SHOULDERS, 0.16f, 0.20f, 0.38f, 0.22f, 0.36f, 0.36f, 0.12f, 0.34f),
    p(CanonicalMuscle.SHOULDERS, 0.84f, 0.20f, 0.62f, 0.22f, 0.64f, 0.36f, 0.88f, 0.34f),
    p(CanonicalMuscle.CORE, 0.40f, 0.38f, 0.60f, 0.38f, 0.58f, 0.58f, 0.42f, 0.58f),
    p(CanonicalMuscle.QUADRICEPS, 0.38f, 0.56f, 0.50f, 0.56f, 0.50f, 0.94f, 0.36f, 0.94f),
    p(CanonicalMuscle.QUADRICEPS, 0.62f, 0.56f, 0.50f, 0.56f, 0.50f, 0.94f, 0.64f, 0.94f),
    p(CanonicalMuscle.BICEPS, 0.06f, 0.32f, 0.20f, 0.30f, 0.22f, 0.48f, 0.06f, 0.50f),
    p(CanonicalMuscle.BICEPS, 0.94f, 0.32f, 0.80f, 0.30f, 0.78f, 0.48f, 0.94f, 0.50f),
)

private val VERTICAL_PULL = listOf(
    p(CanonicalMuscle.BICEPS, 0.18f, 0.04f, 0.32f, 0.04f, 0.30f, 0.28f, 0.16f, 0.26f),
    p(CanonicalMuscle.BICEPS, 0.82f, 0.04f, 0.68f, 0.04f, 0.70f, 0.28f, 0.84f, 0.26f),
    skull(0.50f, 0.26f),
    p(CanonicalMuscle.BACK, 0.32f, 0.28f, 0.68f, 0.28f, 0.64f, 0.56f, 0.36f, 0.56f),
    p(CanonicalMuscle.SHOULDERS, 0.24f, 0.26f, 0.38f, 0.30f, 0.36f, 0.44f, 0.22f, 0.40f),
    p(CanonicalMuscle.SHOULDERS, 0.76f, 0.26f, 0.62f, 0.30f, 0.64f, 0.44f, 0.78f, 0.40f),
    p(CanonicalMuscle.CORE, 0.38f, 0.54f, 0.62f, 0.54f, 0.58f, 0.70f, 0.42f, 0.70f),
    p(CanonicalMuscle.GLUTES, 0.36f, 0.68f, 0.64f, 0.68f, 0.62f, 0.80f, 0.38f, 0.80f),
    p(CanonicalMuscle.QUADRICEPS, 0.36f, 0.78f, 0.50f, 0.78f, 0.48f, 0.96f, 0.36f, 0.96f),
    p(CanonicalMuscle.QUADRICEPS, 0.64f, 0.78f, 0.50f, 0.78f, 0.52f, 0.96f, 0.64f, 0.96f),
)

private val HORIZONTAL_PULL = listOf(
    skull(0.75f, 0.15f, rx = 0.055f, ry = 0.048f),
    p(CanonicalMuscle.BACK, 0.40f, 0.18f, 0.72f, 0.16f, 0.68f, 0.46f, 0.36f, 0.48f),
    p(CanonicalMuscle.SHOULDERS, 0.28f, 0.28f, 0.44f, 0.24f, 0.42f, 0.40f, 0.24f, 0.42f),
    p(CanonicalMuscle.CORE, 0.34f, 0.46f, 0.62f, 0.44f, 0.58f, 0.62f, 0.34f, 0.62f),
    p(CanonicalMuscle.GLUTES, 0.32f, 0.60f, 0.58f, 0.60f, 0.54f, 0.74f, 0.32f, 0.72f),
    p(CanonicalMuscle.HAMSTRINGS, 0.32f, 0.72f, 0.52f, 0.74f, 0.48f, 0.94f, 0.30f, 0.92f),
    p(CanonicalMuscle.BICEPS, 0.12f, 0.34f, 0.28f, 0.32f, 0.30f, 0.52f, 0.12f, 0.54f),
)

private val ARM_CURL = listOf(
    skull(0.50f, 0.10f),
    p(CanonicalMuscle.CORE, 0.40f, 0.16f, 0.60f, 0.16f, 0.58f, 0.46f, 0.42f, 0.46f),
    p(CanonicalMuscle.SHOULDERS, 0.30f, 0.18f, 0.44f, 0.18f, 0.42f, 0.30f, 0.28f, 0.30f),
    p(CanonicalMuscle.SHOULDERS, 0.70f, 0.18f, 0.56f, 0.18f, 0.58f, 0.30f, 0.72f, 0.30f),
    p(CanonicalMuscle.BICEPS, 0.16f, 0.28f, 0.32f, 0.28f, 0.34f, 0.58f, 0.14f, 0.56f),
    p(CanonicalMuscle.BICEPS, 0.84f, 0.28f, 0.68f, 0.28f, 0.66f, 0.58f, 0.86f, 0.56f),
    p(CanonicalMuscle.QUADRICEPS, 0.38f, 0.46f, 0.50f, 0.46f, 0.50f, 0.94f, 0.36f, 0.94f),
    p(CanonicalMuscle.QUADRICEPS, 0.62f, 0.46f, 0.50f, 0.46f, 0.50f, 0.94f, 0.64f, 0.94f),
)

private val ARM_EXT = listOf(
    skull(0.50f, 0.12f),
    p(CanonicalMuscle.CORE, 0.40f, 0.18f, 0.60f, 0.18f, 0.58f, 0.48f, 0.42f, 0.48f),
    p(CanonicalMuscle.TRICEPS, 0.22f, 0.06f, 0.38f, 0.10f, 0.36f, 0.32f, 0.20f, 0.28f),
    p(CanonicalMuscle.TRICEPS, 0.78f, 0.06f, 0.62f, 0.10f, 0.64f, 0.32f, 0.80f, 0.28f),
    p(CanonicalMuscle.SHOULDERS, 0.28f, 0.20f, 0.44f, 0.22f, 0.42f, 0.34f, 0.26f, 0.32f),
    p(CanonicalMuscle.SHOULDERS, 0.72f, 0.20f, 0.56f, 0.22f, 0.58f, 0.34f, 0.74f, 0.32f),
    p(CanonicalMuscle.QUADRICEPS, 0.38f, 0.48f, 0.50f, 0.48f, 0.50f, 0.94f, 0.36f, 0.94f),
    p(CanonicalMuscle.QUADRICEPS, 0.62f, 0.48f, 0.50f, 0.48f, 0.50f, 0.94f, 0.64f, 0.94f),
)

private val HIP = listOf(
    skull(0.16f, 0.32f, rx = 0.055f, ry = 0.048f),
    p(CanonicalMuscle.SHOULDERS, 0.20f, 0.28f, 0.36f, 0.30f, 0.36f, 0.44f, 0.20f, 0.44f),
    p(CanonicalMuscle.CORE, 0.32f, 0.32f, 0.62f, 0.34f, 0.62f, 0.50f, 0.32f, 0.48f),
    p(CanonicalMuscle.GLUTES, 0.58f, 0.28f, 0.86f, 0.30f, 0.86f, 0.52f, 0.58f, 0.50f),
    p(CanonicalMuscle.HAMSTRINGS, 0.62f, 0.50f, 0.84f, 0.52f, 0.72f, 0.86f, 0.54f, 0.80f),
    p(CanonicalMuscle.QUADRICEPS, 0.20f, 0.46f, 0.48f, 0.48f, 0.40f, 0.88f, 0.18f, 0.84f),
)

private val CORE_FLOOR = listOf(
    skull(0.14f, 0.44f, rx = 0.055f, ry = 0.048f),
    p(CanonicalMuscle.SHOULDERS, 0.18f, 0.38f, 0.32f, 0.36f, 0.32f, 0.50f, 0.18f, 0.52f),
    p(CanonicalMuscle.CORE, 0.30f, 0.38f, 0.70f, 0.38f, 0.70f, 0.56f, 0.30f, 0.56f),
    p(CanonicalMuscle.GLUTES, 0.68f, 0.40f, 0.86f, 0.40f, 0.86f, 0.56f, 0.68f, 0.56f),
    p(CanonicalMuscle.QUADRICEPS, 0.72f, 0.54f, 0.90f, 0.54f, 0.86f, 0.86f, 0.70f, 0.82f),
    p(CanonicalMuscle.SHOULDERS, 0.22f, 0.20f, 0.34f, 0.18f, 0.34f, 0.38f, 0.22f, 0.38f),
)

private val SEATED_MACHINE = listOf(
    skull(0.54f, 0.14f),
    p(CanonicalMuscle.SHOULDERS, 0.36f, 0.18f, 0.52f, 0.18f, 0.50f, 0.32f, 0.34f, 0.32f),
    p(CanonicalMuscle.CORE, 0.38f, 0.30f, 0.64f, 0.30f, 0.66f, 0.52f, 0.42f, 0.54f),
    p(CanonicalMuscle.GLUTES, 0.40f, 0.50f, 0.70f, 0.48f, 0.72f, 0.64f, 0.42f, 0.64f),
    p(CanonicalMuscle.QUADRICEPS, 0.18f, 0.54f, 0.46f, 0.56f, 0.36f, 0.88f, 0.14f, 0.84f),
    p(CanonicalMuscle.HAMSTRINGS, 0.48f, 0.62f, 0.70f, 0.62f, 0.68f, 0.90f, 0.46f, 0.88f),
    p(CanonicalMuscle.CALVES, 0.14f, 0.82f, 0.34f, 0.86f, 0.32f, 0.96f, 0.14f, 0.96f),
)

private val CARRY = listOf(
    skull(0.50f, 0.08f),
    p(CanonicalMuscle.SHOULDERS, 0.30f, 0.14f, 0.48f, 0.14f, 0.46f, 0.28f, 0.28f, 0.28f),
    p(CanonicalMuscle.SHOULDERS, 0.70f, 0.14f, 0.52f, 0.14f, 0.54f, 0.28f, 0.72f, 0.28f),
    p(CanonicalMuscle.CORE, 0.38f, 0.26f, 0.62f, 0.26f, 0.60f, 0.52f, 0.40f, 0.52f),
    p(CanonicalMuscle.QUADRICEPS, 0.36f, 0.50f, 0.50f, 0.50f, 0.48f, 0.94f, 0.34f, 0.94f),
    p(CanonicalMuscle.QUADRICEPS, 0.64f, 0.50f, 0.50f, 0.50f, 0.52f, 0.94f, 0.66f, 0.94f),
    p(CanonicalMuscle.BICEPS, 0.18f, 0.28f, 0.30f, 0.28f, 0.28f, 0.68f, 0.16f, 0.68f),
    p(CanonicalMuscle.BICEPS, 0.82f, 0.28f, 0.70f, 0.28f, 0.72f, 0.68f, 0.84f, 0.68f),
)

private fun bar(y: Float, left: Float = 0.08f, right: Float = 0.92f): List<List<Pair<Float, Float>>> {
    val half = 0.018f
    val plate = 0.07f
    val cy = y.coerceIn(plate, 1f - plate)
    val l = left.coerceIn(0f, 1f)
    val r = right.coerceIn(0f, 1f)
    return listOf(
        poly(l, cy - half, r, cy - half, r, cy + half, l, cy + half),
        poly(l, cy - plate, l + 0.08f, cy - plate, l + 0.08f, cy + plate, l, cy + plate),
        poly(r - 0.08f, cy - plate, r, cy - plate, r, cy + plate, r - 0.08f, cy + plate),
    )
}

private fun bell(cx: Float, cy: Float) = poly(
    cx - 0.07f, cy - 0.05f, cx + 0.07f, cy - 0.05f,
    cx + 0.08f, cy + 0.06f, cx - 0.08f, cy + 0.06f,
)

private fun squatKit(equipment: EquipmentType) = when (equipment) {
    EquipmentType.BARBELL, EquipmentType.SMITH -> {
        val kit = bar(0.17f).toMutableList()
        if (equipment == EquipmentType.SMITH) {
            kit += poly(0.10f, 0.04f, 0.16f, 0.04f, 0.16f, 0.96f, 0.10f, 0.96f)
            kit += poly(0.84f, 0.04f, 0.90f, 0.04f, 0.90f, 0.96f, 0.84f, 0.96f)
        }
        kit
    }
    EquipmentType.DUMBBELL, EquipmentType.KETTLEBELL -> listOf(bell(0.50f, 0.30f))
    EquipmentType.MACHINE -> listOf(
        poly(0.18f, 0.08f, 0.28f, 0.08f, 0.28f, 0.96f, 0.18f, 0.96f),
        poly(0.72f, 0.08f, 0.82f, 0.08f, 0.82f, 0.96f, 0.72f, 0.96f),
        poly(0.28f, 0.14f, 0.72f, 0.14f, 0.72f, 0.22f, 0.28f, 0.22f),
    )
    else -> emptyList()
}

private fun hingeKit(equipment: EquipmentType) = when (equipment) {
    EquipmentType.BARBELL, EquipmentType.SMITH -> bar(0.62f, left = 0.48f, right = 0.96f)
    EquipmentType.KETTLEBELL -> listOf(bell(0.72f, 0.58f))
    EquipmentType.DUMBBELL -> listOf(bell(0.68f, 0.56f))
    EquipmentType.CABLE -> listOf(
        poly(0.82f, 0.06f, 0.94f, 0.06f, 0.94f, 0.70f, 0.82f, 0.70f),
        poly(0.70f, 0.54f, 0.84f, 0.54f, 0.84f, 0.62f, 0.70f, 0.62f),
    )
    EquipmentType.MACHINE -> machineFrame()
    else -> emptyList()
}

private fun pressKit(equipment: EquipmentType) = when (equipment) {
    EquipmentType.BARBELL, EquipmentType.SMITH -> bar(0.28f, left = 0.04f, right = 0.70f)
    EquipmentType.DUMBBELL -> listOf(bell(0.16f, 0.18f), bell(0.16f, 0.68f))
    EquipmentType.MACHINE -> listOf(
        poly(0.04f, 0.16f, 0.14f, 0.16f, 0.14f, 0.84f, 0.04f, 0.84f),
        poly(0.14f, 0.24f, 0.40f, 0.24f, 0.40f, 0.32f, 0.14f, 0.32f),
    )
    EquipmentType.BODYWEIGHT -> emptyList()
    else -> bar(0.28f, left = 0.04f, right = 0.70f)
}

private fun overheadKit(equipment: EquipmentType) = when (equipment) {
    EquipmentType.BARBELL, EquipmentType.SMITH -> bar(0.08f)
    EquipmentType.DUMBBELL, EquipmentType.KETTLEBELL ->
        listOf(bell(0.22f, 0.08f), bell(0.78f, 0.08f))
    EquipmentType.MACHINE -> machineFrame() + bar(0.08f, left = 0.22f, right = 0.78f)
    else -> bar(0.08f)
}

private fun flyKit(equipment: EquipmentType) = when (equipment) {
    EquipmentType.DUMBBELL, EquipmentType.KETTLEBELL ->
        listOf(bell(0.08f, 0.42f), bell(0.92f, 0.42f))
    EquipmentType.CABLE, EquipmentType.MACHINE -> listOf(
        poly(0.02f, 0.06f, 0.12f, 0.06f, 0.12f, 0.50f, 0.02f, 0.50f),
        poly(0.88f, 0.06f, 0.98f, 0.06f, 0.98f, 0.50f, 0.88f, 0.50f),
    )
    else -> listOf(bell(0.08f, 0.42f), bell(0.92f, 0.42f))
}

private fun pullKit(equipment: EquipmentType) = when (equipment) {
    EquipmentType.CABLE, EquipmentType.MACHINE -> listOf(
        poly(0.12f, 0.02f, 0.88f, 0.02f, 0.88f, 0.08f, 0.12f, 0.08f),
        poly(0.84f, 0.08f, 0.94f, 0.08f, 0.94f, 0.70f, 0.84f, 0.70f),
    )
    EquipmentType.BODYWEIGHT -> listOf(
        poly(0.10f, 0.02f, 0.90f, 0.02f, 0.90f, 0.07f, 0.10f, 0.07f),
    )
    else -> listOf(poly(0.10f, 0.02f, 0.90f, 0.02f, 0.90f, 0.07f, 0.10f, 0.07f))
}

private fun rowKit(equipment: EquipmentType) = when (equipment) {
    EquipmentType.BARBELL -> bar(0.48f, left = 0.04f, right = 0.42f)
    EquipmentType.DUMBBELL, EquipmentType.KETTLEBELL -> listOf(bell(0.14f, 0.48f))
    EquipmentType.CABLE, EquipmentType.MACHINE -> listOf(
        poly(0.04f, 0.20f, 0.16f, 0.20f, 0.16f, 0.80f, 0.04f, 0.80f),
        poly(0.16f, 0.44f, 0.32f, 0.44f, 0.32f, 0.52f, 0.16f, 0.52f),
    )
    EquipmentType.BODYWEIGHT -> emptyList()
    else -> bar(0.48f, left = 0.04f, right = 0.42f)
}

private fun extensionKit(equipment: EquipmentType) = when (equipment) {
    EquipmentType.BARBELL -> bar(0.06f, left = 0.18f, right = 0.82f)
    EquipmentType.CABLE, EquipmentType.MACHINE -> listOf(
        poly(0.84f, 0.04f, 0.96f, 0.04f, 0.96f, 0.70f, 0.84f, 0.70f),
    )
    else -> listOf(bell(0.22f, 0.08f), bell(0.78f, 0.08f))
}

private fun hipKit(equipment: EquipmentType) = when (equipment) {
    EquipmentType.BARBELL -> bar(0.36f, left = 0.50f, right = 0.96f)
    EquipmentType.MACHINE -> machineFrame()
    EquipmentType.CABLE -> listOf(
        poly(0.86f, 0.08f, 0.96f, 0.08f, 0.96f, 0.80f, 0.86f, 0.80f),
    )
    else -> emptyList()
}

private fun coreKit(equipment: EquipmentType) = when (equipment) {
    EquipmentType.OTHER -> listOf(poly(0.70f, 0.28f, 0.90f, 0.28f, 0.88f, 0.40f, 0.68f, 0.40f))
    EquipmentType.DUMBBELL -> listOf(bell(0.50f, 0.30f))
    EquipmentType.MACHINE, EquipmentType.CABLE -> machineFrame()
    else -> emptyList()
}

private fun seatedKit(equipment: EquipmentType) = when (equipment) {
    EquipmentType.BODYWEIGHT, EquipmentType.BAND -> emptyList()
    else -> machineFrame()
}

private fun machineFrame() = listOf(
    poly(0.72f, 0.06f, 0.86f, 0.06f, 0.86f, 0.94f, 0.72f, 0.94f),
    poly(0.58f, 0.18f, 0.86f, 0.18f, 0.86f, 0.28f, 0.58f, 0.28f),
    poly(0.62f, 0.48f, 0.86f, 0.48f, 0.86f, 0.58f, 0.62f, 0.58f),
)

private fun handBells(
    equipment: EquipmentType,
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
): List<List<Pair<Float, Float>>> = when (equipment) {
    EquipmentType.DUMBBELL, EquipmentType.KETTLEBELL -> listOf(bell(x1, y1), bell(x2, y2))
    EquipmentType.BARBELL -> bar((y1 + y2) / 2f)
    EquipmentType.MACHINE, EquipmentType.CABLE, EquipmentType.SMITH -> machineFrame()
    else -> emptyList()
}

/** Every shipped family has a pose, so a new catalog row cannot silently stand still. */
internal fun poseCoversTheCatalog(): Boolean =
    DefaultExercises.MOVEMENT_FAMILIES.all { poseFor(it) != LiftPose.ANATOMY }

internal fun poseAndKitPoints(
    pose: LiftPose,
    equipment: EquipmentType,
): List<Pair<Float, Float>> =
    platesForPose(pose).flatMap { it.points } + kitPlates(pose, equipment).flatten()
