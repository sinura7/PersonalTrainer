// androidx.room.testing.MigrationTestHelper — declaration-only. See README.md.
//
// room-testing 2.7.2 is a Google-only artifact. The five JVM migration tests use exactly three
// members of it, declared here at Room's signatures:
//
//   * the (Instrumentation, Class<out RoomDatabase>) constructor — Room 2.5+. The bound on the
//     class parameter is `out RoomDatabase`, so `MigrationTestHelper(i, String::class.java)`
//     stays an error.
//   * createDatabase(name, version) and runMigrationsAndValidate(name, version,
//     validateDroppedTables, vararg migrations), both returning SupportSQLiteDatabase — so the
//     `db.query(...).use { ... }` and `db.execSQL(...)` bodies in those tests are checked
//     against the sqlite stub and against android.database.Cursor's real signatures.
//
// It extends org.junit.rules.TestWatcher (from the REAL junit jar) because every use site is a
// `@get:Rule val helper = ...`, and a helper that was not a TestRule would be a runtime failure
// this lane could not see.
//
// DELIBERATELY NARROWER: the (Instrumentation, String assetsFolder) constructor, the
// AutoMigrationSpec and openFactory parameters, and closeWhenFinished() are not declared. A new
// use is a visible RED needing one more line here, not a silent pass.

package androidx.room.testing

import android.app.Instrumentation
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.rules.TestWatcher

open class MigrationTestHelper(
    instrumentation: Instrumentation,
    databaseClass: Class<out RoomDatabase>,
) : TestWatcher() {
    fun createDatabase(name: String, version: Int): SupportSQLiteDatabase =
        TODO("compile-only stub")

    fun runMigrationsAndValidate(
        name: String,
        version: Int,
        validateDroppedTables: Boolean,
        vararg migrations: Migration,
    ): SupportSQLiteDatabase = TODO("compile-only stub")
}
