package app.lawnchair.search;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.app.search.Query;
import android.app.search.SearchContext;
import android.app.search.SearchSession;
import android.app.search.SearchTarget;
import android.app.search.SearchUiManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;

import androidx.annotation.NonNull;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.allapps.AllAppsGridAdapter;
import com.android.launcher3.search.SearchCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import app.lawnchair.preferences.PreferenceChangeListener;
import app.lawnchair.preferences.PreferenceManager;

public class LawnchairDeviceSearchAlgorithm extends LawnchairSearchAlgorithm implements PreferenceChangeListener {

    private final PreferenceManager mPrefs;
    private SearchSession mSearchSession;
    private PendingQuery mActiveQuery;

    public LawnchairDeviceSearchAlgorithm(@NonNull Context context) {
        super(context);
        mPrefs = PreferenceManager.getInstance(context);
        createSearchSession();

        mPrefs.getSearchResultShortcuts().addListener(this);
        mPrefs.getSearchResultPeople().addListener(this);
        mPrefs.getSearchResultPixelTips().addListener(this);
        mPrefs.getSearchResultSettings().addListener(this);
    }

    @Override
    public void onPreferenceChange() {
        createSearchSession();
    }

    private void createSearchSession() {
        UI_HELPER_EXECUTOR.execute(() -> {
            if (mSearchSession != null) {
                mSearchSession.destroy();
            }

            Context context = getContext();
            Bundle extras = new Bundle();
            InvariantDeviceProfile idp = LauncherAppState.getIDP(context);
            extras.putInt("launcher.gridSize", idp.numDatabaseAllAppsColumns);
            extras.putBoolean("allowlist_enabled", false);
            extras.putInt("launcher.maxInlineIcons", 3);

            int resultTypes = 1 /* apps */ | 2 /* shortcuts */;
            if (mPrefs.getSearchResultShortcuts().get()) {
                resultTypes = resultTypes | 1546;
            }
            if (mPrefs.getSearchResultPeople().get()) {
                resultTypes = resultTypes | 4;
            }
            if (mPrefs.getSearchResultPixelTips().get()) {
                resultTypes = resultTypes | 8192;
                extras.putString("tips_source", "superpacks_tips_source");
            }
            if (mPrefs.getSearchResultSettings().get()) {
                resultTypes = resultTypes | 80;
                extras.putString("settings_source", "superpacks_settings_source");
            }
            SearchContext searchContext = new SearchContext(resultTypes, 200, extras);
            SearchUiManager searchManager = context.getSystemService(SearchUiManager.class);
            SearchSession searchSession = searchManager.createSearchSession(searchContext);
            MAIN_EXECUTOR.post(() -> mSearchSession = searchSession);
        });
    }

    @Override
    public void doSearch(String query, SearchCallback<AllAppsGridAdapter.AdapterItem> callback) {
        if (mActiveQuery != null) {
            mActiveQuery.cancel();
        }
        if (mSearchSession == null) {
            return;
        }
        mActiveQuery = new PendingQuery(query, callback);
        Query searchQuery = new Query(query, System.currentTimeMillis(), null);
        mSearchSession.query(searchQuery, MAIN_EXECUTOR, mActiveQuery);
    }

    @Override
    public void cancel(boolean interruptActiveRequests) {
        if (mActiveQuery != null) {
            mActiveQuery.cancel();
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        UI_HELPER_EXECUTOR.execute(() -> {
            if (mSearchSession != null) {
                mSearchSession.destroy();
            }
        });

        mPrefs.getSearchResultShortcuts().removeListener(this);
        mPrefs.getSearchResultPeople().removeListener(this);
        mPrefs.getSearchResultPixelTips().removeListener(this);
        mPrefs.getSearchResultSettings().removeListener(this);
    }

    private class PendingQuery implements Consumer<List<SearchTarget>> {

        private final String mQuery;
        private final SearchCallback<AllAppsGridAdapter.AdapterItem> mCallback;
        private boolean mCanceled;

        public PendingQuery(String query, SearchCallback<AllAppsGridAdapter.AdapterItem> callback) {
            mQuery = query;
            mCallback = callback;
        }

        @Override
        public void accept(List<SearchTarget> platformTargets) {
            if (!mCanceled) {
                List<SearchTargetCompat> targets = new ArrayList<>();
                for (SearchTarget target : platformTargets) {
                    targets.add(SearchTargetCompat.wrap(target));
                }
                List<SearchAdapterItem> adapterItems = transformSearchResults(targets);
                LawnchairSearchAdapterProvider.setFirstItemQuickLaunch(adapterItems);
                mCallback.onSearchResult(mQuery, new ArrayList<>(adapterItems));
            }
        }

        public void cancel() {
            mCanceled = true;
        }
    }

    public static void checkSearchCompatibility(Context context) {
        UI_HELPER_EXECUTOR.execute(() -> {
            SearchContext searchContext = new SearchContext(1 | 2, 200, new Bundle());
            SearchUiManager searchManager = context.getSystemService(SearchUiManager.class);
            SearchSession searchSession = searchManager.createSearchSession(searchContext);
            Query searchQuery = new Query("dummy", System.currentTimeMillis(), null);
            PreferenceManager prefs = PreferenceManager.getInstance(context);
            AtomicBoolean checkDone = new AtomicBoolean(false);
            searchSession.query(searchQuery, MAIN_EXECUTOR, targets -> {
                checkDone.set(true);
                finishCompatibilityCheck(prefs, searchSession, targets.size() != 0);
            });
            new Handler(UI_HELPER_EXECUTOR.getLooper()).postDelayed(() -> {
                if (!checkDone.get()) {
                    finishCompatibilityCheck(prefs, searchSession, false);
                }
            }, 300);
        });
    }

    private static void finishCompatibilityCheck(PreferenceManager prefs, SearchSession session, boolean isCompatible) {
        MAIN_EXECUTOR.execute(() -> prefs.getDeviceSearch().set(isCompatible));
        try {
            session.destroy();
        } catch (Exception ignore) { }
    }
}
