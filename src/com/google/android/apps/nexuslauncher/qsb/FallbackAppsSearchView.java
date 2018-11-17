package com.google.android.apps.nexuslauncher.qsb;

import android.content.Context;
import android.util.AttributeSet;
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
    public void onSearchResult(String query, ArrayList<ComponentKey> apps) {
        if (apps != null && getParent() != null) {
            mApps.setOrderedFilter(apps);
            dV();
            x(true);
            mAppsView.setLastSearchQuery(query);
        }
    }

    @Override
    public final void clearSearchResult() {
        if (getParent() != null) {
            if (mApps.setOrderedFilter(null)) {
                dV();
            }
            x(false);
            DJ.mDoNotRemoveFallback = true;
            mAppsView.onClearSearchResult();
            DJ.mDoNotRemoveFallback = false;
        }
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
}
