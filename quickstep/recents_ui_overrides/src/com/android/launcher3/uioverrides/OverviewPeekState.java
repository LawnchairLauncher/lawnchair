package com.android.launcher3.uioverrides;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;

public class OverviewPeekState extends OverviewState {
    public OverviewPeekState(int id) {
        super(id);
    }

    @Override
    public ScaleAndTranslation getOverviewScaleAndTranslation(Launcher launcher) {
        ScaleAndTranslation result = super.getOverviewScaleAndTranslation(launcher);
        result.translationX = NORMAL.getOverviewScaleAndTranslation(launcher).translationX
                - launcher.getResources().getDimension(R.dimen.overview_peek_distance);
        return result;
    }
}
