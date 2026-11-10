package com.sinura.personaltrainer.testutil

import com.sinura.personaltrainer.data.local.GymSeed

/** Default gym for strength-start tests (Weston seed id). */
object TestGymIds {
    val WESTON: String = GymSeed.ROWS.first { it.id == "gym-af-weston" }.id
}
