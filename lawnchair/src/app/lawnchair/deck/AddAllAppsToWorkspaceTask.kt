package app.lawnchair.deck

import android.content.Context
import android.content.Intent
import android.os.UserHandle
import app.lawnchair.util.categorizeAppsWithSystemAndGoogle
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherSettings
import com.android.launcher3.R
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.ModelTaskController
import com.android.launcher3.model.WorkspaceItemSpaceFinder
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.IntArray
import com.android.launcher3.util.PackageManagerHelper

/**
 * Model task that populates the workspace with installed apps for the deck layout.
 *
 * Apps already present on the workspace are skipped, so this also serves as a
 * reconcile step after restoring a deck snapshot: it only adds apps that were
 * installed while the deck was disabled. New apps join an existing folder with a
 * matching category name when there is one; otherwise categories with multiple
 * apps become new folders and single-app categories go directly on the workspace.
 *
 * [onComplete] is always invoked exactly once, with `true` if anything was added.
 */
class AddAllAppsToWorkspaceTask(
    private val onProgress: ((String) -> Unit)? = null,
    private val onComplete: ((changed: Boolean) -> Unit)? = null,
) : LauncherModel.ModelUpdateTask {

    override fun execute(
        taskController: ModelTaskController,
        dataModel: BgDataModel,
        apps: AllAppsList,
    ) {
        var changed = false
        try {
            changed = addAllApps(taskController, dataModel, apps)
        } finally {
            onComplete?.invoke(changed)
        }
    }

    private fun addAllApps(
        taskController: ModelTaskController,
        dataModel: BgDataModel,
        apps: AllAppsList,
    ): Boolean {
        val context = taskController.context

        onProgress?.invoke(context.getString(R.string.deck_categorizing_apps))
        val categorizedApps = categorizeAppsWithSystemAndGoogle(apps.data.toList(), context)
        if (categorizedApps.isEmpty()) return false

        onProgress?.invoke(context.getString(R.string.deck_adding_apps))

        val idp = InvariantDeviceProfile.INSTANCE.get(context)
        val model = LauncherAppState.getInstance(context).model
        val itemSpaceFinder = WorkspaceItemSpaceFinder(dataModel, idp, model)

        val addedItems = ArrayList<ItemInfo>()
        val addedScreens = IntArray()
        var modifiedExistingFolders = false

        synchronized(dataModel) {
            val workspaceScreens = dataModel.itemsIdMap.collectWorkspaceScreens(context)
            val modelWriter = taskController.getModelWriter()

            categorizedApps.forEach { (category, categoryApps) ->
                // Skip apps that are already on the workspace or inside a folder
                val newItems = categoryApps
                    .mapNotNull { it.makeWorkspaceItem(context) }
                    .filterNot { shortcutExists(dataModel, it.intent, it.user) }
                if (newItems.isEmpty()) return@forEach

                val existingFolder = findFolderByCategory(dataModel, category)
                when {
                    existingFolder != null -> {
                        // Append to the folder this category already has
                        var rank = existingFolder.getContents().size
                        newItems.forEach { item ->
                            item.rank = rank
                            existingFolder.add(item)
                            modelWriter.addOrMoveItemInDatabase(
                                item,
                                existingFolder.id,
                                0,
                                rank % 4,
                                rank / 4,
                            )
                            rank++
                        }
                        modifiedExistingFolders = true
                    }

                    newItems.size == 1 -> {
                        val item = newItems.first()
                        val coords = itemSpaceFinder.findSpaceForItem(
                            workspaceScreens,
                            addedScreens,
                            addedItems,
                            item.spanX,
                            item.spanY,
                            context,
                        )
                        modelWriter.addItemToDatabase(
                            item,
                            LauncherSettings.Favorites.CONTAINER_DESKTOP,
                            coords[0],
                            coords[1],
                            coords[2],
                        )
                        addedItems.add(item)
                    }

                    else -> {
                        val folderInfo = FolderInfo().apply { title = category }
                        newItems.forEach(folderInfo::add)

                        val coords = itemSpaceFinder.findSpaceForItem(
                            workspaceScreens,
                            addedScreens,
                            addedItems,
                            folderInfo.spanX,
                            folderInfo.spanY,
                            context,
                        )
                        modelWriter.addItemToDatabase(
                            folderInfo,
                            LauncherSettings.Favorites.CONTAINER_DESKTOP,
                            coords[0],
                            coords[1],
                            coords[2],
                        )

                        // Folder items are ordered by rank; cellX/cellY are secondary
                        newItems.forEachIndexed { rank, item ->
                            item.rank = rank
                            modelWriter.addItemToDatabase(
                                item,
                                folderInfo.id,
                                0,
                                rank % 4,
                                rank / 4,
                            )
                        }
                        addedItems.add(folderInfo)
                    }
                }
            }
        }

        // The caller triggers a model reload when anything changed, which rebinds both
        // the new top-level items and the contents of folders that were appended to
        return addedItems.isNotEmpty() || modifiedExistingFolders
    }
}

/** Returns the workspace folder whose title matches the given category, if any. */
internal fun findFolderByCategory(dataModel: BgDataModel, category: String): FolderInfo? {
    synchronized(dataModel) {
        dataModel.itemsIdMap.forEach { item ->
            if (item is FolderInfo && item.title?.toString() == category) {
                return item
            }
        }
    }
    return null
}

/**
 * Returns true if the shortcut already exists on the workspace (or inside a folder).
 * Based on AddWorkspaceItemsTask.shortcutExists
 */
internal fun shortcutExists(
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
                if (existingItem.user == user) {
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
