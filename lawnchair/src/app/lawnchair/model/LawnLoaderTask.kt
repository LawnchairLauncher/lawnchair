@file:Suppress("NAME_SHADOWING")

package app.lawnchair.model

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.database.sqlite.SQLiteDatabase
import android.os.UserHandle
import androidx.annotation.Keep
import app.lawnchair.flowerpot.Flowerpot
import app.lawnchair.launcherNullable
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.util.requireSystemService
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.ItemInstallQueue
import com.android.launcher3.model.ItemInstallQueue.PendingInstallShortcutInfo
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.provider.LauncherDbUtils
import com.android.launcher3.util.PackageManagerHelper
import com.patrykmichalik.opto.core.firstBlocking
import com.patrykmichalik.opto.core.onEach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.util.stream.Collectors


@Keep
object LawnLoaderTask {
    private var instance: LawnLoaderTask? = null

    private var isHomeLayout: Boolean = false
    private var isHomeLayoutSet: Boolean = false
    private var launcher: Launcher? = null
    val coroutineScope = CoroutineScope(context = Dispatchers.IO)

    @JvmStatic
    fun get(context: Context): LawnLoaderTask {
        if (instance == null) {
            synchronized(LawnLoaderTask::class.java) {
                if (instance == null) {
                    instance = LawnLoaderTask
                    val pref2 = PreferenceManager2.getInstance(context)
                    isHomeLayout = pref2.isHomeLayoutOnly.firstBlocking()
                    pref2.isHomeLayoutSet.onEach(launchIn = coroutineScope) {
                        isHomeLayoutSet = isHomeLayout
                    }
                    launcher = context.launcherNullable
                }
            }
        }
        return instance!!
    }

    @Synchronized
    fun loadWorkspace(mApp: LauncherAppState, mBgAllAppsList: AllAppsList, mBgDataModel: BgDataModel) {
        if (isHomeLayoutSet) return
        val dbController = mApp.model.modelDbController
        LauncherDbUtils.addWorkspacesTable(dbController.db, true)
        val queItem = ItemInstallQueue.INSTANCE.get(mApp.context)

        queItem.ensureQueueLoaded()
        val appsList = mBgAllAppsList.data

        val screenIds = mBgDataModel.collectWorkspaceScreens()

        var rank = 0
        var id = 100
        var idContainer = 100

        val systemApps: ArrayList<AppInfo> = ArrayList()
        val apps: ArrayList<AppInfo> = ArrayList()
        val categorized = HashMap<String, java.util.ArrayList<AppInfo>>()

        for (app in appsList) {
            if (PackageManagerHelper.isSystemApp(mApp.context, app.intent)) {
                systemApps.add(app)
            } else {
                apps.add(app)
            }
            rank++
            id++
            idContainer++
        }

        // Add system folders
        val systemFolder = mBgDataModel.findOrMakeFolder(id)
        val title = "System Apps"
        systemFolder.id = title.hashCode() + id
        systemFolder.title = title
        systemFolder.rank = rank++
        systemFolder.container = idContainer

        for (sysApp in systemApps) {
            val itemInfo = WorkspaceItemInfo(sysApp)
            itemInfo.container = systemFolder.container
            systemFolder.contents.add(itemInfo)
        }

        try {
            val pots = Flowerpot.Manager.getInstance(mApp.context).getAllPots()

            for (pot in pots) {
                pot.ensureLoaded()
                val potName = pot.displayName
                val potApps = pot.apps.matches
                var appsByCategory: java.util.ArrayList<AppInfo>? = categorized[potName]
                if (appsByCategory == null) {
                    appsByCategory = java.util.ArrayList()
                }
                for (app in apps) {
                    if (potApps.contains(app.getComponentKey())) {
                        appsByCategory.add(app)
                    }
                }
                apps.removeAll(appsByCategory) // remove apps with category
                categorized[potName] = appsByCategory
            }
        } catch (e: Exception) {
            // Ignore
        }

        // Add the screens specified by the items above
        for (name in categorized.keys) {
            val appsByCategory = categorized[name] ?: continue
            val folder = mBgDataModel.findOrMakeFolder(id+1)
            if (appsByCategory.size == 1) { continue
            } else if (appsByCategory.size > 1) {
                for (app in appsList) {
                    if (appsByCategory.contains(app)) {
                        folder.id = name.hashCode() + id
                        folder.title = name
                        folder.rank = rank
                        val workspaceItemInfo = WorkspaceItemInfo(app)
                        folder.container = idContainer++
                        workspaceItemInfo.container = folder.container
                        folder.contents.add(workspaceItemInfo)
                    }
                    rank++
                }
                id++
            }
        }

        screenIds.sorted()

        // Folder
        val folders = mBgDataModel.folders
        for (folder in folders) {
            val isExists = dbController.isExistFolders(folder.title.toString(), false) && shortcutExists(mBgDataModel, folder.intent, folder.user)
            if (isExists) continue
            queItem.queueItem(folder, folder.user)
        }

        // Apps with no categories
        for (app in apps) {
            queItem.queueItem(app.targetPackage, app.user)
        }

        val installQueue = queItem.items.stream()
            .map { info: PendingInstallShortcutInfo ->
                info.getItemInfo(
                    mApp.context
                )
            }
            .collect(Collectors.toList<android.util.Pair<ItemInfo, Any>>())
        mApp.model.addAndBindAddedWorkspaceItems(installQueue)
    }


    fun dbInsertAndCheck(db: SQLiteDatabase, table: String, values: ContentValues?): Int {
        if (values == null) {
            throw RuntimeException("Error: attempting to insert null values")
        }
        if (!values.containsKey(Favorites._ID)) {
            throw RuntimeException("Error: attempting to add item without specifying an id")
        }
        return db.insert(table, null, values).toInt()
    }

    fun addItemWorkSpace(items: ArrayList<WorkspaceItemInfo>, context: Context) {
        val queItem = ItemInstallQueue.INSTANCE.get(context)
        queItem.ensureQueueLoaded()
        for (item in items) {
            val launcherApps: LauncherApps = context.requireSystemService()
            val activity = launcherApps.resolveActivity(item.intent, item.user)
            queItem.queueItem(activity.applicationInfo.packageName, activity.user)
        }
    }

    private fun shortcutExists(
        dataModel: BgDataModel,
        intent: Intent?,
        user: UserHandle,
    ): Boolean {
        val compPkgName: String?
        val intentWithPkg: String
        val intentWithoutPkg: String
        if (intent == null) {
            // Skip items with null intents
            return true
        }
        if (intent.component != null) {
            // If component is not null, an intent with null package will produce
            // the same result and should also be a match.
            compPkgName = intent.component?.packageName
            if (intent.getPackage() != null) {
                intentWithPkg = intent.toUri(0)
                intentWithoutPkg = Intent(intent).setPackage(null).toUri(0)
            } else {
                intentWithPkg = Intent(intent).setPackage(compPkgName).toUri(0)
                intentWithoutPkg = intent.toUri(0)
            }
        } else {
            compPkgName = null
            intentWithPkg = intent.toUri(0)
            intentWithoutPkg = intent.toUri(0)
        }
        val isLauncherAppTarget = PackageManagerHelper.isLauncherAppTarget(intent)
        synchronized(dataModel) {
            for (item in dataModel.itemsIdMap) {
                if (item is WorkspaceItemInfo) {
                    val info = item as WorkspaceItemInfo
                    if (item.getIntent() != null && info.user == user) {
                        val copyIntent =
                            Intent(item.getIntent())
                        copyIntent.sourceBounds = intent.sourceBounds
                        val s = copyIntent.toUri(0)
                        if (intentWithPkg == s || intentWithoutPkg == s) {
                            return true
                        }

                        // checking for existing promise icon with same package name
                        if ((
                                isLauncherAppTarget &&
                                    info.isPromise &&
                                    info.hasStatusFlag(WorkspaceItemInfo.FLAG_AUTOINSTALL_ICON) && info.getTargetComponent() != null
                                ) && compPkgName != null && compPkgName == info.getTargetComponent()!!.packageName
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
