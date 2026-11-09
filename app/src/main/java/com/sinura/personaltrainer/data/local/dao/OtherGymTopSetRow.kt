package com.sinura.personaltrainer.data.local.dao

data class OtherGymTopSetRow(
    val gymId: String,
    val gymShortName: String,
    val weightKg: Double,
    val reps: Int,
    val sessionFinishedAt: Long,
)
