package com.sinura.personaltrainer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.MuscleNormalizer
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.Heat3
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Steel
import com.sinura.personaltrainer.ui.theme.SteelDim
import com.sinura.personaltrainer.ui.theme.Surface1
import com.sinura.personaltrainer.ui.theme.Surface2
import com.sinura.personaltrainer.ui.theme.TextSecondary

/**
 * The catalog's pictures, drawn rather than shipped.
 *
 * Every exercise row has been reserving a 40dp square since the redesign, filled with the
 * lift's initial letter — which sorts nothing, distinguishes nothing, and made a long list
 * read as a column of alphabet. This fills it with something that actually carries
 * information at a glance: which part of you the lift trains, and what you load it with.
 *
 * **Drawn in Compose, not shipped as assets.** A layered VectorDrawable would have to bake
 * the heat ramp into `res/` as static colours, outside the token layer the design checks
 * police, and `android:tint` cannot tint layers independently anyway. Drawing costs a few
 * tens of kilobytes of dex and nothing else — no bitmaps, no image loader, no APK budget.
 *
 * **Identity, not state.** The lit muscle uses a fixed [Heat3], never the owner's current
 * weekly band. The same lift always looks the same, which is what makes a thumbnail useful
 * for recognition; and these rows render inside the picker and the routine editor, which have
 * no heat snapshot to read. Live heat stays the Body tab's job, where it is the whole point.
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
 * One lift, as a figure with its muscles lit and a badge for its kit.
 *
 * Decorative by construction: every surface that shows this already names the lift beside it,
 * so the thumb clears its semantics rather than reading a second, worse version of the name
 * to a screen reader.
 */
@Composable
fun ExerciseThumb(
    exercise: Exercise,
    modifier: Modifier = Modifier,
    size: Dp = ThumbSize.row,
) {
    // `exercise.imageKey` is the hook for commissioned art, and THIS composable is the one
    // place it will ever be read — every surface draws its thumbnail through here, so keyed
    // art arrives everywhere at once or nowhere. Nothing reads it today: a key is composed
    // exactly like a null one, which is what makes writing keys safe before there is art to
    // load. Deliberately not stubbed as a dead branch; the note is the contract.
    val (primary, secondaries) = thumbMuscles(exercise)
    val view = thumbViewFor(primary)
    val shape = RoundedCornerShape(Radius.xs)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Surface1)
            .border(Metrics.hairline, Hairline, shape)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        // The figure's height is the box minus its inset, and its width follows from the
        // anatomy's fixed aspect — computed rather than laid out, because .padding() before
        // .size() would add the inset back around a box already sized to the full square and
        // push the figure outside it.
        val figureHeight = size - Metrics.space1 * 2
        Canvas(
            modifier = Modifier.size(
                width = figureHeight * FIGURE_ASPECT,
                height = figureHeight,
            ),
        ) {
            // Same plates as the Body tab and the launcher. A secondary whose plates live
            // only on the other view has no region here and stays steel.
            drawTemperFigure(
                view = view,
                fill = { plate ->
                    when {
                        plate.muscle == null -> SteelDim
                        plate.muscle == primary -> Heat3
                        plate.muscle in secondaries -> Heat3.copy(alpha = SECONDARY_ALPHA)
                        else -> Steel
                    }
                },
            )
        }
        // The badge overlaps the figure on purpose — a badge in its own gutter would cost the
        // figure a third of a 40dp square, and the equipment is the second thing you read,
        // not a peer of the anatomy.
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

/** A secondary muscle is present, not equal — visible without competing with the primary. */
private const val SECONDARY_ALPHA = 0.4f

/** Badge edge as a share of the thumb edge, and the glyph's share of the badge. */
private const val BADGE_SHARE = 0.45f
private const val BADGE_GLYPH_SHARE = 0.70f
