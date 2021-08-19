package com.android.quickstep.util;

import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT;

import android.content.Context;
import android.os.IBinder;

import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.SplitConfigurationOptions;
import com.android.launcher3.util.SplitConfigurationOptions.StagePosition;
import com.android.launcher3.util.SplitConfigurationOptions.StageType;
import com.android.launcher3.util.SplitConfigurationOptions.StagedSplitTaskPosition;
import com.android.quickstep.SystemUiProxy;
import com.android.wm.shell.splitscreen.ISplitScreenListener;

/**
 * Listeners for system wide split screen position and stage changes.
 * Use {@link #getSplitTaskIds()} to determine which tasks, if any, are in staged split.
 */
public class LauncherSplitScreenListener extends ISplitScreenListener.Stub {

    public static final MainThreadInitializedObject<LauncherSplitScreenListener> INSTANCE =
            new MainThreadInitializedObject<>(LauncherSplitScreenListener::new);

    private final StagedSplitTaskPosition mMainStagePosition = new StagedSplitTaskPosition();
    private final StagedSplitTaskPosition mSideStagePosition = new StagedSplitTaskPosition();

    public LauncherSplitScreenListener(Context context) {
        mMainStagePosition.stageType = SplitConfigurationOptions.STAGE_TYPE_MAIN;
        mSideStagePosition.stageType = SplitConfigurationOptions.STAGE_TYPE_SIDE;
    }

    /** Also call {@link #destroy()} when done. */
    public void init() {
        SystemUiProxy.INSTANCE.getNoCreate().registerSplitScreenListener(this);
    }

    public void destroy() {
        SystemUiProxy.INSTANCE.getNoCreate().unregisterSplitScreenListener(this);
    }

    /**
     * @return index 0 will be task in left/top position, index 1 in right/bottom position.
     *         Will return empty array if device is not in staged split
     */
    public int[] getSplitTaskIds() {
        if (mMainStagePosition.taskId == -1 || mSideStagePosition.taskId == -1) {
            return new int[]{};
        }
        int[] out = new int[2];
        if (mMainStagePosition.stagePosition == STAGE_POSITION_TOP_OR_LEFT) {
            out[0] = mMainStagePosition.taskId;
            out[1] = mSideStagePosition.taskId;
        } else {
            out[1] = mMainStagePosition.taskId;
            out[0] = mSideStagePosition.taskId;
        }
        return out;
    }

    @Override
    public void onStagePositionChanged(@StageType int stage, @StagePosition int position) {
        if (stage == SplitConfigurationOptions.STAGE_TYPE_MAIN) {
            mMainStagePosition.stagePosition = position;
        } else {
            mSideStagePosition.stagePosition = position;
        }
    }

    @Override
    public void onTaskStageChanged(int taskId, @StageType int stage, boolean visible) {
        // If task is not visible but we are tracking it, stop tracking it
        if (!visible) {
            if (mMainStagePosition.taskId == taskId) {
                resetTaskId(mMainStagePosition);
            } else if (mSideStagePosition.taskId == taskId) {
                resetTaskId(mSideStagePosition);
            } // else it's an un-tracked child
            return;
        }

        // If stage has moved to undefined, stop tracking the task
        if (stage == SplitConfigurationOptions.STAGE_TYPE_UNDEFINED) {
            resetTaskId(taskId == mMainStagePosition.taskId ?
                    mMainStagePosition : mSideStagePosition);
            return;
        }

        if (stage == SplitConfigurationOptions.STAGE_TYPE_MAIN) {
            mMainStagePosition.taskId = taskId;
        } else {
            mSideStagePosition.taskId = taskId;
        }
    }

    private void resetTaskId(StagedSplitTaskPosition taskPosition) {
        taskPosition.taskId = -1;
    }

    @Override
    public IBinder asBinder() {
        return this;
    }
}
