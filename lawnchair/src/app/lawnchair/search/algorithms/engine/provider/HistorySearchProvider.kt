package app.lawnchair.search.algorithms.engine.provider

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import app.lawnchair.search.LawnchairRecentSuggestionProvider
import app.lawnchair.search.algorithms.data.RecentKeyword
import app.lawnchair.search.algorithms.engine.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object HistorySearchProvider {
    /**
     * Fetches recent search keywords from the suggestion provider.
     * This is a suspend function that performs its work on the IO dispatcher.
     *
     * @param context The application context.
     * @param maxResults The maximum number of keywords to return.
     * @return A list of [SearchResult.History] items.
     */
    suspend fun getRecentKeywords(context: Context, maxResults: Int): List<SearchResult.History> {
        return withContext(Dispatchers.IO) {
            try {
                val contentResolver: ContentResolver = context.contentResolver
                val uri: Uri = "content://${LawnchairRecentSuggestionProvider.AUTHORITY}/suggestions".toUri()

                val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
                val recentKeywords = mutableListOf<RecentKeyword>()

                cursor?.use {
                    val columnCount = it.columnCount
                    while (it.moveToNext()) {
                        val recentKeywordData = mutableMapOf<String, String>()
                        for (i in 0 until columnCount) {
                            val columnName = it.getColumnName(i)
                            val columnValue = it.getString(i) ?: ""
                            recentKeywordData[columnName] = columnValue
                        }
                        recentKeywords.add(RecentKeyword(recentKeywordData))
                    }
                }

                recentKeywords.asReversed()
                    .take(maxResults)
                    .map { SearchResult.History(data = it) }
            } catch (e: Exception) {
                Log.e("HistorySearchProvider", "Error during recent keyword retrieval", e)
                emptyList()
            }
        }
    }
}
