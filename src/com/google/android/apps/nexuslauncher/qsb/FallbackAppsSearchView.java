package com.google.android.apps.nexuslauncher.qsb;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import com.android.launcher3.ExtendedEditText;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.allapps.AllAppsContainerView;
import com.android.launcher3.allapps.AllAppsStore.OnUpdateListener;
import com.android.launcher3.allapps.AlphabeticalAppsList;
import com.android.launcher3.allapps.search.AllAppsSearchBarController;
import com.android.launcher3.allapps.search.AllAppsSearchBarController.Callbacks;
import com.android.launcher3.util.ComponentKey;

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

    public void onSearchResult(String query, ArrayList<ComponentKey> apps) {
        if (apps != null && getParent() != null) {
            mApps.setOrderedFilter(apps);
            dV();
            x(true);
            mAppsView.setLastSearchQuery(query);
        }
    }

    public final void clearSearchResult() {
        if (getParent() != null) {
            if (this.mApps.setOrderedFilter(null)) {
                dV();
            }
            x(false);
            mAppsView.onClearSearchResult();
        }
    }

    public void onAppsUpdated() {
        this.DI.refreshSearchResult();
    }

    private void x(boolean z) {
//        PredictionsFloatingHeader predictionsFloatingHeader = (PredictionsFloatingHeader) this.mAppsView.getFloatingHeaderView();
//        if (predictionsFloatingHeader != null && z != predictionsFloatingHeader.AI) {
//            predictionsFloatingHeader.AI = z;
//            ActionsRowView actionsRowView = predictionsFloatingHeader.Bo;
//            if (z != actionsRowView.AI) {
//                actionsRowView.AI = z;
//                actionsRowView.da();
//            }
//            PredictionRowView predictionRowView = predictionsFloatingHeader.Bn;
//            if (z != predictionRowView.AI) {
//                predictionRowView.AI = z;
//                predictionRowView.da();
//            }
//            predictionsFloatingHeader.dh();
//        }
    }

    private void dV() {
        this.DJ.aD(0);
        mAppsView.onSearchResultsChanged();
    }
}
