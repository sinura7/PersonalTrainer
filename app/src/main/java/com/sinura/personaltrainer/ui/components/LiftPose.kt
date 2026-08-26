package com.sinura.personaltrainer.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.DefaultExercises
import com.sinura.personaltrainer.domain.EquipmentType

/**
 * One posed silhouette for a lift family.
 *
 * Each pose is a person first — tapered limbs, a torso, a head — then heat
 * plates sit on the working muscle, then the kit. Four-point blobs are not
 * a silhouette. Families share a pose so 101 lifts stay one person.
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
        LiftPose.LUNGE -> handBells(equipment, 0.18f, 0.38f, 0.82f, 0.38f)
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

private fun clamp(x: Float, y: Float): Pair<Float, Float> =
    x.coerceIn(0f, 1f) to y.coerceIn(0f, 1f)

private fun oval(
    cx: Float,
    cy: Float,
    rx: Float,
    ry: Float,
    n: Int = 14,
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

/** Tapered limb. Caps are semicircles so a thigh reads as a thigh, not a stadium blob. */
private fun capsule(
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
    r1: Float,
    r2: Float,
    cap: Int = 7,
): List<Pair<Float, Float>> {
    val dx = x2 - x1
    val dy = y2 - y1
    val len = kotlin.math.hypot(dx, dy).coerceAtLeast(1e-4f)
    val ux = dx / len
    val uy = dy / len
    val nx = -uy
    val ny = ux
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

private fun torso(
    cx: Float,
    top: Float,
    bottom: Float,
    shoulder: Float,
    waist: Float,
    hip: Float,
): List<Pair<Float, Float>> {
    val chest = top + (bottom - top) * 0.28f
    val mid = top + (bottom - top) * 0.58f
    return listOf(
        clamp(cx - shoulder, top),
        clamp(cx - shoulder * 0.55f, top - 0.008f),
        clamp(cx + shoulder * 0.55f, top - 0.008f),
        clamp(cx + shoulder, top),
        clamp(cx + shoulder * 0.92f, chest),
        clamp(cx + waist, mid),
        clamp(cx + hip, bottom),
        clamp(cx - hip, bottom),
        clamp(cx - waist, mid),
        clamp(cx - shoulder * 0.92f, chest),
    )
}

private fun head(cx: Float, cy: Float, rx: Float = 0.050f, ry: Float = 0.046f) =
    PosePlate(null, oval(cx, cy, rx, ry))

private fun bone(
    muscle: CanonicalMuscle?,
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
    r1: Float,
    r2: Float,
) = PosePlate(muscle, capsule(x1, y1, x2, y2, r1, r2))

private fun poly(vararg xy: Float): List<Pair<Float, Float>> {
    val points = ArrayList<Pair<Float, Float>>(xy.size / 2)
    var i = 0
    while (i < xy.size) {
        points.add(clamp(xy[i], xy[i + 1]))
        i += 2
    }
    return points
}

private val SQUAT = listOf(
    head(0.50f, 0.086f),
    bone(null, 0.50f, 0.126f, 0.50f, 0.168f, 0.024f, 0.032f),
    PosePlate(null, torso(0.50f, 0.168f, 0.458f, 0.152f, 0.102f, 0.142f)),
    bone(null, 0.392f, 0.458f, 0.228f, 0.688f, 0.080f, 0.060f),
    bone(null, 0.608f, 0.458f, 0.772f, 0.688f, 0.080f, 0.060f),
    bone(null, 0.228f, 0.688f, 0.272f, 0.918f, 0.056f, 0.036f),
    bone(null, 0.772f, 0.688f, 0.728f, 0.918f, 0.056f, 0.036f),
    PosePlate(null, oval(0.278f, 0.952f, 0.056f, 0.020f)),
    PosePlate(null, oval(0.722f, 0.952f, 0.056f, 0.020f)),
    bone(null, 0.348f, 0.192f, 0.172f, 0.358f, 0.048f, 0.038f),
    bone(null, 0.652f, 0.192f, 0.828f, 0.358f, 0.048f, 0.038f),
    bone(null, 0.172f, 0.358f, 0.228f, 0.172f, 0.036f, 0.028f),
    bone(null, 0.828f, 0.358f, 0.772f, 0.172f, 0.036f, 0.028f),
    bone(CanonicalMuscle.SHOULDERS, 0.348f, 0.186f, 0.278f, 0.258f, 0.050f, 0.038f),
    bone(CanonicalMuscle.SHOULDERS, 0.652f, 0.186f, 0.722f, 0.258f, 0.050f, 0.038f),
    PosePlate(CanonicalMuscle.CORE, torso(0.50f, 0.220f, 0.400f, 0.100f, 0.088f, 0.100f)),
    PosePlate(CanonicalMuscle.GLUTES, torso(0.50f, 0.392f, 0.498f, 0.128f, 0.118f, 0.138f)),
    bone(CanonicalMuscle.QUADRICEPS, 0.392f, 0.468f, 0.248f, 0.662f, 0.066f, 0.050f),
    bone(CanonicalMuscle.QUADRICEPS, 0.608f, 0.468f, 0.752f, 0.662f, 0.066f, 0.050f),
    bone(CanonicalMuscle.CALVES, 0.232f, 0.702f, 0.268f, 0.892f, 0.046f, 0.030f),
    bone(CanonicalMuscle.CALVES, 0.768f, 0.702f, 0.732f, 0.892f, 0.046f, 0.030f),
    bone(CanonicalMuscle.BICEPS, 0.340f, 0.200f, 0.188f, 0.342f, 0.036f, 0.030f),
    bone(CanonicalMuscle.BICEPS, 0.660f, 0.200f, 0.812f, 0.342f, 0.036f, 0.030f),
)

private val HINGE = listOf(
    head(0.64f, 0.118f, rx = 0.048f, ry = 0.044f),
    bone(null, 0.60f, 0.155f, 0.54f, 0.195f, 0.022f, 0.030f),
    PosePlate(null, torso(0.48f, 0.188f, 0.500f, 0.130f, 0.095f, 0.125f)),
    bone(null, 0.42f, 0.500f, 0.30f, 0.780f, 0.078f, 0.058f),
    bone(null, 0.30f, 0.780f, 0.28f, 0.940f, 0.052f, 0.034f),
    PosePlate(null, oval(0.28f, 0.968f, 0.055f, 0.018f)),
    bone(null, 0.56f, 0.230f, 0.72f, 0.580f, 0.046f, 0.036f),
    bone(null, 0.52f, 0.500f, 0.58f, 0.820f, 0.055f, 0.040f),
    bone(CanonicalMuscle.BACK, 0.52f, 0.200f, 0.40f, 0.420f, 0.090f, 0.070f),
    bone(CanonicalMuscle.SHOULDERS, 0.58f, 0.188f, 0.70f, 0.280f, 0.048f, 0.038f),
    PosePlate(CanonicalMuscle.CORE, torso(0.44f, 0.360f, 0.500f, 0.090f, 0.080f, 0.095f)),
    PosePlate(CanonicalMuscle.GLUTES, torso(0.40f, 0.470f, 0.575f, 0.110f, 0.100f, 0.115f)),
    bone(CanonicalMuscle.HAMSTRINGS, 0.40f, 0.575f, 0.30f, 0.800f, 0.060f, 0.046f),
    bone(CanonicalMuscle.QUADRICEPS, 0.50f, 0.510f, 0.56f, 0.760f, 0.048f, 0.036f),
    bone(CanonicalMuscle.CALVES, 0.30f, 0.800f, 0.28f, 0.940f, 0.042f, 0.028f),
    bone(CanonicalMuscle.BICEPS, 0.58f, 0.250f, 0.70f, 0.540f, 0.034f, 0.028f),
)

private val LUNGE = listOf(
    head(0.50f, 0.072f),
    bone(null, 0.50f, 0.112f, 0.50f, 0.152f, 0.022f, 0.030f),
    PosePlate(null, torso(0.50f, 0.152f, 0.400f, 0.140f, 0.095f, 0.125f)),
    bone(null, 0.42f, 0.400f, 0.22f, 0.720f, 0.078f, 0.058f),
    bone(null, 0.58f, 0.400f, 0.76f, 0.620f, 0.070f, 0.052f),
    bone(null, 0.22f, 0.720f, 0.24f, 0.940f, 0.052f, 0.034f),
    bone(null, 0.76f, 0.620f, 0.80f, 0.820f, 0.048f, 0.032f),
    PosePlate(null, oval(0.25f, 0.968f, 0.055f, 0.018f)),
    PosePlate(null, oval(0.80f, 0.850f, 0.050f, 0.018f)),
    bone(null, 0.36f, 0.175f, 0.18f, 0.380f, 0.044f, 0.034f),
    bone(null, 0.64f, 0.175f, 0.82f, 0.380f, 0.044f, 0.034f),
    bone(CanonicalMuscle.SHOULDERS, 0.36f, 0.168f, 0.28f, 0.240f, 0.046f, 0.036f),
    bone(CanonicalMuscle.SHOULDERS, 0.64f, 0.168f, 0.72f, 0.240f, 0.046f, 0.036f),
    PosePlate(CanonicalMuscle.CORE, torso(0.50f, 0.175f, 0.360f, 0.095f, 0.082f, 0.095f)),
    PosePlate(CanonicalMuscle.GLUTES, torso(0.50f, 0.350f, 0.430f, 0.115f, 0.105f, 0.120f)),
    bone(CanonicalMuscle.QUADRICEPS, 0.42f, 0.410f, 0.24f, 0.690f, 0.064f, 0.048f),
    bone(CanonicalMuscle.HAMSTRINGS, 0.58f, 0.410f, 0.74f, 0.600f, 0.056f, 0.042f),
    bone(CanonicalMuscle.CALVES, 0.22f, 0.730f, 0.24f, 0.920f, 0.042f, 0.028f),
    bone(CanonicalMuscle.CALVES, 0.76f, 0.630f, 0.80f, 0.800f, 0.038f, 0.026f),
)

private val HORIZONTAL_PRESS = listOf(
    head(0.12f, 0.40f, rx = 0.048f, ry = 0.044f),
    bone(null, 0.17f, 0.40f, 0.24f, 0.41f, 0.022f, 0.032f),
    PosePlate(null, torso(0.48f, 0.355f, 0.520f, 0.155f, 0.100f, 0.120f)),
    bone(null, 0.70f, 0.430f, 0.86f, 0.620f, 0.070f, 0.052f),
    bone(null, 0.86f, 0.620f, 0.90f, 0.880f, 0.048f, 0.032f),
    PosePlate(null, oval(0.90f, 0.920f, 0.048f, 0.018f)),
    bone(null, 0.34f, 0.355f, 0.24f, 0.140f, 0.046f, 0.036f),
    bone(null, 0.30f, 0.400f, 0.20f, 0.180f, 0.044f, 0.034f),
    bone(CanonicalMuscle.SHOULDERS, 0.28f, 0.360f, 0.24f, 0.250f, 0.048f, 0.038f),
    PosePlate(CanonicalMuscle.CHEST, torso(0.42f, 0.345f, 0.500f, 0.120f, 0.090f, 0.100f)),
    PosePlate(CanonicalMuscle.CORE, torso(0.62f, 0.365f, 0.510f, 0.090f, 0.080f, 0.090f)),
    PosePlate(CanonicalMuscle.GLUTES, torso(0.74f, 0.400f, 0.540f, 0.095f, 0.085f, 0.095f)),
    bone(CanonicalMuscle.QUADRICEPS, 0.72f, 0.450f, 0.86f, 0.640f, 0.056f, 0.042f),
    bone(CanonicalMuscle.TRICEPS, 0.32f, 0.330f, 0.24f, 0.155f, 0.038f, 0.030f),
    bone(CanonicalMuscle.TRICEPS, 0.28f, 0.380f, 0.20f, 0.195f, 0.036f, 0.028f),
)

private val VERTICAL_PRESS = listOf(
    head(0.50f, 0.200f),
    bone(null, 0.50f, 0.240f, 0.50f, 0.278f, 0.022f, 0.030f),
    PosePlate(null, torso(0.50f, 0.278f, 0.560f, 0.145f, 0.100f, 0.128f)),
    bone(null, 0.40f, 0.560f, 0.38f, 0.820f, 0.070f, 0.050f),
    bone(null, 0.60f, 0.560f, 0.62f, 0.820f, 0.070f, 0.050f),
    bone(null, 0.38f, 0.820f, 0.38f, 0.950f, 0.048f, 0.032f),
    bone(null, 0.62f, 0.820f, 0.62f, 0.950f, 0.048f, 0.032f),
    PosePlate(null, oval(0.38f, 0.975f, 0.050f, 0.016f)),
    PosePlate(null, oval(0.62f, 0.975f, 0.050f, 0.016f)),
    bone(null, 0.36f, 0.290f, 0.22f, 0.100f, 0.046f, 0.034f),
    bone(null, 0.64f, 0.290f, 0.78f, 0.100f, 0.046f, 0.034f),
    bone(CanonicalMuscle.SHOULDERS, 0.36f, 0.280f, 0.26f, 0.160f, 0.052f, 0.040f),
    bone(CanonicalMuscle.SHOULDERS, 0.64f, 0.280f, 0.74f, 0.160f, 0.052f, 0.040f),
    PosePlate(CanonicalMuscle.CORE, torso(0.50f, 0.310f, 0.520f, 0.095f, 0.085f, 0.100f)),
    PosePlate(CanonicalMuscle.GLUTES, torso(0.50f, 0.510f, 0.590f, 0.115f, 0.108f, 0.120f)),
    bone(CanonicalMuscle.QUADRICEPS, 0.40f, 0.575f, 0.38f, 0.820f, 0.056f, 0.042f),
    bone(CanonicalMuscle.QUADRICEPS, 0.60f, 0.575f, 0.62f, 0.820f, 0.056f, 0.042f),
    bone(CanonicalMuscle.TRICEPS, 0.34f, 0.250f, 0.22f, 0.110f, 0.036f, 0.028f),
    bone(CanonicalMuscle.TRICEPS, 0.66f, 0.250f, 0.78f, 0.110f, 0.036f, 0.028f),
)

private val FLY = listOf(
    head(0.50f, 0.090f),
    bone(null, 0.50f, 0.130f, 0.50f, 0.170f, 0.022f, 0.030f),
    PosePlate(null, torso(0.50f, 0.170f, 0.520f, 0.145f, 0.100f, 0.128f)),
    bone(null, 0.40f, 0.520f, 0.38f, 0.820f, 0.070f, 0.050f),
    bone(null, 0.60f, 0.520f, 0.62f, 0.820f, 0.070f, 0.050f),
    bone(null, 0.38f, 0.820f, 0.38f, 0.950f, 0.048f, 0.032f),
    bone(null, 0.62f, 0.820f, 0.62f, 0.950f, 0.048f, 0.032f),
    PosePlate(null, oval(0.38f, 0.975f, 0.050f, 0.016f)),
    PosePlate(null, oval(0.62f, 0.975f, 0.050f, 0.016f)),
    bone(null, 0.34f, 0.210f, 0.10f, 0.380f, 0.048f, 0.036f),
    bone(null, 0.66f, 0.210f, 0.90f, 0.380f, 0.048f, 0.036f),
    PosePlate(CanonicalMuscle.CHEST, torso(0.50f, 0.175f, 0.380f, 0.118f, 0.092f, 0.100f)),
    bone(CanonicalMuscle.SHOULDERS, 0.32f, 0.200f, 0.14f, 0.340f, 0.050f, 0.038f),
    bone(CanonicalMuscle.SHOULDERS, 0.68f, 0.200f, 0.86f, 0.340f, 0.050f, 0.038f),
    PosePlate(CanonicalMuscle.CORE, torso(0.50f, 0.370f, 0.500f, 0.090f, 0.082f, 0.095f)),
    bone(CanonicalMuscle.QUADRICEPS, 0.40f, 0.535f, 0.38f, 0.820f, 0.056f, 0.042f),
    bone(CanonicalMuscle.QUADRICEPS, 0.60f, 0.535f, 0.62f, 0.820f, 0.056f, 0.042f),
    bone(CanonicalMuscle.BICEPS, 0.28f, 0.250f, 0.12f, 0.380f, 0.034f, 0.028f),
    bone(CanonicalMuscle.BICEPS, 0.72f, 0.250f, 0.88f, 0.380f, 0.034f, 0.028f),
)

private val VERTICAL_PULL = listOf(
    bone(null, 0.22f, 0.055f, 0.28f, 0.280f, 0.040f, 0.036f),
    bone(null, 0.78f, 0.055f, 0.72f, 0.280f, 0.040f, 0.036f),
    head(0.50f, 0.240f),
    bone(null, 0.50f, 0.280f, 0.50f, 0.318f, 0.022f, 0.030f),
    PosePlate(null, torso(0.50f, 0.318f, 0.620f, 0.145f, 0.100f, 0.128f)),
    bone(null, 0.40f, 0.620f, 0.38f, 0.850f, 0.065f, 0.048f),
    bone(null, 0.60f, 0.620f, 0.62f, 0.850f, 0.065f, 0.048f),
    bone(null, 0.38f, 0.850f, 0.38f, 0.960f, 0.044f, 0.030f),
    bone(null, 0.62f, 0.850f, 0.62f, 0.960f, 0.044f, 0.030f),
    PosePlate(null, oval(0.38f, 0.980f, 0.048f, 0.014f)),
    PosePlate(null, oval(0.62f, 0.980f, 0.048f, 0.014f)),
    bone(CanonicalMuscle.BICEPS, 0.22f, 0.060f, 0.28f, 0.260f, 0.034f, 0.030f),
    bone(CanonicalMuscle.BICEPS, 0.78f, 0.060f, 0.72f, 0.260f, 0.034f, 0.030f),
    PosePlate(CanonicalMuscle.BACK, torso(0.50f, 0.310f, 0.540f, 0.130f, 0.100f, 0.115f)),
    bone(CanonicalMuscle.SHOULDERS, 0.32f, 0.290f, 0.24f, 0.180f, 0.046f, 0.036f),
    bone(CanonicalMuscle.SHOULDERS, 0.68f, 0.290f, 0.76f, 0.180f, 0.046f, 0.036f),
    PosePlate(CanonicalMuscle.CORE, torso(0.50f, 0.530f, 0.640f, 0.090f, 0.082f, 0.095f)),
    PosePlate(CanonicalMuscle.GLUTES, torso(0.50f, 0.620f, 0.720f, 0.110f, 0.102f, 0.115f)),
    bone(CanonicalMuscle.QUADRICEPS, 0.40f, 0.640f, 0.38f, 0.850f, 0.050f, 0.038f),
    bone(CanonicalMuscle.QUADRICEPS, 0.60f, 0.640f, 0.62f, 0.850f, 0.050f, 0.038f),
)

private val HORIZONTAL_PULL = listOf(
    head(0.74f, 0.120f, rx = 0.048f, ry = 0.044f),
    bone(null, 0.70f, 0.158f, 0.64f, 0.200f, 0.022f, 0.028f),
    PosePlate(null, torso(0.52f, 0.195f, 0.520f, 0.125f, 0.092f, 0.115f)),
    bone(null, 0.44f, 0.520f, 0.36f, 0.820f, 0.072f, 0.052f),
    bone(null, 0.36f, 0.820f, 0.34f, 0.950f, 0.048f, 0.032f),
    PosePlate(null, oval(0.34f, 0.975f, 0.050f, 0.016f)),
    bone(null, 0.38f, 0.280f, 0.14f, 0.480f, 0.046f, 0.036f),
    bone(CanonicalMuscle.BACK, 0.56f, 0.210f, 0.42f, 0.450f, 0.088f, 0.068f),
    bone(CanonicalMuscle.SHOULDERS, 0.40f, 0.250f, 0.28f, 0.360f, 0.046f, 0.036f),
    PosePlate(CanonicalMuscle.CORE, torso(0.48f, 0.420f, 0.560f, 0.085f, 0.078f, 0.090f)),
    PosePlate(CanonicalMuscle.GLUTES, torso(0.44f, 0.540f, 0.660f, 0.100f, 0.092f, 0.105f)),
    bone(CanonicalMuscle.HAMSTRINGS, 0.42f, 0.660f, 0.36f, 0.850f, 0.056f, 0.042f),
    bone(CanonicalMuscle.BICEPS, 0.34f, 0.300f, 0.16f, 0.480f, 0.036f, 0.028f),
)

private val ARM_CURL = listOf(
    head(0.50f, 0.078f),
    bone(null, 0.50f, 0.118f, 0.50f, 0.158f, 0.022f, 0.030f),
    PosePlate(null, torso(0.50f, 0.158f, 0.480f, 0.140f, 0.098f, 0.125f)),
    bone(null, 0.40f, 0.480f, 0.38f, 0.800f, 0.070f, 0.050f),
    bone(null, 0.60f, 0.480f, 0.62f, 0.800f, 0.070f, 0.050f),
    bone(null, 0.38f, 0.800f, 0.38f, 0.950f, 0.048f, 0.032f),
    bone(null, 0.62f, 0.800f, 0.62f, 0.950f, 0.048f, 0.032f),
    PosePlate(null, oval(0.38f, 0.975f, 0.050f, 0.016f)),
    PosePlate(null, oval(0.62f, 0.975f, 0.050f, 0.016f)),
    bone(null, 0.34f, 0.200f, 0.16f, 0.420f, 0.046f, 0.038f),
    bone(null, 0.66f, 0.200f, 0.84f, 0.420f, 0.046f, 0.038f),
    bone(null, 0.16f, 0.420f, 0.22f, 0.620f, 0.036f, 0.030f),
    bone(null, 0.84f, 0.420f, 0.78f, 0.620f, 0.036f, 0.030f),
    PosePlate(CanonicalMuscle.CORE, torso(0.50f, 0.175f, 0.430f, 0.095f, 0.085f, 0.098f)),
    bone(CanonicalMuscle.SHOULDERS, 0.34f, 0.185f, 0.26f, 0.260f, 0.046f, 0.036f),
    bone(CanonicalMuscle.SHOULDERS, 0.66f, 0.185f, 0.74f, 0.260f, 0.046f, 0.036f),
    bone(CanonicalMuscle.BICEPS, 0.32f, 0.220f, 0.18f, 0.480f, 0.042f, 0.036f),
    bone(CanonicalMuscle.BICEPS, 0.68f, 0.220f, 0.82f, 0.480f, 0.042f, 0.036f),
    bone(CanonicalMuscle.QUADRICEPS, 0.40f, 0.495f, 0.38f, 0.800f, 0.056f, 0.042f),
    bone(CanonicalMuscle.QUADRICEPS, 0.60f, 0.495f, 0.62f, 0.800f, 0.056f, 0.042f),
)

private val ARM_EXT = listOf(
    head(0.50f, 0.160f),
    bone(null, 0.50f, 0.200f, 0.50f, 0.240f, 0.022f, 0.030f),
    PosePlate(null, torso(0.50f, 0.240f, 0.520f, 0.140f, 0.098f, 0.125f)),
    bone(null, 0.40f, 0.520f, 0.38f, 0.820f, 0.070f, 0.050f),
    bone(null, 0.60f, 0.520f, 0.62f, 0.820f, 0.070f, 0.050f),
    bone(null, 0.38f, 0.820f, 0.38f, 0.950f, 0.048f, 0.032f),
    bone(null, 0.62f, 0.820f, 0.62f, 0.950f, 0.048f, 0.032f),
    PosePlate(null, oval(0.38f, 0.975f, 0.050f, 0.016f)),
    PosePlate(null, oval(0.62f, 0.975f, 0.050f, 0.016f)),
    bone(null, 0.36f, 0.250f, 0.22f, 0.080f, 0.044f, 0.034f),
    bone(null, 0.64f, 0.250f, 0.78f, 0.080f, 0.044f, 0.034f),
    PosePlate(CanonicalMuscle.CORE, torso(0.50f, 0.255f, 0.480f, 0.095f, 0.085f, 0.098f)),
    bone(CanonicalMuscle.TRICEPS, 0.34f, 0.230f, 0.22f, 0.090f, 0.040f, 0.032f),
    bone(CanonicalMuscle.TRICEPS, 0.66f, 0.230f, 0.78f, 0.090f, 0.040f, 0.032f),
    bone(CanonicalMuscle.SHOULDERS, 0.36f, 0.245f, 0.28f, 0.180f, 0.044f, 0.034f),
    bone(CanonicalMuscle.SHOULDERS, 0.64f, 0.245f, 0.72f, 0.180f, 0.044f, 0.034f),
    bone(CanonicalMuscle.QUADRICEPS, 0.40f, 0.535f, 0.38f, 0.820f, 0.056f, 0.042f),
    bone(CanonicalMuscle.QUADRICEPS, 0.60f, 0.535f, 0.62f, 0.820f, 0.056f, 0.042f),
)

private val HIP = listOf(
    head(0.12f, 0.520f, rx = 0.048f, ry = 0.044f),
    bone(null, 0.17f, 0.500f, 0.28f, 0.430f, 0.022f, 0.028f),
    PosePlate(null, torso(0.46f, 0.280f, 0.460f, 0.125f, 0.095f, 0.130f)),
    bone(null, 0.28f, 0.450f, 0.20f, 0.820f, 0.062f, 0.046f),
    bone(null, 0.62f, 0.300f, 0.58f, 0.560f, 0.070f, 0.052f),
    bone(null, 0.58f, 0.560f, 0.56f, 0.860f, 0.050f, 0.034f),
    bone(null, 0.20f, 0.820f, 0.18f, 0.950f, 0.040f, 0.028f),
    PosePlate(null, oval(0.18f, 0.975f, 0.048f, 0.016f)),
    PosePlate(null, oval(0.56f, 0.890f, 0.050f, 0.016f)),
    bone(CanonicalMuscle.SHOULDERS, 0.26f, 0.430f, 0.34f, 0.360f, 0.044f, 0.034f),
    PosePlate(CanonicalMuscle.CORE, torso(0.40f, 0.330f, 0.430f, 0.090f, 0.080f, 0.095f)),
    PosePlate(CanonicalMuscle.GLUTES, torso(0.62f, 0.250f, 0.400f, 0.115f, 0.105f, 0.125f)),
    bone(CanonicalMuscle.HAMSTRINGS, 0.62f, 0.320f, 0.58f, 0.560f, 0.056f, 0.042f),
    bone(CanonicalMuscle.QUADRICEPS, 0.28f, 0.470f, 0.20f, 0.800f, 0.052f, 0.040f),
)

private val CORE_FLOOR = listOf(
    head(0.12f, 0.42f, rx = 0.048f, ry = 0.044f),
    bone(null, 0.17f, 0.42f, 0.26f, 0.43f, 0.022f, 0.030f),
    PosePlate(null, torso(0.48f, 0.370f, 0.540f, 0.145f, 0.095f, 0.115f)),
    bone(null, 0.70f, 0.450f, 0.86f, 0.700f, 0.068f, 0.050f),
    bone(null, 0.86f, 0.700f, 0.88f, 0.900f, 0.044f, 0.030f),
    PosePlate(null, oval(0.88f, 0.935f, 0.048f, 0.016f)),
    bone(null, 0.28f, 0.360f, 0.24f, 0.200f, 0.040f, 0.032f),
    bone(CanonicalMuscle.SHOULDERS, 0.26f, 0.370f, 0.24f, 0.240f, 0.044f, 0.034f),
    PosePlate(CanonicalMuscle.CORE, torso(0.48f, 0.375f, 0.530f, 0.115f, 0.090f, 0.100f)),
    PosePlate(CanonicalMuscle.GLUTES, torso(0.72f, 0.400f, 0.545f, 0.095f, 0.085f, 0.095f)),
    bone(CanonicalMuscle.QUADRICEPS, 0.72f, 0.540f, 0.86f, 0.780f, 0.054f, 0.040f),
    bone(CanonicalMuscle.SHOULDERS, 0.28f, 0.220f, 0.30f, 0.360f, 0.040f, 0.032f),
)

private val SEATED_MACHINE = listOf(
    head(0.54f, 0.120f),
    bone(null, 0.52f, 0.160f, 0.50f, 0.205f, 0.022f, 0.030f),
    PosePlate(null, torso(0.50f, 0.200f, 0.500f, 0.130f, 0.095f, 0.125f)),
    bone(null, 0.38f, 0.500f, 0.18f, 0.780f, 0.078f, 0.058f),
    bone(null, 0.58f, 0.520f, 0.62f, 0.860f, 0.068f, 0.050f),
    bone(null, 0.18f, 0.780f, 0.16f, 0.940f, 0.048f, 0.032f),
    PosePlate(null, oval(0.16f, 0.968f, 0.050f, 0.016f)),
    bone(null, 0.38f, 0.220f, 0.22f, 0.420f, 0.042f, 0.034f),
    bone(CanonicalMuscle.SHOULDERS, 0.40f, 0.200f, 0.32f, 0.280f, 0.046f, 0.036f),
    PosePlate(CanonicalMuscle.CORE, torso(0.50f, 0.230f, 0.480f, 0.095f, 0.085f, 0.100f)),
    PosePlate(CanonicalMuscle.GLUTES, torso(0.54f, 0.470f, 0.600f, 0.115f, 0.105f, 0.120f)),
    bone(CanonicalMuscle.QUADRICEPS, 0.36f, 0.520f, 0.20f, 0.760f, 0.064f, 0.048f),
    bone(CanonicalMuscle.HAMSTRINGS, 0.58f, 0.560f, 0.62f, 0.840f, 0.054f, 0.040f),
    bone(CanonicalMuscle.CALVES, 0.18f, 0.790f, 0.16f, 0.940f, 0.040f, 0.026f),
)

private val CARRY = listOf(
    head(0.50f, 0.070f),
    bone(null, 0.50f, 0.110f, 0.50f, 0.150f, 0.022f, 0.030f),
    PosePlate(null, torso(0.50f, 0.150f, 0.500f, 0.145f, 0.100f, 0.128f)),
    bone(null, 0.40f, 0.500f, 0.38f, 0.800f, 0.070f, 0.050f),
    bone(null, 0.60f, 0.500f, 0.62f, 0.800f, 0.070f, 0.050f),
    bone(null, 0.38f, 0.800f, 0.38f, 0.950f, 0.048f, 0.032f),
    bone(null, 0.62f, 0.800f, 0.62f, 0.950f, 0.048f, 0.032f),
    PosePlate(null, oval(0.38f, 0.975f, 0.050f, 0.016f)),
    PosePlate(null, oval(0.62f, 0.975f, 0.050f, 0.016f)),
    bone(null, 0.34f, 0.190f, 0.18f, 0.520f, 0.046f, 0.036f),
    bone(null, 0.66f, 0.190f, 0.82f, 0.520f, 0.046f, 0.036f),
    bone(null, 0.18f, 0.520f, 0.16f, 0.720f, 0.034f, 0.028f),
    bone(null, 0.82f, 0.520f, 0.84f, 0.720f, 0.034f, 0.028f),
    bone(CanonicalMuscle.SHOULDERS, 0.34f, 0.175f, 0.26f, 0.255f, 0.048f, 0.038f),
    bone(CanonicalMuscle.SHOULDERS, 0.66f, 0.175f, 0.74f, 0.255f, 0.048f, 0.038f),
    PosePlate(CanonicalMuscle.CORE, torso(0.50f, 0.175f, 0.460f, 0.100f, 0.088f, 0.102f)),
    bone(CanonicalMuscle.QUADRICEPS, 0.40f, 0.515f, 0.38f, 0.800f, 0.056f, 0.042f),
    bone(CanonicalMuscle.QUADRICEPS, 0.60f, 0.515f, 0.62f, 0.800f, 0.056f, 0.042f),
    bone(CanonicalMuscle.BICEPS, 0.32f, 0.210f, 0.18f, 0.520f, 0.036f, 0.030f),
    bone(CanonicalMuscle.BICEPS, 0.68f, 0.210f, 0.82f, 0.520f, 0.036f, 0.030f),
)

private fun bar(y: Float, left: Float = 0.06f, right: Float = 0.94f): List<List<Pair<Float, Float>>> {
    val shaft = 0.009f
    val outerH = 0.070f
    val innerH = 0.052f
    val w = 0.024f
    val gap = 0.006f
    val cy = y.coerceIn(outerH, 1f - outerH)
    val l = left.coerceIn(0f, 1f)
    val r = right.coerceIn(0f, 1f)
    return listOf(
        poly(l, cy - shaft, r, cy - shaft, r, cy + shaft, l, cy + shaft),
        poly(l, cy - outerH, l + w, cy - outerH, l + w, cy + outerH, l, cy + outerH),
        poly(
            l + w + gap, cy - innerH, l + w * 2 + gap, cy - innerH,
            l + w * 2 + gap, cy + innerH, l + w + gap, cy + innerH,
        ),
        poly(r - w, cy - outerH, r, cy - outerH, r, cy + outerH, r - w, cy + outerH),
        poly(
            r - w * 2 - gap, cy - innerH, r - w - gap, cy - innerH,
            r - w - gap, cy + innerH, r - w * 2 - gap, cy + innerH,
        ),
    )
}

private fun dumbbell(cx: Float, cy: Float): List<List<Pair<Float, Float>>> {
    val handle = 0.010f
    return listOf(
        poly(
            cx - 0.048f, cy - handle, cx + 0.048f, cy - handle,
            cx + 0.048f, cy + handle, cx - 0.048f, cy + handle,
        ),
        poly(
            cx - 0.074f, cy - 0.036f, cx - 0.044f, cy - 0.036f,
            cx - 0.044f, cy + 0.036f, cx - 0.074f, cy + 0.036f,
        ),
        poly(
            cx + 0.044f, cy - 0.036f, cx + 0.074f, cy - 0.036f,
            cx + 0.074f, cy + 0.036f, cx + 0.044f, cy + 0.036f,
        ),
    )
}

private fun kettle(cx: Float, cy: Float): List<List<Pair<Float, Float>>> = listOf(
    oval(cx, cy + 0.018f, 0.052f, 0.046f),
    poly(
        cx - 0.028f, cy - 0.042f, cx + 0.028f, cy - 0.042f,
        cx + 0.022f, cy - 0.012f, cx - 0.022f, cy - 0.012f,
    ),
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
    EquipmentType.DUMBBELL -> dumbbell(0.50f, 0.32f)
    EquipmentType.KETTLEBELL -> kettle(0.50f, 0.32f)
    EquipmentType.MACHINE -> listOf(
        poly(0.18f, 0.08f, 0.28f, 0.08f, 0.28f, 0.96f, 0.18f, 0.96f),
        poly(0.72f, 0.08f, 0.82f, 0.08f, 0.82f, 0.96f, 0.72f, 0.96f),
        poly(0.28f, 0.14f, 0.72f, 0.14f, 0.72f, 0.22f, 0.28f, 0.22f),
    )
    else -> emptyList()
}

private fun hingeKit(equipment: EquipmentType) = when (equipment) {
    EquipmentType.BARBELL, EquipmentType.SMITH -> bar(0.58f, left = 0.50f, right = 0.96f)
    EquipmentType.KETTLEBELL -> kettle(0.72f, 0.58f)
    EquipmentType.DUMBBELL -> dumbbell(0.68f, 0.56f)
    EquipmentType.CABLE -> listOf(
        poly(0.82f, 0.06f, 0.94f, 0.06f, 0.94f, 0.70f, 0.82f, 0.70f),
        poly(0.70f, 0.54f, 0.84f, 0.54f, 0.84f, 0.62f, 0.70f, 0.62f),
    )
    EquipmentType.MACHINE -> machineFrame()
    else -> emptyList()
}

private fun pressKit(equipment: EquipmentType) = when (equipment) {
    EquipmentType.BARBELL, EquipmentType.SMITH -> bar(0.16f, left = 0.06f, right = 0.62f)
    EquipmentType.DUMBBELL -> dumbbell(0.18f, 0.16f) + dumbbell(0.18f, 0.68f)
    EquipmentType.MACHINE -> listOf(
        poly(0.04f, 0.16f, 0.14f, 0.16f, 0.14f, 0.84f, 0.04f, 0.84f),
        poly(0.14f, 0.24f, 0.40f, 0.24f, 0.40f, 0.32f, 0.14f, 0.32f),
    )
    EquipmentType.BODYWEIGHT -> emptyList()
    else -> bar(0.16f, left = 0.06f, right = 0.62f)
}

private fun overheadKit(equipment: EquipmentType) = when (equipment) {
    EquipmentType.BARBELL, EquipmentType.SMITH -> bar(0.08f)
    EquipmentType.DUMBBELL, EquipmentType.KETTLEBELL ->
        dumbbell(0.22f, 0.10f) + dumbbell(0.78f, 0.10f)
    EquipmentType.MACHINE -> machineFrame() + bar(0.08f, left = 0.22f, right = 0.78f)
    else -> bar(0.08f)
}

private fun flyKit(equipment: EquipmentType) = when (equipment) {
    EquipmentType.DUMBBELL, EquipmentType.KETTLEBELL ->
        dumbbell(0.10f, 0.40f) + dumbbell(0.90f, 0.40f)
    EquipmentType.CABLE, EquipmentType.MACHINE -> listOf(
        poly(0.02f, 0.06f, 0.12f, 0.06f, 0.12f, 0.50f, 0.02f, 0.50f),
        poly(0.88f, 0.06f, 0.98f, 0.06f, 0.98f, 0.50f, 0.88f, 0.50f),
    )
    else -> dumbbell(0.10f, 0.40f) + dumbbell(0.90f, 0.40f)
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
    EquipmentType.DUMBBELL -> dumbbell(0.14f, 0.48f)
    EquipmentType.KETTLEBELL -> kettle(0.14f, 0.48f)
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
    else -> dumbbell(0.22f, 0.10f) + dumbbell(0.78f, 0.10f)
}

private fun hipKit(equipment: EquipmentType) = when (equipment) {
    EquipmentType.BARBELL -> bar(0.30f, left = 0.42f, right = 0.92f)
    EquipmentType.MACHINE -> machineFrame()
    EquipmentType.CABLE -> listOf(
        poly(0.86f, 0.08f, 0.96f, 0.08f, 0.96f, 0.80f, 0.86f, 0.80f),
    )
    else -> emptyList()
}

private fun coreKit(equipment: EquipmentType) = when (equipment) {
    EquipmentType.OTHER -> listOf(poly(0.70f, 0.28f, 0.90f, 0.28f, 0.88f, 0.40f, 0.68f, 0.40f))
    EquipmentType.DUMBBELL -> dumbbell(0.50f, 0.30f)
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
    EquipmentType.DUMBBELL -> dumbbell(x1, y1) + dumbbell(x2, y2)
    EquipmentType.KETTLEBELL -> kettle(x1, y1) + kettle(x2, y2)
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
