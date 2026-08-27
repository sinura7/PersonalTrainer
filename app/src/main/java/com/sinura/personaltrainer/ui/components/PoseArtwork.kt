package com.sinura.personaltrainer.ui.components

import androidx.annotation.DrawableRes
import com.sinura.personaltrainer.R

/**
 * The locked 18-still pack — the ChatGPT pictures, shipped.
 *
 * One still per lift family plus unlit/heat front and back. Catalog rows do
 * not get their own `imageKey`. Unknown families stand on the unlit figure
 * of the view [thumbViewFor] already picked.
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
