package com.sinura.personaltrainer.domain


enum class HistoryKind { WORKOUT, ACTIVITY }

data class HistoryEntry(
    val id: String,
    val kind: HistoryKind,
    val title: String,
    val sortMillis: Long,
    val localEpochDay: Long,
    val workingSets: Int,
    val cardioMinutes: Int,
    val work: SetWork,
    val durationMinutes: Int,
    val stills: List<Exercise> = emptyList(),
)

data class HistoryMonthGroup(
    val month: CivilYearMonth,
    val entries: List<HistoryEntry>,
)

fun WorkoutSession.toHistoryEntry(
    time: TimePort,
    zoneId: String = time.defaultZoneId(),
): HistoryEntry = HistoryEntry(
    id = id,
    kind = HistoryKind.WORKOUT,
    title = routineName ?: "Workout",
    sortMillis = performedAtMs(),
    localEpochDay = performedEpochDay(time, zoneId),
    workingSets = workingSetCount(),
    cardioMinutes = 0,
    work = work(),
    durationMinutes = durationMinutes,
    stills = HistoryCardCopy.stills(exercises.map { it.exercise }),
)

fun ActivitySession.toHistoryEntry(): HistoryEntry = HistoryEntry(
    id = id,
    kind = HistoryKind.ACTIVITY,
    title = title.ifBlank {
        when {
            isCardioOnly -> "Cardio"
            isMixed -> "Mixed session"
            else -> "Workout"
        }
    },
    sortMillis = performedStart.instantMillis,
    localEpochDay = localEpochDay,
    workingSets = strengthSetCount(),
    cardioMinutes = cardioMinutes(),
    work = strengthWork(),
    durationMinutes = cardioMinutes().coerceAtLeast(0),
    stills = HistoryCardCopy.stillsFromBlocks(blocks),
)

fun groupHistoryByMonth(entries: List<HistoryEntry>): List<HistoryMonthGroup> = entries
    .groupBy { CivilYearMonth.from(CivilDate.fromEpochDay(it.localEpochDay)) }
    .entries
    .sortedByDescending { it.key }
    .map { (month, rows) ->
        HistoryMonthGroup(
            month = month,
            entries = rows.sortedByDescending { it.sortMillis },
        )
    }
