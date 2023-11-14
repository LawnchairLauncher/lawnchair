package app.lawnchair.search

import android.util.SparseIntArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.util.contains
import app.lawnchair.LawnchairLauncher
import app.lawnchair.allapps.SearchItemDecorator
import app.lawnchair.allapps.SearchResultView
import app.lawnchair.allapps.SearchResultView.Companion.EXTRA_QUICK_LAUNCH
import com.android.app.search.LayoutType
import com.android.launcher3.R
import com.android.launcher3.allapps.ActivityAllAppsContainerView
import com.android.launcher3.allapps.AllAppsGridAdapter
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.allapps.search.DefaultSearchAdapterProvider
import com.android.launcher3.util.PackageManagerHelper

class LawnchairSearchAdapterProvider(
    launcher: LawnchairLauncher,
    private val appsView: ActivityAllAppsContainerView<*>,
) : DefaultSearchAdapterProvider(launcher) {

    private val decorator = SearchItemDecorator(appsView)
    private val layoutIdMap = SparseIntArray().apply {
        append(SEARCH_RESULT_ICON, R.layout.search_result_icon)
        append(SEARCH_RESULT_ICON_ROW, R.layout.search_result_tall_icon_row)
        append(SEARCH_RESULT_SMALL_ICON_ROW, R.layout.search_result_small_icon_row)
        append(SEARCH_RESULT_DIVIDER, R.layout.search_result_divider)
        append(SEARCH_RESULT_MARKET, R.layout.search_result_market)
    }
    private var quickLaunchItem: SearchResultView? = null
        set(value) {
            field = value
            appsView.searchUiManager.setFocusedResultTitle(field?.titleText, field?.titleText)
        }

    override fun isViewSupported(viewType: Int): Boolean = layoutIdMap.contains(viewType)

    override fun onBindView(holder: BaseAllAppsAdapter.ViewHolder, position: Int) {
        val adapterItem = appsView.mSearchRecyclerView.mApps.adapterItems[position] as SearchAdapterItem
        val itemView = holder.itemView as SearchResultView
        itemView.bind(adapterItem.searchTarget, emptyList())
        if (itemView.isQuickLaunch) {
            quickLaunchItem = itemView
        }
    }

    override fun onCreateViewHolder(
        layoutInflater: LayoutInflater,
        parent: ViewGroup?,
        viewType: Int,
    ): BaseAllAppsAdapter.ViewHolder {
        val view = layoutInflater.inflate(layoutIdMap[viewType], parent, false)
        if (viewType == SEARCH_RESULT_MARKET) {
            val marketSearchIntent = PackageManagerHelper
                .getMarketSearchIntent(
                    view.context,
                    appsView.searchUiManager.editText?.text.toString(),
                )
            val marketView = layoutInflater.inflate(R.layout.search_result_market, parent, false)
            marketView.setOnClickListener {
                mLauncher.startActivitySafely(it, marketSearchIntent, null)
            }
            return BaseAllAppsAdapter.ViewHolder(marketView)
        }
        return BaseAllAppsAdapter.ViewHolder(view)
    }

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
        private const val SEARCH_RESULT_MARKET = 1 shl 12

        val viewTypeMap = mapOf(
            LayoutType.ICON_SINGLE_VERTICAL_TEXT to SEARCH_RESULT_ICON,
            LayoutType.ICON_HORIZONTAL_TEXT to SEARCH_RESULT_ICON_ROW,
            LayoutType.SMALL_ICON_HORIZONTAL_TEXT to SEARCH_RESULT_SMALL_ICON_ROW,
            LayoutType.HORIZONTAL_MEDIUM_TEXT to SEARCH_RESULT_SMALL_ICON_ROW,
            LayoutType.EMPTY_DIVIDER to SEARCH_RESULT_DIVIDER,
            LayoutType.ICON_HORIZONTAL_TEXT to SEARCH_RESULT_MARKET,
        )

        fun setFirstItemQuickLaunch(items: List<SearchAdapterItem>) {
            val hasQuickLaunch = items.any { it.searchTarget.extras.getBoolean(EXTRA_QUICK_LAUNCH, false) }
            if (!hasQuickLaunch) {
                items.firstOrNull()?.searchTarget?.extras?.apply {
                    putBoolean(EXTRA_QUICK_LAUNCH, true)
                }
            }
        }
    }
}
