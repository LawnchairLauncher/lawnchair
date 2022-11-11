package app.lawnchair.search

import android.app.search.Query
import android.app.search.SearchContext
import android.app.search.SearchSession
import android.app.search.SearchTarget
import android.app.search.SearchUiManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import app.lawnchair.preferences.PreferenceChangeListener
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.util.requireSystemService
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.allapps.AllAppsGridAdapter
import com.android.launcher3.search.SearchCallback
import com.android.launcher3.util.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

class LawnchairDeviceSearchAlgorithm(context: Context) : LawnchairSearchAlgorithm(context),
    PreferenceChangeListener {
    private val mPrefs: PreferenceManager
    private var mSearchSession: SearchSession? = null
    private var mActiveQuery: PendingQuery? = null

    init {
        mPrefs = PreferenceManager.getInstance(context).also {
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

    private fun createSearchSession() {
        Executors.UI_HELPER_EXECUTOR.execute {
            mSearchSession?.destroy()
            val context = context
            val extras = Bundle()
            val idp: InvariantDeviceProfile = LauncherAppState.getIDP(context)
            extras.putInt("launcher.gridSize", idp.numDatabaseAllAppsColumns)
            extras.putBoolean("allowlist_enabled", false)
            extras.putInt("launcher.maxInlineIcons", 3)
            var resultTypes = 1 /* apps */ or 2 /* shortcuts */
            if (mPrefs.searchResultShortcuts.get()) {
                resultTypes = resultTypes or 1546
            }
            if (mPrefs.searchResultPeople.get()) {
                resultTypes = resultTypes or 4
            }
            if (mPrefs.searchResultPixelTips.get()) {
                resultTypes = resultTypes or 8192
                extras.putString("tips_source", "superpacks_tips_source")
            }
            if (mPrefs.searchResultSettings.get()) {
                resultTypes = resultTypes or 80
                extras.putString("settings_source", "superpacks_settings_source")
            }
            val searchContext = SearchContext(resultTypes, 200, extras)
            val searchSession = context.requireSystemService<SearchUiManager>()
                .createSearchSession(searchContext)
            Executors.MAIN_EXECUTOR.post { mSearchSession = searchSession }
        }
    }

    override fun doSearch(query: String, callback: SearchCallback<AllAppsGridAdapter.AdapterItem>) {
        mActiveQuery?.cancel()
        mSearchSession ?: return
        mActiveQuery = PendingQuery(query, callback)
        val searchQuery = Query(query, System.currentTimeMillis(), null)
        mSearchSession?.query(searchQuery, Executors.MAIN_EXECUTOR, mActiveQuery)
    }

    override fun cancel(interruptActiveRequests: Boolean) {
        mActiveQuery?.cancel()
    }

    override fun destroy() {
        super.destroy()
        Executors.UI_HELPER_EXECUTOR.execute {
            mSearchSession?.destroy()
        }
        mPrefs.searchResultShortcuts.removeListener(this)
        mPrefs.searchResultPeople.removeListener(this)
        mPrefs.searchResultPixelTips.removeListener(this)
        mPrefs.searchResultSettings.removeListener(this)
    }

    private inner class PendingQuery(
        private val mQuery: String,
        callback: SearchCallback<AllAppsGridAdapter.AdapterItem>
    ) : Consumer<List<SearchTarget>> {
        private val mCallback: SearchCallback<AllAppsGridAdapter.AdapterItem>
        private var mCanceled = false

        init {
            mCallback = callback
        }

        override fun accept(platformTargets: List<SearchTarget>) {
            if (!mCanceled) {
                val targets: MutableList<SearchTargetCompat> = ArrayList()
                for (target in platformTargets) {
                    targets.add(SearchTargetCompat.wrap(target))
                }
                val adapterItems = transformSearchResults(targets)
                LawnchairSearchAdapterProvider.setFirstItemQuickLaunch(adapterItems)
                mCallback.onSearchResult(mQuery, ArrayList<AllAppsGridAdapter.AdapterItem>(adapterItems))
            }
        }

        fun cancel() {
            mCanceled = true
        }
    }

    companion object {
        fun checkSearchCompatibility(context: Context) {
            Executors.UI_HELPER_EXECUTOR.execute {
                val searchContext = SearchContext(1 or 2, 200, Bundle())
                val searchManager: SearchUiManager =
                    context.getSystemService(SearchUiManager::class.java)
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
            } catch (ignore: Exception) {
            }
        }
    }
}
