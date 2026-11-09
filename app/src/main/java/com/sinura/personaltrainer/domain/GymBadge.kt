package com.sinura.personaltrainer.domain

/** Default badge and validation for gym short names (stored badge is two A–Z / 0–9 chars). */
object GymBadge {
    private val BADGE_PATTERN = Regex("^[A-Z0-9]{2}$")

    fun defaultForShortName(shortName: String): String {
        val words = shortName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            words.size >= 2 -> "${words[0].first().uppercaseChar()}${words[1].first().uppercaseChar()}"
            words.size == 1 -> {
                val w = words[0]
                when {
                    w.length >= 2 -> "${w[0].uppercaseChar()}${w[1].uppercaseChar()}"
                    w.length == 1 -> "${w[0].uppercaseChar()}A"
                    else -> "GY"
                }
            }
            else -> "GY"
        }.toString()
    }

    fun isValid(badge: String): Boolean = BADGE_PATTERN.matches(badge.trim().uppercase())
}
