package com.google.android.apps.nexuslauncher.qsb;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;

import com.android.launcher3.ExtendedEditText;
import com.android.launcher3.Launcher;
import com.android.launcher3.allapps.AllAppsGridAdapter;
import com.android.launcher3.allapps.AllAppsRecyclerView;
import com.android.launcher3.allapps.AlphabeticalAppsList;
import com.android.launcher3.allapps.search.AllAppsSearchBarController;
import com.android.launcher3.discovery.AppDiscoveryItem;
import com.android.launcher3.discovery.AppDiscoveryUpdateState;
import com.google.android.apps.nexuslauncher.search.SearchThread;

import java.util.ArrayList;

public class FallbackAppsSearchView extends ExtendedEditText implements AllAppsSearchBarController.Callbacks {
    private AllAppsQsbLayout mQsbLayout;
    private AllAppsGridAdapter mAdapter;
    private AlphabeticalAppsList mApps;
    private AllAppsRecyclerView mAppsRecyclerView;
    private final AllAppsSearchBarController mSearchBarController;

    public FallbackAppsSearchView(Context context) {
        this(context, null);
    }

    public FallbackAppsSearchView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FallbackAppsSearchView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mSearchBarController = new AllAppsSearchBarController();
    }

    private void notifyResultChanged() {
        mQsbLayout.useAlpha(0);
        mAppsRecyclerView.onSearchResultsChanged();
    }

    public void bu(AllAppsQsbLayout qsbLayout, AlphabeticalAppsList apps, AllAppsRecyclerView appsRecyclerView) {
        mQsbLayout = qsbLayout;
        mApps = apps;
        mAppsRecyclerView = appsRecyclerView;
        mAdapter = (AllAppsGridAdapter) appsRecyclerView.getAdapter();
        mSearchBarController.initialize(new SearchThread(getContext()), this, Launcher.getLauncher(getContext()), this);
    }

    public void clearSearchResult() {
        if (getParent() != null && mApps.setOrderedFilter(null)) {
            notifyResultChanged();
        }
    }

    @Override
    public void onAppDiscoverySearchUpdate(@Nullable AppDiscoveryItem app, @NonNull AppDiscoveryUpdateState state) {

    }

    public void onSearchResult(final String lastSearchQuery, final ArrayList orderedFilter) {
        if (orderedFilter != null && getParent() != null) {
            mApps.setOrderedFilter(orderedFilter);
            notifyResultChanged();
            mAdapter.setLastSearchQuery(lastSearchQuery);
        }
    }

    public void refreshSearchResult() {
        mSearchBarController.refreshSearchResult();
    }
}
