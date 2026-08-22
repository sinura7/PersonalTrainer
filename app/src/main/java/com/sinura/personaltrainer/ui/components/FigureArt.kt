package com.sinura.personaltrainer.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.ui.theme.HairlineStrong
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Steel
import com.sinura.personaltrainer.ui.theme.SteelDim
import com.sinura.personaltrainer.ui.theme.Volt

/**
 * The Temper figure: polygonal plates with a hairline gap, the same language as the launcher.
 *
 * One geometry serves the Body tab, the 40dp thumbnails, the empty-state mark and the
 * monochrome notification silhouette. A muscle is a plate (or a pair); lighting it is a
 * fill, not a second set of coordinates.
 */
enum class BodyView(val label: String) {
    FRONT("Front"),
    BACK("Back"),
}

/** Axis-aligned tap target for a plate. Fractions of the figure box — see [FIGURE_ASPECT]. */
internal data class BodyHotspot(
    val muscle: CanonicalMuscle,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/** One steel plate. [muscle] is null for structure (head, neck, feet) that never takes heat. */
internal data class BodyPlate(
    val muscle: CanonicalMuscle?,
    val points: List<Pair<Float, Float>>,
) {
    init {
        require(points.size >= 3) { "A plate needs three corners" }
    }

    val left: Float get() = points.minOf { it.first }
    val top: Float get() = points.minOf { it.second }
    val right: Float get() = points.maxOf { it.first }
    val bottom: Float get() = points.maxOf { it.second }
}

/**
 * Width over height of the figure box.
 *
 * Everything anatomical in this file is expressed against that box, which is why it has to
 * be constant. The plates split left and right where a real pair of muscles does, so the
 * channel between them stays open for the sternum on the front and the spine on the back.
 */
internal const val FIGURE_ASPECT = 0.52f

internal fun platesFor(view: BodyView): List<BodyPlate> = when (view) {
    BodyView.FRONT -> FRONT_PLATES
    BodyView.BACK -> BACK_PLATES
}

internal fun hotspotsFor(view: BodyView): List<BodyHotspot> =
    platesFor(view).mapNotNull { plate ->
        val muscle = plate.muscle ?: return@mapNotNull null
        BodyHotspot(
            muscle = muscle,
            left = plate.left,
            top = plate.top,
            width = plate.right - plate.left,
            height = plate.bottom - plate.top,
        )
    }

/**
 * Draw every plate of [view].
 *
 * [fill] is per plate so a thumbnail can light one muscle at Heat3 while the Body tab
 * paints the weekly ramp, without a second copy of the geometry.
 */
internal fun DrawScope.drawTemperFigure(
    view: BodyView,
    fill: (BodyPlate) -> Color,
    selected: CanonicalMuscle? = null,
    selectedStroke: Color = Volt,
    edge: Color? = null,
) {
    val hair = Metrics.hairline.toPx()
    platesFor(view).forEach { plate ->
        val path = plate.toPath(size.width, size.height)
        drawPath(path = path, color = fill(plate))
        if (edge != null) {
            drawPath(path = path, color = edge, style = Stroke(width = hair))
        }
        if (selected != null && plate.muscle == selected) {
            drawPath(
                path = path,
                color = selectedStroke,
                style = Stroke(width = Metrics.emphasisBorder.toPx()),
            )
        }
    }
}

/**
 * Unlit steel figure. [detail] is kept so existing callers do not change; the plates carry
 * the anatomy now, so the old sternum/spine hairlines are gone.
 */
internal fun DrawScope.drawFigure(view: BodyView, detail: Boolean = true) {
    drawTemperFigure(
        view = view,
        fill = { plate -> if (plate.muscle == null) SteelDim else Steel },
        edge = if (detail) HairlineStrong else null,
    )
}

/** The launcher pose: front torso, one Heat3 plate — the viewer's-right pec. */
internal fun BodyPlate.isTemperAccent(): Boolean =
    muscle == CanonicalMuscle.CHEST && left > 0.5f

private fun BodyPlate.toPath(width: Float, height: Float): Path = Path().apply {
    val first = points.first()
    moveTo(first.first * width, first.second * height)
    for (i in 1 until points.size) {
        val point = points[i]
        lineTo(point.first * width, point.second * height)
    }
    close()
}

private fun plate(muscle: CanonicalMuscle?, vararg xy: Float): BodyPlate {
    require(xy.size >= 6 && xy.size % 2 == 0)
    val points = ArrayList<Pair<Float, Float>>(xy.size / 2)
    var i = 0
    while (i < xy.size) {
        points.add(xy[i] to xy[i + 1])
        i += 2
    }
    return BodyPlate(muscle, points)
}

/*
 * Coordinates are fractions of the figure box. Gaps between neighbours are deliberate —
 * that is the Temper seam, the same void that sits between the launcher plates.
 */

private val FRONT_PLATES = listOf(
    plate(null, 0.418f, 0.012f, 0.582f, 0.012f, 0.574f, 0.086f, 0.426f, 0.086f),
    plate(null, 0.448f, 0.092f, 0.552f, 0.092f, 0.552f, 0.148f, 0.448f, 0.148f),
    plate(
        CanonicalMuscle.SHOULDERS,
        0.078f, 0.168f, 0.318f, 0.152f, 0.300f, 0.248f, 0.062f, 0.286f,
    ),
    plate(
        CanonicalMuscle.SHOULDERS,
        0.682f, 0.152f, 0.922f, 0.168f, 0.938f, 0.286f, 0.700f, 0.248f,
    ),
    plate(
        CanonicalMuscle.CHEST,
        0.324f, 0.156f, 0.492f, 0.168f, 0.492f, 0.292f, 0.286f, 0.274f, 0.306f, 0.196f,
    ),
    plate(
        CanonicalMuscle.CHEST,
        0.508f, 0.168f, 0.676f, 0.156f, 0.694f, 0.196f, 0.714f, 0.274f, 0.508f, 0.292f,
    ),
    plate(
        CanonicalMuscle.BICEPS,
        0.058f, 0.298f, 0.248f, 0.286f, 0.236f, 0.448f, 0.074f, 0.468f,
    ),
    plate(
        CanonicalMuscle.BICEPS,
        0.752f, 0.286f, 0.942f, 0.298f, 0.926f, 0.468f, 0.764f, 0.448f,
    ),
    plate(
        CanonicalMuscle.CORE,
        0.368f, 0.304f, 0.632f, 0.304f, 0.662f, 0.384f, 0.632f, 0.456f, 0.368f, 0.456f, 0.338f, 0.384f,
    ),
    plate(CanonicalMuscle.CORE, 0.286f, 0.312f, 0.354f, 0.328f, 0.348f, 0.400f, 0.272f, 0.392f),
    plate(CanonicalMuscle.CORE, 0.646f, 0.328f, 0.714f, 0.312f, 0.728f, 0.392f, 0.652f, 0.400f),
    plate(CanonicalMuscle.CORE, 0.276f, 0.408f, 0.352f, 0.416f, 0.360f, 0.488f, 0.270f, 0.476f),
    plate(CanonicalMuscle.CORE, 0.648f, 0.416f, 0.724f, 0.408f, 0.730f, 0.476f, 0.640f, 0.488f),
    plate(
        CanonicalMuscle.QUADRICEPS,
        0.292f, 0.504f, 0.484f, 0.504f, 0.468f, 0.728f, 0.308f, 0.746f,
    ),
    plate(
        CanonicalMuscle.QUADRICEPS,
        0.516f, 0.504f, 0.708f, 0.504f, 0.692f, 0.746f, 0.532f, 0.728f,
    ),
    plate(
        CanonicalMuscle.CALVES,
        0.312f, 0.760f, 0.464f, 0.758f, 0.450f, 0.952f, 0.324f, 0.968f,
    ),
    plate(
        CanonicalMuscle.CALVES,
        0.536f, 0.758f, 0.688f, 0.760f, 0.676f, 0.968f, 0.550f, 0.952f,
    ),
    plate(null, 0.300f, 0.972f, 0.456f, 0.958f, 0.470f, 0.996f, 0.286f, 0.996f),
    plate(null, 0.544f, 0.958f, 0.700f, 0.972f, 0.714f, 0.996f, 0.530f, 0.996f),
)

private val BACK_PLATES = listOf(
    plate(null, 0.418f, 0.012f, 0.582f, 0.012f, 0.574f, 0.086f, 0.426f, 0.086f),
    plate(null, 0.448f, 0.092f, 0.552f, 0.092f, 0.552f, 0.148f, 0.448f, 0.148f),
    plate(
        CanonicalMuscle.SHOULDERS,
        0.078f, 0.168f, 0.318f, 0.152f, 0.300f, 0.248f, 0.062f, 0.286f,
    ),
    plate(
        CanonicalMuscle.SHOULDERS,
        0.682f, 0.152f, 0.922f, 0.168f, 0.938f, 0.286f, 0.700f, 0.248f,
    ),
    plate(
        CanonicalMuscle.BACK,
        0.324f, 0.156f, 0.492f, 0.168f, 0.492f, 0.392f, 0.300f, 0.408f, 0.306f, 0.196f,
    ),
    plate(
        CanonicalMuscle.BACK,
        0.508f, 0.168f, 0.676f, 0.156f, 0.694f, 0.196f, 0.700f, 0.408f, 0.508f, 0.392f,
    ),
    plate(
        CanonicalMuscle.TRICEPS,
        0.058f, 0.298f, 0.248f, 0.286f, 0.236f, 0.448f, 0.074f, 0.468f,
    ),
    plate(
        CanonicalMuscle.TRICEPS,
        0.752f, 0.286f, 0.942f, 0.298f, 0.926f, 0.468f, 0.764f, 0.448f,
    ),
    plate(
        CanonicalMuscle.GLUTES,
        0.304f, 0.416f, 0.492f, 0.400f, 0.492f, 0.508f, 0.300f, 0.516f,
    ),
    plate(
        CanonicalMuscle.GLUTES,
        0.508f, 0.400f, 0.696f, 0.416f, 0.700f, 0.516f, 0.508f, 0.508f,
    ),
    plate(
        CanonicalMuscle.HAMSTRINGS,
        0.292f, 0.524f, 0.484f, 0.524f, 0.468f, 0.736f, 0.308f, 0.752f,
    ),
    plate(
        CanonicalMuscle.HAMSTRINGS,
        0.516f, 0.524f, 0.708f, 0.524f, 0.692f, 0.752f, 0.532f, 0.736f,
    ),
    plate(
        CanonicalMuscle.CALVES,
        0.312f, 0.760f, 0.464f, 0.758f, 0.450f, 0.952f, 0.324f, 0.968f,
    ),
    plate(
        CanonicalMuscle.CALVES,
        0.536f, 0.758f, 0.688f, 0.760f, 0.676f, 0.968f, 0.550f, 0.952f,
    ),
    plate(null, 0.300f, 0.972f, 0.456f, 0.958f, 0.470f, 0.996f, 0.286f, 0.996f),
    plate(null, 0.544f, 0.958f, 0.700f, 0.972f, 0.714f, 0.996f, 0.530f, 0.996f),
)
