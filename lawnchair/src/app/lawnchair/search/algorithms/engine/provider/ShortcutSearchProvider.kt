package app.lawnchair.search.algorithms.engine.provider

import android.content.Context
import android.content.pm.ShortcutInfo
import app.lawnchair.launcher
import app.lawnchair.search.algorithms.engine.SearchResult
import app.lawnchair.util.isDefaultLauncher
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.popup.PopupPopulator
import com.android.launcher3.shortcuts.ShortcutRequest

object ShortcutSearchProvider {
    fun search(context: Context, appResults: List<SearchResult.App>): List<SearchResult.Shortcut> {
        if (appResults.size == 1 && context.isDefaultLauncher()) {
            val singleApp = appResults.first().data
            val shortcuts = getShortcuts(singleApp, context)
            return shortcuts.map { SearchResult.Shortcut(data = it) }
        }
        return emptyList()
    }

    private fun getShortcuts(app: AppInfo, context: Context): List<ShortcutInfo> {
        val shortcuts = ShortcutRequest(context.launcher, app.user)
            .withContainer(app.targetComponent)
            .query(ShortcutRequest.PUBLISHED)
        return PopupPopulator.sortAndFilterShortcuts(shortcuts)
    }
}
