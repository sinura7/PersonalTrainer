// androidx.room / androidx.sqlite.db — declaration-only stubs for tools/compile-check.sh.
//
// WHY: Room 2.7.2 comes from Google's Maven, which this environment cannot reach. Without
// these declarations none of app/src/main/java/.../data/local can be type-checked at all.
//
// SOUNDNESS RULE: a stub must be no more permissive than the real API, because a loose stub
// hides real errors. The annotations below therefore keep Room's exact element types
// (Array<Index>, Array<ForeignKey>, Array<String>, KClass<*>) rather than Array<Any>, so a
// swapped argument is a compile error here just as KSP would reject it in CI. Parameters
// Room requires — @Query's SQL, @ForeignKey's entity/parentColumns/childColumns,
// @Relation's parentColumn/entityColumn, @Database's version — carry NO default, so an
// annotation that forgets one fails here rather than in the merge gate. Parameters Room
// defaults are defaulted, so the lane does not invent a false RED.
//
// NOT a runtime fake: nothing here executes. KSP still generates the real DAO/database
// implementations under Gradle; this lane checks declarations only. See the caveats in
// tools/compile-check.sh for what a green result does NOT prove.

package androidx.room

import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class Entity(
    val tableName: String = "",
    val indices: Array<Index> = [],
    val inheritSuperIndices: Boolean = false,
    val primaryKeys: Array<String> = [],
    val foreignKeys: Array<ForeignKey> = [],
    val ignoredColumns: Array<String> = [],
)

// `@Target()` — empty, applicable nowhere — is what Room declares, and is correct: @Index is
// only ever legal as an argument inside @Entity, which does not go through target checking.
// A stray top-level @Index is therefore a compile error here, as it is under KSP.
@Target()
@Retention(AnnotationRetention.BINARY)
annotation class Index(
    // MUST be `vararg val value: String`, not `val value: Array<String>`: the repo writes both
    // Index("nameKey") (positional single string) and Index(value = ["ruleId", "localEpochDay"]),
    // and only the vararg form accepts both. Typed String, never Any — Index(42) must not compile.
    vararg val value: String,
    val orders: Array<Order> = [],
    val name: String = "",
    val unique: Boolean = false,
) {
    enum class Order { ASC, DESC }
}

@Target()
@Retention(AnnotationRetention.BINARY)
annotation class ForeignKey(
    // No defaults on these three. Room requires all of them; a foreign key that forgot
    // childColumns must be RED here, not green-here-and-red-in-CI.
    val entity: KClass<*>,
    val parentColumns: Array<String>,
    val childColumns: Array<String>,
    val onDelete: Int = NO_ACTION,
    val onUpdate: Int = NO_ACTION,
    val deferred: Boolean = false,
) {
    companion object {
        // `const val`, not `val`: only a compile-time constant is legal as an annotation
        // argument, so a plain `val` would turn `onDelete = ForeignKey.CASCADE` into a false RED.
        // Values match Room's so a reader of this file is not misled.
        const val NO_ACTION: Int = 1
        const val RESTRICT: Int = 2
        const val SET_NULL: Int = 3
        const val SET_DEFAULT: Int = 4
        const val CASCADE: Int = 5
    }
}

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class PrimaryKey(val autoGenerate: Boolean = false)

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class ColumnInfo(
    // Both non-null String. Typing either as String? would let `defaultValue = null` compile,
    // which Room rejects. The sentinel defaults are Room's literal values: an EMPTY
    // defaultValue is a real `DEFAULT ''` clause, not "unset", and data/local/Migrations.kt is
    // explicit that these strings must match the migration SQL byte-for-byte.
    val name: String = INHERIT_FIELD_NAME,
    val typeAffinity: Int = UNDEFINED,
    val index: Boolean = false,
    val collate: Int = UNSPECIFIED,
    val defaultValue: String = VALUE_UNSPECIFIED,
) {
    companion object {
        const val INHERIT_FIELD_NAME: String = "[field-name]"
        const val VALUE_UNSPECIFIED: String = "[value-unspecified]"
        const val UNDEFINED: Int = 1
        const val TEXT: Int = 2
        const val INTEGER: Int = 3
        const val REAL: Int = 4
        const val BLOB: Int = 5
        const val UNSPECIFIED: Int = 1
        const val BINARY: Int = 2
        const val NOCASE: Int = 3
        const val RTRIM: Int = 4
        const val LOCALIZED: Int = 5
        const val UNICODE: Int = 6
    }
}

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class Embedded(val prefix: String = "")

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class Relation(
    // `entity` is defaulted (Room mirrors Java's Object.class default) but parentColumn and
    // entityColumn are NOT: a @Relation that forgot entityColumn must not compile. Kotlin
    // allows a defaulted parameter before required ones as long as callers name their
    // arguments, which every use site in this repo does, so Room's real order is kept.
    val entity: KClass<*> = Any::class,
    val parentColumn: String,
    val entityColumn: String,
    val projection: Array<String> = [],
)

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class Dao

// `value` is required. Defaulting it to "" would make a bare @Query compile; Room fails on
// that. Every use site passes the SQL positionally, which one required parameter accepts.
// NOTE: this lane checks that the SQL is a String literal and nothing else. It does NOT parse
// the SQL, does not check column names, does not check that the return type matches the
// projection, and does not verify @Insert/@Update entity types — all of which Room's KSP
// processor does under Gradle and none of which any compile-only lane can reproduce.
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Query(val value: String)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Insert(
    val entity: KClass<*> = Any::class,
    val onConflict: Int = OnConflictStrategy.ABORT,
)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Update(
    val entity: KClass<*> = Any::class,
    val onConflict: Int = OnConflictStrategy.ABORT,
)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Upsert(val entity: KClass<*> = Any::class)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Delete(val entity: KClass<*> = Any::class)

// FUNCTION only, exactly as Room declares it. Room's @Transaction is
// @Target(ElementType.METHOD); it is NOT legal on a class, and the Kotlin front-end (not KSP)
// rejects a class-level use under Gradle. Adding CLASS here would make `@Transaction class Foo`
// a green in this lane and a red in CI.
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Transaction

// Room declares this as an annotation class whose companion holds the Int constants, and
// @Insert(onConflict = ...) takes an Int. Kept as an Int rather than a custom enum so a stray
// integer is as (im)possible here as it is under Room.
@Retention(AnnotationRetention.BINARY)
annotation class OnConflictStrategy {
    companion object {
        const val REPLACE: Int = 1
        const val ABORT: Int = 3
        const val IGNORE: Int = 5
    }
}

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class Database(
    val entities: Array<KClass<*>> = [],
    val views: Array<KClass<*>> = [],
    // No default: Room requires a version, and a @Database that forgot one must be RED.
    val version: Int,
    val exportSchema: Boolean = true,
)

abstract class RoomDatabase {
    /** The SQLite handle behind the database. data/repository/FoundationReset.kt runs
     *  `PRAGMA integrity_check` through it before the foundation cutover. */
    open val openHelper: androidx.sqlite.db.SupportSQLiteOpenHelper get() = TODO("compile-only stub")

    open fun close() { TODO("compile-only stub") }

    /**
     * DELIBERATELY NARROWER THAN ROOM. Only the two builder methods this repo calls are
     * declared. A newly used builder method (`addCallback`, `setJournalMode`, …) will fail
     * here with "unresolved reference" and needs adding — a visible, one-line fix. The
     * alternative, a catch-all builder, would silently accept anything.
     *
     * `fallbackToDestructiveMigration` is intentionally absent. This database holds the only
     * copy of the owner's training history and both TrainerDatabase.create and
     * TemperDatabase.create carry a comment forbidding it; leaving it out means this lane
     * cannot be the thing that lets it in by accident.
     */
    open class Builder<T : RoomDatabase> internal constructor() {
        fun addMigrations(vararg migrations: androidx.room.migration.Migration): Builder<T> =
            TODO("compile-only stub")

        // Used only by app/src/test, which builds in-memory databases on the test thread and
        // pins the executors so a query can be observed. Declared here rather than in
        // tools/test-stubs because they are members of THIS class and a second copy of
        // RoomDatabase.Builder on the test classpath would shadow this one wholesale.
        // Every signature is Room's; none is looser.
        fun allowMainThreadQueries(): Builder<T> = TODO("compile-only stub")

        fun setQueryExecutor(executor: java.util.concurrent.Executor): Builder<T> =
            TODO("compile-only stub")

        fun setTransactionExecutor(executor: java.util.concurrent.Executor): Builder<T> =
            TODO("compile-only stub")

        fun setQueryCallback(
            queryCallback: QueryCallback,
            executor: java.util.concurrent.Executor,
        ): Builder<T> = TODO("compile-only stub")

        fun build(): T = TODO("compile-only stub")
    }

    /** `fun interface`, as Room declares it, so the SAM-converted lambda at
     *  data/repository/WorkoutRepositoryHistoryBeforeTest.kt:44 resolves and its two parameter
     *  types are checked. bindArgs is List<Any?> — Room's exact type. */
    fun interface QueryCallback {
        fun onQuery(sqlQuery: String, bindArgs: List<Any?>)
    }
}

object Room {
    fun <T : RoomDatabase> databaseBuilder(
        context: android.content.Context,
        klass: Class<T>,
        name: String?,
    ): RoomDatabase.Builder<T> = TODO("compile-only stub")

    fun <T : RoomDatabase> inMemoryDatabaseBuilder(
        context: android.content.Context,
        klass: Class<T>,
    ): RoomDatabase.Builder<T> = TODO("compile-only stub")
}

/**
 * room-ktx. Both `suspend`s are load-bearing: dropping the one on the function would let a
 * transaction be opened from non-suspend code, and dropping the one on `block` would reject
 * the suspending bodies every call site in this repo actually passes.
 */
suspend fun <R> RoomDatabase.withTransaction(block: suspend () -> R): R =
    TODO("compile-only stub")
