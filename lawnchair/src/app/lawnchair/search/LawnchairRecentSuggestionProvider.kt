package app.lawnchair.search

import android.content.SearchRecentSuggestionsProvider
import com.android.launcher3.BuildConfig

class LawnchairRecentSuggestionProvider : SearchRecentSuggestionsProvider() {
    companion object {
        const val AUTHORITY = BuildConfig.APPLICATION_ID + ".search.LawnchairRecentSuggestionProvider"
        const val MODE = DATABASE_MODE_QUERIES
    }

    init {
        setupSuggestions(AUTHORITY, MODE)
    }
}
