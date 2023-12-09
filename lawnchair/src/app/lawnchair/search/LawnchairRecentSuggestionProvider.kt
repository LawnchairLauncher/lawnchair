package app.lawnchair.search

import android.content.SearchRecentSuggestionsProvider

class LawnchairRecentSuggestionProvider : SearchRecentSuggestionsProvider() {
    companion object {
        const val AUTHORITY = "app.lawnchair.search.LawnchairRecentSuggestionProvider"
        const val MODE = DATABASE_MODE_QUERIES
    }

    init {
        setupSuggestions(AUTHORITY, MODE)
    }
}
