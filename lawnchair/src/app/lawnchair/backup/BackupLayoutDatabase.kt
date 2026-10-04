package app.lawnchair.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.UserManager
import app.lawnchair.LawnchairProto.GridState
import com.android.launcher3.LauncherFiles
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.model.DatabaseHelper
import com.android.launcher3.model.DeviceGridState
import java.io.File

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
        // DatabaseHelper normally recovers failed upgrades by creating an empty database. Reject
        // that recovery for a backup, and avoid its onOpen writes to the live downgrade schema.
        val helper = object : DatabaseHelper(
            context,
            archive.absolutePath,
            { user -> context.getSystemService(UserManager::class.java).getSerialNumberForUser(user) },
            { error("Backup has no launcher layout") },
        ) {
            override fun onCreate(db: SQLiteDatabase) {
                error("Unable to migrate launcher backup")
            }

            override fun onOpen(db: SQLiteDatabase) = Unit
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
    }

    /** Use a separate migration source only when the target's grid actually differs. */
    fun install(context: Context, archive: File, gridState: GridState, target: DeviceGridState) {
        val targetDatabase = target.dbFile
        check(targetDatabase != SOURCE_DATABASE)
        val source = context.getDatabasePath(SOURCE_DATABASE)
        context.deleteDatabase(SOURCE_DATABASE)
        archive.copyTo(source.apply { parentFile?.mkdirs() }, overwrite = true)
        // Finish the potentially failing copy before removing the current layout.
        context.deleteDatabase(targetDatabase)
        context.deleteDatabase(LawnchairBackup.RESTORED_DB_FILE_NAME)
        val archived = DeviceGridState(gridState)
        val sameGrid = archived.columns == target.columns && archived.rows == target.rows &&
            archived.numHotseat == target.numHotseat && archived.deviceType == target.deviceType
        val databaseName = if (sameGrid) {
            check(source.renameTo(context.getDatabasePath(targetDatabase))) { "Unable to install launcher database" }
            targetDatabase
        } else {
            SOURCE_DATABASE
        }
        DeviceGridState(gridState).writeToPrefs(context, true)
        check(
            context.getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
                .edit().putString(DeviceGridState.KEY_DB_FILE, databaseName).commit(),
        ) { "Unable to select restored launcher database" }
    }
}
