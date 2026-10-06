package app.lawnchair.backup

import android.app.Application
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import app.lawnchair.LawnchairProto.GridState
import com.android.launcher3.LauncherFiles
import com.android.launcher3.model.DeviceGridState
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], application = Application::class)
class BackupLayoutInstallRollbackTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val grid = GridState.newBuilder().setGridSize("4,6").setHotseatCount(4).build()
    private val targetName = "launcher_6_4_4.db"
    private val preferences get() = context.getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY, 0)

    private fun seedFiles(): Map<File, ByteArray> = buildMap {
        listOf(targetName, BackupLayoutDatabase.SOURCE_DATABASE, LawnchairBackup.RESTORED_DB_FILE_NAME).forEach { name ->
            listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
                val file = File(context.getDatabasePath(name).path + suffix)
                file.parentFile!!.mkdirs()
                file.writeText("previous $name$suffix")
                put(file, file.readBytes())
            }
        }
    }

    private fun seedPreferences() {
        preferences.edit().clear()
            .putString(DeviceGridState.KEY_DB_FILE, targetName)
            .putString(DeviceGridState.KEY_WORKSPACE_SIZE, "5,7")
            .putInt(DeviceGridState.KEY_HOTSEAT_COUNT, 5)
            .putBoolean("unrelated", true)
            .commit()
    }

    private fun install(
        operations: BackupLayoutDatabase.InstallOperations,
        sameGrid: Boolean = true,
        finishRestore: () -> Unit = {},
    ) {
        // Follow the public restore ordering, including selection keys from another device.
        BackupSharedPreferences.apply(
            preferences,
            mapOf(
                DeviceGridState.KEY_DB_FILE to "missing-archive-grid.db",
                DeviceGridState.KEY_WORKSPACE_SIZE to "8,9",
                DeviceGridState.KEY_HOTSEAT_COUNT to 8,
                DeviceGridState.KEY_DEVICE_TYPE to 1,
                "unrelated" to true,
            ),
        )
        val archive = File(context.cacheDir, "incoming-layout.db").apply { writeText("validated archive") }
        val target = DeviceGridState(if (sameGrid) 4 else 5, 6, 4, 0, targetName, 0)
        BackupLayoutDatabase.install(context, archive, grid, target, operations, finishRestore)
    }

    private fun assertRestored(files: Map<File, ByteArray>, values: Map<String, *>) {
        files.forEach { (file, bytes) -> assertArrayEquals(file.name, bytes, file.readBytes()) }
        assertEquals(values, preferences.all)
        val xml = File(context.applicationInfo.dataDir, "shared_prefs/${LauncherFiles.SHARED_PREFERENCES_KEY}.xml")
        assertEquals(values, BackupSharedPreferences.read(xml))
        assertTrue(context.getDatabasePath(targetName).parentFile!!.listFiles()!!.none { it.name.startsWith(".layout-install-") })
    }

    @Test
    fun installationRenameFailureRestoresBothGridPathsAndEverySidecar() {
        listOf(true, false).forEach { sameGrid ->
            val files = seedFiles()
            seedPreferences()
            val values = preferences.all
            val operations = object : BackupLayoutDatabase.InstallOperations {
                override fun rename(source: File, destination: File): Boolean =
                    source.name != "incoming.db" && source.renameTo(destination)
            }

            assertThrows(IllegalStateException::class.java) { install(operations, sameGrid) }

            assertRestored(files, values)
        }
    }

    @Test
    fun partialPreservationFailureRestoresFilesAlreadyMoved() {
        val files = seedFiles()
        seedPreferences()
        val values = preferences.all
        val operations = object : BackupLayoutDatabase.InstallOperations {
            override fun rename(source: File, destination: File): Boolean =
                source.name != "$targetName-wal" && source.renameTo(destination)
        }

        assertThrows(IllegalStateException::class.java) { install(operations) }

        assertRestored(files, values)
    }

    @Test
    fun selectionCommitFailureRestoresFilesAndCachedAndPersistedGridKeys() {
        listOf(true, false).forEach { sameGrid ->
            val files = seedFiles()
            seedPreferences()
            val values = preferences.all
            var commits = 0
            val operations = object : BackupLayoutDatabase.InstallOperations {
                override fun commit(editor: SharedPreferences.Editor): Boolean {
                    // commit() may update the cache before reporting a disk failure. Persist too,
                    // so rollback must repair both views, not just leave the old XML in place.
                    val persisted = editor.commit()
                    return ++commits != 1 && persisted
                }
            }

            assertThrows(IllegalStateException::class.java) { install(operations, sameGrid) }

            assertEquals(2, commits)
            assertRestored(files, values)
        }
    }

    @Test
    fun openWalConnectionCanContinueWritingAfterFailedInstallation() {
        val target = context.getDatabasePath(targetName).apply { parentFile!!.mkdirs() }
        seedPreferences()
        SQLiteDatabase.openOrCreateDatabase(target, null).use { live ->
            assertTrue(live.enableWriteAheadLogging())
            live.execSQL("CREATE TABLE items (_id INTEGER PRIMARY KEY, title TEXT)")
            live.execSQL("INSERT INTO items VALUES (1, 'before restore')")
            var commits = 0
            val operations = object : BackupLayoutDatabase.InstallOperations {
                override fun commit(editor: SharedPreferences.Editor): Boolean {
                    val persisted = editor.commit()
                    return ++commits != 1 && persisted
                }
            }

            assertThrows(IllegalStateException::class.java) { install(operations) }

            live.execSQL("INSERT INTO items VALUES (2, 'after rollback')")
            live.rawQuery("SELECT COUNT(*) FROM items", null).use {
                assertTrue(it.moveToFirst())
                assertEquals(2, it.getInt(0))
            }
        }
        SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY).use {
            it.rawQuery("SELECT title FROM items ORDER BY _id", null).use { rows ->
                assertTrue(rows.moveToNext())
                assertEquals("before restore", rows.getString(0))
                assertTrue(rows.moveToNext())
                assertEquals("after rollback", rows.getString(0))
            }
        }
        assertEquals(targetName, preferences.getString(DeviceGridState.KEY_DB_FILE, null))
    }

    @Test
    fun restoreProcessingFailureRestoresBothGridPathsAndEverySidecar() {
        listOf(true, false).forEach { sameGrid ->
            listOf(true, false).forEach { rejectsRestore ->
                val files = seedFiles()
                seedPreferences()
                val values = preferences.all
                val operations = object : BackupLayoutDatabase.InstallOperations {}
                val failure = assertThrows(IllegalStateException::class.java) {
                    install(operations, sameGrid) {
                        val selected = preferences.getString(DeviceGridState.KEY_DB_FILE, null)!!
                        assertEquals(if (sameGrid) targetName else BackupLayoutDatabase.SOURCE_DATABASE, selected)
                        val database = context.getDatabasePath(selected)
                        database.writeText("partially processed layout")
                        listOf("-wal", "-shm", "-journal").forEach { suffix ->
                            File(database.path + suffix).writeText("incoming sidecar")
                        }
                        if (rejectsRestore) {
                            // performRestore catches processing exceptions and returns false.
                            check(false) { "Restore rejected" }
                        }
                        error("Restore threw")
                    }
                }
                assertEquals(if (rejectsRestore) "Restore rejected" else "Restore threw", failure.message)
                assertRestored(files, values)
            }
        }
    }

    @Test
    fun failedProcessingRemovesNewSidecarsAndLeavesOriginalWalConnectionWritable() {
        listOf(true, false).forEach { sameGrid ->
            val target = context.getDatabasePath(targetName).apply { parentFile!!.mkdirs() }
            SQLiteDatabase.deleteDatabase(target)
            seedPreferences()
            val values = preferences.all
            SQLiteDatabase.openOrCreateDatabase(target, null).use { live ->
                assertTrue(live.enableWriteAheadLogging())
                live.execSQL("CREATE TABLE items (_id INTEGER PRIMARY KEY)")
                live.execSQL("INSERT INTO items VALUES (1)")
                val operations = object : BackupLayoutDatabase.InstallOperations {}
                assertThrows(IllegalStateException::class.java) {
                    install(operations, sameGrid) {
                        val selected = context.getDatabasePath(preferences.getString(DeviceGridState.KEY_DB_FILE, null)!!)
                        // Simulate sidecars created by the failed incoming connection.
                        listOf("-wal", "-shm", "-journal").forEach { File(selected.path + it).writeText("incoming") }
                        error("Restore failed")
                    }
                }
                assertFalse(File(target.path + "-journal").exists())
                if (!sameGrid) {
                    val source = context.getDatabasePath(BackupLayoutDatabase.SOURCE_DATABASE)
                    listOf("", "-wal", "-shm", "-journal").forEach { assertFalse(File(source.path + it).exists()) }
                }
                live.execSQL("INSERT INTO items VALUES (2)")
            }
            SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
                database.rawQuery("SELECT COUNT(*) FROM items", null).use {
                    assertTrue(it.moveToFirst())
                    assertEquals(2, it.getInt(0))
                }
            }
            assertEquals(values, preferences.all)
        }
    }

    @Test
    fun successfulProcessingDiscardsRecoveryFilesOnlyAfterItReturns() {
        listOf(true, false).forEach { sameGrid ->
            seedFiles()
            seedPreferences()
            val operations = object : BackupLayoutDatabase.InstallOperations {}
            val directory = context.getDatabasePath(targetName).parentFile!!
            install(operations, sameGrid) {
                val staging = directory.listFiles()!!.single { it.name.startsWith(".layout-install-") }
                assertEquals("previous $targetName", File(staging, "previous-0").readText())
                val selected = preferences.getString(DeviceGridState.KEY_DB_FILE, null)!!
                context.getDatabasePath(selected).writeText("processed layout")
            }
            val selected = preferences.getString(DeviceGridState.KEY_DB_FILE, null)!!
            assertEquals("processed layout", context.getDatabasePath(selected).readText())
            assertTrue(directory.listFiles()!!.none { it.name.startsWith(".layout-install-") })
        }
    }

    @Test
    fun failedRollbackRetainsTheOnlyPreservedDatabaseCopy() {
        val files = seedFiles()
        seedPreferences()
        val operations = object : BackupLayoutDatabase.InstallOperations {
            override fun rename(source: File, destination: File): Boolean =
                source.name != "incoming.db" && destination.name != targetName && source.renameTo(destination)
        }

        val error = assertThrows(IllegalStateException::class.java) { install(operations) }

        assertEquals(1, error.suppressed.size)
        val staging = context.getDatabasePath(targetName).parentFile!!.listFiles()!!.single { it.name.startsWith(".layout-install-") }
        assertArrayEquals(files.getValue(context.getDatabasePath(targetName)), File(staging, "previous-0").readBytes())
        assertFalse(context.getDatabasePath(targetName).exists())
    }
}
