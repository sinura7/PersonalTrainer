package com.sinura.personaltrainer.domain

/**
 * What the RPE chips mean, in gym English.
 *
 * The scale is optional. The chips used to sit unlabeled except for the
 * letters "RPE", then a generic 6–9 explainer with no memory of this lift.
 * The row now names the last logged effort when history has one.
 */
object RpeCopy {
    const val OPTIONAL = "Optional."

    fun blurb(lastRpe: Int?): String {
        val history = lastRpe?.let { "Last time RPE $it. " }.orEmpty()
        return history + OPTIONAL
    }

    fun recommended(lastRpe: Int?): Int? = lastRpe?.takeIf { it in 6..10 }
}
