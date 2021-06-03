package app.lawnchair.allapps

import android.content.Context
import app.lawnchair.launcher
import app.lawnchair.util.preferences.PreferenceManager
import app.lawnchair.util.preferences.subscribe
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.allapps.AlphabeticalAppsList
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.util.ItemInfoMatcher
import java.lang.IllegalArgumentException

class LawnchairAlphabeticalAppsList(context: Context, appsStore: AllAppsStore, isWork: Boolean) :
    AlphabeticalAppsList(context, appsStore, isWork) {

    private val hiddenApps = PreferenceManager.getInstance(context).hiddenAppSet
    private var itemFilter: ItemInfoMatcher? = null

    init {
        super.updateItemFilter { info, cn ->
            if (info !is AppInfo) {
                throw IllegalArgumentException("info must be an AppInfo")
            }
            when {
                itemFilter?.matches(info, cn) == false -> false
                hiddenApps.get().contains(info.toComponentKey().toString()) -> false
                else -> true
            }
        }
        hiddenApps.subscribe(context.launcher, this::onAppsUpdated)
    }

    override fun updateItemFilter(itemFilter: ItemInfoMatcher?) {
        this.itemFilter = itemFilter
        onAppsUpdated()
    }
}
