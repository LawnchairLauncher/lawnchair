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
import com.patrykmichalik.opto.core.onEach

class LawnchairAlphabeticalAppsList(
    context: Context,
    appsStore: AllAppsStore,
    adapterProvider: WorkAdapterProvider?,
) : AlphabeticalAppsList(context, appsStore, adapterProvider) {

    private var hiddenApps: Set<String> = setOf()
    private var itemFilter: ItemInfoMatcher? = null

    init {
        super.updateItemFilter { info, cn ->
            require(info is AppInfo) { "`info` must be an instance of `AppInfo`." }
            when {
                itemFilter?.matches(info, cn) == false -> false
                hiddenApps.contains(info.toComponentKey().toString()) -> false
                else -> true
            }
        }

        val prefs = PreferenceManager2.getInstance(context)
        prefs.hiddenApps.onEach(launchIn = context.launcher.lifecycleScope) {
            hiddenApps = it
            onAppsUpdated()
        }
    }

    override fun updateItemFilter(itemFilter: ItemInfoMatcher?) {
        this.itemFilter = itemFilter
        onAppsUpdated()
    }
}
