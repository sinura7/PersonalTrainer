// androidx.sqlite.db.SupportSQLiteDatabase — declaration-only stub. See compile-stubs/README.md.
//
// DELIBERATELY NARROWER THAN THE REAL INTERFACES, which have ~40 members between them. Only the
// three the repo uses are declared: execSQL (every migration), query (FoundationReset's
// PRAGMA integrity_check) and readableDatabase. Adding insert/update/delete/transaction
// speculatively would widen what compiles for no gain; a file that starts using one fails here
// with "unresolved reference" and this file gets one more line.
//
// `query` returns android.database.Cursor from the REAL Android jar, not a stub, so
// `cursor.use { it.moveToFirst() && it.getString(0) ... }` is checked against Cursor's actual
// signatures — including that getString returns a platform String and that Cursor is Closeable.

package androidx.sqlite.db

import android.database.Cursor

// `: Closeable` is Room's own supertype, not a convenience: the migration tests in app/src/test
// write `helper.createDatabase(DB, 1).close()` and `helper.createDatabase(...).use { db -> ... }`,
// and without it those would be false REDs while a genuinely unclosed handle stays invisible
// either way.
interface SupportSQLiteDatabase : java.io.Closeable {
    fun execSQL(sql: String)
    fun query(query: String): Cursor
}

interface SupportSQLiteOpenHelper {
    val readableDatabase: SupportSQLiteDatabase
    val writableDatabase: SupportSQLiteDatabase
}
