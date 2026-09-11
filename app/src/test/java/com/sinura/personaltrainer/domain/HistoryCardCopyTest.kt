package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryCardCopyTest {
    @Test
    fun theHistoryCardPicturesThreeLifts() {
        assertEquals(3, HistoryCardCopy.STILL_LIMIT)
        assertEquals(RoutineCardCopy.STILL_LIMIT, HistoryCardCopy.STILL_LIMIT)
    }

    @Test
    fun stillsKeepSessionOrderAndDropTheRest() {
        val lifts = (1..5).map { index ->
            Exercise(
                id = "ex-$index",
                name = "Lift $index",
                muscleGroup = "Chest",
                notes = "",
                isCustom = false,
            )
        }
        assertEquals(
            listOf("ex-1", "ex-2", "ex-3"),
            HistoryCardCopy.stills(lifts).map { it.id },
        )
        assertTrue(HistoryCardCopy.stills(emptyList()).isEmpty())
    }

    @Test
    fun cardioBlocksAreNotPicturedAndStrengthKeepsOrder() {
        val blocks = listOf(
            CardioBlock(
                id = "run",
                sortOrder = 0,
                type = CardioType.RUN,
                indoor = false,
                elapsedSeconds = 600,
                movingSeconds = 600,
                distanceMeters = 1_000.0,
                elevationMeters = null,
                heartRateBpm = null,
                energyKj = null,
                rpe = null,
                routeRef = null,
            ),
            StrengthBlock(
                id = "bench",
                sortOrder = 2,
                exerciseId = "ex-bench",
                exerciseName = "Bench",
                loadType = LoadType.EXTERNAL,
                equipment = EquipmentType.BARBELL,
                muscles = emptyList(),
                sets = emptyList(),
            ),
            StrengthBlock(
                id = "squat",
                sortOrder = 1,
                exerciseId = "ex-squat",
                exerciseName = "Squat",
                loadType = LoadType.EXTERNAL,
                equipment = EquipmentType.BARBELL,
                muscles = emptyList(),
                sets = emptyList(),
            ),
        )
        assertEquals(
            listOf("ex-squat", "ex-bench"),
            HistoryCardCopy.stillsFromBlocks(blocks).map { it.id },
        )
        assertTrue(
            HistoryCardCopy.stillsFromBlocks(blocks.filterIsInstance<CardioBlock>()).isEmpty(),
        )
    }
}
