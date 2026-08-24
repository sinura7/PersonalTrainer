package com.sinura.personaltrainer.data.local.dao

/** SQL aggregate of a lift's best finished working weight. No set rows. */
data class ExerciseBestWeightRow(
    val exerciseId: String,
    val bestKg: Double,
)
