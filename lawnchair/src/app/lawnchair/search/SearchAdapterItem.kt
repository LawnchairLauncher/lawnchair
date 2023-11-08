package app.lawnchair.search

import app.lawnchair.allapps.SearchItemBackground
import com.android.launcher3.allapps.BaseAllAppsAdapter

data class SearchAdapterItem(
    val searchTarget: SearchTargetCompat,
    val background: SearchItemBackground?,
    val viewType: Int
) : BaseAllAppsAdapter.AdapterItem(viewType) {

    companion object {

        fun createAdapterItem(
            target: SearchTargetCompat,
            background: SearchItemBackground?
        ): SearchAdapterItem? {
            val type = LawnchairSearchAdapterProvider.viewTypeMap[target.layoutType] ?: return null
            return SearchAdapterItem(target, background, type)
        }
    }
}