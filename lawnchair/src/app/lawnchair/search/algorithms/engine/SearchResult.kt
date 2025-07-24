package app.lawnchair.search.algorithms.engine

import android.annotation.DrawableRes
import android.content.pm.ShortcutInfo
import app.lawnchair.search.adapter.HEADER
import app.lawnchair.search.adapter.SPACE
import app.lawnchair.search.algorithms.data.ContactInfo
import app.lawnchair.search.algorithms.data.IFileInfo
import app.lawnchair.search.algorithms.data.RecentKeyword
import app.lawnchair.search.algorithms.data.SettingInfo
import com.android.launcher3.model.data.AppInfo

/**
 * A clean, type-safe, internal representation of any possible search result.
 * This is the "domain model" for the search engine. It has no knowledge of the UI.
 */
sealed class SearchResult {
    data class App(val data: AppInfo) : SearchResult()
    data class Contact(val data: ContactInfo) : SearchResult()
    data class File(val data: IFileInfo) : SearchResult()
    data class Setting(val data: SettingInfo) : SearchResult()
    data class Shortcut(val data: ShortcutInfo) : SearchResult()
    data class WebSuggestion(val suggestion: String, val provider: String) : SearchResult()
    data class History(val data: RecentKeyword) : SearchResult()
    data class Calculation(val data: app.lawnchair.search.algorithms.data.Calculation) : SearchResult()

    sealed class Action : SearchResult() {
        data class MarketSearch(val query: String) : Action()
        data class WebSearch(
            val query: String,
            val providerName: String,
            val searchUrl: String,
            @DrawableRes val providerIconRes: Int,
        ) : Action()
        data class Header(val title: String, val pkg: String = HEADER) : Action()
        data class Divider(val pkg: String = SPACE) : Action()
    }
}
