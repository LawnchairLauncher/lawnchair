package ch.deletescape.lawnchair.globalsearch

import android.content.Context
import android.support.v7.view.ContextThemeWrapper
import ch.deletescape.lawnchair.LawnchairConfig
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.ensureOnMainThread
import ch.deletescape.lawnchair.globalsearch.providers.*
import ch.deletescape.lawnchair.globalsearch.providers.web.*
import ch.deletescape.lawnchair.theme.ThemeManager
import ch.deletescape.lawnchair.theme.ThemeOverride
import ch.deletescape.lawnchair.useApplicationContext
import ch.deletescape.lawnchair.util.SingletonHolder
import com.android.launcher3.BuildConfig
import com.android.launcher3.Utilities

class SearchProviderController(private val context: Context) : ColorEngine.OnColorChangeListener {

    private val prefs by lazy { Utilities.getLawnchairPrefs(context) }
    private var cache: SearchProvider? = null
    private var cached: String = ""

    private val themeOverride = ThemeOverride(ThemeOverride.Launcher(), ThemeListener())
    private var themeRes: Int = 0

    private val listeners = HashSet<OnProviderChangeListener>()

    val isGoogle get() = searchProvider is GoogleSearchProvider

    init {
        ThemeManager.getInstance(context).addOverride(themeOverride)
        ColorEngine.getInstance(context).addColorChangeListeners(this, ColorEngine.Resolvers.ACCENT)
    }

    fun addOnProviderChangeListener(listener: OnProviderChangeListener) {
        listeners.add(listener)
    }

    fun removeOnProviderChangeListener(listener: OnProviderChangeListener) {
        listeners.remove(listener)
    }

    fun onSearchProviderChanged() {
        cache = null
        notifyProviderChanged()
    }

    private fun notifyProviderChanged() {
        HashSet(listeners).forEach(OnProviderChangeListener::onSearchProviderChanged)
    }

    val searchProvider: SearchProvider
        get() {
            val curr = prefs.searchProvider
            if (cache == null || cached != curr) {
                cache = createProvider(prefs.searchProvider) {
                    val lcConfig = LawnchairConfig.getInstance(context)
                    createProvider(lcConfig.defaultSearchProvider) { AppSearchSearchProvider(context) }
                }
                cached = cache!!::class.java.name
                if (prefs.searchProvider != cached) {
                    prefs.searchProvider = cached
                }
                notifyProviderChanged()
            }
            return cache!!
        }

    private fun createProvider(providerName: String, fallback: () -> SearchProvider): SearchProvider {
        try {
            val constructor = Class.forName(providerName).getConstructor(Context::class.java)
            val themedContext = ContextThemeWrapper(context, themeRes)
            val prov = constructor.newInstance(themedContext) as SearchProvider
            if (prov.isAvailable) {
                return prov
            }
        } catch (ignored: Exception) { }
        return fallback()
    }

    override fun onColorChange(resolveInfo: ColorEngine.ResolveInfo) {
        if (resolveInfo.key == ColorEngine.Resolvers.ACCENT) {
            cache = null
            notifyProviderChanged()
        }
    }

    inner class ThemeListener : ThemeOverride.ThemeOverrideListener {

        override val isAlive = true

        override fun applyTheme(themeRes: Int) {
            this@SearchProviderController.themeRes = themeRes
        }

        override fun reloadTheme() {
            cache = null
            applyTheme(themeOverride.getTheme(context))
            onSearchProviderChanged()
        }
    }

    interface OnProviderChangeListener {

        fun onSearchProviderChanged()
    }

    companion object : SingletonHolder<SearchProviderController, Context>(ensureOnMainThread(useApplicationContext(::SearchProviderController))) {
        fun getSearchProviders(context: Context) = listOf(
                AppSearchSearchProvider(context),
                GoogleSearchProvider(context),
                // TODO: fall back to this if google is not available per default
                GoogleWebSearchProvider(context),
                SFinderSearchProvider(context),
                if (BuildConfig.FEATURE_QUINOA) {
                    SesameSearchProvider(context)
                } else {
                    DisabledDummySearchProvider(context)
                },
                GoogleGoSearchProvider(context),
                FirefoxSearchProvider(context),
                DuckDuckGoSearchProvider(context),
                DDGWebSearchProvider(context),
                BingSearchProvider(context),
                BingWebSearchProvider(context),
                StartpageWebSearchProvider(context),
                BaiduSearchProvider(context),
                BaiduWebSearchProvider(context),
                YandexSearchProvider(context),
                YandexWebSearchProvider(context),
                QwantSearchProvider(context),
                QwantWebSearchProvider(context),
                EcosiaWebSearchProvider(context),
                SearchLiteSearchProvider(context),
                CoolSearchSearchProvider(context),
                EdgeSearchProvider(context),
                NaverWebSearchProvider(context),
                YahooWebSearchProvider(context)
        ).filter { it.isAvailable }
    }
}
