package app.lawnchair.backup

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File

/** Copies committed rows, including WAL contents, while holding a consistent source transaction. */
internal object BackupDatabaseSnapshot {
    fun create(source: File, destination: File) {
        SQLiteDatabase.openDatabase(source.path, null, SQLiteDatabase.OPEN_READWRITE).use { input ->
            SQLiteDatabase.openOrCreateDatabase(destination, null).use { output ->
                input.beginTransactionNonExclusive()
                try {
                    output.beginTransaction()
                    try {
                        val schema = mutableListOf<Pair<String, String>>()
                        input.rawQuery(
                            "SELECT name, sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata' ORDER BY type = 'table' DESC",
                            null,
                        ).use { cursor ->
                            while (cursor.moveToNext()) schema += cursor.getString(0) to cursor.getString(1)
                        }
                        val tables = mutableSetOf<String>()
                        input.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata'", null).use { cursor ->
                            while (cursor.moveToNext()) tables += cursor.getString(0)
                        }
                        schema.filter { it.first in tables }.forEach { (name, sql) ->
                            output.execSQL(sql)
                            val quotedName = "\"${name.replace("\"", "\"\"")}\""
                            input.rawQuery("SELECT * FROM $quotedName", null).use { cursor ->
                                while (cursor.moveToNext()) {
                                    val values = ContentValues()
                                    copyRow(cursor, values)
                                    output.insertOrThrow(quotedName, null, values)
                                }
                            }
                        }
                        schema.filter { it.first !in tables }.forEach { (_, sql) -> output.execSQL(sql) }
                        input.rawQuery("SELECT name FROM sqlite_master WHERE name = 'sqlite_sequence'", null).use { sequence ->
                            if (sequence.moveToFirst()) {
                                output.delete("sqlite_sequence", null, null)
                                input.rawQuery("SELECT * FROM sqlite_sequence", null).use { cursor ->
                                    while (cursor.moveToNext()) {
                                        val values = ContentValues()
                                        copyRow(cursor, values)
                                        output.insertOrThrow("sqlite_sequence", null, values)
                                    }
                                }
                            }
                        }
                        output.version = input.version
                        output.setTransactionSuccessful()
                    } finally {
                        output.endTransaction()
                    }
                } finally {
                    input.endTransaction()
                }
            }
        }
    }

    internal fun copyRow(cursor: Cursor, values: ContentValues) {
        repeat(cursor.columnCount) { column ->
            val name = cursor.getColumnName(column)
            when (cursor.getType(column)) {
                Cursor.FIELD_TYPE_NULL -> values.putNull(name)
                Cursor.FIELD_TYPE_BLOB -> values.put(name, cursor.getBlob(column))
                Cursor.FIELD_TYPE_INTEGER -> values.put(name, cursor.getLong(column))
                Cursor.FIELD_TYPE_FLOAT -> values.put(name, cursor.getDouble(column))
                else -> values.put(name, cursor.getString(column))
            }
        }
    }
}
