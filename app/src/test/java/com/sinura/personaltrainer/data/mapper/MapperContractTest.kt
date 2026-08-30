package com.sinura.personaltrainer.data.mapper

import com.sinura.personaltrainer.data.backup.BackupActivity
import com.sinura.personaltrainer.data.backup.BackupActivityBlock
import com.sinura.personaltrainer.data.backup.BackupActivityMuscle
import com.sinura.personaltrainer.data.backup.BackupCapturedTime
import com.sinura.personaltrainer.data.backup.BackupStrengthSet
import com.sinura.personaltrainer.data.local.entity.ActivityBlockEntity
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.MeasurableGoalEntity
import com.sinura.personaltrainer.data.local.entity.MissedWorkDecisionEntity
import com.sinura.personaltrainer.data.local.entity.ReminderDeliveryEntity
import com.sinura.personaltrainer.data.local.entity.ScheduleOccurrenceEntity
import com.sinura.personaltrainer.data.local.entity.ScheduleRuleEntity
import com.sinura.personaltrainer.data.local.entity.ScheduleSlotEntity
import com.sinura.personaltrainer.data.local.relation.ActivityBlockGraph
import com.sinura.personaltrainer.domain.ActivityOrigin
import com.sinura.personaltrainer.domain.ActivitySession
import com.sinura.personaltrainer.domain.ActivitySource
import com.sinura.personaltrainer.domain.ActivityStatus
import com.sinura.personaltrainer.domain.CapturedCivilTime
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.GoalKind
import com.sinura.personaltrainer.domain.GoalPeriod
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.MeasurableGoal
import com.sinura.personaltrainer.domain.MissedWorkChoice
import com.sinura.personaltrainer.domain.MuscleCredit
import com.sinura.personaltrainer.domain.MuscleNormalizer
import com.sinura.personaltrainer.domain.OccurrenceStatus
import com.sinura.personaltrainer.domain.ReminderDelivery
import com.sinura.personaltrainer.domain.ReminderDeliveryStatus
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.ScheduleOccurrence
import com.sinura.personaltrainer.domain.ScheduleRule
import com.sinura.personaltrainer.domain.SessionFocusKind
import com.sinura.personaltrainer.domain.StrengthBlock
import com.sinura.personaltrainer.domain.StrengthSet
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.ZonePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Persistence wire names for the enums history and backup round-trip through.
 *
 * These values are stored as `.name` (or the block-kind literals STRENGTH/CARDIO).
 * Renaming one without a migration bricks every existing database and backup.
 */
class MapperContractTest {

    @Test
    fun loadTypeWireNamesAreTheStoredTokens() {
        assertEquals(
            listOf("EXTERNAL", "STACK", "BODYWEIGHT", "BODYWEIGHT_PLUS", "ASSISTED"),
            LoadType.entries.map { it.name },
        )
        LoadType.entries.forEach { type ->
            assertEquals(type, LoadType.fromStorage(type.name))
        }
        assertEquals(LoadType.EXTERNAL, LoadType.fromStorage(null))
        assertEquals(LoadType.EXTERNAL, LoadType.fromStorage("WEIGHTED_BODYWEIGHT"))
        assertEquals(LoadType.BODYWEIGHT_PLUS, LoadType.fromLegacyStorage("weighted_bodyweight"))
    }

    @Test
    fun equipmentTypeWireNamesAreTheStoredTokens() {
        assertEquals(
            listOf(
                "BARBELL", "DUMBBELL", "CABLE", "MACHINE", "SMITH",
                "KETTLEBELL", "BAND", "BODYWEIGHT", "HYPER_PRO", "OTHER",
            ),
            EquipmentType.entries.map { it.name },
        )
        EquipmentType.entries.forEach { type ->
            assertEquals(type, EquipmentType.fromStorage(type.name))
        }
        assertEquals(EquipmentType.OTHER, EquipmentType.fromStorage(null))
        assertEquals(EquipmentType.OTHER, EquipmentType.fromStorage("TRAP_BAR"))
        assertEquals(EquipmentType.BARBELL, EquipmentType.fromLegacyStorage("barbell"))
        assertNull(EquipmentType.fromLegacyStorage("trap_bar"))
    }

    @Test
    fun activityAndPlannerEnumWireNamesAreTheStoredTokens() {
        assertEquals(listOf("ACTIVE", "COMPLETED"), ActivityStatus.entries.map { it.name })
        assertEquals(listOf("LIVE", "BACKDATED", "IMPORTED"), ActivityOrigin.entries.map { it.name })
        assertEquals(listOf("TEMPER"), ActivitySource.entries.map { it.name })
        assertEquals(
            listOf("RUN", "RIDE", "ROW", "SWIM", "WALK", "HIKE", "SKI", "OTHER"),
            CardioType.entries.map { it.name },
        )
        assertEquals(listOf("STRENGTH", "CARDIO", "MIXED"), ScheduleModality.entries.map { it.name })
        assertEquals(listOf("FOLLOW_DEVICE", "FIXED"), ZonePolicy.entries.map { it.name })
        assertEquals(
            listOf("PLANNED", "DONE", "SKIPPED", "MISSED", "MOVED"),
            OccurrenceStatus.entries.map { it.name },
        )
        assertEquals(
            listOf(
                "PENDING", "DELIVERED", "STARTED", "SNOOZED", "MOVED",
                "SKIPPED", "CANCELLED", "STALE",
            ),
            ReminderDeliveryStatus.entries.map { it.name },
        )
        assertEquals(
            listOf("UPPER", "LOWER", "PUSH", "PULL", "LEGS", "FULL_BODY", "RECOVERY"),
            SessionFocusKind.entries.map { it.name },
        )
        assertEquals(
            listOf(
                "ADHERENCE", "SESSION_COUNT", "ACTIVE_MINUTES", "LIFT_TARGET",
                "CARDIO_DURATION", "CARDIO_DISTANCE", "BODYWEIGHT",
            ),
            GoalKind.entries.map { it.name },
        )
        assertEquals(
            listOf("WEEK", "MONTH", "YEAR", "ALL_TIME"),
            GoalPeriod.entries.map { it.name },
        )
        assertEquals(
            listOf("MOVE_REMAINING", "ADAPT_WEEK", "KEEP_DATES", "SKIP_MISSED"),
            MissedWorkChoice.entries.map { it.name },
        )
    }

    @Test
    fun exerciseRoundTripWritesEnumNamesAndNameKey() {
        val domain = Exercise(
            id = "ex-1",
            name = "Barbell Bench Press",
            muscleGroup = "Chest",
            notes = "paused",
            isCustom = true,
            equipment = EquipmentType.BARBELL,
            loadType = LoadType.EXTERNAL,
            movementKey = "bench-press",
            imageKey = "bench",
            muscles = listOf(MuscleCredit(muscleKey = "chest", weight = 1.0)),
        )
        val entity = domain.toEntity()
        assertEquals("BARBELL", entity.equipment)
        assertEquals("EXTERNAL", entity.loadType)
        assertEquals(MuscleNormalizer.nameKeyOf("Barbell Bench Press"), entity.nameKey)
        val credits = listOf(MuscleCredit(muscleKey = "chest", weight = 1.0))
        assertEquals(domain.copy(muscles = credits), entity.toDomain(credits))
    }

    @Test
    fun junkLoadAndEquipmentOnAnExerciseRowDoNotCrash() {
        val row = ExerciseEntity(
            id = "ex-junk",
            name = "Odd lift",
            muscleGroup = "Chest",
            notes = "",
            isCustom = true,
            equipment = "TRAP_BAR",
            loadType = "UNKNOWN",
            nameKey = "odd lift",
        )
        val domain = row.toDomain()
        assertEquals(EquipmentType.OTHER, domain.equipment)
        assertEquals(LoadType.EXTERNAL, domain.loadType)
    }

    @Test
    fun scheduleSlotDropsUnknownFocusOrBadAnchor() {
        val base = ScheduleSlotEntity(
            id = "slot-1",
            position = 0,
            routineId = "r1",
            focusKind = "PUSH",
            anchorDay = 0,
            createdAt = 1L,
            updatedAt = 1L,
        )
        val ok = base.toDomain()
        assertEquals(SessionFocusKind.PUSH, ok?.focusKind)
        assertEquals(Weekday.MONDAY, ok?.anchorDay)

        assertNull(base.copy(focusKind = "YOGA").toDomain())
        assertNull(base.copy(anchorDay = 7).toDomain())
        assertNull(base.copy(anchorDay = -1).toDomain())
        assertNull(base.copy(routineId = null, focusKind = null).toDomain())
    }

    @Test
    fun plannerRowsFallBackRatherThanCrashOnUnknownEnums() {
        val rule = ScheduleRuleEntity(
            id = "rule-1",
            weekday = 1,
            hour = 18,
            minute = 0,
            modality = "YOGA",
            zonePolicy = "GUESS",
            fixedZoneId = null,
            routineId = null,
            templateId = null,
            focusKind = "YOGA",
            reminderOffsetMinutes = 15,
            enabled = 0,
            createdAtMs = 1L,
            updatedAtMs = 1L,
        ).toDomain()
        assertEquals(ScheduleModality.STRENGTH, rule.modality)
        assertEquals(ZonePolicy.FOLLOW_DEVICE, rule.zonePolicy)
        assertNull(rule.focusKind)
        assertEquals(false, rule.enabled)
        assertEquals(Weekday.MONDAY, rule.weekday)

        val occurrence = ScheduleOccurrenceEntity(
            id = "occ-1",
            ruleId = "rule-1",
            status = "GHOST",
            instantMs = 10L,
            zoneId = "UTC",
            offsetSeconds = 0,
            localEpochDay = 20_000L,
            hour = 18,
            minute = 0,
            completedActivityId = null,
            createdAtMs = 1L,
            updatedAtMs = 1L,
        ).toDomain()
        assertEquals(OccurrenceStatus.PLANNED, occurrence.status)

        val delivery = ReminderDeliveryEntity(
            id = "rem-1",
            occurrenceId = "occ-1",
            scheduledAtMs = 10L,
            status = "FIRED",
            createdAtMs = 1L,
            updatedAtMs = 1L,
        ).toDomain()
        assertEquals(ReminderDeliveryStatus.PENDING, delivery.status)

        val decision = MissedWorkDecisionEntity(
            weekStartEpochDay = 20_000L,
            choice = "GIVE_UP",
            decidedAtMs = 1L,
        ).toDomain()
        assertEquals(MissedWorkChoice.KEEP_DATES, decision.choice)
    }

    @Test
    fun plannerRoundTripWritesEnumNamesAndIsoWeekday() {
        val captured = CapturedCivilTime(
            instantMillis = 10L,
            zoneId = "UTC",
            offsetSeconds = 0,
            localEpochDay = 20_000L,
        )
        val rule = ScheduleRule(
            id = "rule-1",
            weekday = Weekday.SUNDAY,
            hour = 7,
            minute = 30,
            modality = ScheduleModality.CARDIO,
            zonePolicy = ZonePolicy.FIXED,
            fixedZoneId = "Asia/Tokyo",
            routineId = null,
            templateId = "tmpl-run",
            focusKind = SessionFocusKind.RECOVERY,
            reminderOffsetMinutes = 10,
            enabled = true,
            createdAtMs = 1L,
            updatedAtMs = 2L,
        )
        val ruleRow = rule.toEntity()
        assertEquals(7, ruleRow.weekday)
        assertEquals("CARDIO", ruleRow.modality)
        assertEquals("FIXED", ruleRow.zonePolicy)
        assertEquals("RECOVERY", ruleRow.focusKind)
        assertEquals(1, ruleRow.enabled)
        assertEquals(rule, ruleRow.toDomain())

        val occurrence = ScheduleOccurrence(
            id = "occ-1",
            ruleId = "rule-1",
            status = OccurrenceStatus.MOVED,
            captured = captured,
            hour = 7,
            minute = 30,
            completedActivityId = "act-1",
            createdAtMs = 1L,
            updatedAtMs = 2L,
        )
        assertEquals("MOVED", occurrence.toEntity().status)
        assertEquals(occurrence, occurrence.toEntity().toDomain())

        val delivery = ReminderDelivery(
            id = "rem-1",
            occurrenceId = "occ-1",
            scheduledAtMs = 10L,
            status = ReminderDeliveryStatus.SNOOZED,
            createdAtMs = 1L,
            updatedAtMs = 2L,
        )
        assertEquals("SNOOZED", delivery.toEntity().status)
        assertEquals(delivery, delivery.toEntity().toDomain())
    }

    @Test
    fun goalRoundTripAndUnknownKindFallback() {
        val goal = MeasurableGoal(
            id = "g1",
            kind = GoalKind.LIFT_TARGET,
            targetValue = 140.0,
            exerciseId = "ex-1",
            exerciseName = "Squat",
            period = GoalPeriod.MONTH,
            captured = CapturedCivilTime(10L, "UTC", 0, 20_000L),
            paused = true,
            createdAtMs = 1L,
            updatedAtMs = 2L,
        )
        val row = goal.toEntity()
        assertEquals("LIFT_TARGET", row.kind)
        assertEquals("MONTH", row.period)
        assertEquals(goal, row.toDomain())

        val unknown = MeasurableGoalEntity(
            id = "g2",
            kind = "STREAK",
            targetValue = 1.0,
            exerciseId = null,
            exerciseName = null,
            period = "DAY",
            instantMs = 10L,
            zoneId = "UTC",
            offsetSeconds = 0,
            localEpochDay = 20_000L,
            paused = false,
            createdAtMs = 1L,
            updatedAtMs = 1L,
        ).toDomain()
        assertEquals(GoalKind.SESSION_COUNT, unknown.kind)
        assertEquals(GoalPeriod.WEEK, unknown.period)
    }

    @Test
    fun activityBlockKindsAreStrengthAndCardioLiterals() {
        val strength = StrengthBlock(
            id = "b1",
            sortOrder = 0,
            exerciseId = "ex-1",
            exerciseName = "Squat",
            loadType = LoadType.STACK,
            equipment = EquipmentType.MACHINE,
            muscles = listOf(MuscleCredit(muscleKey = "quadriceps", weight = 1.0)),
            sets = listOf(
                StrengthSet(
                    id = "s1",
                    setNumber = 1,
                    weightKg = 100.0,
                    reps = 5,
                    rpe = 8,
                    isWarmup = false,
                    completedAtMs = 10L,
                ),
            ),
        )
        val strengthRow = strength.toEntity(sessionId = "act-1", templateId = null)
        assertEquals("STRENGTH", strengthRow.kind)
        assertEquals("STACK", strengthRow.loadType)
        assertEquals("MACHINE", strengthRow.equipment)
        assertEquals("quadriceps\t1.0", strengthRow.musclesEncoded)

        val cardio = CardioBlock(
            id = "b2",
            sortOrder = 1,
            type = CardioType.RUN,
            indoor = true,
            elapsedSeconds = 600L,
            movingSeconds = 580L,
            distanceMeters = 2_000.0,
            elevationMeters = 12.0,
            heartRateBpm = 150,
            energyKj = 400.0,
            rpe = 6,
            routeRef = null,
            intervals = emptyList(),
        )
        val cardioRow = cardio.toEntity(sessionId = "act-1", templateId = null)
        assertEquals("CARDIO", cardioRow.kind)
        assertEquals("RUN", cardioRow.cardioType)
    }

    @Test
    fun activityMuscleCodecRoundTripsTabSeparatedCredits() {
        val muscles = listOf(
            MuscleCredit(muscleKey = "chest", weight = 1.0),
            MuscleCredit(muscleKey = "triceps", weight = 0.4),
        )
        val encoded = ActivityMuscleCodec.encode(muscles)
        assertEquals("chest\t1.0\ntriceps\t0.4", encoded)
        assertEquals(muscles, ActivityMuscleCodec.decode(encoded))
        assertEquals(
            listOf(MuscleCredit(muscleKey = "chest", weight = 1.0)),
            ActivityMuscleCodec.decode("chest"),
        )
        assertEquals(emptyList<MuscleCredit>(), ActivityMuscleCodec.decode(" \n\n"))
    }

    @Test
    fun roomGraphUnknownBlockKindThrows() {
        val graph = ActivityBlockGraph(
            block = ActivityBlockEntity(
                id = "b1",
                sessionId = "act-1",
                templateId = null,
                sortOrder = 0,
                kind = "YOGA",
                exerciseId = null,
                exerciseName = null,
                loadType = null,
                equipment = null,
                musclesEncoded = null,
                cardioType = null,
                indoor = null,
                elapsedSeconds = null,
                movingSeconds = null,
                distanceMeters = null,
                elevationMeters = null,
                heartRateBpm = null,
                energyKj = null,
                rpe = null,
                routeRef = null,
            ),
            strengthSets = emptyList(),
            cardioIntervals = emptyList(),
        )
        try {
            graph.toDomain()
            fail("unknown block kind must not become a silent cardio row")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("YOGA"))
        }
    }

    @Test
    fun backupUnknownBlockKindReadsAsCardioOther() {
        val block = BackupActivityBlock(
            id = "b1",
            sortOrder = 0,
            kind = "YOGA",
        ).toDomain()
        assertTrue(block is CardioBlock)
        assertEquals(CardioType.OTHER, (block as CardioBlock).type)
    }

    @Test
    fun activityBackupRoundTripKeepsWireNames() {
        val captured = CapturedCivilTime(10L, "UTC", 0, 20_000L)
        val backup = BackupActivity(
            id = "act-1",
            status = "COMPLETED",
            origin = "BACKDATED",
            source = "TEMPER",
            title = "Squat day",
            notes = "",
            performedStart = BackupCapturedTime(10L, "UTC", 0, 20_000L),
            createdAtMs = 1L,
            updatedAtMs = 2L,
            revision = 3L,
            blocks = listOf(
                BackupActivityBlock(
                    id = "b1",
                    sortOrder = 0,
                    kind = "STRENGTH",
                    exerciseId = "ex-1",
                    exerciseName = "Squat",
                    loadType = "EXTERNAL",
                    equipment = "BARBELL",
                    muscles = listOf(BackupActivityMuscle("quadriceps", 1.0)),
                    sets = listOf(
                        BackupStrengthSet("s1", 1, 100.0, 5, null, false, 10L),
                    ),
                ),
            ),
        )
        val domain = backup.toDomain()
        assertEquals(ActivityStatus.COMPLETED, domain.status)
        assertEquals(ActivityOrigin.BACKDATED, domain.origin)
        assertEquals(ActivitySource.TEMPER, domain.source)
        assertEquals(captured, domain.performedStart)
        val again = domain.toBackup()
        assertEquals("COMPLETED", again.status)
        assertEquals("BACKDATED", again.origin)
        assertEquals("TEMPER", again.source)
        assertEquals("STRENGTH", again.blocks.single().kind)
        assertEquals("EXTERNAL", again.blocks.single().loadType)
        assertEquals("BARBELL", again.blocks.single().equipment)
    }

    @Test
    fun liveActivityEntityCarriesTheLiveToken() {
        val captured = CapturedCivilTime(10L, "UTC", 0, 20_000L)
        val live = ActivitySession(
            id = "act-live",
            status = ActivityStatus.ACTIVE,
            origin = ActivityOrigin.LIVE,
            source = ActivitySource.TEMPER,
            title = "Live",
            notes = "",
            performedStart = captured,
            performedEnd = null,
            templateId = null,
            occurrenceId = null,
            blocks = emptyList(),
            createdAtMs = 1L,
            updatedAtMs = 1L,
            revision = 1L,
        )
        assertEquals("LIVE", live.toEntity().liveToken)
        assertEquals("ACTIVE", live.toEntity().status)
        val done = live.copy(status = ActivityStatus.COMPLETED)
        assertNull(done.toEntity().liveToken)
        assertEquals("COMPLETED", done.toEntity().status)
    }
}
