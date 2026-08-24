package com.sinura.personaltrainer.data.local

/**
 * The one authorized database generation (ADR-010).
 *
 * Phase 5 cuts from [TrainerDatabase] (`personal_trainer.db`, v2) to
 * [TemperDatabase] (`temper.db`, v1). After P5.7 this generation is
 * frozen: a second wipe is a defect. Later schema changes migrate
 * `TemperDatabase` with generated artifacts and tests.
 */
object FoundationGeneration {
    const val NAME = "temper"
    const val DATABASE_FILE = "temper.db"
    const val VERSION = 1
    const val LEGACY_DATABASE_FILE = "personal_trainer.db"

    /**
     * Set true at P5.7. When frozen, [FoundationReset] may delete a leftover
     * legacy file but must not wipe the foundation database.
     */
    const val FROZEN = true
}
