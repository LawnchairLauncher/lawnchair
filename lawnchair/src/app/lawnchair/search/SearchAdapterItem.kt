package app.lawnchair.search

import android.os.Bundle
import android.os.Process
import app.lawnchair.allapps.SearchItemBackground
import com.android.launcher3.BuildConfig
import com.android.launcher3.allapps.AllAppsGridAdapter
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.util.ComponentKey

data class SearchAdapterItem(
    val searchTarget: SearchTargetCompat,
    val background: SearchItemBackground
) : AllAppsGridAdapter.AdapterItem() {

    companion object {

        fun fromApp(
            pos: Int,
            appInfo: AppInfo,
            background: SearchItemBackground
        ): SearchAdapterItem {
            val componentName = appInfo.componentName
            val user = appInfo.user
            val target = SearchTargetCompat.Builder(
                SearchTargetCompat.RESULT_TYPE_APPLICATION,
                SearchTargetCompat.LAYOUT_TYPE_ICON,
                ComponentKey(componentName, user).toString()
            )
                .setPackageName(componentName.packageName)
                .setUserHandle(user)
                .setExtras(Bundle().apply {
                    putString("class", componentName.className)
                })
                .build()
            return SearchAdapterItem(target, background).apply {
                viewType = LawnchairSearchAdapterProvider.viewTypeMap[target.layoutType]!!
                position = pos
            }
        }

        fun fromAction(
            pos: Int,
            id: String,
            action: SearchActionCompat,
            background: SearchItemBackground,
            extras: Bundle = Bundle()
        ): SearchAdapterItem {
            val target = SearchTargetCompat.Builder(
                SearchTargetCompat.RESULT_TYPE_SHORTCUT,
                SearchTargetCompat.LAYOUT_TYPE_ICON_ROW,
                id
            )
                .setPackageName(BuildConfig.APPLICATION_ID)
                .setUserHandle(Process.myUserHandle())
                .setSearchAction(action)
                .setExtras(extras)
                .build()
            return SearchAdapterItem(target, background).apply {
                viewType = LawnchairSearchAdapterProvider.viewTypeMap[target.layoutType]!!
                position = pos
            }
        }
    }
}
