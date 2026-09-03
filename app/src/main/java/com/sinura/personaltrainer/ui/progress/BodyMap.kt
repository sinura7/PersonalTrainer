package com.sinura.personaltrainer.ui.progress

import androidx.compose.animation.animateColorAsState
import com.sinura.personaltrainer.ui.theme.instrumentTween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.BodyHeatCopy
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.BodyHeatSnapshot
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.MuscleLoadSummary
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.BodyView
import com.sinura.personaltrainer.ui.components.FIGURE_ASPECT
import com.sinura.personaltrainer.ui.components.InstrumentChip
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.MetricCluster
import com.sinura.personaltrainer.ui.components.drawTemperFigure
import com.sinura.personaltrainer.ui.components.hotspotsFor
import com.sinura.personaltrainer.ui.theme.Hairline
import com.sinura.personaltrainer.ui.theme.HairlineStrong
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Motion
import com.sinura.personaltrainer.ui.theme.Radius
import com.sinura.personaltrainer.ui.theme.SteelDim
import com.sinura.personaltrainer.ui.theme.Surface1
import com.sinura.personaltrainer.ui.theme.SurfacePressed
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.theme.heatColor
import com.sinura.personaltrainer.ui.units.LocalWeightUnit

@Composable
fun BodyMapCard(
    snapshot: BodyHeatSnapshot,
    view: BodyView,
    onViewChange: (BodyView) -> Unit,
    selected: CanonicalMuscle?,
    onSelect: (CanonicalMuscle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val panelHeight = BodyViewport.figureHeightDp(
        LocalConfiguration.current.screenHeightDp,
    ).dp
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        HeatLegend()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(panelHeight)
                .clip(RoundedCornerShape(Radius.lg))
                .background(Surface1)
                .border(Metrics.hairline, Hairline, RoundedCornerShape(Radius.lg))
                .testTag(BodyTags.MAP)
                .semantics {
                    contentDescription = BodyTags.MAP_SPOKEN
                },
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
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawTemperFigure(
                        view = view,
                        fill = { plate ->
                            plate.muscle?.let { heatColor(snapshot.load(it).heat) } ?: SteelDim
                        },
                        selected = selected,
                        selectedStroke = Volt,
                        edge = HairlineStrong,
                    )
                }
                hotspotsFor(view).forEach { spot ->
                    // Invisible tap targets. The plate fill lives on the canvas so the
                    // geometry can stay polygonal; these boxes only have to be hittable.
                    key(spot.muscle, spot.left) {
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
                                // Sighted shortcut only. Anatomy plates sit under 48 dp
                                // (FND-023). TalkBack uses the muscle rows under the map.
                                .clickable { onSelect(spot.muscle) }
                                .clearAndSetSemantics { },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(Metrics.space2),
                horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
            ) {
                BodyView.entries.forEach { option ->
                    InstrumentChip(
                        label = option.label,
                        selected = view == option,
                        onClick = { onViewChange(option) },
                        modifier = Modifier.testTag(
                            if (option == BodyView.FRONT) BodyTags.VIEW_FRONT else BodyTags.VIEW_BACK,
                        ),
                    )
                }
            }
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
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Metrics.space1),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
        Text(
            BodyHeatCopy.LEGEND_CAPTION,
            style = InstrumentType.caption,
            color = TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
                .size(width = LEGEND_DOT, height = LEGEND_DOT)
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
        animationSpec = instrumentTween(Motion.BASE),
        label = "row-${load.muscle.name}",
    )
    val spoken = muscleRowSpoken(load, unit)
    InstrumentRow(
        title = load.muscle.displayName,
        modifier = modifier
            .background(if (selected) SurfacePressed else Color.Transparent)
            .testTag(BodyTags.muscle(load.muscle))
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        subtitle = recencyLabel(load),
        onClick = onClick,
        leading = {
            Box(
                modifier = Modifier
                    .size(width = HEAT_SWATCH_WIDTH, height = HEAT_SWATCH_HEIGHT)
                    .background(fill)
                    .clearAndSetSemantics { },
            )
        },
    ) {
        MetricCluster(value = load.workingSets.toString(), label = "sets")
        // Reps for a muscle trained only with bodyweight lifts. "0 kg" beside a real set
        // count would read as the app having failed to notice the work.
        val column = SetCopy.workColumn(load.work, unit)
        MetricCluster(value = column.value, label = column.label)
    }
}

fun recencyLabel(load: MuscleLoadSummary): String = when (val days = load.daysSinceLastTrained) {
    null -> "Not trained yet"
    0 -> "Trained today"
    1 -> "1 day ago"
    else -> "$days days ago"
}

/** One TalkBack name for the reliable 48 dp muscle row (FND-023). */
fun muscleRowSpoken(load: MuscleLoadSummary, unit: WeightUnit): String {
    val column = SetCopy.workColumn(load.work, unit)
    return "${load.muscle.displayName}, ${recencyLabel(load)}, " +
        "${load.band.legendLabel} load, ${load.workingSets} sets, " +
        "${column.value} ${column.label}"
}

object BodyTags {
    const val MAP = "body-map"
    const val MAP_SPOKEN =
        "Body map illustration. Use the muscle list below to open a muscle."
    const val MUSCLES = "body-muscles"
    const val WINDOW_DAY = "body-window-day"
    const val WINDOW_WEEK = "body-window-week"
    const val WINDOW_MONTH = "body-window-month"
    const val FIND_LIFTS = "body-find-lifts"
    const val VIEW_FRONT = "body-view-front"
    const val VIEW_BACK = "body-view-back"
    const val EMPTY = "body-empty-window"
    const val START_SHEET = "body-start-sheet"

    fun muscle(muscle: CanonicalMuscle): String = "body-muscle-${muscle.name}"

    fun window(window: HeatWindow): String = when (window) {
        HeatWindow.DAY -> WINDOW_DAY
        HeatWindow.CURRENT_WEEK -> WINDOW_WEEK
        HeatWindow.CURRENT_MONTH -> WINDOW_MONTH
    }
}

private val LEGEND_DOT = 10.dp
private val HEAT_SWATCH_WIDTH = 10.dp
private val HEAT_SWATCH_HEIGHT = 32.dp
