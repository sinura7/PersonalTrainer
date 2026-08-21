package com.sinura.personaltrainer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sinura.personaltrainer.data.local.dao.CatalogDao
import com.sinura.personaltrainer.data.local.dao.ExerciseDao
import com.sinura.personaltrainer.data.local.dao.RoutineDao
import com.sinura.personaltrainer.data.local.dao.ScheduleDao
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.ExerciseMuscleEntity
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.data.local.entity.ScheduleSlotEntity
import com.sinura.personaltrainer.data.local.entity.SeedMetaEntity
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity

@Database(
    entities = [
        ExerciseEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class,
        WorkoutSessionEntity::class,
        SessionExerciseEntity::class,
        SetLogEntity::class,
        ExerciseMuscleEntity::class,
        SeedMetaEntity::class,
        ScheduleSlotEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class TrainerDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun routineDao(): RoutineDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun catalogDao(): CatalogDao
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        fun create(context: Context): TrainerDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                TrainerDatabase::class.java,
                "personal_trainer.db",
            )
                // No fallbackToDestructiveMigration, here or ever: this database holds the
                // only copy of the owner's training history, and a destructive fallback turns
                // a migration bug into a silent wipe on next launch.
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
