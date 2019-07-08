package com.google.android.apps.nexuslauncher.qsb;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import ch.deletescape.lawnchair.globalsearch.SearchProvider;
import ch.deletescape.lawnchair.globalsearch.SearchProviderController;
import ch.deletescape.lawnchair.globalsearch.providers.web.WebSearchProvider;
import com.android.launcher3.ExtendedEditText;
import com.android.launcher3.Launcher;
import com.android.launcher3.allapps.AllAppsContainerView;
import com.android.launcher3.allapps.AllAppsStore.OnUpdateListener;
import com.android.launcher3.allapps.AlphabeticalAppsList;
import com.android.launcher3.allapps.search.AllAppsSearchBarController;
import com.android.launcher3.allapps.search.AllAppsSearchBarController.Callbacks;
import com.android.launcher3.util.ComponentKey;
import com.google.android.apps.nexuslauncher.allapps.PredictionsFloatingHeader;

import java.util.ArrayList;
import java.util.List;

public class FallbackAppsSearchView extends ExtendedEditText implements OnUpdateListener, Callbacks {
    final AllAppsSearchBarController DI;
    AllAppsQsbLayout DJ;
    AlphabeticalAppsList mApps;
    AllAppsContainerView mAppsView;

    public FallbackAppsSearchView(Context context) {
        this(context, null);
    }

    public FallbackAppsSearchView(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public FallbackAppsSearchView(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        this.DI = new AllAppsSearchBarController();
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Launcher.getLauncher(getContext()).getAppsView().getAppsStore().addUpdateListener(this);
    }

    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Launcher.getLauncher(getContext()).getAppsView().getAppsStore().removeUpdateListener(this);
    }

    @Override
    public void onSearchResult(String query, ArrayList<ComponentKey> apps, List<String> suggestions) {
        if (getParent() != null) {
            if (apps != null) {
                mApps.setOrderedFilter(apps);
            }
            if (suggestions != null) {
                mApps.setSearchSuggestions(suggestions);
            }
            if (apps != null || suggestions != null) {
                dV();
                x(true);
                mAppsView.setLastSearchQuery(query);
            }
        }
    }

    @Override
    public final void clearSearchResult() {
        if (getParent() != null) {
            if (mApps.setOrderedFilter(null) || mApps.setSearchSuggestions(null)) {
                dV();
            }
            x(false);
            DJ.mDoNotRemoveFallback = true;
            mAppsView.onClearSearchResult();
            DJ.mDoNotRemoveFallback = false;
        }
    }

    @Override
    public boolean onSubmitSearch() {
        if (mApps.hasNoFilteredResults()) {
            return false;
        }
        SearchProvider provider = getSearchProvider();
        if (provider instanceof WebSearchProvider) {
            ((WebSearchProvider) provider).openResults(getText().toString());
            return true;
        }
        Intent i = mApps.getFilteredApps().get(0).getIntent();
        getContext().startActivity(i);
        return true;
    }

    public void onAppsUpdated() {
        this.DI.refreshSearchResult();
    }

    private void x(boolean z) {
        PredictionsFloatingHeader predictionsFloatingHeader = (PredictionsFloatingHeader) mAppsView.getFloatingHeaderView();
        predictionsFloatingHeader.setCollapsed(z);
    }

    private void dV() {
        this.DJ.setShadowAlpha(0);
        mAppsView.onSearchResultsChanged();
    }

    private SearchProvider getSearchProvider() {
        return SearchProviderController.Companion.getInstance(getContext()).getSearchProvider();
    }
}
