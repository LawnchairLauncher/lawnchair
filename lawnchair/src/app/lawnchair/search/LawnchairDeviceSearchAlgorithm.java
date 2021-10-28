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
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.allapps.AllAppsGridAdapter;
import com.android.launcher3.search.SearchCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import app.lawnchair.preferences.PreferenceManager;

public class LawnchairDeviceSearchAlgorithm extends LawnchairSearchAlgorithm {

    private SearchSession mSearchSession;
    private PendingQuery mActiveQuery;

    public LawnchairDeviceSearchAlgorithm(@NonNull Context context) {
        super(context);
        UI_HELPER_EXECUTOR.execute(() -> {
            Bundle extras = new Bundle();
            InvariantDeviceProfile idp = LauncherAppState.getIDP(context);
            extras.putInt("launcher.gridSize", idp.numDatabaseAllAppsColumns);
            extras.putBoolean("allowlist_enabled", false);
            extras.putInt("launcher.maxInlineIcons", 3);
            extras.putString("settings_source", "superpacks_settings_source");
            extras.putString("tips_source", "superpacks_tips_source");

            PreferenceManager prefs = PreferenceManager.getInstance(context);
            int resultTypes = 1 /* apps */ | 2 /* shortcuts */;
            if (prefs.getSearchResultShortcuts().get()) {
                resultTypes = resultTypes | 1546;
            }
            if (prefs.getSearchResultPeople().get()) {
                resultTypes = resultTypes | 4;
            }
            if (prefs.getSearchResultPixelTips().get()) {
                resultTypes = resultTypes | 8192;
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
                Log.d("DeviceSearchAlg", "got search results");
                List<SearchTargetCompat> targets = platformTargets
                        .stream()
                        .map(SearchTargetCompat::wrap)
                        .collect(Collectors.toList());
                targets.forEach(target -> {
                    //noinspection StringBufferReplaceableByString
                    String itemInfo = new StringBuilder()
                            .append("type=")
                            .append(target.getResultType())
                            .append(", layout=")
                            .append(target.getLayoutType())
                            .append(", id=")
                            .append(target.getId())
                            .toString();
                    Log.d("DeviceSearchAlg", itemInfo);
                });
                List<SearchAdapterItem> adapterItems = transformSearchResults(targets);
                LawnchairSearchAdapterProvider.setFirstItemQuickLaunch(adapterItems);
                mCallback.onSearchResult(mQuery, new ArrayList<>(adapterItems));
            }
        }

        public void cancel() {
            mCanceled = true;
        }
    }
}
