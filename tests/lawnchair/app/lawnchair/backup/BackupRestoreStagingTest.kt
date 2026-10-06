package app.lawnchair.backup

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import app.lawnchair.LawnchairProto.BackupInfo
import app.lawnchair.LawnchairProto.GridState
import com.android.launcher3.LauncherFiles
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.model.DatabaseHelper
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], application = Application::class)
class BackupRestoreStagingTest {
    @Test
    fun incompleteArchiveDoesNotReplaceLiveSettingsOrDatabase() {
        val context = RuntimeEnvironment.getApplication()
        val liveDatabase = context.getDatabasePath("launcher.db").apply {
            parentFile!!.mkdirs()
            writeText("live layout")
        }
        val livePreferences = File(context.applicationInfo.dataDir, "shared_prefs/com.android.launcher3.prefs.xml").apply {
            parentFile!!.mkdirs()
            writeText("live preferences")
        }
        val archive = File(context.cacheDir, "incomplete.zip")
        ZipOutputStream(archive.outputStream()).use {
            it.putNextEntry(ZipEntry("com.android.launcher3.prefs.xml"))
            it.write("replacement preferences".toByteArray())
        }
        val backup = LawnchairBackup(context, Uri.fromFile(archive)).apply {
            info = BackupInfo.newBuilder().setContents(LawnchairBackup.INCLUDE_LAYOUT_AND_SETTINGS).build()
        }
        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { backup.restore(LawnchairBackup.INCLUDE_LAYOUT_AND_SETTINGS) }
        }
        assertEquals("Backup has no launcher layout", error.message)
        assertEquals("live layout", liveDatabase.readText())
        assertEquals("live preferences", livePreferences.readText())
    }

    @Test
    fun invalidPresentLayoutsLeaveLiveFilesAndCachedPreferencesUntouched() {
        val context = RuntimeEnvironment.getApplication()
        val liveDatabase = context.getDatabasePath("launcher.db").apply {
            parentFile!!.mkdirs()
            writeText("live layout")
        }
        val preferences = context.getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY, 0)
        preferences.edit().putString("pref_iconPackPackage", "live icon pack").commit()
        val preferencesFile = File(context.applicationInfo.dataDir, "shared_prefs/${LauncherFiles.SHARED_PREFERENCES_KEY}.xml")
        val savedPreferences = preferencesFile.readText()
        val invalidLayouts = listOf(
            File(context.cacheDir, "corrupt.db").apply { writeText("not SQLite") },
            File(context.cacheDir, "missing-table.db").apply {
                SQLiteDatabase.openOrCreateDatabase(this, null).use { it.version = DatabaseHelper.SCHEMA_VERSION }
            },
            File(context.cacheDir, "missing-columns.db").apply {
                SQLiteDatabase.openOrCreateDatabase(this, null).use {
                    it.version = DatabaseHelper.SCHEMA_VERSION
                    it.execSQL("CREATE TABLE favorites (_id INTEGER PRIMARY KEY)")
                }
            },
            File(context.cacheDir, "future-version.db").apply {
                SQLiteDatabase.openOrCreateDatabase(this, null).use {
                    Favorites.addTableToDb(it, 0, false)
                    it.version = DatabaseHelper.SCHEMA_VERSION + 1
                }
            },
        )
        invalidLayouts.forEachIndexed { index, layout ->
            val archive = File(context.cacheDir, "invalid-$index.zip")
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("launcher.db"))
                layout.inputStream().use { it.copyTo(zip) }
                zip.putNextEntry(ZipEntry("${LauncherFiles.SHARED_PREFERENCES_KEY}.xml"))
                zip.write("<map><string name='pref_iconPackPackage'>archived icon pack</string></map>".toByteArray())
            }
            val backup = LawnchairBackup(context, Uri.fromFile(archive)).apply {
                info = BackupInfo.newBuilder()
                    .setContents(LawnchairBackup.INCLUDE_LAYOUT_AND_SETTINGS)
                    .setGridState(GridState.newBuilder().setGridSize("4,6").setHotseatCount(4))
                    .build()
            }
            assertThrows(Exception::class.java) {
                runBlocking { backup.restore(LawnchairBackup.INCLUDE_LAYOUT_AND_SETTINGS) }
            }
            assertEquals("live layout", liveDatabase.readText())
            assertEquals(savedPreferences, preferencesFile.readText())
            assertEquals("live icon pack", preferences.getString("pref_iconPackPackage", null))
        }
    }

}
