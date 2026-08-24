package com.sinura.personaltrainer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import com.sinura.personaltrainer.data.local.dao.ActivityDao
import com.sinura.personaltrainer.data.local.dao.BodyweightDao
import com.sinura.personaltrainer.data.local.dao.TrainingBlockDao
import com.sinura.personaltrainer.data.local.entity.ActivityBlockEntity
import com.sinura.personaltrainer.data.local.entity.ActivityCardioIntervalEntity
import com.sinura.personaltrainer.data.local.entity.ActivitySessionEntity
import com.sinura.personaltrainer.data.local.entity.ActivityStrengthSetEntity
import com.sinura.personaltrainer.data.local.entity.ActivityTemplateEntity
import com.sinura.personaltrainer.data.local.entity.BodyweightEntryEntity
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.TrainingBlockEntity
import com.sinura.personaltrainer.data.local.entity.ExerciseMuscleEntity
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.data.local.entity.ScheduleSlotEntity
import com.sinura.personaltrainer.data.local.entity.SeedMetaEntity
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity

/**
 * Foundation generation (ADR-010). New name, new schema folder, new
 * version series. Not a silent `TrainerDatabase` v2→v3 patch.
 *
 * Holds the v2 catalog / routine / schedule / legacy session tables so
 * current UI keeps working after cutover, plus the ADR-007 activity
 * tables. `fallbackToDestructiveMigration` is banned here too.
 */
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
        ActivitySessionEntity::class,
        ActivityTemplateEntity::class,
        ActivityBlockEntity::class,
        ActivityStrengthSetEntity::class,
        ActivityCardioIntervalEntity::class,
        BodyweightEntryEntity::class,
        TrainingBlockEntity::class,
    ],
    version = FoundationGeneration.VERSION,
    exportSchema = true,
)
abstract class TemperDatabase : AppRoomDatabase() {
    abstract fun activityDao(): ActivityDao
    abstract fun bodyweightDao(): BodyweightDao
    abstract fun trainingBlockDao(): TrainingBlockDao

    companion object {
        fun create(context: Context): TemperDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                TemperDatabase::class.java,
                FoundationGeneration.DATABASE_FILE,
            )
                // No fallbackToDestructiveMigration, here or ever: a migration
                // bug must fail closed, not silently erase training history.
                .addMigrations(MIGRATION_TEMPER_1_2)
                .build()
        }
    }
}
