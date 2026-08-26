package com.sinura.personaltrainer.ui.components

import androidx.compose.ui.geometry.Offset
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
 *
 * Front and back share one structure list — head, neck, delts, forearms, hands, calves,
 * feet — so flipping the view cannot drift the silhouette. Working plates are the only
 * thing that changes.
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
    BodyView.FRONT -> FIGURE_SHARED_STRUCTURE + FRONT_WORKING
    BodyView.BACK -> FIGURE_SHARED_STRUCTURE + BACK_WORKING
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

private fun BodyPlate.toPath(width: Float, height: Float): Path =
    smoothPlatePath(points, width, height)

/**
 * Closed plate with quadratic corners. Coordinates stay the Temper
 * fractions; the stroke is what makes the figure read as high-definition
 * anatomy instead of a handful of raw polygons, at every density.
 */
internal fun smoothPlatePath(
    points: List<Pair<Float, Float>>,
    width: Float,
    height: Float,
): Path = Path().apply {
    val n = points.size
    if (n < 3) return@apply
    fun px(i: Int) = Offset(points[i].first * width, points[i].second * height)
    fun mid(a: Int, b: Int): Offset {
        val p = px(a)
        val q = px(b)
        return Offset((p.x + q.x) * 0.5f, (p.y + q.y) * 0.5f)
    }
    val start = mid(n - 1, 0)
    moveTo(start.x, start.y)
    for (i in 0 until n) {
        val corner = px(i)
        val next = mid(i, (i + 1) % n)
        quadraticTo(corner.x, corner.y, next.x, next.y)
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
 *
 * FIGURE_SHARED_STRUCTURE is the contract that front and back are the same person.
 * Do not fork those numbers in the working lists.
 */

internal val FIGURE_SHARED_STRUCTURE: List<BodyPlate> = listOf(
    // Skull — denser oval so quadratic corners read as a head, not a pentagon.
    plate(
        null,
        0.445f, 0.006f, 0.500f, 0.002f, 0.555f, 0.006f,
        0.582f, 0.022f, 0.596f, 0.048f, 0.592f, 0.072f,
        0.568f, 0.092f, 0.500f, 0.098f, 0.432f, 0.092f,
        0.408f, 0.072f, 0.404f, 0.048f, 0.418f, 0.022f,
    ),
    plate(null, 0.456f, 0.092f, 0.544f, 0.092f, 0.552f, 0.146f, 0.448f, 0.146f),
    // Delts — same outer cap on both views.
    plate(
        CanonicalMuscle.SHOULDERS,
        0.088f, 0.162f, 0.228f, 0.148f, 0.312f, 0.154f,
        0.308f, 0.206f, 0.268f, 0.258f, 0.148f, 0.278f,
        0.068f, 0.248f, 0.062f, 0.198f,
    ),
    plate(
        CanonicalMuscle.SHOULDERS,
        0.912f, 0.162f, 0.772f, 0.148f, 0.688f, 0.154f,
        0.692f, 0.206f, 0.732f, 0.258f, 0.852f, 0.278f,
        0.932f, 0.248f, 0.938f, 0.198f,
    ),
    // Forearms and hands — structure, never heat. Completes the arm the working
    // biceps / triceps plates start.
    plate(
        null,
        0.056f, 0.428f, 0.208f, 0.412f, 0.198f, 0.528f,
        0.088f, 0.548f, 0.042f, 0.488f,
    ),
    plate(
        null,
        0.944f, 0.428f, 0.792f, 0.412f, 0.802f, 0.528f,
        0.912f, 0.548f, 0.958f, 0.488f,
    ),
    plate(
        null,
        0.046f, 0.552f, 0.188f, 0.538f, 0.178f, 0.608f,
        0.108f, 0.628f, 0.038f, 0.588f,
    ),
    plate(
        null,
        0.954f, 0.552f, 0.812f, 0.538f, 0.822f, 0.608f,
        0.892f, 0.628f, 0.962f, 0.588f,
    ),
    // Calves — gastroc diamond, identical both sides of the flip.
    plate(
        CanonicalMuscle.CALVES,
        0.308f, 0.762f, 0.458f, 0.756f, 0.448f, 0.888f,
        0.398f, 0.952f, 0.328f, 0.968f, 0.298f, 0.868f,
    ),
    plate(
        CanonicalMuscle.CALVES,
        0.692f, 0.762f, 0.542f, 0.756f, 0.552f, 0.888f,
        0.602f, 0.952f, 0.672f, 0.968f, 0.702f, 0.868f,
    ),
    plate(null, 0.292f, 0.972f, 0.448f, 0.958f, 0.478f, 0.996f, 0.278f, 0.996f),
    plate(null, 0.708f, 0.972f, 0.552f, 0.958f, 0.522f, 0.996f, 0.722f, 0.996f),
)

private val FRONT_WORKING: List<BodyPlate> = listOf(
    // Pecs. Viewer-right (left > 0.5) is the one Temper accent.
    plate(
        CanonicalMuscle.CHEST,
        0.318f, 0.156f, 0.492f, 0.164f, 0.492f, 0.288f,
        0.428f, 0.308f, 0.318f, 0.292f, 0.288f, 0.248f,
        0.298f, 0.192f,
    ),
    plate(
        CanonicalMuscle.CHEST,
        0.508f, 0.164f, 0.682f, 0.156f, 0.702f, 0.192f,
        0.712f, 0.248f, 0.682f, 0.292f, 0.572f, 0.308f,
        0.508f, 0.288f,
    ),
    // Upper arm — same silhouette as the back's triceps.
    plate(
        CanonicalMuscle.BICEPS,
        0.068f, 0.268f, 0.228f, 0.258f, 0.248f, 0.318f,
        0.228f, 0.398f, 0.088f, 0.418f, 0.052f, 0.348f,
    ),
    plate(
        CanonicalMuscle.BICEPS,
        0.932f, 0.268f, 0.772f, 0.258f, 0.752f, 0.318f,
        0.772f, 0.398f, 0.912f, 0.418f, 0.948f, 0.348f,
    ),
    plate(CanonicalMuscle.CORE, 0.378f, 0.316f, 0.622f, 0.316f, 0.632f, 0.378f, 0.368f, 0.378f),
    plate(CanonicalMuscle.CORE, 0.368f, 0.386f, 0.632f, 0.386f, 0.638f, 0.448f, 0.362f, 0.448f),
    plate(CanonicalMuscle.CORE, 0.358f, 0.456f, 0.642f, 0.456f, 0.618f, 0.508f, 0.382f, 0.508f),
    plate(CanonicalMuscle.CORE, 0.278f, 0.318f, 0.360f, 0.328f, 0.354f, 0.390f, 0.268f, 0.382f),
    plate(CanonicalMuscle.CORE, 0.722f, 0.318f, 0.640f, 0.328f, 0.646f, 0.390f, 0.732f, 0.382f),
    plate(CanonicalMuscle.CORE, 0.268f, 0.398f, 0.352f, 0.406f, 0.362f, 0.478f, 0.262f, 0.468f),
    plate(CanonicalMuscle.CORE, 0.732f, 0.398f, 0.648f, 0.406f, 0.638f, 0.478f, 0.738f, 0.468f),
    plate(
        CanonicalMuscle.QUADRICEPS,
        0.288f, 0.518f, 0.398f, 0.512f, 0.388f, 0.738f,
        0.308f, 0.752f, 0.272f, 0.628f,
    ),
    plate(
        CanonicalMuscle.QUADRICEPS,
        0.406f, 0.512f, 0.492f, 0.518f, 0.484f, 0.728f, 0.396f, 0.738f,
    ),
    plate(
        CanonicalMuscle.QUADRICEPS,
        0.712f, 0.518f, 0.602f, 0.512f, 0.612f, 0.738f,
        0.692f, 0.752f, 0.728f, 0.628f,
    ),
    plate(
        CanonicalMuscle.QUADRICEPS,
        0.594f, 0.512f, 0.508f, 0.518f, 0.516f, 0.728f, 0.604f, 0.738f,
    ),
)

private val BACK_WORKING: List<BodyPlate> = listOf(
    // Traps.
    plate(
        CanonicalMuscle.BACK,
        0.318f, 0.152f, 0.682f, 0.152f, 0.658f, 0.218f,
        0.500f, 0.232f, 0.342f, 0.218f,
    ),
    // Lats.
    plate(
        CanonicalMuscle.BACK,
        0.248f, 0.198f, 0.368f, 0.218f, 0.428f, 0.368f,
        0.348f, 0.418f, 0.238f, 0.328f, 0.228f, 0.248f,
    ),
    plate(
        CanonicalMuscle.BACK,
        0.752f, 0.198f, 0.632f, 0.218f, 0.572f, 0.368f,
        0.652f, 0.418f, 0.762f, 0.328f, 0.772f, 0.248f,
    ),
    // Mid-back / rhomboids, then erectors.
    plate(
        CanonicalMuscle.BACK,
        0.436f, 0.228f, 0.564f, 0.228f, 0.556f, 0.368f,
        0.500f, 0.388f, 0.444f, 0.368f,
    ),
    plate(
        CanonicalMuscle.BACK,
        0.398f, 0.378f, 0.602f, 0.378f, 0.588f, 0.488f, 0.412f, 0.488f,
    ),
    // Upper arm — same silhouette as the front's biceps.
    plate(
        CanonicalMuscle.TRICEPS,
        0.068f, 0.268f, 0.228f, 0.258f, 0.248f, 0.318f,
        0.228f, 0.398f, 0.088f, 0.418f, 0.052f, 0.348f,
    ),
    plate(
        CanonicalMuscle.TRICEPS,
        0.932f, 0.268f, 0.772f, 0.258f, 0.752f, 0.318f,
        0.772f, 0.398f, 0.912f, 0.418f, 0.948f, 0.348f,
    ),
    plate(
        CanonicalMuscle.GLUTES,
        0.308f, 0.492f, 0.492f, 0.482f, 0.492f, 0.568f,
        0.418f, 0.588f, 0.300f, 0.558f,
    ),
    plate(
        CanonicalMuscle.GLUTES,
        0.692f, 0.492f, 0.508f, 0.482f, 0.508f, 0.568f,
        0.582f, 0.588f, 0.700f, 0.558f,
    ),
    plate(
        CanonicalMuscle.GLUTES,
        0.296f, 0.562f, 0.418f, 0.588f, 0.492f, 0.572f,
        0.484f, 0.618f, 0.308f, 0.608f,
    ),
    plate(
        CanonicalMuscle.GLUTES,
        0.704f, 0.562f, 0.582f, 0.588f, 0.504f, 0.572f,
        0.516f, 0.618f, 0.692f, 0.608f,
    ),
    plate(
        CanonicalMuscle.HAMSTRINGS,
        0.288f, 0.626f, 0.398f, 0.620f, 0.388f, 0.748f,
        0.308f, 0.758f, 0.272f, 0.688f,
    ),
    plate(
        CanonicalMuscle.HAMSTRINGS,
        0.406f, 0.620f, 0.492f, 0.626f, 0.484f, 0.738f, 0.396f, 0.748f,
    ),
    plate(
        CanonicalMuscle.HAMSTRINGS,
        0.712f, 0.626f, 0.602f, 0.620f, 0.612f, 0.748f,
        0.692f, 0.758f, 0.728f, 0.688f,
    ),
    plate(
        CanonicalMuscle.HAMSTRINGS,
        0.594f, 0.620f, 0.508f, 0.626f, 0.516f, 0.738f, 0.604f, 0.748f,
    ),
)
