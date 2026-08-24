package com.sinura.personaltrainer.util

import com.sinura.personaltrainer.domain.IdPort
import java.util.UUID

/**
 * JVM adapter for [IdPort]. Production uses random UUIDs. Tests use a
 * sequential factory so fixtures stay deterministic.
 */
fun interface IdFactory : IdPort {
    companion object {
        val Uuid: IdFactory = IdFactory { UUID.randomUUID().toString() }
    }
}
