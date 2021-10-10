package app.lawnchair.search

import android.util.SparseIntArray
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.util.containsKey
import app.lawnchair.LawnchairLauncher
import app.lawnchair.allapps.SearchItemDecorator
import app.lawnchair.allapps.SearchResultView
import com.android.launcher3.R
import com.android.launcher3.allapps.AllAppsContainerView
import com.android.launcher3.allapps.AllAppsGridAdapter
import com.android.launcher3.allapps.search.DefaultSearchAdapterProvider

class LawnchairSearchAdapterProvider(
    launcher: LawnchairLauncher,
    private val appsView: AllAppsContainerView
) : DefaultSearchAdapterProvider(launcher, appsView) {

    private val decorator = SearchItemDecorator(appsView)
    private val layoutIdMap = SparseIntArray().apply {
        append(SEARCH_RESULT_ICON, R.layout.search_result_icon)
    }

    override fun isViewSupported(viewType: Int): Boolean {
        return layoutIdMap.containsKey(viewType)
    }

    override fun onBindView(holder: AllAppsGridAdapter.ViewHolder, position: Int) {
        val adapterItem = appsView.apps.adapterItems[position] as SearchAdapterItem
        val itemView = holder.itemView as SearchResultView
        itemView.bind(adapterItem.searchTarget)
        super.onBindView(holder, position)
    }

    override fun onCreateViewHolder(
        layoutInflater: LayoutInflater,
        parent: ViewGroup?,
        viewType: Int
    ): AllAppsGridAdapter.ViewHolder {
        return AllAppsGridAdapter.ViewHolder(layoutInflater.inflate(layoutIdMap[viewType], parent, false))
    }

    override fun getDecorator() = decorator

    companion object {
        private const val SEARCH_RESULT_ICON = (1 shl 8) and AllAppsGridAdapter.VIEW_TYPE_ICON

        val viewTypeMap = mapOf(
            SearchTargetCompat.LAYOUT_TYPE_ICON to SEARCH_RESULT_ICON,
        )
    }
}
