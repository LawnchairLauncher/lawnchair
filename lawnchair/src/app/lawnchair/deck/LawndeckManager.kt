package app.lawnchair.deck

import android.content.Context
import android.util.Log
import app.lawnchair.LawnchairLauncher
import app.lawnchair.flowerpot.Flowerpot
import app.lawnchair.launcher
import app.lawnchair.launcherNullable
import app.lawnchair.util.restartLauncher
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings
import com.android.launcher3.model.ItemInstallQueue
import com.android.launcher3.model.ModelDbController
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.provider.RestoreDbTask
import com.android.launcher3.util.ComponentKey
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LawndeckManager(private val context: Context) {

    // TODO

    private val launcher = context.launcherNullable ?: LawnchairLauncher.instance?.launcher

    suspend fun enableLawndeck(
        onProgress: ((String) -> Unit)? = null,
    ) = withContext(Dispatchers.IO) {
        val completionDeferred = CompletableDeferred<Unit>()

        if (!backupExists("bk")) createBackup("bk")
        if (backupExists("lawndeck")) {
            onProgress?.invoke("Restoring previous layout...")
            restoreBackup("lawndeck")
            completionDeferred.complete(Unit)
        } else {
            onProgress?.invoke("Categorizing apps...")
            addAllAppsToWorkspace(onProgress) {
                completionDeferred.complete(Unit)
            }
        }

        completionDeferred.await()
    }

    suspend fun disableLawndeck() = withContext(Dispatchers.IO) {
        if (backupExists("bk")) {
            createBackup("lawndeck")
            restoreBackup("bk")
        }
    }

    private fun createBackup(suffix: String) = runCatching {
        getDatabaseFiles(suffix).apply {
            db.copyTo(backupDb, overwrite = true)
            if (journal.exists()) journal.copyTo(backupJournal, overwrite = true)
        }
    }.onFailure { Log.e("LawndeckManager", "Failed to create backup: $suffix", it) }

    private fun restoreBackup(suffix: String) = runCatching {
        getDatabaseFiles(suffix).apply {
            backupDb.copyTo(db, overwrite = true)
            if (backupJournal.exists()) backupJournal.copyTo(journal, overwrite = true)
        }
        postRestoreActions()
    }.onFailure { Log.e("LawndeckManager", "Failed to restore backup: $suffix", it) }

    private fun getDatabaseFiles(suffix: String): DatabaseFiles {
        val idp = InvariantDeviceProfile.INSTANCE.get(context)
        val dbFile = context.getDatabasePath(idp.dbFile)
        return DatabaseFiles(
            db = dbFile,
            backupDb = File(dbFile.parent, "${suffix}_${idp.dbFile}"),
            journal = File(dbFile.parent, "${idp.dbFile}-journal"),
            backupJournal = File(dbFile.parent, "${suffix}_${idp.dbFile}-journal"),
        )
    }

    private fun backupExists(suffix: String): Boolean = getDatabaseFiles(suffix).backupDb.exists()

    private fun postRestoreActions() {
        ModelDbController(context).let { RestoreDbTask.performRestore(context, it) }
        restartLauncher(context)
    }

    private fun addAllAppsToWorkspace(
        onProgress: ((String) -> Unit)?,
        onComplete: (() -> Unit)?,
    ) {
        val apps = launcher?.mAppsView?.appsStore?.apps ?: return
        if (apps.isEmpty()) {
            onComplete?.invoke()
            return
        }

        onProgress?.invoke("Categorizing apps...")

        // Use flowerpot to categorize apps
        val potsManager = Flowerpot.Manager.getInstance(context)
        val categorizedApps = potsManager.categorizeApps(apps.map { it as? AppInfo })

        onProgress?.invoke("Adding apps to workspace...")

        val launcher = this.launcher ?: return
        val model = launcher.model

        // Collect folders to add and count single apps
        val foldersToAdd = mutableListOf<FolderInfo>()
        var singleAppCount = 0

        // Process each category
        categorizedApps.forEach { (category, categoryApps) ->
            if (categoryApps.isEmpty()) return@forEach

            if (categoryApps.size == 1) {
                // Single app - add directly to workspace
                val app = categoryApps.first()
                ItemInstallQueue.INSTANCE.get(context).queueItem(app.targetPackage, app.user)
                singleAppCount++
            } else {
                // Multiple apps - create folder
                onProgress?.invoke("Creating folder: $category...")
                val folderInfo = createFolderInfo(category, categoryApps)
                if (folderInfo != null) {
                    foldersToAdd.add(folderInfo)
                }
            }
        }

        // Add all folders with their items to workspace using custom task
        if (foldersToAdd.isNotEmpty()) {
            // Wait for folder task to complete
            model.enqueueModelUpdateTask(
                AddFoldersWithItemsTask(foldersToAdd) {
                    // Callback runs on UI thread from model task
                    // Also wait for ItemInstallQueue to finish for single apps
                    // ItemInstallQueue processes asynchronously, so we need to wait a bit
                    if (singleAppCount > 0) {
                        // Post to handler to give ItemInstallQueue time to process
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            onComplete?.invoke()
                        }, 800) // Wait for queue to process
                    } else {
                        onComplete?.invoke()
                    }
                },
            )
        } else {
            // No folders, but may have single apps
            if (singleAppCount > 0) {
                // Give ItemInstallQueue time to process
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    onComplete?.invoke()
                }, 800) // Wait for queue to process
            } else {
                onComplete?.invoke()
            }
        }
    }

    /**
     * Adds a newly installed app to the workspace with proper categorization.
     * This is called when a new app is installed and deck layout is enabled.
     *
     * @param packageName The package name of the newly installed app
     * @param user The user handle for the app
     * @param modelWriter The ModelWriter to use for database operations (must be called from model thread)
     * @param dataModel The BgDataModel to search for existing folders
     */
    fun addNewlyInstalledApp(
        packageName: String,
        user: android.os.UserHandle,
        modelWriter: com.android.launcher3.model.ModelWriter,
        dataModel: com.android.launcher3.model.BgDataModel,
    ) {
        // Get app info from LauncherApps directly (app might not be in all apps list yet)
        val launcherApps = context.getSystemService(android.content.pm.LauncherApps::class.java)
            ?: return
        val activities = launcherApps.getActivityList(packageName, user)
        if (activities.isEmpty()) return

        val activityInfo = activities[0]
        val appInfo = AppInfo(context, activityInfo, user)

        // Use flowerpot to categorize the app
        val potsManager = Flowerpot.Manager.getInstance(context)
        val categorizedApps = potsManager.categorizeApps(listOf(appInfo))

        if (categorizedApps.isEmpty()) {
            // No category found, add directly to workspace
            ItemInstallQueue.INSTANCE.get(context).queueItem(packageName, user)
            return
        }

        // Get the category for this app (categorizedApps is a Map<String, List<AppInfo>>)
        val categoryEntry = categorizedApps.entries.firstOrNull() ?: run {
            ItemInstallQueue.INSTANCE.get(context).queueItem(packageName, user)
            return
        }
        val category = categoryEntry.key

        // Check if there's already a folder for this category on workspace
        val existingFolder = findFolderByCategory(category, dataModel)

        if (existingFolder != null) {
            // Add app to existing folder
            val workspaceItem = appInfo.makeWorkspaceItem(context) ?: return
            existingFolder.add(workspaceItem)
            // Update folder in database
            modelWriter.addOrMoveItemInDatabase(
                workspaceItem,
                existingFolder.id,
                0,
                existingFolder.getContents().size % 4,
                existingFolder.getContents().size / 4,
            )
        } else {
            // Single app in category, add directly to workspace
            // The app will be categorized properly when added
            ItemInstallQueue.INSTANCE.get(context).queueItem(packageName, user)
        }
    }

    private fun findFolderByCategory(category: String, dataModel: com.android.launcher3.model.BgDataModel): FolderInfo? {
        // Search through workspace items to find folder with matching category name
        synchronized(dataModel) {
            dataModel.itemsIdMap.forEach { item ->
                if (item is FolderInfo && item.title?.toString() == category) {
                    return item
                }
            }
        }
        return null
    }

    private fun createFolderInfo(categoryName: String, apps: List<AppInfo>): FolderInfo? {
        if (apps.isEmpty()) return null

        val folderInfo = FolderInfo().apply {
            title = categoryName
        }

        // Create workspace items for each app and add to folder
        apps.forEach { app ->
            val workspaceItem = app.makeWorkspaceItem(context) ?: return@forEach
            folderInfo.add(workspaceItem)
        }

        // Only return folder if it has items
        return if (folderInfo.getContents().isNotEmpty()) folderInfo else null
    }

    private data class DatabaseFiles(
        val db: File,
        val backupDb: File,
        val journal: File,
        val backupJournal: File,
    )
}
