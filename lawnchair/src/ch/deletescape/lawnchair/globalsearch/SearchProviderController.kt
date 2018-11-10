package ch.deletescape.lawnchair.globalsearch

import android.content.Context
import android.support.v7.view.ContextThemeWrapper
import ch.deletescape.lawnchair.ensureOnMainThread
import ch.deletescape.lawnchair.globalsearch.providers.*
import ch.deletescape.lawnchair.theme.ThemeManager
import ch.deletescape.lawnchair.theme.ThemeOverride
import ch.deletescape.lawnchair.useApplicationContext
import ch.deletescape.lawnchair.util.SingletonHolder
import com.android.launcher3.BuildConfig
import com.android.launcher3.Utilities

class SearchProviderController(private val context: Context) {

    private val prefs by lazy { Utilities.getLawnchairPrefs(context) }
    private var cache: SearchProvider? = null
    private var cached: String = ""

    private var themeRes: Int = 0

    val isGoogle get() = searchProvider is GoogleSearchProvider

    init {
        ThemeManager.getInstance(context).addOverride(ThemeOverride(ThemeOverride.Launcher(), ThemeListener()))
    }

    val searchProvider: SearchProvider
        get() {
            val curr = prefs.searchProvider
            if (cache == null || cached != curr) {
                cache = null
                try {
                    val constructor = Class.forName(prefs.searchProvider).getConstructor(Context::class.java)
                    val themedContext = ContextThemeWrapper(context, themeRes)
                    val prov = constructor.newInstance(themedContext) as SearchProvider
                    if (prov.isAvailable) {
                        cache = prov
                    }
                } catch (ignored: Exception) { }
                if(cache == null) cache = GoogleSearchProvider(context)
                cached = cache!!::class.java.name
            }
            return cache!!
        }

    inner class ThemeListener : ThemeOverride.ThemeOverrideListener {

        override val isAlive = true

        override fun applyTheme(themeRes: Int) {
            this@SearchProviderController.themeRes = themeRes
        }

        override fun reloadTheme() {
            cache = null
        }
    }

    companion object : SingletonHolder<SearchProviderController, Context>(ensureOnMainThread(useApplicationContext(::SearchProviderController))) {
        fun getSearchProviders(context: Context) = listOf(
                AppSearchSearchProvider(context),
                GoogleSearchProvider(context),
                if (BuildConfig.FEATURE_QUINOA) {
                    SesameSearchProvider(context)
                } else {
                    DisabledDummySearchProvider(context)
                },
                DuckDuckGoSearchProvider(context),
                BingSearchProvider(context),
                GoogleGoSearchProvider(context),
                BaiduSearchProvider(context),
                YandexSearchProvider(context),
                SearchLiteSearchProvider(context),
                CoolSearchSearchProvider(context)
        ).filter { it.isAvailable }
    }
}
