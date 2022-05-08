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
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;
import com.android.wm.shell.splitscreen.ISplitScreenListener;

/**
 * Listeners for system wide split screen position and stage changes.
 *
 * Use {@link #getRunningSplitTaskIds()} to determine which tasks, if any, are actively in
 * staged split.
 *
 * Use {@link #getPersistentSplitIds()} to know if tasks were in split screen before a quickswitch
 * gesture happened.
 */
public class LauncherSplitScreenListener extends ISplitScreenListener.Stub {

    public static final MainThreadInitializedObject<LauncherSplitScreenListener> INSTANCE =
            new MainThreadInitializedObject<>(LauncherSplitScreenListener::new);

    private static final int[] EMPTY_ARRAY = {};

    private final StagedSplitTaskPosition mMainStagePosition = new StagedSplitTaskPosition();
    private final StagedSplitTaskPosition mSideStagePosition = new StagedSplitTaskPosition();

    private boolean mIsRecentsListFrozen = false;
    private final TaskStackChangeListener mTaskStackListener = new TaskStackChangeListener() {
        @Override
        public void onRecentTaskListFrozenChanged(boolean frozen) {
            super.onRecentTaskListFrozenChanged(frozen);
            mIsRecentsListFrozen = frozen;

            if (frozen) {
                mPersistentGroupedIds = getRunningSplitTaskIds();
            } else {
                mPersistentGroupedIds = EMPTY_ARRAY;
            }
        }
    };

    /**
     * Gets set to current split taskIDs whenever the task list is frozen, and set to empty array
     * whenever task list unfreezes. This also gets set to empty array whenever the user swipes to
     * home - in that case the task list does not unfreeze immediately after the gesture, so it's
     * done via {@link #notifySwipingToHome()}.
     *
     * When not empty, this indicates that we need to load a GroupedTaskView as the most recent
     * page, so user can quickswitch back to a grouped task.
     */
    private int[] mPersistentGroupedIds;

    public LauncherSplitScreenListener(Context context) {
        mMainStagePosition.stageType = SplitConfigurationOptions.STAGE_TYPE_MAIN;
        mSideStagePosition.stageType = SplitConfigurationOptions.STAGE_TYPE_SIDE;
    }

    /** Also call {@link #destroy()} when done. */
    public void init() {
        SystemUiProxy.INSTANCE.getNoCreate().registerSplitScreenListener(this);
        TaskStackChangeListeners.getInstance().registerTaskStackListener(mTaskStackListener);
    }

    public void destroy() {
        SystemUiProxy.INSTANCE.getNoCreate().unregisterSplitScreenListener(this);
        TaskStackChangeListeners.getInstance().unregisterTaskStackListener(mTaskStackListener);
    }

    /**
     * This method returns the active split taskIDs that were active if a user quickswitched from
     * split screen to a fullscreen app as long as the recents task list remains frozen.
     */
    public int[] getPersistentSplitIds() {
        if (mIsRecentsListFrozen) {
            return mPersistentGroupedIds;
        } else {
            return getRunningSplitTaskIds();
        }
    }
    /**
     * @return index 0 will be task in left/top position, index 1 in right/bottom position.
     *         Will return empty array if device is not in staged split
     */
    public int[] getRunningSplitTaskIds() {
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

    /** Notifies SystemUi to remove any split screen state */
    public void notifySwipingToHome() {
        boolean hasSplitTasks = LauncherSplitScreenListener.INSTANCE.getNoCreate()
                .getPersistentSplitIds().length > 0;
        if (!hasSplitTasks) {
            return;
        }

        mPersistentGroupedIds = EMPTY_ARRAY;
    }

    private void resetTaskId(StagedSplitTaskPosition taskPosition) {
        taskPosition.taskId = -1;
    }

    @Override
    public IBinder asBinder() {
        return this;
    }
}
