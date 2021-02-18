package com.google.android.apps.nexuslauncher.search;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.logging.StatsLogUtils;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;
import java.util.ArrayList;

class LogContainerProvider extends FrameLayout implements StatsLogUtils.LogContainerProvider {
    private final int mPredictedRank;

    public LogContainerProvider(Context context, int predictedRank) {
        super(context);
        mPredictedRank = predictedRank;
    }

    @Override
    public void fillInLogContainerData(View v, ItemInfo info, LauncherLogProto.Target target, LauncherLogProto.Target targetParent) {
        if (mPredictedRank >= 0) {
            targetParent.containerType = 7;
            target.predictedRank = mPredictedRank;
        } else {
            targetParent.containerType = 8;
        }
    }
}
