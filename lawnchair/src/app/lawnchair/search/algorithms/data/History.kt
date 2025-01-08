package app.lawnchair.search.algorithms.data

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log
import app.lawnchair.search.LawnchairRecentSuggestionProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RecentKeyword(
    val data: Map<String, String>,
) {
    fun getValueByKey(key: String): String? {
        return data[key]
    }
}

suspend fun getRecentKeyword(context: Context, query: String, max: Int, callback: SearchCallback) {
    try {
        if (query.isEmpty() || query.isBlank() || max <= 0) {
            callback.onSearchLoaded(emptyList())
            return
        }

        callback.onLoading()

        withContext(Dispatchers.IO) {
            val contentResolver: ContentResolver = context.contentResolver
            val uri: Uri =
                Uri.parse("content://${LawnchairRecentSuggestionProvider.AUTHORITY}/suggestions")
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
            callback.onSearchLoaded(recentKeywords.asReversed().take(max))
        }
    } catch (e: Exception) {
        Log.e("Exception", "Error during recent keyword retrieval: ${e.message}")
        callback.onSearchFailed("Error during recent keyword retrieval: ${e.message}")
    }
}
