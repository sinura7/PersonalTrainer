package com.sinura.personaltrainer.domain

/** Kotlin-built gym strings (static copy lives in res/values/strings.xml in later packets). */
object GymCopy {
    const val UNASSIGNED_TAG = "UN"
    const val RESTORED_GYM_NAME = "Restored gym"

    const val VALIDATION_NAME_REQUIRED = "Name is required"
    const val VALIDATION_NAME_TAKEN = "Another gym is called that"
    const val VALIDATION_BADGE_TAKEN = "Another gym uses that badge"
    const val VALIDATION_BADGE_INVALID = "Badge needs two letters or digits"
}
