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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
            // detail = false: at ~21dp wide a hairline sternum rule is sub-pixel, and drawing
            // it puts grey mush over the muscle colour instead of anatomy.
            drawFigure(view, detail = false)
            hotspotsFor(view).forEach { spot ->
                val colour = when {
                    spot.muscle == primary -> Heat3
                    spot.muscle in secondaries -> Heat3.copy(alpha = SECONDARY_ALPHA)
                    else -> null
                }
                // A secondary whose plates live only on the other view has no region here and
                // is simply skipped. Mirroring it onto this side would draw a muscle where the
                // lift does not train one.
                if (colour != null) {
                    // Both axes read from this canvas, which IS the figure box every hotspot
                    // fraction is expressed against — the same contract the Body tab uses.
                    drawRoundRect(
                        color = colour,
                        topLeft = Offset(this.size.width * spot.left, this.size.height * spot.top),
                        size = Size(
                            width = this.size.width * spot.width,
                            height = this.size.height * spot.height,
                        ),
                        cornerRadius = CornerRadius(Radius.xs.toPx() * PLATE_RADIUS_SHARE),
                    )
                }
            }
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
 * 18dp badge alike. Stroke only, in [TextSecondary], with no accent anywhere: the thumbnail
 * is metadata, and the volt budget belongs to the one thing on a screen that is a decision.
 */
private fun DrawScope.drawGlyph(glyph: EquipmentGlyph, tint: Color) {
    val stroke = Metrics.hairline.toPx() * GLYPH_STROKE_SCALE

    fun x(f: Float) = size.width * f
    fun y(f: Float) = size.height * f

    fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
        drawLine(tint, Offset(x(x1), y(y1)), Offset(x(x2), y(y2)), stroke, StrokeCap.Round)
    }

    /** A plate, drawn as a short thick stroke rather than a rect so it reads at 8dp. */
    fun plate(atX: Float, height: Float) {
        line(atX, 0.5f - height / 2f, atX, 0.5f + height / 2f)
    }

    fun circle(cx: Float, cy: Float, r: Float) {
        drawCircle(tint, radius = x(r), center = Offset(x(cx), y(cy)), style = Stroke(stroke))
    }

    fun bar(left: Float, top: Float, right: Float, bottom: Float) {
        drawRect(
            color = tint,
            topLeft = Offset(x(left), y(top)),
            size = Size(x(right - left), y(bottom - top)),
            style = Stroke(stroke),
        )
    }

    when (glyph) {
        EquipmentGlyph.BARBELL -> {
            line(0.05f, 0.5f, 0.95f, 0.5f)
            plate(0.20f, 0.52f)
            plate(0.28f, 0.36f)
            plate(0.72f, 0.36f)
            plate(0.80f, 0.52f)
        }

        EquipmentGlyph.DUMBBELL -> {
            line(0.32f, 0.5f, 0.68f, 0.5f)
            plate(0.24f, 0.44f)
            plate(0.76f, 0.44f)
        }

        EquipmentGlyph.MACHINE -> {
            line(0.5f, 0.10f, 0.5f, 0.34f)
            var top = 0.36f
            repeat(4) {
                bar(0.30f, top, 0.70f, top + 0.08f)
                top += 0.12f
            }
        }

        EquipmentGlyph.CABLE -> {
            circle(0.5f, 0.20f, 0.11f)
            line(0.56f, 0.28f, 0.72f, 0.60f)
            bar(0.60f, 0.60f, 0.86f, 0.74f)
        }

        EquipmentGlyph.SMITH -> {
            line(0.22f, 0.08f, 0.22f, 0.92f)
            line(0.78f, 0.08f, 0.78f, 0.92f)
            line(0.10f, 0.55f, 0.90f, 0.55f)
            line(0.22f, 0.55f, 0.28f, 0.49f)
            line(0.78f, 0.55f, 0.84f, 0.49f)
        }

        EquipmentGlyph.KETTLEBELL -> {
            circle(0.5f, 0.62f, 0.24f)
            val handle = Path().apply {
                moveTo(x(0.32f), y(0.48f))
                cubicTo(x(0.34f), y(0.16f), x(0.66f), y(0.16f), x(0.68f), y(0.48f))
            }
            drawPath(handle, tint, style = Stroke(stroke, cap = StrokeCap.Round))
        }

        EquipmentGlyph.BAND -> {
            circle(0.5f, 0.44f, 0.28f)
            line(0.38f, 0.66f, 0.62f, 0.88f)
            line(0.62f, 0.66f, 0.38f, 0.88f)
        }

        EquipmentGlyph.BODYWEIGHT -> {
            circle(0.5f, 0.18f, 0.10f)
            line(0.5f, 0.30f, 0.5f, 0.58f)
            line(0.5f, 0.38f, 0.28f, 0.52f)
            line(0.5f, 0.38f, 0.72f, 0.52f)
            line(0.5f, 0.58f, 0.34f, 0.88f)
            line(0.5f, 0.58f, 0.66f, 0.88f)
        }

        EquipmentGlyph.OTHER -> {
            val diamond = Path().apply {
                moveTo(x(0.5f), y(0.12f))
                lineTo(x(0.88f), y(0.5f))
                lineTo(x(0.5f), y(0.88f))
                lineTo(x(0.12f), y(0.5f))
                close()
            }
            drawPath(diamond, tint, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

/** The plates are softened, not square, but less than a card is — they are anatomy, not UI. */
private const val PLATE_RADIUS_SHARE = 0.5f

/** A secondary muscle is present, not equal — visible without competing with the primary. */
private const val SECONDARY_ALPHA = 0.4f

/** Badge edge as a share of the thumb edge, and the glyph's share of the badge. */
private const val BADGE_SHARE = 0.45f
private const val BADGE_GLYPH_SHARE = 0.70f

/** A hairline is right for a 200dp figure and invisible for an 8dp glyph. */
private const val GLYPH_STROKE_SCALE = 1.5f
