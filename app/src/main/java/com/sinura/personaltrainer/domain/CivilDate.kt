package com.sinura.personaltrainer.domain

/**
 * A proleptic Gregorian calendar date.
 *
 * Epoch day 0 is 1970-01-01, the same numbering [java.time.LocalDate] uses, so
 * stored epoch days do not change when this type replaces that one. Arithmetic
 * lives here so shared-target domain code never imports `java.time`.
 */
data class CivilDate(
    val year: Int,
    val month: Int,
    val day: Int,
) : Comparable<CivilDate> {
    init {
        require(month in 1..12) { "month must be 1..12, was $month" }
        require(day in 1..lengthOfMonth(year, month)) {
            "day $day is not valid for $year-$month"
        }
    }

    val dayOfMonth: Int get() = day
    val epochDay: Long get() = toEpochDay(year, month, day)
    val dayOfWeek: Weekday get() = Weekday.fromEpochDay(epochDay)

    fun plusDays(days: Long): CivilDate = fromEpochDay(epochDay + days)
    fun minusDays(days: Long): CivilDate = plusDays(-days)

    fun previousOrSame(weekday: Weekday): CivilDate {
        val delta = (dayOfWeek.ordinal - weekday.ordinal + Weekday.DAYS_IN_WEEK) %
            Weekday.DAYS_IN_WEEK
        return minusDays(delta.toLong())
    }

    fun nextOrSame(weekday: Weekday): CivilDate {
        val delta = (weekday.ordinal - dayOfWeek.ordinal + Weekday.DAYS_IN_WEEK) %
            Weekday.DAYS_IN_WEEK
        return plusDays(delta.toLong())
    }

    override fun compareTo(other: CivilDate): Int = epochDay.compareTo(other.epochDay)

    override fun toString(): String =
        "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

    companion object {
        /** Days from year 0000-01-01 to 1970-01-01, matching `java.time.LocalDate`. */
        const val DAYS_0000_TO_1970 = 719528L
        const val DAYS_PER_CYCLE = 146097L

        fun fromEpochDay(epochDay: Long): CivilDate {
            var zeroDay = epochDay + DAYS_0000_TO_1970
            // Adjust to 0000-03-01 so the leap-day sits at the end of the cycle year.
            zeroDay -= 60
            var adjust = 0L
            if (zeroDay < 0) {
                val adjustCycles = (zeroDay + 1) / DAYS_PER_CYCLE - 1
                adjust = adjustCycles * 400
                zeroDay += -adjustCycles * DAYS_PER_CYCLE
            }
            var yearEst = (400 * zeroDay + 591) / DAYS_PER_CYCLE
            var doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400)
            if (doyEst < 0) {
                yearEst -= 1
                doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400)
            }
            yearEst += adjust
            val marchDoy0 = doyEst.toInt()
            val marchMonth0 = (marchDoy0 * 5 + 2) / 153
            val month = (marchMonth0 + 2) % 12 + 1
            val dom = marchDoy0 - (marchMonth0 * 153 + 2) / 5 + 1
            yearEst += (marchMonth0 / 10).toLong()
            return CivilDate(yearEst.toInt(), month, dom)
        }

        fun of(year: Int, month: Int, day: Int): CivilDate = CivilDate(year, month, day)

        fun isLeapYear(year: Int): Boolean =
            year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

        fun lengthOfMonth(year: Int, month: Int): Int = when (month) {
            2 -> if (isLeapYear(year)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }

        internal fun toEpochDay(year: Int, month: Int, day: Int): Long {
            val y = year.toLong()
            val m = month.toLong()
            var total = 0L
            total += 365 * y
            total += if (y >= 0) {
                (y + 3) / 4 - (y + 99) / 100 + (y + 399) / 400
            } else {
                y / 4 - y / 100 + y / 400
            }
            total += (367 * m - 362) / 12
            total += day - 1
            if (m > 2) {
                total -= 1
                if (!isLeapYear(year)) total -= 1
            }
            return total - DAYS_0000_TO_1970
        }
    }
}

/**
 * Local wall time on a [CivilDate], before a zone is applied.
 *
 * The composer resolves this through [TimePort.resolveLocal] so a DST gap or
 * overlap is an explicit choice, not an implicit `ZonedDateTime` default.
 */
data class CivilDateTime(
    val date: CivilDate,
    val hour: Int,
    val minute: Int,
    val second: Int = 0,
    val nanoOfSecond: Int = 0,
) {
    init {
        require(hour in 0..23) { "hour must be 0..23, was $hour" }
        require(minute in 0..59) { "minute must be 0..59, was $minute" }
        require(second in 0..59) { "second must be 0..59, was $second" }
        require(nanoOfSecond in 0..999_999_999) { "nano must be 0..999999999" }
    }
}

data class CivilYearMonth(
    val year: Int,
    val month: Int,
) : Comparable<CivilYearMonth> {
    init {
        require(month in 1..12) { "month must be 1..12, was $month" }
    }

    fun atDay(day: Int): CivilDate = CivilDate(year, month, day)

    fun atEndOfMonth(): CivilDate = atDay(CivilDate.lengthOfMonth(year, month))

    fun plusMonths(months: Long): CivilYearMonth {
        val index = year.toLong() * 12 + (month - 1) + months
        val newYear = Math.floorDiv(index, 12).toInt()
        val newMonth = Math.floorMod(index, 12).toInt() + 1
        return CivilYearMonth(newYear, newMonth)
    }

    fun minusMonths(months: Long): CivilYearMonth = plusMonths(-months)

    override fun compareTo(other: CivilYearMonth): Int =
        compareValuesBy(this, other, { it.year }, { it.month })

    override fun toString(): String =
        "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}"

    companion object {
        fun from(date: CivilDate): CivilYearMonth = CivilYearMonth(date.year, date.month)

        fun of(year: Int, month: Int): CivilYearMonth = CivilYearMonth(year, month)
    }
}
