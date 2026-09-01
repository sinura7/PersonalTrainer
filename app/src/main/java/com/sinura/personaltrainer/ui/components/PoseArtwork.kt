package com.sinura.personaltrainer.ui.components

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.sinura.personaltrainer.R
import kotlin.math.roundToInt

/**
 * The locked 18-still pack — family fallback plus Body unlit/heat.
 *
 * Catalog rows resolve through [keyedArtwork] first. Unknown families and
 * customs stand on these stills: one pose per family, or the unlit figure
 * of the view [thumbViewFor] already picked.
 *
 * Body live heat paints on the unlit front/back stills. Bind once from
 * application start so [drawTemperFigure] can blit without LocalContext.
 */
@DrawableRes
internal fun artworkFor(pose: LiftPose, view: BodyView): Int = when (pose) {
    LiftPose.ANATOMY -> when (view) {
        BodyView.FRONT -> R.drawable.temper_front_unlit
        BodyView.BACK -> R.drawable.temper_back_unlit
    }
    LiftPose.SQUAT -> R.drawable.temper_pose_squat
    LiftPose.HINGE -> R.drawable.temper_pose_hinge
    LiftPose.LUNGE -> R.drawable.temper_pose_lunge
    LiftPose.HORIZONTAL_PRESS -> R.drawable.temper_pose_hpress
    LiftPose.VERTICAL_PRESS -> R.drawable.temper_pose_vpress
    LiftPose.FLY -> R.drawable.temper_pose_fly
    LiftPose.VERTICAL_PULL -> R.drawable.temper_pose_vpull
    LiftPose.HORIZONTAL_PULL -> R.drawable.temper_pose_row
    LiftPose.ARM_CURL -> R.drawable.temper_pose_curl
    LiftPose.ARM_EXT -> R.drawable.temper_pose_ext
    LiftPose.HIP -> R.drawable.temper_pose_hip
    LiftPose.CORE_FLOOR -> R.drawable.temper_pose_core
    LiftPose.SEATED_MACHINE -> R.drawable.temper_pose_machine
    LiftPose.CARRY -> R.drawable.temper_pose_carry
}

@DrawableRes
internal fun demoHeatArtwork(view: BodyView): Int = when (view) {
    BodyView.FRONT -> R.drawable.temper_front_heat
    BodyView.BACK -> R.drawable.temper_back_heat
}

internal object TemperStillCache {
    @Volatile
    private var front: ImageBitmap? = null

    @Volatile
    private var back: ImageBitmap? = null

    fun bind(resources: Resources) {
        // JVM unit tests keep the steel-plate fallback so heat pixel contracts stay exact.
        if (Build.FINGERPRINT.contains("robolectric", ignoreCase = true)) return
        if (front != null && back != null) return
        front = decode(resources, R.drawable.temper_front_unlit)
        back = decode(resources, R.drawable.temper_back_unlit)
    }

    fun bitmap(view: BodyView): ImageBitmap? = when (view) {
        BodyView.FRONT -> front
        BodyView.BACK -> back
    }

    private fun decode(resources: Resources, @DrawableRes id: Int): ImageBitmap? {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
            inScaled = false
        }
        val decoded = BitmapFactory.decodeResource(resources, id, options) ?: return null
        val argb = Bitmap.Config.ARGB_8888
        val working = if (decoded.isMutable && decoded.config == argb) {
            decoded
        } else {
            decoded.copy(argb, true) ?: return null
        }
        if (working !== decoded) decoded.recycle()
        punchPit(working)
        return working.asImageBitmap()
    }
}

/**
 * The shipped stills are RGB on a near-black square. Punch that pit to
 * transparent so a live heat wash SrcAtops onto the person, not the padding.
 *
 * Figure plates sit around luma 58–95; the webp pit compresses to 0–6.
 */
internal fun punchPit(bitmap: Bitmap) {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    for (i in pixels.indices) {
        if (isPitSample(pixels[i])) pixels[i] = 0
    }
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
}

/** Packed ARGB; the pit is near-black, not the steel plates. */
internal fun isPitSample(packedArgb: Int, ceiling: Int = PIT_LUMA_CEILING): Boolean {
    val red = (packedArgb ushr 16) and 0xFF
    val green = (packedArgb ushr 8) and 0xFF
    val blue = packedArgb and 0xFF
    return maxOf(red, green, blue) <= ceiling
}

internal const val PIT_LUMA_CEILING = 10

/** Center-crop an image to [dstAspect] (width / height) so the still fills the figure box. */
internal fun stillSrc(image: ImageBitmap, dstAspect: Float): Pair<IntOffset, IntSize> =
    stillSrc(width = image.width, height = image.height, dstAspect = dstAspect)

internal fun stillSrc(width: Int, height: Int, dstAspect: Float): Pair<IntOffset, IntSize> {
    val imageAspect = width.toFloat() / height.toFloat().coerceAtLeast(1f)
    return if (imageAspect > dstAspect) {
        val srcWidth = (height * dstAspect).roundToInt().coerceIn(1, width)
        val srcX = ((width - srcWidth) / 2).coerceAtLeast(0)
        IntOffset(srcX, 0) to IntSize(srcWidth, height)
    } else {
        val srcHeight = (width / dstAspect.coerceAtLeast(0.01f)).roundToInt().coerceIn(1, height)
        val srcY = ((height - srcHeight) / 2).coerceAtLeast(0)
        IntOffset(0, srcY) to IntSize(width, srcHeight)
    }
}
