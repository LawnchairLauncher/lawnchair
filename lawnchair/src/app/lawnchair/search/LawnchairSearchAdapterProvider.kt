package app.lawnchair.search

import android.util.SparseIntArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.util.containsKey
import app.lawnchair.LawnchairLauncher
import app.lawnchair.allapps.SearchItemDecorator
import app.lawnchair.allapps.SearchResultView
import com.android.app.search.LayoutType
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
        append(SEARCH_RESULT_ICON_ROW, R.layout.search_result_tall_icon_row)
        append(SEARCH_RESULT_SMALL_ICON_ROW, R.layout.search_result_small_icon_row)
        append(SEARCH_RESULT_DIVIDER, R.layout.search_result_divider)
    }
    private var quickLaunchItem: SearchResultView? = null
        set(value) {
            field = value
            appsView.searchUiManager.setFocusedResultTitle(field?.titleText)
        }

    override fun isViewSupported(viewType: Int): Boolean = layoutIdMap.containsKey(viewType)

    override fun onBindView(holder: AllAppsGridAdapter.ViewHolder, position: Int) {
        val adapterItem = appsView.apps.adapterItems[position] as SearchAdapterItem
        if ((adapterItem.viewType and SEARCH_RESULT_DIVIDER) != 0) return

        val itemView = holder.itemView as SearchResultView
        itemView.bind(adapterItem.searchTarget, emptyList())
        if (itemView.isQuickLaunch) {
            quickLaunchItem = itemView
        }
    }

    override fun onCreateViewHolder(
        layoutInflater: LayoutInflater,
        parent: ViewGroup?,
        viewType: Int
    ): AllAppsGridAdapter.ViewHolder =
        AllAppsGridAdapter.ViewHolder(layoutInflater.inflate(layoutIdMap[viewType], parent, false))

    override fun getItemsPerRow(viewType: Int, appsPerRow: Int) =
        if (viewType != SEARCH_RESULT_ICON) 1 else super.getItemsPerRow(viewType, appsPerRow)

    override fun launchHighlightedItem(): Boolean = quickLaunchItem?.launch() ?: false

    override fun getHighlightedItem() = quickLaunchItem as View?

    override fun getDecorator() = decorator

    companion object {
        private const val SEARCH_RESULT_ICON = (1 shl 8) or AllAppsGridAdapter.VIEW_TYPE_ICON
        private const val SEARCH_RESULT_ICON_ROW = 1 shl 9
        private const val SEARCH_RESULT_SMALL_ICON_ROW = 1 shl 10
        private const val SEARCH_RESULT_DIVIDER = 1 shl 11

        val viewTypeMap = mapOf(
            LayoutType.ICON_SINGLE_VERTICAL_TEXT to SEARCH_RESULT_ICON,
            LayoutType.ICON_HORIZONTAL_TEXT to SEARCH_RESULT_ICON_ROW,
            LayoutType.SMALL_ICON_HORIZONTAL_TEXT to SEARCH_RESULT_SMALL_ICON_ROW,
            LayoutType.DIVIDER to SEARCH_RESULT_DIVIDER,
        )

        fun decorateSearchResults(items: List<SearchAdapterItem>): List<SearchAdapterItem> {
            items.firstOrNull()?.searchTarget?.extras?.apply {
                putBoolean(SearchResultView.EXTRA_QUICK_LAUNCH, true)
            }
            return items
        }
    }
}
