package app.lawnchair.backup

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import app.lawnchair.data.AppDatabase
import java.io.File

/** Imports into Room's live connection so cached DAOs never write to a replaced database file. */
internal object BackupPreferencesDatabase {
    fun restore(context: Context, archive: File, live: AppDatabase) {
        val staged = Room.databaseBuilder(context, AppDatabase::class.java, archive.absolutePath)
            .addMigrations(AppDatabase.MIGRATION_1_3, AppDatabase.MIGRATION_2_3)
            .build()
        try {
            // Room validates and, when needed, migrates the staged copy before live data changes.
            val source = staged.openHelper.writableDatabase
            val target = live.openHelper.writableDatabase
            val tables = tables(source)
            check(tables == tables(target)) { "Incompatible preferences database" }
            live.runInTransaction {
                target.execSQL("PRAGMA defer_foreign_keys = ON")
                tables.forEach { target.execSQL("DELETE FROM ${quote(it)}") }
                tables.forEach { copyTable(source, target, it) }
                if (hasSequence(source) && hasSequence(target)) {
                    target.execSQL("DELETE FROM sqlite_sequence")
                    copyTable(source, target, "sqlite_sequence")
                }
                target.query("PRAGMA foreign_key_check").use {
                    check(!it.moveToFirst()) { "Invalid preferences database relationships" }
                }
            }
        } finally {
            staged.close()
        }
    }

    private fun tables(db: SupportSQLiteDatabase): List<String> = db.query(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata', 'room_master_table') ORDER BY name",
    ).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
    }

    private fun hasSequence(db: SupportSQLiteDatabase): Boolean = db.query(
        "SELECT name FROM sqlite_master WHERE name = 'sqlite_sequence'",
    ).use { it.moveToFirst() }

    private fun copyTable(source: SupportSQLiteDatabase, target: SupportSQLiteDatabase, table: String) {
        val quoted = quote(table)
        source.query("SELECT * FROM $quoted").use { cursor ->
            while (cursor.moveToNext()) {
                val values = ContentValues()
                BackupDatabaseSnapshot.copyRow(cursor, values)
                target.insert(quoted, SQLiteDatabase.CONFLICT_ABORT, values)
            }
        }
    }

    private fun quote(value: String) = "\"${value.replace("\"", "\"\"")}\""
}
