package app.lawnchair.deck

import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.util.Log
import app.lawnchair.flowerpot.Flowerpot
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.DeviceGridState
import com.android.launcher3.model.ItemInstallQueue
import com.android.launcher3.model.ModelDbController
import com.android.launcher3.model.ModelWriter
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.provider.RestoreDbTask
import com.android.launcher3.util.ApplicationInfoWrapper
import java.io.File
import java.util.Properties
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages the deck (drawerless) layout: switching between the regular and deck
 * workspace databases, populating the deck with categorized apps, and keeping
 * newly installed apps categorized while the deck is active.
 */
class LawndeckManager(private val context: Context) {

    suspend fun enableLawndeck(
        onProgress: ((String) -> Unit)? = null,
    ) = withContext(Dispatchers.IO) {
        // Always refresh the regular-layout snapshot so disabling later restores the
        // layout the user had just now, not the one from the first time deck was enabled
        createBackup(BACKUP_DEFAULT)
        if (backupExists(BACKUP_LAWNDECK)) {
            onProgress?.invoke(context.getString(R.string.deck_restoring_layout))
            restoreBackup(BACKUP_LAWNDECK)
        }
        // Populate the deck. On first enable this adds every app; after restoring a
        // snapshot it adds the apps that were installed while the deck was disabled
        // (apps already on the workspace are skipped)
        addAllAppsToWorkspace(onProgress)
    }

    suspend fun disableLawndeck() = withContext(Dispatchers.IO) {
        if (backupExists(BACKUP_DEFAULT)) {
            createBackup(BACKUP_LAWNDECK)
            restoreBackup(BACKUP_DEFAULT)
        }
    }

    private suspend fun addAllAppsToWorkspace(onProgress: ((String) -> Unit)?) {
        val model = LauncherAppState.getInstance(context).model
        val changed = CompletableDeferred<Boolean>()
        // Make sure the model is loaded first (it is unloaded right after a restore):
        // enqueueModelUpdateTask silently drops tasks while the model is not loaded,
        // which would leave the deferred hanging forever
        model.loadAsync { dataModel ->
            if (dataModel == null) {
                Log.w(TAG, "Cannot populate deck: launcher model failed to load")
                changed.complete(false)
            } else {
                model.enqueueModelUpdateTask(
                    AddAllAppsToWorkspaceTask(onProgress) { changed.complete(it) },
                )
            }
        }
        if (changed.await()) {
            withContext(Dispatchers.Main) { model.forceReload() }
        }
    }

    /**
     * Snapshots every grid database (the user may have several from grid-size changes)
     * along with the current [DeviceGridState], so a later restore knows which grid
     * this snapshot belongs to.
     */
    private fun createBackup(suffix: String) = runCatching {
        val dir = databasesDir()
        deleteBackup(suffix)
        gridDbNames().forEach { name ->
            File(dir, name).copyTo(File(dir, "${suffix}_$name"), overwrite = true)
            val journal = File(dir, "$name-journal")
            if (journal.exists()) journal.copyTo(File(dir, "${suffix}_$name-journal"), overwrite = true)
        }
        writeGridState(suffix)
    }.onFailure { Log.e(TAG, "Failed to create backup: $suffix", it) }

    private suspend fun restoreBackup(suffix: String) = runCatching {
        val dir = databasesDir()
        val prefix = "${suffix}_"
        val backups = dir.listFiles().orEmpty()
            .filter { it.name.startsWith(prefix) && GRID_DB_PATTERN.matches(it.name.removePrefix(prefix)) }
        check(backups.isNotEmpty()) { "No backup found for suffix: $suffix" }

        val restoredNames = backups.map { backup ->
            val name = backup.name.removePrefix(prefix)
            // Delete stale SQLite side files first: a leftover journal could roll back
            // (and corrupt) the freshly restored database
            File(dir, "$name-journal").delete()
            File(dir, "$name-wal").delete()
            File(dir, "$name-shm").delete()
            backup.copyTo(File(dir, name), overwrite = true)
            val backupJournal = File(dir, "$prefix$name-journal")
            if (backupJournal.exists()) backupJournal.copyTo(File(dir, "$name-journal"), overwrite = true)
            name
        }

        val recordedState = readGridState(suffix)
        if (recordedState != null) {
            // Grid DBs created after this snapshot belong to the other layout mode;
            // remove them so launcher can't pick up a stale layout from them
            gridDbNames().filterNot { it in restoredNames }.forEach { context.deleteDatabase(it) }

            val idp = InvariantDeviceProfile.INSTANCE.get(context)
            if (recordedState.dbFile != idp.dbFile && recordedState.dbFile in restoredNames) {
                // The grid changed while this snapshot was inactive. Point the migration
                // source prefs at the restored grid so launcher migrates it into the
                // current grid natively on the next workspace load.
                recordedState.writeToPrefs(context)
            }
        }

        RestoreDbTask.performRestore(context, ModelDbController(context))
        withContext(Dispatchers.Main) {
            LauncherAppState.getInstance(context).model.forceReload()
        }
    }.onFailure { Log.e(TAG, "Failed to restore backup: $suffix", it) }

    private fun backupExists(suffix: String): Boolean {
        val dbName = readGridState(suffix)?.dbFile
            ?: InvariantDeviceProfile.INSTANCE.get(context).dbFile
        return File(databasesDir(), "${suffix}_$dbName").exists()
    }

    private fun deleteBackup(suffix: String) {
        databasesDir().listFiles().orEmpty()
            .filter { it.name.startsWith("${suffix}_launcher") }
            .forEach { it.delete() }
        gridStateFile(suffix).delete()
    }

    private fun databasesDir(): File = context.getDatabasePath(InvariantDeviceProfile.INSTANCE.get(context).dbFile).parentFile!!

    /** Grid databases present on disk, covering both AOSP and Lawnchair naming schemes. */
    private fun gridDbNames(): List<String> = databasesDir().listFiles().orEmpty()
        .map { it.name }
        .filter { GRID_DB_PATTERN.matches(it) }

    private fun writeGridState(suffix: String) {
        val state = DeviceGridState(InvariantDeviceProfile.INSTANCE.get(context))
        val props = Properties().apply {
            setProperty(KEY_COLUMNS, state.columns.toString())
            setProperty(KEY_ROWS, state.rows.toString())
            setProperty(KEY_HOTSEAT, state.numHotseat.toString())
            setProperty(KEY_DEVICE_TYPE, state.deviceType.toString())
            setProperty(KEY_DB_FILE, state.dbFile)
            setProperty(KEY_GRID_TYPE, state.gridType.toString())
        }
        gridStateFile(suffix).outputStream().use { props.store(it, null) }
    }

    private fun readGridState(suffix: String): DeviceGridState? {
        val file = gridStateFile(suffix)
        if (!file.exists()) return null
        return runCatching {
            val props = Properties().apply { file.inputStream().use(::load) }
            DeviceGridState(
                props.getProperty(KEY_COLUMNS).toInt(),
                props.getProperty(KEY_ROWS).toInt(),
                props.getProperty(KEY_HOTSEAT).toInt(),
                props.getProperty(KEY_DEVICE_TYPE).toInt(),
                props.getProperty(KEY_DB_FILE),
                props.getProperty(KEY_GRID_TYPE).toInt(),
            )
        }.onFailure { Log.e(TAG, "Failed to read grid state for backup: $suffix", it) }.getOrNull()
    }

    private fun gridStateFile(suffix: String): File = File(databasesDir(), "${suffix}_grid_state.properties")

    /**
     * Adds a newly installed app to the workspace with proper categorization.
     * This is called from the model thread when a new app is installed and the
     * deck layout is enabled.
     *
     * @param packageName The package name of the newly installed app
     * @param user The user handle for the app
     * @param modelWriter The ModelWriter to use for database operations
     * @param dataModel The BgDataModel to search for existing folders
     */
    fun addNewlyInstalledApp(
        packageName: String,
        user: UserHandle,
        modelWriter: ModelWriter,
        dataModel: BgDataModel,
    ) {
        // Get app info from LauncherApps directly (app might not be in all apps list yet)
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return
        val activityInfo = launcherApps.getActivityList(packageName, user).firstOrNull() ?: return
        val appInfo = AppInfo(context, activityInfo, user)

        val workspaceItem = appInfo.makeWorkspaceItem(context) ?: return
        if (shortcutExists(dataModel, workspaceItem.intent, user)) return

        val category = resolveCategory(appInfo, packageName)
        val existingFolder = category?.let { findFolderByCategory(dataModel, it) }

        if (existingFolder != null) {
            synchronized(dataModel) {
                // Folder items are ordered by rank; the new item goes last
                val rank = existingFolder.getContents().size
                workspaceItem.rank = rank
                existingFolder.add(workspaceItem)
                modelWriter.addOrMoveItemInDatabase(
                    workspaceItem,
                    existingFolder.id,
                    0,
                    rank % 4,
                    rank / 4,
                )
            }
        } else {
            // No matching folder, add directly to the workspace
            ItemInstallQueue.INSTANCE.get(context).queueItem(packageName, user)
        }
    }

    /** Determines the deck category: Google Apps > System Apps > Flowerpot categories. */
    private fun resolveCategory(appInfo: AppInfo, packageName: String): String? {
        val intent = appInfo.intent
        return when {
            packageName.startsWith("com.google.") -> "Google Apps"

            intent != null && ApplicationInfoWrapper(context, intent).isSystem() -> "System Apps"

            else -> Flowerpot.Manager.getInstance(context)
                .categorizeApps(listOf(appInfo))
                .keys
                .firstOrNull()
        }
    }

    companion object {
        private const val TAG = "LawndeckManager"
        private const val BACKUP_DEFAULT = "bk"
        private const val BACKUP_LAWNDECK = "lawndeck"

        private const val KEY_COLUMNS = "columns"
        private const val KEY_ROWS = "rows"
        private const val KEY_HOTSEAT = "hotseat"
        private const val KEY_DEVICE_TYPE = "deviceType"
        private const val KEY_DB_FILE = "dbFile"
        private const val KEY_GRID_TYPE = "gridType"

        /**
         * Matches grid db files in both naming schemes:
         * AOSP ("launcher.db", "launcher_4_by_5.db") and
         * Lawnchair ("launcher_<rows>_<columns>_<hotseat>.db")
         */
        private val GRID_DB_PATTERN = Regex("""launcher(_\d+_by_\d+|_\d+_\d+_\d+)?\.db""")
    }
}
