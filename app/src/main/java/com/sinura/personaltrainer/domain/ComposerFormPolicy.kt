package com.sinura.personaltrainer.domain

/**
 * JVM-testable save rules for the backdated activity composer (P11).
 */
object ComposerFormPolicy {
    fun canSave(mode: String, strengthCount: Int, cardioCount: Int): Boolean =
        ComposerCopy.canSave(mode, strengthCount, cardioCount)
}
