package com.sinura.personaltrainer.ui.units

import androidx.compose.runtime.staticCompositionLocalOf
import com.sinura.personaltrainer.domain.DistanceUnit
import com.sinura.personaltrainer.domain.WeightUnit

val LocalWeightUnit = staticCompositionLocalOf { WeightUnit.LBS }

/** Resolved distance: a stored choice, or miles with pounds and kilometres with kilograms. */
val LocalDistanceUnit = staticCompositionLocalOf { DistanceUnit.MI }
