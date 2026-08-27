package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.MuscleNormalizer
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextSecondary

/**
 * The catalog's pictures: the locked 18-still pack, not a second drawing of them.
 *
 * Every exercise row has been reserving a 40dp square since the redesign. A known
 * [Exercise.movementKey] shows that family's still. Customs and unknown families
 * stand on the unlit front or back still. The equipment badge stays the second read.
 *
 * **Identity, not state.** Heat is baked into the family stills. Live weekly heat
 * stays the Body tab's job. `imageKey` remains null on catalog rows — the pack is
 * keyed by family, not 101 files.
 */
object ThumbSize {
    val row = 40.dp
    val header = 56.dp
    val chipGlyph = 20.dp
}

/**
 * The nine drawings. One per [EquipmentType], with no fallthrough — the enum and the glyph
 * set were designed against each other.
 */
enum class EquipmentGlyph {
    BARBELL,
    DUMBBELL,
    MACHINE,
    CABLE,
    SMITH,
    KETTLEBELL,
    BAND,
    BODYWEIGHT,
    OTHER,
}

fun glyphFor(equipment: EquipmentType): EquipmentGlyph = when (equipment) {
    EquipmentType.BARBELL -> EquipmentGlyph.BARBELL
    EquipmentType.DUMBBELL -> EquipmentGlyph.DUMBBELL
    EquipmentType.CABLE -> EquipmentGlyph.CABLE
    EquipmentType.MACHINE -> EquipmentGlyph.MACHINE
    EquipmentType.SMITH -> EquipmentGlyph.SMITH
    EquipmentType.KETTLEBELL -> EquipmentGlyph.KETTLEBELL
    EquipmentType.BAND -> EquipmentGlyph.BAND
    EquipmentType.BODYWEIGHT -> EquipmentGlyph.BODYWEIGHT
    EquipmentType.OTHER -> EquipmentGlyph.OTHER
}

/**
 * Which way the figure faces, decided by the lift's primary muscle.
 *
 * The rule is anatomical, not arbitrary: a muscle is drawn on the side its plate lives on in
 * the body map, so a bench press shows a front figure with the chest lit and a row shows a
 * back figure with the lats lit. Two muscles appear on both tables — shoulders and calves —
 * and they are settled here to the side you see them from when they are the point of the
 * lift: a lateral raise is a front view, and a calf raise is a back one.
 *
 * [CanonicalMuscle.OTHER] gets the front figure with nothing lit. A plain body is a truthful
 * answer to "we could not place this"; picking a region at random would not be.
 */
fun thumbViewFor(primary: CanonicalMuscle): BodyView = when (primary) {
    CanonicalMuscle.CHEST -> BodyView.FRONT
    CanonicalMuscle.BICEPS -> BodyView.FRONT
    CanonicalMuscle.CORE -> BodyView.FRONT
    CanonicalMuscle.QUADRICEPS -> BodyView.FRONT
    CanonicalMuscle.SHOULDERS -> BodyView.FRONT
    CanonicalMuscle.OTHER -> BodyView.FRONT
    CanonicalMuscle.BACK -> BodyView.BACK
    CanonicalMuscle.TRICEPS -> BodyView.BACK
    CanonicalMuscle.GLUTES -> BodyView.BACK
    CanonicalMuscle.HAMSTRINGS -> BodyView.BACK
    CanonicalMuscle.CALVES -> BodyView.BACK
}

/**
 * The muscles a thumbnail lights, primary first.
 *
 * Reads the junction credits the catalog ships, and falls back to the free-text muscle group
 * only when a lift has none — a custom the owner typed in. Never throws and never returns
 * nothing useful: an unplaceable lift resolves to [CanonicalMuscle.OTHER] and draws a plain
 * figure, because a missing picture must never be more than a missing picture.
 */
internal fun thumbMuscles(exercise: Exercise): Pair<CanonicalMuscle, Set<CanonicalMuscle>> {
    val credited = exercise.muscles.mapNotNull { credit ->
        MuscleNormalizer.resolveKey(credit.muscleKey)?.let { it to credit.weight }
    }
    if (credited.isEmpty()) {
        val mapping = MuscleNormalizer.normalize(exercise.muscleGroup)
        return mapping.primary to mapping.secondaries.toSet()
    }
    val primary = credited.maxByOrNull { it.second }?.first ?: CanonicalMuscle.OTHER
    val secondaries = credited.map { it.first }.filterNot { it == primary }.toSet()
    return primary to secondaries
}

/**
 * One lift: the family's still, plus the equipment badge.
 *
 * Decorative by construction: every surface that shows this already names the lift
 * beside it, so the thumb clears its semantics rather than reading a second, worse
 * version of the name to a screen reader.
 */
@Composable
fun ExerciseThumb(
    exercise: Exercise,
    modifier: Modifier = Modifier,
    size: Dp = ThumbSize.row,
) {
    val (primary, _) = thumbMuscles(exercise)
    val view = thumbViewFor(primary)
    val pose = poseFor(exercise.movementKey)
    val shape = RoundedCornerShape(Radius.xs)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Pit)
            .border(Metrics.hairline, Hairline, shape)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(artworkFor(pose = pose, view = view)),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .padding(Metrics.space1),
            contentScale = ContentScale.Fit,
        )
        EquipmentBadge(
            glyph = glyphFor(exercise.equipment),
            size = size * BADGE_SHARE,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

@Composable
private fun EquipmentBadge(
    glyph: EquipmentGlyph,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radius.xs)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Surface2)
            .border(Metrics.hairline, Hairline, shape),
        contentAlignment = Alignment.Center,
    ) {
        EquipmentGlyphIcon(glyph = glyph, size = size * BADGE_GLYPH_SHARE)
    }
}

/** The glyph on its own, for the in-workout lift chips where a figure would be illegible. */
@Composable
fun EquipmentGlyphIcon(
    glyph: EquipmentGlyph,
    modifier: Modifier = Modifier,
    size: Dp = ThumbSize.chipGlyph,
    tint: Color = TextSecondary,
) {
    Canvas(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics { },
    ) {
        drawGlyph(glyph, tint)
    }
}

/**
 * The nine drawings, in a unit square.
 *
 * Every coordinate is a fraction, so one set of numbers serves the 20dp chip glyph and the
 * 18dp badge alike. Filled plates, same language as the figure: no stroke-only stick
 * figures, no accent. The volt budget belongs to the one thing on a screen that is a decision.
 */
private fun DrawScope.drawGlyph(glyph: EquipmentGlyph, tint: Color) {
    fun x(f: Float) = size.width * f
    fun y(f: Float) = size.height * f

    fun slab(left: Float, top: Float, right: Float, bottom: Float) {
        drawRect(
            color = tint,
            topLeft = Offset(x(left), y(top)),
            size = Size(x(right - left), y(bottom - top)),
        )
    }

    fun poly(vararg xy: Float) {
        val path = Path().apply {
            moveTo(x(xy[0]), y(xy[1]))
            var i = 2
            while (i < xy.size) {
                lineTo(x(xy[i]), y(xy[i + 1]))
                i += 2
            }
            close()
        }
        drawPath(path, tint)
    }

    when (glyph) {
        EquipmentGlyph.BARBELL -> {
            slab(0.06f, 0.46f, 0.94f, 0.54f)
            slab(0.16f, 0.22f, 0.26f, 0.78f)
            slab(0.28f, 0.32f, 0.36f, 0.68f)
            slab(0.64f, 0.32f, 0.72f, 0.68f)
            slab(0.74f, 0.22f, 0.84f, 0.78f)
        }

        EquipmentGlyph.DUMBBELL -> {
            slab(0.30f, 0.46f, 0.70f, 0.54f)
            slab(0.16f, 0.28f, 0.32f, 0.72f)
            slab(0.68f, 0.28f, 0.84f, 0.72f)
        }

        EquipmentGlyph.MACHINE -> {
            slab(0.46f, 0.08f, 0.54f, 0.30f)
            slab(0.28f, 0.32f, 0.72f, 0.46f)
            slab(0.28f, 0.50f, 0.72f, 0.64f)
            slab(0.28f, 0.68f, 0.72f, 0.82f)
        }

        EquipmentGlyph.CABLE -> {
            poly(0.38f, 0.08f, 0.62f, 0.08f, 0.62f, 0.28f, 0.38f, 0.28f)
            slab(0.46f, 0.28f, 0.54f, 0.58f)
            slab(0.36f, 0.58f, 0.78f, 0.76f)
        }

        EquipmentGlyph.SMITH -> {
            slab(0.16f, 0.08f, 0.26f, 0.92f)
            slab(0.74f, 0.08f, 0.84f, 0.92f)
            slab(0.10f, 0.46f, 0.90f, 0.58f)
        }

        EquipmentGlyph.KETTLEBELL -> {
            slab(0.34f, 0.12f, 0.66f, 0.22f)
            slab(0.30f, 0.22f, 0.40f, 0.42f)
            slab(0.60f, 0.22f, 0.70f, 0.42f)
            poly(0.26f, 0.42f, 0.74f, 0.42f, 0.80f, 0.86f, 0.20f, 0.86f)
        }

        EquipmentGlyph.BAND -> {
            poly(0.22f, 0.18f, 0.78f, 0.18f, 0.70f, 0.36f, 0.30f, 0.36f)
            poly(0.22f, 0.42f, 0.78f, 0.42f, 0.70f, 0.60f, 0.30f, 0.60f)
            slab(0.44f, 0.60f, 0.56f, 0.86f)
        }

        EquipmentGlyph.BODYWEIGHT -> {
            slab(0.40f, 0.06f, 0.60f, 0.18f)
            poly(0.18f, 0.20f, 0.82f, 0.20f, 0.74f, 0.48f, 0.26f, 0.48f)
            slab(0.28f, 0.52f, 0.46f, 0.92f)
            slab(0.54f, 0.52f, 0.72f, 0.92f)
        }

        EquipmentGlyph.OTHER -> {
            poly(0.50f, 0.10f, 0.88f, 0.50f, 0.50f, 0.90f, 0.12f, 0.50f)
        }
    }
}

/** Badge edge as a share of the thumb edge, and the glyph's share of the badge. */
private const val BADGE_SHARE = 0.45f
private const val BADGE_GLYPH_SHARE = 0.70f
