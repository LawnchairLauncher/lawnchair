package app.lawnchair.backup

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import app.lawnchair.LawnchairProto.GridState
import com.android.launcher3.LauncherFiles
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.model.DatabaseHelper
import com.android.launcher3.model.DeviceGridState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], application = Application::class)
class BackupLayoutDatabaseTest {
    @Test
    fun installationSelectsValidatedArchiveInsteadOfArchivedDatabaseFilename() {
        val context = RuntimeEnvironment.getApplication()
        val grid = GridState.newBuilder().setGridSize("4,6").setHotseatCount(4).build()
        val archive = File(context.cacheDir, "valid-layout.db")
        SQLiteDatabase.openOrCreateDatabase(archive, null).use {
            Favorites.addTableToDb(it, 0, false)
            it.version = DatabaseHelper.SCHEMA_VERSION
            it.execSQL("INSERT INTO favorites (_id, title) VALUES (42, 'archived item')")
        }
        val archivedDatabase = context.getDatabasePath("launcher_6_4_4.db").apply {
            parentFile!!.mkdirs()
            writeText("unrelated existing grid")
        }
        val targetDatabase = context.getDatabasePath("launcher_5_6_6.db").apply { writeText("old target layout") }
        val preferences = context.getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY, 0)
        preferences.edit().putString(DeviceGridState.KEY_DB_FILE, archivedDatabase.name).commit()

        BackupLayoutDatabase.validate(context, archive, grid)
        BackupLayoutDatabase.install(context, archive, grid, targetDatabase.name)

        val selectedDatabase = preferences.getString(DeviceGridState.KEY_DB_FILE, null)
        assertEquals(BackupLayoutDatabase.SOURCE_DATABASE, selectedDatabase)
        assertEquals("4,6", preferences.getString(DeviceGridState.KEY_WORKSPACE_SIZE, null))
        assertFalse(targetDatabase.exists())
        assertEquals("unrelated existing grid", archivedDatabase.readText())
        SQLiteDatabase.openDatabase(context.getDatabasePath(selectedDatabase!!).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT _id, title FROM favorites", null).use {
                assertTrue(it.moveToFirst())
                assertEquals(42, it.getInt(0))
                assertEquals("archived item", it.getString(1))
            }
        }
    }
}
