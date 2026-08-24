package com.sinura.personaltrainer.data.local

import androidx.room.RoomDatabase
import com.sinura.personaltrainer.data.local.dao.CatalogDao
import com.sinura.personaltrainer.data.local.dao.ExerciseDao
import com.sinura.personaltrainer.data.local.dao.RoutineDao
import com.sinura.personaltrainer.data.local.dao.ScheduleDao
import com.sinura.personaltrainer.data.local.dao.WorkoutDao

/**
 * Shared DAO surface for [TrainerDatabase] (legacy, migration tests) and
 * [TemperDatabase] (foundation generation). Repositories take this type so
 * the cutover is a constructor change, not a rewrite.
 */
abstract class AppRoomDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun routineDao(): RoutineDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun catalogDao(): CatalogDao
    abstract fun scheduleDao(): ScheduleDao
}
