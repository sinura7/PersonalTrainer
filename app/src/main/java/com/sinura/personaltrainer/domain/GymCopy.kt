package com.sinura.personaltrainer.domain

/** Kotlin-built gym strings (static copy lives in res/values/strings.xml in later packets). */
object GymCopy {
    const val UNASSIGNED_TAG = "UN"
    const val RESTORED_GYM_NAME = "Restored gym"

    fun startsOnPick(sessionName: String): String = "$sessionName starts as soon as you pick."
}
