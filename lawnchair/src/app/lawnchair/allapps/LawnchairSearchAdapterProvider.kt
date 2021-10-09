package app.lawnchair.allapps

import android.view.LayoutInflater
import android.view.ViewGroup
import app.lawnchair.LawnchairLauncher
import com.android.launcher3.BubbleTextView
import com.android.launcher3.R
import com.android.launcher3.allapps.AllAppsContainerView
import com.android.launcher3.allapps.AllAppsGridAdapter
import com.android.launcher3.allapps.AllAppsGridAdapter.AdapterItem
import com.android.launcher3.allapps.search.DefaultSearchAdapterProvider
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.touch.ItemLongClickListener

class LawnchairSearchAdapterProvider(
    private val launcher: LawnchairLauncher,
    private val appsView: AllAppsContainerView
) : DefaultSearchAdapterProvider(launcher, appsView) {

    private val decorator = SearchItemDecorator(appsView)

    override fun isViewSupported(viewType: Int): Boolean {
        return viewType == SEARCH_RESULT_ICON
    }

    override fun onBindView(holder: AllAppsGridAdapter.ViewHolder, position: Int) {
        val adapterItem = appsView.apps.adapterItems[position]
        when (holder.itemViewType) {
            SEARCH_RESULT_ICON -> {
                val info = adapterItem.appInfo
                val icon = holder.itemView as BubbleTextView
                icon.reset()
                icon.applyFromApplicationInfo(info)
            }
        }
        super.onBindView(holder, position)
    }

    override fun onCreateViewHolder(
        layoutInflater: LayoutInflater,
        parent: ViewGroup?,
        viewType: Int
    ): AllAppsGridAdapter.ViewHolder {
        return when (viewType) {
            SEARCH_RESULT_ICON -> {
                val icon = layoutInflater.inflate(
                    R.layout.all_apps_icon, parent, false) as BubbleTextView
                icon.setLongPressTimeoutFactor(1f)
                icon.setOnClickListener(launcher.itemOnClickListener)
                icon.setOnLongClickListener(ItemLongClickListener.INSTANCE_ALL_APPS)
                // Ensure the all apps icon height matches the workspace icons in portrait mode.
                icon.layoutParams.height = mLauncher.deviceProfile.allAppsCellHeightPx
                AllAppsGridAdapter.ViewHolder(icon)
            }
            else -> super.onCreateViewHolder(layoutInflater, parent, viewType)
        }
    }

    override fun getDecorator() = decorator

    companion object {
        private const val SEARCH_RESULT_ICON = (1 shl 8) and AllAppsGridAdapter.VIEW_TYPE_ICON

        fun asIcon(
            pos: Int, sectionName: String, appInfo: AppInfo, appIndex: Int, background: SearchItemBackground
        ): AdapterItem {
            val item = AdapterItem()
            item.viewType = SEARCH_RESULT_ICON
            item.position = pos
            item.sectionName = sectionName
            item.appInfo = appInfo
            item.appIndex = appIndex
            item.decorationInfo = SearchDecorationInfo(background)
            return item
        }
    }
}
