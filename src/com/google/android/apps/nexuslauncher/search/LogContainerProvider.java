package com.google.android.apps.nexuslauncher.search;

import android.content.Context;
import android.widget.FrameLayout;

import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.logging.StatsLogUtils;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;
import java.util.ArrayList;

class LogContainerProvider extends FrameLayout implements StatsLogUtils.LogContainerProvider {
    private final int mPredictedRank;

    public LogContainerProvider(Context context, int predictedRank) {
        super(context);
        mPredictedRank = predictedRank;
    }

    @Override
    public void fillInLogContainerData(ItemInfo info, Target child, ArrayList<Target> parents) {
        if (mPredictedRank >= 0) {
            child.containerType = 7;
            child.predictedRank = mPredictedRank;
        } else {
            for (Target parent : parents) {
                parent.containerType = 8;
            }
        }
    }
}
