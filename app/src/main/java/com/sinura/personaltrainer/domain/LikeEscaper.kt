package com.sinura.personaltrainer.domain

/**
 * Makes a user's typing safe to drop into a SQL `LIKE` pattern.
 *
 * `LIKE` gives two characters meaning the person typing them does not intend: `%` matches any
 * run of characters and `_` matches any single one. So searching the library for `100%` did not
 * search for "100%" — it searched for "100" followed by anything, and returned every lift whose
 * name or muscle group starts with 100. Nothing crashed, which is why it went unnoticed: the
 * result set was simply wrong, and quietly.
 *
 * Escaping requires a designated escape character, and the query must declare it with
 * `ESCAPE '\'` or the backslashes are matched literally. Both halves are needed; either alone
 * makes it worse.
 *
 * The backslash is escaped FIRST. Doing it after the wildcards would find the backslashes this
 * function had just inserted and double them, turning `\%` into `\\%` — an escaped backslash
 * followed by a live wildcard, which is the bug this exists to prevent.
 */
object LikeEscaper {
    const val ESCAPE_CHAR = "\\"

    fun escape(raw: String): String = raw
        .replace(ESCAPE_CHAR, ESCAPE_CHAR + ESCAPE_CHAR)
        .replace("%", "$ESCAPE_CHAR%")
        .replace("_", "${ESCAPE_CHAR}_")
}
