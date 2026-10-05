package app.lawnchair.backup

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.os.UserManager
import app.lawnchair.LawnchairProto.GridState
import com.android.launcher3.LauncherFiles
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.model.DatabaseHelper
import com.android.launcher3.model.DeviceGridState
import java.io.File
import kotlin.io.path.createTempDirectory

/** Validate and migrate only the extracted copy, before changing any live launcher state. */
internal object BackupLayoutDatabase {
    const val SOURCE_DATABASE = "backup_restore.db"

    fun validate(context: Context, archive: File, gridState: GridState) {
        val dimensions = gridState.gridSize.split(',').map { it.toIntOrNull() }
        require(dimensions.size == 2 && dimensions.all { it != null && it > 0 } && gridState.hotseatCount > 0) {
            "Invalid backup grid"
        }
        SQLiteDatabase.openDatabase(archive.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            check(db.isDatabaseIntegrityOk) { "Invalid launcher database" }
            check(db.version in 12..DatabaseHelper.SCHEMA_VERSION) { "Unsupported launcher database version" }
            db.rawQuery("SELECT _id FROM favorites LIMIT 0", null).use { }
        }
        // Discard the trial upgrade. The installed original must migrate with the restored
        // preferences, and only that real upgrade may pin legacy Android shortcuts.
        val validationFile = File.createTempFile("layout-validation-", ".db", archive.parentFile)
        try {
            archive.copyTo(validationFile, overwrite = true)
            // DatabaseHelper normally recovers failed upgrades by creating an empty database. Reject
            // that recovery for a backup, and avoid its onOpen writes to the live downgrade schema.
            val helper = object : DatabaseHelper(
                context,
                validationFile.absolutePath,
                { user -> context.getSystemService(UserManager::class.java).getSerialNumberForUser(user) },
                { error("Backup has no launcher layout") },
            ) {
                override fun onCreate(db: SQLiteDatabase) {
                    error("Unable to migrate launcher backup")
                }

                override fun onOpen(db: SQLiteDatabase) = Unit
                override fun shouldMigrateWorkspaceItems() = false
            }
            try {
                val db = helper.writableDatabase
                db.rawQuery("SELECT ${Favorites.getColumns(0)} FROM favorites LIMIT 0", null).use { }
                db.rawQuery("PRAGMA table_info(favorites)", null).use { columns ->
                    var hasProfileDefault = false
                    var hasPrimaryKey = false
                    while (columns.moveToNext()) {
                        val name = columns.getString(columns.getColumnIndexOrThrow("name"))
                        if (name == Favorites._ID) {
                            hasPrimaryKey = columns.getInt(columns.getColumnIndexOrThrow("pk")) == 1 &&
                                columns.getString(columns.getColumnIndexOrThrow("type")).equals("INTEGER", ignoreCase = true)
                        }
                        if (name == Favorites.PROFILE_ID) {
                            hasProfileDefault = columns.getString(columns.getColumnIndexOrThrow("dflt_value"))?.toLongOrNull() != null
                        }
                    }
                    check(hasPrimaryKey && hasProfileDefault) { "Invalid launcher identity columns" }
                }
                check(db.isDatabaseIntegrityOk) { "Invalid launcher database" }
            } finally {
                helper.close()
            }
        } finally {
            SQLiteDatabase.deleteDatabase(validationFile)
        }
    }

    internal interface InstallOperations {
        fun rename(source: File, destination: File): Boolean = source.renameTo(destination)
        fun commit(editor: SharedPreferences.Editor): Boolean = editor.commit()
    }

    private val installOperations = object : InstallOperations {}

    /** Call on the model executor: rollback must finish before an existing DB handle writes again. */
    fun install(
        context: Context,
        archive: File,
        gridState: GridState,
        target: DeviceGridState,
        operations: InstallOperations = installOperations,
    ) {
        val targetDatabase = target.dbFile
        check(targetDatabase != SOURCE_DATABASE)
        val targetFile = context.getDatabasePath(targetDatabase)
        val directory = requireNotNull(targetFile.parentFile).apply { mkdirs() }
        val staging = createTempDirectory(directory.toPath(), ".layout-install-").toFile()
        val incoming = File(staging, "incoming.db")
        val preferences = context.getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
        val keys = listOf(DeviceGridState.KEY_DB_FILE, DeviceGridState.KEY_WORKSPACE_SIZE, DeviceGridState.KEY_HOTSEAT_COUNT, DeviceGridState.KEY_DEVICE_TYPE)
        val previousPreferences = preferences.all.filterKeys { it in keys }
        val archived = DeviceGridState(gridState)
        val sameGrid = archived.columns == target.columns && archived.rows == target.rows &&
            archived.numHotseat == target.numHotseat && archived.deviceType == target.deviceType
        val databaseName = if (sameGrid) targetDatabase else SOURCE_DATABASE
        val destination = context.getDatabasePath(databaseName)
        val preserved = mutableListOf<Pair<File, File>>()
        var installed = false
        var preferencesChanged = false
        var canCleanUp = true
        try {
            // Complete the copy before moving any live database or its sidecars.
            archive.copyTo(incoming)
            listOf(targetDatabase, SOURCE_DATABASE, LawnchairBackup.RESTORED_DB_FILE_NAME).distinct().forEach { name ->
                val database = context.getDatabasePath(name)
                listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
                    val original = File(database.path + suffix)
                    if (original.exists()) {
                        val saved = File(staging, "previous-${preserved.size}")
                        check(operations.rename(original, saved)) { "Unable to preserve launcher database: $original" }
                        preserved += original to saved
                    }
                }
            }
            check(operations.rename(incoming, destination)) { "Unable to install launcher database" }
            installed = true
            // One checked commit selects both the database and the grid that describes it.
            preferencesChanged = true
            check(
                operations.commit(
                    preferences.edit()
                        .putString(DeviceGridState.KEY_DB_FILE, databaseName)
                        .putString(DeviceGridState.KEY_WORKSPACE_SIZE, gridState.gridSize)
                        .putInt(DeviceGridState.KEY_HOTSEAT_COUNT, gridState.hotseatCount)
                        .putInt(DeviceGridState.KEY_DEVICE_TYPE, gridState.deviceType),
                ),
            ) { "Unable to select restored launcher database" }
        } catch (failure: Throwable) {
            fun rollback(action: () -> Unit) {
                try {
                    action()
                } catch (rollbackFailure: Throwable) {
                    canCleanUp = false
                    failure.addSuppressed(rollbackFailure)
                }
            }
            if (installed) {
                rollback {
                    check(destination.delete()) { "Unable to remove failed launcher installation: $destination" }
                }
            }
            preserved.asReversed().forEach { (original, saved) ->
                rollback {
                    check(operations.rename(saved, original)) { "Unable to recover launcher database from $saved" }
                }
            }
            if (preferencesChanged) {
                rollback {
                    // A failed SharedPreferences commit can still change its in-memory map.
                    val editor = preferences.edit()
                    keys.forEach { editor.remove(it) }
                    previousPreferences.forEach { (key, value) ->
                        when (value) {
                            is String -> editor.putString(key, value)
                            is Int -> editor.putInt(key, value)
                        }
                    }
                    check(operations.commit(editor)) { "Unable to recover launcher database selection" }
                }
            }
            throw failure
        } finally {
            // Keep recovery files if rollback itself failed; never discard the only old copy.
            if (canCleanUp) staging.deleteRecursively()
        }
    }
}
