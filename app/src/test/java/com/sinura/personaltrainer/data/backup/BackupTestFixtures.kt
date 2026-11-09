package com.sinura.personaltrainer.data.backup

/** Minimal gym list for v6 backup fixtures (Packet G1). */
object BackupTestFixtures {
    fun v6Gym(): BackupGym = BackupGym(
        id = "gym-af-weston",
        name = "Anytime Fitness Weston",
        shortName = "Weston",
        badge = "WS",
        sortOrder = 3,
        archivedAtMs = null,
        createdAtMs = 1L,
        updatedAtMs = 1L,
    )

    fun v6Gyms(): List<BackupGym> = listOf(v6Gym())
}
