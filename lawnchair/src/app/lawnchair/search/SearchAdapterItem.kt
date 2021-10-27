package app.lawnchair.search

import app.lawnchair.allapps.SearchItemBackground
import com.android.launcher3.allapps.AllAppsGridAdapter

data class SearchAdapterItem(
    val searchTarget: SearchTargetCompat,
    val background: SearchItemBackground?
) : AllAppsGridAdapter.AdapterItem() {

    companion object {

        fun createAdapterItem(
            pos: Int,
            target: SearchTargetCompat,
            background: SearchItemBackground?
        ): SearchAdapterItem? {
            val type = LawnchairSearchAdapterProvider.viewTypeMap[target.layoutType] ?: return null
            return SearchAdapterItem(target, background).apply {
                viewType = type
                position = pos
            }
        }
    }
}
