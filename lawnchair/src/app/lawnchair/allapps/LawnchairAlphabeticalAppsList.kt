package app.lawnchair.allapps

import android.content.Context
import android.util.Log
import androidx.lifecycle.lifecycleScope
import app.lawnchair.launcher
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.util.categorizeApps
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.allapps.AlphabeticalAppsList
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem
import com.android.launcher3.allapps.PrivateProfileManager
import com.android.launcher3.allapps.WorkProfileManager
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.views.ActivityContext
import com.patrykmichalik.opto.core.onEach
import java.util.function.Predicate

class LawnchairAlphabeticalAppsList<T>(
    private val context: T,
    appsStore: AllAppsStore<T>,
    workProfileManager: WorkProfileManager?,
    privateProfileManager: PrivateProfileManager?,
) : AlphabeticalAppsList<T>(context, appsStore, workProfileManager, privateProfileManager)
    where T : Context, T : ActivityContext {

    private var hiddenApps: Set<String> = setOf()
    private val prefs2 = PreferenceManager2.getInstance(context)
    private val prefs = PreferenceManager.getInstance(context)
    init {
        try {
            prefs2.hiddenApps.onEach(launchIn = context.launcher.lifecycleScope) {
                hiddenApps = it
                onAppsUpdated()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed initialize ignore: ", t)
        }
    }

    override fun updateItemFilter(itemFilter: Predicate<ItemInfo>?) {
        this.mItemFilter = Predicate { info ->
            require(info is AppInfo) { "`info` must be an instance of `AppInfo`." }
            val componentKey = info.toComponentKey().toString()
            itemFilter?.test(info) != false && !hiddenApps.contains(componentKey)
        }
        onAppsUpdated()
    }

    override fun addAppsWithSections(appList: List<AppInfo?>?, startPosition: Int): Int {
        if (appList.isNullOrEmpty()) return startPosition
        val drawerListDefault = prefs.drawerList.get()
        var position = startPosition

        if (!drawerListDefault) {
            val categorizedApps = categorizeApps(context, appList)

            if (categorizedApps.isNotEmpty()) {
                for ((category, apps) in categorizedApps) {
                    if (apps.size <= 1) {
                        val app = apps[0]
                        mAdapterItems.add(AdapterItem.asApp(app))
                    } else {
                        val folderInfo = FolderInfo()
                        folderInfo.title = category
                        for (app in apps) {
                            folderInfo.add(app)
                        }
                        mAdapterItems.add(AdapterItem.asFolder(folderInfo))
                    }
                    position++
                }
            }
        } else {
            position = super.addAppsWithSections(appList, startPosition)
        }

        return position
    }
}
