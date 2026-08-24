package com.sinura.personaltrainer.util

import java.util.UUID

/**
 * Stable ID source. Production uses random UUIDs. Tests use a sequential
 * factory so fixtures and assertions stay deterministic.
 */
fun interface IdFactory {
    fun newId(): String

    companion object {
        val Uuid: IdFactory = IdFactory { UUID.randomUUID().toString() }
    }
}
