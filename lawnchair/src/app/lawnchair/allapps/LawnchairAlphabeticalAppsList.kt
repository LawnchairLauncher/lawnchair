package app.lawnchair.allapps

import android.content.Context
import androidx.lifecycle.lifecycleScope
import app.lawnchair.launcher
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.allapps.AlphabeticalAppsList
import com.android.launcher3.allapps.WorkAdapterProvider
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.util.ItemInfoMatcher
import com.patrykmichalik.preferencemanager.onEach

class LawnchairAlphabeticalAppsList(
    context: Context,
    appsStore: AllAppsStore,
    adapterProvider: WorkAdapterProvider?,
) : AlphabeticalAppsList(context, appsStore, adapterProvider) {

    private val preferenceManager2 = PreferenceManager2.getInstance(context)
    private var itemFilter: ItemInfoMatcher? = null

    init {
        preferenceManager2.hiddenApps.onEach(launchIn = context.launcher.lifecycleScope) {
            super.updateItemFilter { info, cn ->
                require(info is AppInfo) { "`info` must be an instance of `AppInfo`." }
                when {
                    itemFilter?.matches(info, cn) == false -> false
                    it.contains(info.toComponentKey().toString()) -> false
                    else -> true
                }
            }
        }
    }

    override fun updateItemFilter(itemFilter: ItemInfoMatcher?) {
        this.itemFilter = itemFilter
        onAppsUpdated()
    }
}
