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
import app.lawnchair.search.data.SearchResultActionCallBack
import com.android.app.search.LayoutType
import com.android.launcher3.R
import com.android.launcher3.allapps.ActivityAllAppsContainerView
import com.android.launcher3.allapps.AllAppsGridAdapter
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.allapps.search.DefaultSearchAdapterProvider

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
        append(SEARCH_TEXT_HEADER, R.layout.search_result_text_header)
        append(SEARCH_PEOPLE_TILE, R.layout.search_result_icon_right_left)
        append(SEARCH_RESULT_FILE_TILE, R.layout.search_result_icon_right_left)
        append(SEARCH_RESULT_SUGGESTION_TILE, R.layout.search_result_small_icon_row)
        append(SEARCH_RESULT_SETTINGS_TILE, R.layout.search_result_small_icon_row)
        append(SEARCH_RESULT_RECENT_TILE, R.layout.search_result_small_icon_row)
    }
    private var quickLaunchItem: SearchResultView? = null
        set(value) {
            field = value
            appsView.searchUiManager.setFocusedResultTitle(field?.titleText, field?.titleText, true)
        }

    override fun isViewSupported(viewType: Int): Boolean = layoutIdMap.contains(viewType)

    override fun onBindView(holder: BaseAllAppsAdapter.ViewHolder, position: Int) {
        val adapterItem = appsView.mSearchRecyclerView.mApps.adapterItems[position] as SearchAdapterItem
        adapterItem.setRippleEffect(holder.itemView)
        val itemView = holder.itemView as SearchResultView
        itemView.bind(
            adapterItem.searchTarget,
            emptyList(),
            object : SearchResultActionCallBack {
                override fun action() {
                    appsView.searchUiManager.refreshResults()
                }
            },
        )
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

        val layoutParams = ViewGroup.MarginLayoutParams(view.layoutParams)
        val horizontalMargin = 48
        layoutParams.leftMargin = horizontalMargin
        layoutParams.rightMargin = horizontalMargin
        view.layoutParams = layoutParams

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
        private const val SEARCH_TEXT_HEADER = 1 shl 12
        private const val SEARCH_PEOPLE_TILE = 1 shl 13
        private const val SEARCH_RESULT_FILE_TILE = 1 shl 14
        private const val SEARCH_RESULT_SUGGESTION_TILE = 1 shl 15
        private const val SEARCH_RESULT_SETTINGS_TILE = 1 shl 16
        private const val SEARCH_RESULT_RECENT_TILE = 1 shl 17

        val viewTypeMap = mapOf(
            LayoutType.ICON_SINGLE_VERTICAL_TEXT to SEARCH_RESULT_ICON,
            LayoutType.ICON_HORIZONTAL_TEXT to SEARCH_RESULT_ICON_ROW,
            LayoutType.SMALL_ICON_HORIZONTAL_TEXT to SEARCH_RESULT_SMALL_ICON_ROW,
            LayoutType.HORIZONTAL_MEDIUM_TEXT to SEARCH_RESULT_SUGGESTION_TILE,
            LayoutType.EMPTY_DIVIDER to SEARCH_RESULT_DIVIDER,
            LayoutType.TEXT_HEADER to SEARCH_TEXT_HEADER,
            LayoutType.PEOPLE_TILE to SEARCH_PEOPLE_TILE,
            LayoutType.THUMBNAIL to SEARCH_RESULT_FILE_TILE,
            LayoutType.ICON_SLICE to SEARCH_RESULT_SETTINGS_TILE,
            LayoutType.WIDGET_LIVE to SEARCH_RESULT_RECENT_TILE,
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
