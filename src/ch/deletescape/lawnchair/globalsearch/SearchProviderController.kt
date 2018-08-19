package ch.deletescape.lawnchair.globalsearch

import android.content.Context
import ch.deletescape.lawnchair.ensureOnMainThread
import ch.deletescape.lawnchair.globalsearch.providers.*
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
            val curr = prefs.searchProvider
            if (cache == null || cached != curr) {
                cache = null
                try {
                    val prov = Class.forName(prefs.searchProvider).getConstructor(Context::class.java).newInstance(context) as SearchProvider
                    if(prov.isAvailable) {
                        cache = prov
                    }
                } catch (ignored: Exception) { }
                if(cache == null) cache = GoogleSearchProvider(context)
                cached = cache!!::class.java.name
            }
            return cache!!
        }

    companion object : SingletonHolder<SearchProviderController, Context>(ensureOnMainThread(useApplicationContext(::SearchProviderController))) {
        fun getSearchProviders(context: Context) = listOf(
                AppSearchSearchProvider(context),
                GoogleSearchProvider(context),
                SesameSearchProvider(context),
                DuckDuckGoSearchProvider(context),
                BingSearchProvider(context),
                GoogleGoSearchProvider(context),
                BaiduSearchProvider(context),
                YandexSearchProvider(context)
        ).filter { it.isAvailable }
    }
}