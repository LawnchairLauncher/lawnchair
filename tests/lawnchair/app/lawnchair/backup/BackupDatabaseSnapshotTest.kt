package app.lawnchair.backup

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class BackupDatabaseSnapshotTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun snapshotIncludesCommittedWalRowsAndPreservesSchemaAndTypes() {
        val source = temporaryFolder.newFile("source.db")
        val snapshot = temporaryFolder.newFile("snapshot.db")
        SQLiteDatabase.openOrCreateDatabase(source, null).use { db ->
            db.enableWriteAheadLogging()
            db.version = 32
            db.execSQL("CREATE TABLE items (_id INTEGER PRIMARY KEY, title TEXT, icon BLOB, optional TEXT)")
            db.execSQL("CREATE UNIQUE INDEX item_title ON items(title)")
            db.execSQL("INSERT INTO items VALUES (7, 'Maps', X'0102FF', NULL)")
            BackupDatabaseSnapshot.create(source, snapshot)
            db.execSQL("UPDATE items SET title = 'Changed' WHERE _id = 7")
        }
        SQLiteDatabase.openDatabase(snapshot.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(32, db.version)
            db.rawQuery("SELECT * FROM items", null).use {
                assertTrue(it.moveToFirst())
                assertEquals(7, it.getInt(0))
                assertEquals("Maps", it.getString(1))
                assertArrayEquals(byteArrayOf(1, 2, -1), it.getBlob(2))
                assertTrue(it.isNull(3))
            }
            db.rawQuery("PRAGMA integrity_check", null).use {
                assertTrue(it.moveToFirst())
                assertEquals("ok", it.getString(0))
            }
            db.rawQuery("SELECT name FROM sqlite_master WHERE type='index' AND name='item_title'", null).use {
                assertTrue(it.moveToFirst())
            }
        }
    }

    @Test
    fun snapshotPreservesNumericTypesWithoutColumnAffinity() {
        val source = temporaryFolder.newFile("untyped.db")
        val snapshot = temporaryFolder.newFile("untyped-snapshot.db")
        SQLiteDatabase.openOrCreateDatabase(source, null).use { db ->
            db.execSQL("CREATE TABLE values_table (integer_value, real_value, text_value)")
            db.execSQL("INSERT INTO values_table VALUES (1, 1.5, '1')")
            BackupDatabaseSnapshot.create(source, snapshot)
        }
        SQLiteDatabase.openDatabase(snapshot.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT * FROM values_table", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(Cursor.FIELD_TYPE_INTEGER, cursor.getType(0))
                assertEquals(Cursor.FIELD_TYPE_FLOAT, cursor.getType(1))
                assertEquals(Cursor.FIELD_TYPE_STRING, cursor.getType(2))
            }
        }
    }

}
