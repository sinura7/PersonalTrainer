package com.sinura.personaltrainer.ui.progress

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.BodyHeatSnapshot
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.MuscleLoadSummary
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.MetricCluster
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.HairlineStrong
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Motion
import com.sinura.personaltrainer.ui.theme.OutlineSolid
import com.sinura.personaltrainer.ui.theme.OutlineSolidVariant
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.Surface1
import com.sinura.personaltrainer.ui.theme.SurfacePressed
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.heatColor
import com.sinura.personaltrainer.ui.units.LocalWeightUnit

enum class BodyView(val label: String) {
    FRONT("Front"),
    BACK("Back"),
}

/** A muscle plate, as a fraction of the figure box — never of the screen. See [FIGURE_ASPECT]. */
private data class BodyHotspot(
    val muscle: CanonicalMuscle,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

@Composable
fun BodyMapCard(
    snapshot: BodyHeatSnapshot,
    view: BodyView,
    onViewChange: (BodyView) -> Unit,
    selected: CanonicalMuscle?,
    onSelect: (CanonicalMuscle) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Metrics.space3),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Metrics.space2)) {
            BodyView.entries.forEach { option ->
                InstrumentChip(
                    label = option.label,
                    selected = view == option,
                    onClick = { onViewChange(option) },
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PANEL_HEIGHT)
                .clip(RoundedCornerShape(Radius.lg))
                .background(Surface1)
                .border(Metrics.hairline, Hairline, RoundedCornerShape(Radius.lg)),
            contentAlignment = Alignment.Center,
        ) {
            // The figure owns a fixed ratio rather than the card's width. Every plate below is
            // a fraction of this box, so letting the box stretch with the device stretched the
            // anatomy with it: on a wide phone the body came out squat and the plates drifted
            // off the muscles they name.
            //
            // Anatomy sizes the plates, so an arm or a calf lands under the 48dp touch floor
            // and cannot be padded out without covering its neighbour. The muscle rows under
            // the map select the same muscles at full row height, and are the reliable target.
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = Metrics.space4)
                    .aspectRatio(FIGURE_ASPECT, matchHeightConstraintsFirst = true),
            ) {
                val figureWidth = maxWidth
                val figureHeight = maxHeight
                Canvas(modifier = Modifier.fillMaxSize()) { drawFigure(view) }
                hotspotsFor(view).forEach { spot ->
                    // Keyed, so switching views does not hand a plate the colour animation of
                    // whichever plate happened to occupy its index in the other view.
                    key(spot.muscle, spot.left) {
                        val load = snapshot.load(spot.muscle)
                        val fill by animateColorAsState(
                            targetValue = heatColor(load.heat),
                            animationSpec = tween(durationMillis = Motion.BASE, easing = Motion.Standard),
                            label = "heat-${spot.muscle.name}",
                        )
                        val isSelected = selected == spot.muscle
                        val shape = RoundedCornerShape(Radius.xs)
                        Box(
                            modifier = Modifier
                                .offset(
                                    x = figureWidth * spot.left,
                                    y = figureHeight * spot.top,
                                )
                                .size(
                                    width = figureWidth * spot.width,
                                    height = figureHeight * spot.height,
                                )
                                .clip(shape)
                                .background(fill)
                                .border(
                                    width = if (isSelected) SELECTED_BORDER else Metrics.hairline,
                                    color = if (isSelected) Volt else HairlineStrong,
                                    shape = shape,
                                )
                                .semantics {
                                    contentDescription =
                                        "${spot.muscle.displayName}, ${load.band.legendLabel} load"
                                    role = Role.Button
                                }
                                .clickable { onSelect(spot.muscle) },
                        )
                    }
                }
            }
        }
        HeatLegend()
    }
}

/**
 * The body, drawn as a wireframe rather than as two slabs.
 *
 * Front and back genuinely differ: the plates split around a sternum on one and a spine on
 * the other, and the details between them — clavicles against trapezius, kneecaps against
 * knee creases, toes against heels — change with the view. The toggle used to redraw the
 * identical pair of rectangles, so nothing on screen confirmed that it had done anything.
 */
private fun DrawScope.drawFigure(view: BodyView) {
    // Opaque, not the outline colour at an alpha: the parts of the figure overlap where they
    // join, and a translucent fill would draw every one of those joins as a bright seam.
    val body = OutlineSolidVariant
    val detail = OutlineSolid
    val hair = Metrics.hairline.toPx()
    val limb = CornerRadius(Radius.sm.toPx())

    fun px(fraction: Float) = size.width * fraction
    fun py(fraction: Float) = size.height * fraction

    fun slab(left: Float, top: Float, right: Float, bottom: Float) {
        drawRoundRect(
            color = body,
            topLeft = Offset(px(left), py(top)),
            size = Size(px(right - left), py(bottom - top)),
            cornerRadius = limb,
        )
    }

    fun blob(left: Float, top: Float, right: Float, bottom: Float) {
        drawOval(
            color = body,
            topLeft = Offset(px(left), py(top)),
            size = Size(px(right - left), py(bottom - top)),
        )
    }

    fun rule(fromX: Float, fromY: Float, toX: Float, toY: Float, weight: Float = 1f) {
        drawLine(
            color = detail,
            start = Offset(px(fromX), py(fromY)),
            end = Offset(px(toX), py(toY)),
            strokeWidth = hair * weight,
            cap = StrokeCap.Round,
        )
    }

    val torso = Path().apply {
        moveTo(px(0.278f), py(0.188f))
        lineTo(px(0.345f), py(0.158f))
        lineTo(px(0.655f), py(0.158f))
        lineTo(px(0.722f), py(0.188f))
        lineTo(px(0.706f), py(0.300f))
        lineTo(px(0.652f), py(0.395f))
        lineTo(px(0.674f), py(0.452f))
        lineTo(px(0.702f), py(0.522f))
        lineTo(px(0.298f), py(0.522f))
        lineTo(px(0.326f), py(0.452f))
        lineTo(px(0.348f), py(0.395f))
        lineTo(px(0.294f), py(0.300f))
        close()
    }
    drawPath(path = torso, color = body)

    blob(0.420f, 0.008f, 0.580f, 0.112f)
    slab(0.458f, 0.095f, 0.542f, 0.180f)
    slab(0.126f, 0.192f, 0.282f, 0.382f)
    slab(0.718f, 0.192f, 0.874f, 0.382f)
    slab(0.142f, 0.372f, 0.268f, 0.552f)
    slab(0.732f, 0.372f, 0.858f, 0.552f)
    slab(0.300f, 0.500f, 0.462f, 0.748f)
    slab(0.538f, 0.500f, 0.700f, 0.748f)
    slab(0.316f, 0.734f, 0.446f, 0.958f)
    slab(0.554f, 0.734f, 0.684f, 0.958f)

    when (view) {
        BodyView.FRONT -> {
            blob(0.146f, 0.540f, 0.262f, 0.606f)
            blob(0.738f, 0.540f, 0.854f, 0.606f)
            blob(0.288f, 0.940f, 0.474f, 0.996f)
            blob(0.526f, 0.940f, 0.712f, 0.996f)
            rule(0.396f, 0.204f, 0.500f, 0.226f)
            rule(0.604f, 0.204f, 0.500f, 0.226f)
            rule(0.500f, 0.232f, 0.500f, 0.500f)
            drawCircle(
                color = detail,
                radius = px(0.030f),
                center = Offset(px(0.381f), py(0.742f)),
                style = Stroke(width = hair),
            )
            drawCircle(
                color = detail,
                radius = px(0.030f),
                center = Offset(px(0.619f), py(0.742f)),
                style = Stroke(width = hair),
            )
        }

        BodyView.BACK -> {
            slab(0.152f, 0.546f, 0.256f, 0.600f)
            slab(0.744f, 0.546f, 0.848f, 0.600f)
            slab(0.320f, 0.946f, 0.442f, 0.996f)
            slab(0.558f, 0.946f, 0.680f, 0.996f)
            rule(0.500f, 0.176f, 0.372f, 0.230f)
            rule(0.500f, 0.176f, 0.628f, 0.230f)
            rule(0.500f, 0.172f, 0.500f, 0.520f, weight = 2f)
            VERTEBRAE.forEach { at -> rule(0.484f, at, 0.516f, at) }
            rule(0.330f, 0.744f, 0.432f, 0.744f)
            rule(0.568f, 0.744f, 0.670f, 0.744f)
        }
    }
}

/**
 * Colour is never the only channel here: each swatch carries its band as a kicker, and the
 * rows below state the volume as a numeral. "Rest" is on the scale rather than off it, so a
 * muscle with no work in the window still has a name for what it is showing.
 */
@Composable
fun HeatLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Kicker("Load")
        Row(
            horizontalArrangement = Arrangement.spacedBy(Metrics.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendSwatch("Rest", heatColor(0f))
            LegendSwatch("Low", heatColor(0.22f))
            LegendSwatch("Moderate", heatColor(0.5f))
            LegendSwatch("High", heatColor(0.95f))
        }
    }
}

@Composable
private fun LegendSwatch(label: String, color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Metrics.space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(LEGEND_DOT)
                .clip(CircleShape)
                .background(color),
        )
        Kicker(label, color = TextTertiary)
    }
}

/**
 * One muscle, as a readout.
 *
 * The trailing edge — the slot the eye lands on and the only column that lines up down the
 * list — used to hold the band word, which the swatch beside the name already says in
 * colour. The volume it duplicated was buried mid-sentence in "4 sets · 3,120 kg · 2 days
 * ago". The numbers now hold the columns and the sentence is gone.
 */
@Composable
fun MuscleHeatRow(
    load: MuscleLoadSummary,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    unit: WeightUnit = LocalWeightUnit.current,
) {
    val fill by animateColorAsState(
        targetValue = heatColor(load.heat),
        animationSpec = tween(durationMillis = Motion.BASE, easing = Motion.Standard),
        label = "row-${load.muscle.name}",
    )
    InstrumentRow(
        title = load.muscle.displayName,
        modifier = modifier.background(if (selected) SurfacePressed else Color.Transparent),
        subtitle = recencyLabel(load),
        onClick = onClick,
        leading = {
            Box(
                modifier = Modifier
                    .size(width = HEAT_SWATCH_WIDTH, height = HEAT_SWATCH_HEIGHT)
                    .clip(RoundedCornerShape(Radius.xs))
                    .background(fill)
                    .semantics { contentDescription = "${load.band.legendLabel} load" },
            )
        },
    ) {
        MetricCluster(value = load.workingSets.toString(), label = "sets")
        MetricCluster(
            value = WeightConverter.formatGroupedNumber(
                WeightConverter.toDisplayValue(load.volumeKg, unit),
            ),
            label = unit.suffix,
        )
    }
}

fun recencyLabel(load: MuscleLoadSummary): String = when (val days = load.daysSinceLastTrained) {
    null -> "Not trained yet"
    0 -> "Trained today"
    1 -> "1 day ago"
    else -> "$days days ago"
}

private fun hotspotsFor(view: BodyView): List<BodyHotspot> = when (view) {
    BodyView.FRONT -> FRONT_HOTSPOTS
    BodyView.BACK -> BACK_HOTSPOTS
}

/**
 * Width over height of the figure box.
 *
 * Everything anatomical in this file is expressed against that box, which is why it has to
 * be constant. The plates split left and right where a real pair of muscles does, so the
 * channel between them stays open for the sternum on the front and the spine on the back.
 */
private const val FIGURE_ASPECT = 0.52f

private val FRONT_HOTSPOTS = listOf(
    BodyHotspot(CanonicalMuscle.SHOULDERS, 0.176f, 0.186f, 0.150f, 0.080f),
    BodyHotspot(CanonicalMuscle.SHOULDERS, 0.674f, 0.186f, 0.150f, 0.080f),
    BodyHotspot(CanonicalMuscle.CHEST, 0.334f, 0.226f, 0.140f, 0.100f),
    BodyHotspot(CanonicalMuscle.CHEST, 0.526f, 0.226f, 0.140f, 0.100f),
    BodyHotspot(CanonicalMuscle.BICEPS, 0.126f, 0.272f, 0.156f, 0.120f),
    BodyHotspot(CanonicalMuscle.BICEPS, 0.718f, 0.272f, 0.156f, 0.120f),
    BodyHotspot(CanonicalMuscle.CORE, 0.386f, 0.344f, 0.228f, 0.140f),
    BodyHotspot(CanonicalMuscle.QUADRICEPS, 0.300f, 0.524f, 0.162f, 0.200f),
    BodyHotspot(CanonicalMuscle.QUADRICEPS, 0.538f, 0.524f, 0.162f, 0.200f),
    BodyHotspot(CanonicalMuscle.CALVES, 0.316f, 0.762f, 0.130f, 0.176f),
    BodyHotspot(CanonicalMuscle.CALVES, 0.554f, 0.762f, 0.130f, 0.176f),
)

private val BACK_HOTSPOTS = listOf(
    BodyHotspot(CanonicalMuscle.SHOULDERS, 0.176f, 0.186f, 0.150f, 0.080f),
    BodyHotspot(CanonicalMuscle.SHOULDERS, 0.674f, 0.186f, 0.150f, 0.080f),
    BodyHotspot(CanonicalMuscle.BACK, 0.334f, 0.226f, 0.146f, 0.170f),
    BodyHotspot(CanonicalMuscle.BACK, 0.520f, 0.226f, 0.146f, 0.170f),
    BodyHotspot(CanonicalMuscle.TRICEPS, 0.126f, 0.272f, 0.156f, 0.120f),
    BodyHotspot(CanonicalMuscle.TRICEPS, 0.718f, 0.272f, 0.156f, 0.120f),
    BodyHotspot(CanonicalMuscle.GLUTES, 0.330f, 0.420f, 0.150f, 0.096f),
    BodyHotspot(CanonicalMuscle.GLUTES, 0.520f, 0.420f, 0.150f, 0.096f),
    BodyHotspot(CanonicalMuscle.HAMSTRINGS, 0.300f, 0.540f, 0.162f, 0.186f),
    BodyHotspot(CanonicalMuscle.HAMSTRINGS, 0.538f, 0.540f, 0.162f, 0.186f),
    BodyHotspot(CanonicalMuscle.CALVES, 0.316f, 0.762f, 0.130f, 0.176f),
    BodyHotspot(CanonicalMuscle.CALVES, 0.554f, 0.762f, 0.130f, 0.176f),
)

private val VERTEBRAE = listOf(0.250f, 0.310f, 0.370f, 0.440f, 0.500f)

private val PANEL_HEIGHT = 440.dp
private val SELECTED_BORDER = 2.dp
private val LEGEND_DOT = 10.dp
private val HEAT_SWATCH_WIDTH = 10.dp
private val HEAT_SWATCH_HEIGHT = 32.dp
