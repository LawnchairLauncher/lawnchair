package app.lawnchair.backup

import android.app.Application
import androidx.room.Room
import app.lawnchair.data.AppDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], application = Application::class)
class BackupPreferencesDatabaseTest {
    @Test
    fun restoreUpdatesOpenConnectionAndPreservesFolderRelationships() = runBlocking(Dispatchers.IO) {
        val context = RuntimeEnvironment.getApplication()
        val sourceFile = context.getDatabasePath("source-preferences")
        val source = Room.databaseBuilder(context, AppDatabase::class.java, "source-preferences").build()
        val live = Room.databaseBuilder(context, AppDatabase::class.java, "live-preferences").build()
        try {
            source.openHelper.writableDatabase.execSQL("INSERT INTO Folders (id, title, hide, rank, timestamp) VALUES (7, 'saved folder', 0, 0, 1)")
            source.openHelper.writableDatabase.execSQL("INSERT INTO FolderItems (id, folderId, rank, item_info, timestamp) VALUES (11, 7, 0, 'saved app', 1)")
            val snapshot = File(context.cacheDir, "preferences-snapshot")
            BackupDatabaseSnapshot.create(sourceFile, snapshot)
            val connection = live.openHelper.writableDatabase
            connection.execSQL("INSERT INTO Folders (id, title, hide, rank, timestamp) VALUES (2, 'changed folder', 0, 0, 1)")
            connection.execSQL("INSERT INTO FolderItems (id, folderId, rank, item_info, timestamp) VALUES (3, 2, 0, 'changed app', 1)")
            BackupPreferencesDatabase.restore(context, snapshot, live)
            connection.query("SELECT Folders.title, FolderItems.item_info FROM Folders JOIN FolderItems ON Folders.id = FolderItems.folderId").use {
                assertTrue(it.moveToFirst())
                assertEquals("saved folder", it.getString(0))
                assertEquals("saved app", it.getString(1))
                assertEquals(1, it.count)
            }
            // A subsequent write through the already-open connection keeps the restored rows.
            connection.execSQL("UPDATE Folders SET rank = 1 WHERE id = 7")
            connection.query("SELECT title, rank FROM Folders WHERE id = 7").use {
                assertTrue(it.moveToFirst())
                assertEquals("saved folder", it.getString(0))
                assertEquals(1, it.getInt(1))
            }
        } finally {
            source.close()
            live.close()
        }
    }
}
