package app.lawnchair.search

import android.app.search.Query
import android.app.search.SearchContext
import android.app.search.SearchSession
import android.app.search.SearchTarget
import android.app.search.SearchUiManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import androidx.core.os.bundleOf
import app.lawnchair.preferences.PreferenceChangeListener
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.util.requireSystemService
import com.android.launcher3.LauncherAppState
import com.android.launcher3.allapps.AllAppsGridAdapter
import com.android.launcher3.search.SearchCallback
import com.android.launcher3.util.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

class LawnchairDeviceSearchAlgorithm(context: Context) : LawnchairSearchAlgorithm(context),
    PreferenceChangeListener {
    private val prefs: PreferenceManager
    private var searchSession: SearchSession? = null
    private var activeQuery: PendingQuery? = null

    init {
        prefs = PreferenceManager.getInstance(context).also {
            createSearchSession()
            it.searchResultShortcuts.addListener(this)
            it.searchResultPeople.addListener(this)
            it.searchResultPixelTips.addListener(this)
            it.searchResultSettings.addListener(this)
        }
    }

    override fun onPreferenceChange() {
        createSearchSession()
    }

    override fun doSearch(query: String, callback: SearchCallback<AllAppsGridAdapter.AdapterItem>) {
        activeQuery?.cancel()
        searchSession ?: return
        activeQuery = PendingQuery(query, callback)
        val searchQuery = Query(query, System.currentTimeMillis(), null)
        searchSession!!.query(searchQuery, Executors.MAIN_EXECUTOR, activeQuery)
    }

    override fun cancel(interruptActiveRequests: Boolean) {
        activeQuery?.cancel()
    }

    override fun destroy() {
        super.destroy()
        Executors.UI_HELPER_EXECUTOR.execute {
            searchSession?.destroy()
        }
        prefs.let {
            it.searchResultShortcuts.removeListener(this)
            it.searchResultPeople.removeListener(this)
            it.searchResultPixelTips.removeListener(this)
            it.searchResultSettings.removeListener(this)
        }
    }

    private fun createSearchSession() {
        Executors.UI_HELPER_EXECUTOR.execute {
            searchSession?.destroy()
            val idp = LauncherAppState.getIDP(context)
            val extras = bundleOf(
                "launcher.gridSize" to idp.numDatabaseAllAppsColumns,
                "allowlist_enabled" to false,
                "launcher.maxInlineIcons" to 3,
            )
            var resultTypes = 1 /* apps */ or 2 /* shortcuts */
            if (prefs.searchResultShortcuts.get()) {
                resultTypes = resultTypes or 1546
            }
            if (prefs.searchResultPeople.get()) {
                resultTypes = resultTypes or 4
            }
            if (prefs.searchResultPixelTips.get()) {
                resultTypes = resultTypes or 8192
                extras.putString("tips_source", "superpacks_tips_source")
            }
            if (prefs.searchResultSettings.get()) {
                resultTypes = resultTypes or 80
                extras.putString("settings_source", "superpacks_settings_source")
            }
            val searchContext = SearchContext(resultTypes, 200, extras)
            val searchSession = context.requireSystemService<SearchUiManager>()
                .createSearchSession(searchContext)
            Executors.MAIN_EXECUTOR.post { this.searchSession = searchSession }
        }
    }

    private inner class PendingQuery(
        private val query: String,
        private val callback: SearchCallback<AllAppsGridAdapter.AdapterItem>
    ) : Consumer<List<SearchTarget>> {
        private var canceled = false

        override fun accept(platformTargets: List<SearchTarget>) {
            if (!canceled) {
                val targets = platformTargets.map { SearchTargetCompat.wrap(it) }
                val adapterItems = transformSearchResults(targets)
                LawnchairSearchAdapterProvider.setFirstItemQuickLaunch(adapterItems)
                callback.onSearchResult(
                    query,
                    ArrayList<AllAppsGridAdapter.AdapterItem>(adapterItems)
                )
            }
        }

        fun cancel() {
            canceled = true
        }
    }

    companion object {
        fun checkSearchCompatibility(context: Context) {
            Executors.UI_HELPER_EXECUTOR.execute {
                val searchContext = SearchContext(1 or 2, 200, Bundle())
                val searchManager = context.requireSystemService<SearchUiManager>()
                val searchSession: SearchSession = searchManager.createSearchSession(searchContext)
                val searchQuery = Query("dummy", System.currentTimeMillis(), null)
                val prefs = PreferenceManager.getInstance(context)
                val checkDone = AtomicBoolean(false)
                searchSession.query(searchQuery, Executors.MAIN_EXECUTOR) { targets ->
                    checkDone.set(true)
                    finishCompatibilityCheck(prefs, searchSession, targets.isNotEmpty())
                }
                Handler(Executors.UI_HELPER_EXECUTOR.looper).postDelayed(
                    {
                        if (!checkDone.get()) {
                            finishCompatibilityCheck(prefs, searchSession, false)
                        }
                    }, 300
                )
            }
        }

        private fun finishCompatibilityCheck(
            prefs: PreferenceManager,
            session: SearchSession,
            isCompatible: Boolean
        ) {
            Executors.MAIN_EXECUTOR.execute { prefs.deviceSearch.set(isCompatible) }
            try {
                session.destroy()
            } catch (_: Exception) {
            }
        }
    }
}
