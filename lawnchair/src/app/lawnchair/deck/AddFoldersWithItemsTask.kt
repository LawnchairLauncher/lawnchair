package app.lawnchair.deck

import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.util.Pair
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherSettings
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.ModelTaskController
import com.android.launcher3.model.WorkspaceItemSpaceFinder
import com.android.launcher3.model.data.CollectionInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.IntArray
import com.android.launcher3.util.PackageManagerHelper

/**
 * Custom model task to add folders with their items to the workspace.
 * This properly handles adding folders and then adding items to those folders.
 */
class AddFoldersWithItemsTask(
    private val folders: List<FolderInfo>,
    private val onComplete: (() -> Unit)? = null,
) : LauncherModel.ModelUpdateTask {

    private val itemSpaceFinder = WorkspaceItemSpaceFinder()

    override fun execute(
        taskController: ModelTaskController,
        dataModel: BgDataModel,
        apps: AllAppsList,
    ) {
        if (folders.isEmpty()) {
            return
        }

        val context = taskController.app.context
        val addedItemsFinal = ArrayList<ItemInfo>()
        val addedWorkspaceScreensFinal = IntArray()

        synchronized(dataModel) {
            val workspaceScreens = dataModel.collectWorkspaceScreens()
            val modelWriter = taskController.getModelWriter()

            folders.forEach { folderInfo ->
                // Find space for the folder
                val coords = itemSpaceFinder.findSpaceForItem(
                    taskController.app,
                    dataModel,
                    workspaceScreens,
                    addedWorkspaceScreensFinal,
                    folderInfo.spanX,
                    folderInfo.spanY,
                )
                val screenId = coords[0]
                val cellX = coords[1]
                val cellY = coords[2]

                // Add folder to database
                modelWriter.addItemToDatabase(
                    folderInfo,
                    LauncherSettings.Favorites.CONTAINER_DESKTOP,
                    screenId,
                    cellX,
                    cellY,
                )

                // Now add items to the folder
                // Items need to be added with proper rank/position
                folderInfo.getContents().forEachIndexed { index, item ->
                    if (item is WorkspaceItemInfo) {
                        // Check if item already exists on workspace
                        if (shortcutExists(dataModel, item.intent, item.user)) {
                            return@forEachIndexed
                        }

                        // Add item to folder using folder's ID as container
                        // Use rank as position - folder will arrange items
                        modelWriter.addOrMoveItemInDatabase(
                            item,
                            folderInfo.id,
                            0, // screenId is 0 for items in folders
                            index % 4, // cellX - approximate grid position
                            index / 4, // cellY - approximate grid position
                        )
                    }
                }

                addedItemsFinal.add(folderInfo)
            }
        }

        // Schedule callback to bind items
        if (addedItemsFinal.isNotEmpty()) {
            taskController.scheduleCallbackTask { callbacks ->
                val addAnimated = ArrayList<ItemInfo>()
                val addNotAnimated = ArrayList<ItemInfo>()

                if (addedItemsFinal.isNotEmpty()) {
                    val lastScreenId = addedItemsFinal.last().screenId
                    addedItemsFinal.forEach { item ->
                        if (item.screenId == lastScreenId) {
                            addAnimated.add(item)
                        } else {
                            addNotAnimated.add(item)
                        }
                    }
                }

                callbacks.bindAppsAdded(
                    addedWorkspaceScreensFinal,
                    ArrayList(addNotAnimated),
                    ArrayList(addAnimated),
                )

                // Notify completion after items are bound
                onComplete?.invoke()
            }
        } else {
            // No items to add, notify completion immediately
            onComplete?.invoke()
        }
    }

    /**
     * Returns true if the shortcut already exists on the workspace.
     * Based on AddWorkspaceItemsTask.shortcutExists
     */
    private fun shortcutExists(
        dataModel: BgDataModel,
        intent: Intent?,
        user: UserHandle,
    ): Boolean {
        if (intent == null) {
            return true
        }

        val compPkgName: String?
        val intentWithPkg: String
        val intentWithoutPkg: String

        if (intent.component != null) {
            compPkgName = intent.component!!.packageName
            if (intent.`package` != null) {
                intentWithPkg = intent.toUri(0)
                intentWithoutPkg = Intent(intent).apply { `package` = null }.toUri(0)
            } else {
                intentWithPkg = Intent(intent).apply { `package` = compPkgName }.toUri(0)
                intentWithoutPkg = intent.toUri(0)
            }
        } else {
            compPkgName = null
            intentWithPkg = intent.toUri(0)
            intentWithoutPkg = intent.toUri(0)
        }

        val isLauncherAppTarget = PackageManagerHelper.isLauncherAppTarget(intent)

        synchronized(dataModel) {
            dataModel.itemsIdMap.forEach { existingItem ->
                if (existingItem is WorkspaceItemInfo) {
                    val existingIntent = existingItem.intent
                    if (existingIntent != null && existingItem.user == user) {
                        val copyIntent = Intent(existingIntent)
                        copyIntent.sourceBounds = intent.sourceBounds
                        val s = copyIntent.toUri(0)
                        if (intentWithPkg == s || intentWithoutPkg == s) {
                            return true
                        }

                        // Check for existing promise icon with same package name
                        if (isLauncherAppTarget &&
                            existingItem.isPromise() &&
                            existingItem.hasStatusFlag(WorkspaceItemInfo.FLAG_AUTOINSTALL_ICON) &&
                            existingItem.targetComponent != null &&
                            compPkgName != null &&
                            compPkgName == existingItem.targetComponent!!.packageName
                        ) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }
}
