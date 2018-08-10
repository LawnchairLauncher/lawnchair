package ch.deletescape.lawnchair.globalsearch

import android.content.Context
import ch.deletescape.lawnchair.ensureOnMainThread
import ch.deletescape.lawnchair.useApplicationContext
import ch.deletescape.lawnchair.util.SingletonHolder
import com.android.launcher3.Utilities

class SearchProviderController(private val context: Context) {

    private val prefs by lazy { Utilities.getLawnchairPrefs(context) }
    private var cache: SearchProvider? = null
    private var cached: String = ""

    val isGoogle get() = searchProvider is GoogleSearchProvider

    val searchProvider: SearchProvider
        get() {
            if (cache == null || cached != prefs.searchProvider) {
                cache = try {
                    Class.forName(prefs.searchProvider).getConstructor(Context::class.java).newInstance(context) as SearchProvider
                } catch (e: Exception) {
                    GoogleSearchProvider(context)
                }
                cached = prefs.searchProvider
            }
            return cache!!
        }

    companion object : SingletonHolder<SearchProviderController, Context>(ensureOnMainThread(useApplicationContext(::SearchProviderController))) {
        fun getSearchProviders(context: Context) = listOf(
                GoogleSearchProvider(context),
                SesameSearchProvider(context),
                AppSearchSearchProvider(context),
                DuckDuckGoSearchProvider(context),
                BingSearchProvider(context),
                GoogleGoSearchProvider(context)
        ).filter { it.isAvailable }
    }
}