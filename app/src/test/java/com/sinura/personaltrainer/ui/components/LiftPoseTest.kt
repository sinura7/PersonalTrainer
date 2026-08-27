package com.sinura.personaltrainer.ui.components

import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.DefaultExercises
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.Exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Family poses stay inside the thumb square, cover the catalog, and light the
 * primary the junction already named. SVG dump is the same geometry the
 * Canvas draws — proof the language is one person, not 101 pictures.
 */
class LiftPoseTest {
    @Test
    fun unknownAndCustomFamiliesStandStill() {
        assertEquals(LiftPose.ANATOMY, poseFor(null))
        assertEquals(LiftPose.ANATOMY, poseFor("not-a-family"))
    }

    @Test
    fun everyShippedFamilyHasAPose() {
        assertTrue(poseCoversTheCatalog())
        DefaultExercises.MOVEMENT_FAMILIES.forEach { key ->
            assertTrue("$key fell back to standing", poseFor(key) != LiftPose.ANATOMY)
        }
    }

    @Test
    fun everyBuiltInLiftLightsItsPrimaryOnThePose() {
        DefaultExercises.catalog().forEach { seed ->
            val exercise = Exercise(
                id = seed.id,
                name = seed.name,
                muscleGroup = seed.muscleGroup,
                notes = "",
                isCustom = false,
                equipment = seed.equipment,
                loadType = seed.loadType,
                movementKey = seed.movementKey,
                muscles = seed.credits,
            )
            val (primary, _) = thumbMuscles(exercise)
            val pose = poseFor(seed.movementKey)
            assertTrue("${seed.id} fell back to standing", pose != LiftPose.ANATOMY)
            val plated = platesForPose(pose).mapNotNull { it.muscle }.toSet()
            assertTrue(
                "${seed.id} primary $primary has no plate on $pose",
                primary in plated,
            )
        }
    }

    @Test
    fun poseAndKitStayInsideTheSquare() {
        val outside = ArrayList<String>()
        LiftPose.entries.filter { it != LiftPose.ANATOMY }.forEach { pose ->
            EquipmentType.entries.forEach { equipment ->
                poseAndKitPoints(pose, equipment).forEach { (x, y) ->
                    if (x !in 0f..1f) outside += "$pose $equipment x=$x"
                    if (y !in 0f..1f) outside += "$pose $equipment y=$y"
                }
            }
        }
        assertTrue(outside.joinToString(separator = "\n"), outside.isEmpty())
    }

    @Test
    fun everyPoseHasASilhouetteUnderlay() {
        LiftPose.entries.filter { it != LiftPose.ANATOMY }.forEach { pose ->
            assertTrue(
                "$pose has no structure plates",
                platesForPose(pose).any { it.muscle == null },
            )
            assertTrue(
                "$pose has no torso",
                personInk(pose).any { it is PoseInk.Fill && it.muscle == null },
            )
        }
    }

    @Test
    fun everyPoseHasInnerMusclePlates() {
        LiftPose.entries.filter { it != LiftPose.ANATOMY }.forEach { pose ->
            val inner = personInk(pose).count { it.muscle != null }
            assertTrue("$pose only has $inner inner plates", inner >= 8)
            assertTrue(
                "$pose has no Temper plates",
                personInk(pose).any { it is PoseInk.Fill && it.muscle != null },
            )
        }
    }

    @Test
    fun bibleCamerasKeepBothLimbs() {
        LiftPose.entries.filter { it != LiftPose.ANATOMY }.forEach { pose ->
            val centroids = platesForPose(pose).map { plate ->
                plate.points.map { it.first }.average()
            }
            assertTrue("$pose lost the left side", centroids.any { it < 0.45 })
            assertTrue("$pose lost the right side", centroids.any { it > 0.55 })
        }
    }

    @Test
    fun writeSilhouetteBoard() {
        val dir = File("/opt/cursor/artifacts")
        if (!dir.isDirectory) return
        dir.resolve("silhouette-board.svg").writeText(silhouetteBoardSvg())
        dir.resolve("body-figure-board.svg").writeText(bodyFigureBoardSvg())
        assertTrue(dir.resolve("silhouette-board.svg").length() > 0)
        assertTrue(dir.resolve("body-figure-board.svg").length() > 0)
    }
}

private data class PoseCell(
    val label: String,
    val pose: LiftPose,
    val equipment: EquipmentType,
    val primary: CanonicalMuscle,
    val secondaries: Set<CanonicalMuscle>,
)

private val POSE_BOARD = listOf(
    PoseCell("Squat", LiftPose.SQUAT, EquipmentType.BARBELL, CanonicalMuscle.QUADRICEPS, setOf(CanonicalMuscle.GLUTES, CanonicalMuscle.CORE)),
    PoseCell("Hinge", LiftPose.HINGE, EquipmentType.BARBELL, CanonicalMuscle.GLUTES, setOf(CanonicalMuscle.HAMSTRINGS, CanonicalMuscle.BACK)),
    PoseCell("Lunge", LiftPose.LUNGE, EquipmentType.DUMBBELL, CanonicalMuscle.QUADRICEPS, setOf(CanonicalMuscle.HAMSTRINGS, CanonicalMuscle.GLUTES, CanonicalMuscle.CORE)),
    PoseCell("H. press", LiftPose.HORIZONTAL_PRESS, EquipmentType.BARBELL, CanonicalMuscle.CHEST, setOf(CanonicalMuscle.SHOULDERS, CanonicalMuscle.TRICEPS)),
    PoseCell("V. press", LiftPose.VERTICAL_PRESS, EquipmentType.DUMBBELL, CanonicalMuscle.SHOULDERS, setOf(CanonicalMuscle.TRICEPS, CanonicalMuscle.CORE)),
    PoseCell("Fly", LiftPose.FLY, EquipmentType.DUMBBELL, CanonicalMuscle.CHEST, setOf(CanonicalMuscle.SHOULDERS)),
    PoseCell("V. pull", LiftPose.VERTICAL_PULL, EquipmentType.BODYWEIGHT, CanonicalMuscle.BACK, setOf(CanonicalMuscle.BICEPS, CanonicalMuscle.SHOULDERS)),
    PoseCell("Row", LiftPose.HORIZONTAL_PULL, EquipmentType.BARBELL, CanonicalMuscle.BACK, setOf(CanonicalMuscle.BICEPS, CanonicalMuscle.SHOULDERS)),
    PoseCell("Curl", LiftPose.ARM_CURL, EquipmentType.DUMBBELL, CanonicalMuscle.BICEPS, setOf(CanonicalMuscle.SHOULDERS, CanonicalMuscle.CORE)),
    PoseCell("Ext", LiftPose.ARM_EXT, EquipmentType.DUMBBELL, CanonicalMuscle.TRICEPS, setOf(CanonicalMuscle.SHOULDERS)),
    PoseCell("Hip", LiftPose.HIP, EquipmentType.BARBELL, CanonicalMuscle.GLUTES, setOf(CanonicalMuscle.HAMSTRINGS, CanonicalMuscle.CORE)),
    PoseCell("Core", LiftPose.CORE_FLOOR, EquipmentType.BODYWEIGHT, CanonicalMuscle.CORE, setOf(CanonicalMuscle.SHOULDERS)),
    PoseCell("Machine", LiftPose.SEATED_MACHINE, EquipmentType.MACHINE, CanonicalMuscle.QUADRICEPS, setOf(CanonicalMuscle.GLUTES, CanonicalMuscle.HAMSTRINGS)),
    PoseCell("Carry", LiftPose.CARRY, EquipmentType.DUMBBELL, CanonicalMuscle.CORE, setOf(CanonicalMuscle.SHOULDERS, CanonicalMuscle.QUADRICEPS)),
)

private const val PIT = "#07090B"
private const val SURFACE = "#0E1215"
private const val STEEL = "#5B5B5A"
private const val STEEL_DIM = "#3E4042"
private const val HEAT = "#E25A50"
private const val KIT = "#9BA7AE"
private const val INK = "#9BA7AE"

private fun silhouetteBoardSvg(): String {
    val cell = 168
    val cols = 7
    val rows = 2
    val pad = 16
    val width = pad * 2 + cols * cell
    val height = pad * 2 + rows * cell + 28
    val sb = StringBuilder()
    sb.append("""<svg xmlns="http://www.w3.org/2000/svg" width="$width" height="$height" viewBox="0 0 $width $height">""")
    sb.append("""<rect width="100%" height="100%" fill="$PIT"/>""")
    sb.append("""<text x="$pad" y="22" fill="$INK" font-family="sans-serif" font-size="13">Library poses — Temper plates on a skeleton, hairline seams, Heat3 on working plates</text>""")
    POSE_BOARD.forEachIndexed { i, cellData ->
        val col = i % cols
        val row = i / cols
        val x = pad + col * cell
        val y = 28 + pad + row * cell
        sb.append(poseCellSvg(cellData, x, y, cell - 12))
    }
    sb.append("</svg>")
    return sb.toString()
}

private fun bodyFigureBoardSvg(): String {
    val figH = 420
    val figW = (figH * FIGURE_ASPECT).toInt()
    val pad = 24
    val width = pad * 3 + figW * 2
    val height = pad * 2 + figH + 36
    val sb = StringBuilder()
    sb.append("""<svg xmlns="http://www.w3.org/2000/svg" width="$width" height="$height" viewBox="0 0 $width $height">""")
    sb.append("""<rect width="100%" height="100%" fill="$PIT"/>""")
    sb.append("""<text x="$pad" y="22" fill="$INK" font-family="sans-serif" font-size="13">Body tab — standing figure, live heat on the working plates</text>""")
    sb.append(figureSvg(BodyView.FRONT, pad, pad + 16, figW, figH, CanonicalMuscle.CHEST))
    sb.append(figureSvg(BodyView.BACK, pad * 2 + figW, pad + 16, figW, figH, CanonicalMuscle.BACK))
    sb.append("""<text x="${pad + figW / 2}" y="${height - 8}" fill="$INK" font-family="sans-serif" font-size="12" text-anchor="middle">Front</text>""")
    sb.append("""<text x="${pad * 2 + figW + figW / 2}" y="${height - 8}" fill="$INK" font-family="sans-serif" font-size="12" text-anchor="middle">Back</text>""")
    sb.append("</svg>")
    return sb.toString()
}

private fun poseCellSvg(cell: PoseCell, x: Int, y: Int, size: Int): String {
    val inner = size - 22
    val sb = StringBuilder()
    sb.append("""<g transform="translate($x $y)">""")
    sb.append("""<rect width="$size" height="$size" rx="6" fill="$SURFACE" stroke="#39434A" stroke-width="1"/>""")
    val secondaries = cell.secondaries
    fun color(muscle: CanonicalMuscle?): Pair<String, String> = when (muscle) {
        null -> STEEL_DIM to "1"
        cell.primary -> HEAT to "1"
        in secondaries -> HEAT to "0.4"
        else -> STEEL to "1"
    }
    val person = personInk(cell.pose)
    person.forEach { ink ->
        sb.append(inkEl(ink, inner, inner, 11, 8, STEEL_DIM, "1"))
    }
    person.forEach { ink ->
        val (fill, opacity) = color(ink.muscle)
        sb.append(inkEl(ink, inner, inner, 11, 8, fill, opacity))
    }
    person.forEach { ink ->
        if (ink is PoseInk.Fill) {
            sb.append(inkEl(ink, inner, inner, 11, 8, "none", "1", strokeOnly = true))
        }
    }
    kitInk(cell.pose, cell.equipment).forEach { ink ->
        sb.append(inkEl(ink, inner, inner, 11, 8, KIT, "1"))
    }
    sb.append("""<text x="${size / 2}" y="${size - 6}" fill="$INK" font-family="sans-serif" font-size="11" text-anchor="middle">${cell.label}</text>""")
    sb.append("</g>")
    return sb.toString()
}

private fun figureSvg(
    view: BodyView,
    x: Int,
    y: Int,
    w: Int,
    h: Int,
    primary: CanonicalMuscle,
): String {
    val sb = StringBuilder()
    sb.append("""<g transform="translate($x $y)">""")
    sb.append(pathEl(FIGURE_OUTLINE, w, h, 0, 0, STEEL_DIM, "1"))
    val secondaries = when (view) {
        BodyView.FRONT -> setOf(
            CanonicalMuscle.SHOULDERS,
            CanonicalMuscle.BICEPS,
            CanonicalMuscle.CORE,
            CanonicalMuscle.QUADRICEPS,
        )
        BodyView.BACK -> setOf(
            CanonicalMuscle.GLUTES,
            CanonicalMuscle.HAMSTRINGS,
            CanonicalMuscle.TRICEPS,
            CanonicalMuscle.CALVES,
        )
    }
    platesFor(view).forEach { plate ->
        val fill = when (plate.muscle) {
            null -> STEEL_DIM
            primary -> HEAT
            in secondaries -> HEAT
            else -> STEEL
        }
        val opacity = when (plate.muscle) {
            primary -> "1"
            in secondaries -> "0.4"
            else -> "1"
        }
        sb.append(pathEl(plate.points, w, h, 0, 0, fill, opacity))
    }
    sb.append("</g>")
    return sb.toString()
}

private fun inkEl(
    ink: PoseInk,
    width: Int,
    height: Int,
    ox: Int,
    oy: Int,
    color: String,
    opacity: String,
    strokeOnly: Boolean = false,
): String {
    fun x(v: Float) = ox + v * width
    fun y(v: Float) = oy + v * height
    val m = minOf(width, height).toFloat()
    return when (ink) {
        is PoseInk.Limb ->
            """<line x1="${x(ink.x1)}" y1="${y(ink.y1)}" x2="${x(ink.x2)}" y2="${y(ink.y2)}" """ +
                """stroke="$color" stroke-opacity="$opacity" stroke-width="${ink.width * m}" """ +
                """stroke-linecap="round"/>"""
        is PoseInk.Taper ->
            pathEl(ink.toPlate().points, width, height, ox, oy, color, opacity)
        is PoseInk.Fill ->
            if (strokeOnly) {
                pathEl(ink.toPlate().points, width, height, ox, oy, "none", "1", stroke = true)
            } else {
                pathEl(ink.toPlate().points, width, height, ox, oy, color, opacity)
            }
        is PoseInk.Dot ->
            """<circle cx="${x(ink.x)}" cy="${y(ink.y)}" r="${ink.r * m}" """ +
                """fill="$color" fill-opacity="$opacity"/>"""
        is PoseInk.Oval ->
            """<ellipse cx="${x(ink.x)}" cy="${y(ink.y)}" rx="${ink.rx * width}" ry="${ink.ry * height}" """ +
                """fill="$color" fill-opacity="$opacity"/>"""
        is PoseInk.Rect ->
            """<rect x="${x(ink.left)}" y="${y(ink.top)}" width="${(ink.right - ink.left) * width}" """ +
                """height="${(ink.bottom - ink.top) * height}" rx="${0.012f * m}" """ +
                """fill="$color" fill-opacity="$opacity"/>"""
    }
}

private fun pathEl(
    points: List<Pair<Float, Float>>,
    width: Int,
    height: Int,
    ox: Int,
    oy: Int,
    fill: String,
    opacity: String,
    stroke: Boolean = false,
): String {
    val n = points.size
    if (n < 3) return ""
    fun px(i: Int) = ox + points[i].first * width
    fun py(i: Int) = oy + points[i].second * height
    fun lerp(ax: Float, ay: Float, bx: Float, by: Float, t: Float) =
        (ax + (bx - ax) * t) to (ay + (by - ay) * t)
    val t = 0.16f
    val d = StringBuilder()
    val start = lerp(px(0), py(0), px(n - 1), py(n - 1), t)
    d.append("M ${start.first} ${start.second}")
    for (i in 0 until n) {
        val prev = if (i == 0) n - 1 else i - 1
        val next = (i + 1) % n
        val arrive = lerp(px(i), py(i), px(prev), py(prev), t)
        val leave = lerp(px(i), py(i), px(next), py(next), t)
        if (i != 0) d.append(" L ${arrive.first} ${arrive.second}")
        d.append(" Q ${px(i)} ${py(i)} ${leave.first} ${leave.second}")
    }
    d.append(" Z")
    val edge = if (stroke) {
        """ stroke="#FFFFFF" stroke-opacity="0.14" stroke-width="0.7""""
    } else {
        ""
    }
    val fillAttr = if (fill == "none") {
        """fill="none""""
    } else {
        """fill="$fill" fill-opacity="$opacity""""
    }
    return "<path d=\"" + d + "\" " + fillAttr + edge + "/>"
}
