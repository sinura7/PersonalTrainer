// androidx.room.migration.Migration — declaration-only stub. See compile-stubs/README.md.
//
// SOUNDNESS: startVersion/endVersion are constructor parameters with no defaults, exactly as
// Room declares them, so `object : Migration(1, 2)` is the only legal shape. `migrate` is open
// rather than abstract, matching Room 2.7 where it has a default body.
//
// DELIBERATELY NARROWER THAN ROOM: Room 2.7 also declares `migrate(connection: SQLiteConnection)`
// for its KMP driver API. Only the SupportSQLiteDatabase overload is declared here — the one
// every migration in this repo overrides. A migration written against the driver API would be
// a visible RED needing this stub extended, not a silent pass.

package androidx.room.migration

import androidx.sqlite.db.SupportSQLiteDatabase

abstract class Migration(
    val startVersion: Int,
    val endVersion: Int,
) {
    open fun migrate(db: SupportSQLiteDatabase) {
        throw UnsupportedOperationException("compile-only stub")
    }
}
