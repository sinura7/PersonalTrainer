package com.sinura.personaltrainer.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.OutlineSolid
import com.sinura.personaltrainer.ui.theme.OutlineSolidVariant
import com.sinura.personaltrainer.ui.theme.Radius

/**
 * The body figure, and the plate geometry that lands muscles on it.
 *
 * Lifted out of the Body tab because it is now drawn in two places at two very different
 * sizes: the full map, and a 40dp thumbnail on every exercise row. Leaving it in `BodyMap.kt`
 * would have meant either a UI screen importing from another UI screen, or a second copy of
 * the anatomy that agrees with the first only until one of them is edited.
 *
 * This is a pure move. Every coordinate is what the Body tab shipped with; the only addition
 * is [drawFigure]'s `detail` parameter, which the tab passes as its previous behaviour.
 */
enum class BodyView(val label: String) {
    FRONT("Front"),
    BACK("Back"),
}

/** A muscle plate, as a fraction of the figure box — never of the screen. See [FIGURE_ASPECT]. */
internal data class BodyHotspot(
    val muscle: CanonicalMuscle,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/**
 * The body, drawn as a wireframe rather than as two slabs.
 *
 * Front and back genuinely differ: the plates split around a sternum on one and a spine on
 * the other, and the details between them — clavicles against trapezius, kneecaps against
 * knee creases, toes against heels — change with the view. The toggle used to redraw the
 * identical pair of rectangles, so nothing on screen confirmed that it had done anything.
 */
internal fun DrawScope.drawFigure(view: BodyView, detail: Boolean = true) {
    // [detail] off is for the 40dp thumbnails, where the figure is ~21dp wide and a hairline
    // sternum rule or a 0.03-radius kneecap lands well under a pixel — drawn, they are grey
    // mush over the muscle colour rather than anatomy. Slabs, blobs and torso survive at that
    // size and are what makes the front and back readably different. The Body tab passes true
    // and renders exactly as it always has.
    // Opaque, not the outline colour at an alpha: the parts of the figure overlap where they
    // join, and a translucent fill would draw every one of those joins as a bright seam.
    val body = OutlineSolidVariant
    val detailColour = OutlineSolid
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
            color = detailColour,
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
            if (detail) {
                rule(0.396f, 0.204f, 0.500f, 0.226f)
                rule(0.604f, 0.204f, 0.500f, 0.226f)
                rule(0.500f, 0.232f, 0.500f, 0.500f)
                drawCircle(
                    color = detailColour,
                    radius = px(0.030f),
                    center = Offset(px(0.381f), py(0.742f)),
                    style = Stroke(width = hair),
                )
                drawCircle(
                    color = detailColour,
                    radius = px(0.030f),
                    center = Offset(px(0.619f), py(0.742f)),
                    style = Stroke(width = hair),
                )
            }
        }

        BodyView.BACK -> {
            slab(0.152f, 0.546f, 0.256f, 0.600f)
            slab(0.744f, 0.546f, 0.848f, 0.600f)
            slab(0.320f, 0.946f, 0.442f, 0.996f)
            slab(0.558f, 0.946f, 0.680f, 0.996f)
            if (detail) {
                rule(0.500f, 0.176f, 0.372f, 0.230f)
                rule(0.500f, 0.176f, 0.628f, 0.230f)
                rule(0.500f, 0.172f, 0.500f, 0.520f, weight = 2f)
                VERTEBRAE.forEach { at -> rule(0.484f, at, 0.516f, at) }
                rule(0.330f, 0.744f, 0.432f, 0.744f)
                rule(0.568f, 0.744f, 0.670f, 0.744f)
            }
        }
    }
}

internal fun hotspotsFor(view: BodyView): List<BodyHotspot> = when (view) {
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
internal const val FIGURE_ASPECT = 0.52f

internal val FRONT_HOTSPOTS = listOf(
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

internal val BACK_HOTSPOTS = listOf(
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
