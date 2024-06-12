/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.quickstep;

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_RECENTS;
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.content.Intent.ACTION_CHOOSER;
import static android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT;

import android.annotation.UserIdInt;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.util.SplitConfigurationOptions;
import com.android.launcher3.util.SplitConfigurationOptions.SplitStageInfo;
import com.android.launcher3.util.SplitConfigurationOptions.StagePosition;
import com.android.launcher3.util.SplitConfigurationOptions.StageType;
import com.android.launcher3.util.TraceHelper;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.Task.TaskKey;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;
import com.android.wm.shell.splitscreen.ISplitScreenListener;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * This class tracked the top-most task and  some 'approximate' task history to allow faster
 * system state estimation during touch interaction
 */
public class TopTaskTracker extends ISplitScreenListener.Stub
        implements TaskStackChangeListener, SafeCloseable {

    private static final String TAG = "TopTaskTracker";

    private static final boolean DEBUG = true;

    public static MainThreadInitializedObject<TopTaskTracker> INSTANCE =
            new MainThreadInitializedObject<>(TopTaskTracker::new);

    private static final int HISTORY_SIZE = 5;

    // Ordered list with first item being the most recent task.
    private final LinkedList<RunningTaskInfo> mOrderedTaskList = new LinkedList<>();

    private final Context mContext;
    private final SplitStageInfo mMainStagePosition = new SplitStageInfo();
    private final SplitStageInfo mSideStagePosition = new SplitStageInfo();
    private int mPinnedTaskId = INVALID_TASK_ID;

    private TopTaskTracker(Context context) {
        mContext = context;
        mMainStagePosition.stageType = SplitConfigurationOptions.STAGE_TYPE_MAIN;
        mSideStagePosition.stageType = SplitConfigurationOptions.STAGE_TYPE_SIDE;

        TaskStackChangeListeners.getInstance().registerTaskStackListener(this);
        SystemUiProxy.INSTANCE.get(context).registerSplitScreenListener(this);
    }

    @Override
    public void close() {
        TaskStackChangeListeners.getInstance().unregisterTaskStackListener(this);
        SystemUiProxy.INSTANCE.get(mContext).unregisterSplitScreenListener(this);
    }

    @Override
    public void onTaskRemoved(int taskId) {
        mOrderedTaskList.removeIf(rto -> rto.taskId == taskId);
        if (DEBUG) {
            Log.i(TAG, "onTaskRemoved: taskId=" + taskId);
        }
    }

    @Override
    public void onTaskMovedToFront(RunningTaskInfo taskInfo) {
        if (!mOrderedTaskList.isEmpty()
                && mOrderedTaskList.getFirst().taskId != taskInfo.taskId
                && DEBUG) {
            Log.i(TAG, "onTaskMovedToFront: (moved taskInfo to front) taskId=" + taskInfo.taskId
                    + ", baseIntent=" + taskInfo.baseIntent);
        }
        mOrderedTaskList.removeIf(rto -> rto.taskId == taskInfo.taskId);
        mOrderedTaskList.addFirst(taskInfo);

        // Keep the home display's top running task in the first while adding a non-home
        // display's task to the list, to avoid showing non-home display's task upon going to
        // Recents animation.
        if (taskInfo.displayId != DEFAULT_DISPLAY) {
            final RunningTaskInfo topTaskOnHomeDisplay = mOrderedTaskList.stream()
                    .filter(rto -> rto.displayId == DEFAULT_DISPLAY).findFirst().orElse(null);
            if (topTaskOnHomeDisplay != null) {
                if (DEBUG) {
                    Log.i(TAG, "onTaskMovedToFront: (removing top task on home display) taskId="
                            + topTaskOnHomeDisplay.taskId
                            + ", baseIntent=" + topTaskOnHomeDisplay.baseIntent);
                }
                mOrderedTaskList.removeIf(rto -> rto.taskId == topTaskOnHomeDisplay.taskId);
                mOrderedTaskList.addFirst(topTaskOnHomeDisplay);
            }
        }

        if (mOrderedTaskList.size() >= HISTORY_SIZE) {
            // If we grow in size, remove the last taskInfo which is not part of the split task.
            Iterator<RunningTaskInfo> itr = mOrderedTaskList.descendingIterator();
            while (itr.hasNext()) {
                RunningTaskInfo info = itr.next();
                if (info.taskId != taskInfo.taskId
                        && info.taskId != mMainStagePosition.taskId
                        && info.taskId != mSideStagePosition.taskId) {
                    if (DEBUG) {
                        Log.i(TAG, "onTaskMovedToFront: (removing task list overflow) taskId="
                                + taskInfo.taskId + ", baseIntent=" + taskInfo.baseIntent);
                    }
                    itr.remove();
                    return;
                }
            }
        }
    }

    @Override
    public void onStagePositionChanged(@StageType int stage, @StagePosition int position) {
        if (DEBUG) {
            Log.i(TAG, "onStagePositionChanged: stage=" + stage + ", position=" + position);
        }
        if (stage == SplitConfigurationOptions.STAGE_TYPE_MAIN) {
            mMainStagePosition.stagePosition = position;
        } else {
            mSideStagePosition.stagePosition = position;
        }
    }

    @Override
    public void onTaskStageChanged(int taskId, @StageType int stage, boolean visible) {
        if (DEBUG) {
            Log.i(TAG, "onTaskStageChanged: taskId=" + taskId
                    + ", stage=" + stage + ", visible=" + visible);
        }
        // If a task is not visible anymore or has been moved to undefined, stop tracking it.
        if (!visible || stage == SplitConfigurationOptions.STAGE_TYPE_UNDEFINED) {
            if (mMainStagePosition.taskId == taskId) {
                mMainStagePosition.taskId = INVALID_TASK_ID;
            } else if (mSideStagePosition.taskId == taskId) {
                mSideStagePosition.taskId = INVALID_TASK_ID;
            } // else it's an un-tracked child
            return;
        }

        if (stage == SplitConfigurationOptions.STAGE_TYPE_MAIN) {
            mMainStagePosition.taskId = taskId;
        } else {
            mSideStagePosition.taskId = taskId;
        }
    }

    @Override
    public void onActivityPinned(String packageName, int userId, int taskId, int stackId) {
        if (DEBUG) {
            Log.i(TAG, "onActivityPinned: packageName=" + packageName
                    + ", userId=" + userId + ", stackId=" + stackId);
        }
        mPinnedTaskId = taskId;
    }

    @Override
    public void onActivityUnpinned() {
        if (DEBUG) {
            Log.i(TAG, "onActivityUnpinned");
        }
        mPinnedTaskId = INVALID_TASK_ID;
    }

    /**
     * @return index 0 will be task in left/top position, index 1 in right/bottom position.
     * Will return empty array if device is not in staged split
     */
    public int[] getRunningSplitTaskIds() {
        if (mMainStagePosition.taskId == INVALID_TASK_ID
                || mSideStagePosition.taskId == INVALID_TASK_ID) {
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


    /**
     * Returns the CachedTaskInfo for the top most task
     */
    @NonNull
    @UiThread
    public CachedTaskInfo getCachedTopTask(boolean filterOnlyVisibleRecents) {
        if (filterOnlyVisibleRecents) {
            // Since we only know about the top most task, any filtering may not be applied on the
            // cache. The second to top task may change while the top task is still the same.
            RunningTaskInfo[] tasks = TraceHelper.allowIpcs("getCachedTopTask.true", () ->
                    ActivityManagerWrapper.getInstance().getRunningTasks(true));
            return new CachedTaskInfo(Arrays.asList(tasks));
        }

        if (mOrderedTaskList.isEmpty()) {
            RunningTaskInfo[] tasks = TraceHelper.allowIpcs("getCachedTopTask.false", () ->
                    ActivityManagerWrapper.getInstance().getRunningTasks(
                            false /* filterOnlyVisibleRecents */));
            Collections.addAll(mOrderedTaskList, tasks);
        }

        // Strip the pinned task
        ArrayList<RunningTaskInfo> tasks = new ArrayList<>(mOrderedTaskList);
        tasks.removeIf(t -> t.taskId == mPinnedTaskId);
        return new CachedTaskInfo(tasks);
    }

    public void dump(String prefix, PrintWriter writer) {
        writer.println(prefix + "TopTaskTracker:");

        writer.println(prefix + "\tmOrderedTaskList=[");
        for (RunningTaskInfo taskInfo : mOrderedTaskList) {
            writer.println(prefix + "\t\t(taskId=" + taskInfo.taskId
                    + "; baseIntent=" + taskInfo.baseIntent
                    + "; isRunning=" + taskInfo.isRunning + ")");
        }
        writer.println(prefix + "\t]");
        writer.println(prefix + "\tmMainStagePosition=" + mMainStagePosition);
        writer.println(prefix + "\tmSideStagePosition=" + mSideStagePosition);
        writer.println(prefix + "\tmPinnedTaskId=" + mPinnedTaskId);
    }

    /**
     * Class to provide information about a task which can be safely cached and do not change
     * during the lifecycle of the task.
     */
    public static class CachedTaskInfo {

        @Nullable
        private final RunningTaskInfo mTopTask;
        public final List<RunningTaskInfo> mAllCachedTasks;

        CachedTaskInfo(List<RunningTaskInfo> allCachedTasks) {
            mAllCachedTasks = allCachedTasks;
            mTopTask = allCachedTasks.isEmpty() ? null : allCachedTasks.get(0);
        }

        public int getTaskId() {
            return mTopTask == null ? INVALID_TASK_ID : mTopTask.taskId;
        }

        /**
         * Returns true if the root of the task chooser activity
         */
        public boolean isRootChooseActivity() {
            return mTopTask != null && ACTION_CHOOSER.equals(mTopTask.baseIntent.getAction());
        }

        /**
         * If the given task holds an activity that is excluded from recents, and there
         * is another running task that is not excluded from recents, returns that underlying task.
         */
        public @Nullable CachedTaskInfo otherVisibleTaskThisIsExcludedOver() {
            if (mTopTask == null
                    || (mTopTask.baseIntent.getFlags() & FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) == 0) {
                // Not an excluded task.
                return null;
            }
            List<RunningTaskInfo> visibleNonExcludedTasks = mAllCachedTasks.stream()
                    .filter(t -> t.isVisible
                            && (t.baseIntent.getFlags() & FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) == 0)
                    .toList();
            return visibleNonExcludedTasks.isEmpty() ? null
                    : new CachedTaskInfo(visibleNonExcludedTasks);
        }

        /**
         * Returns true if this represents the HOME task
         */
        public boolean isHomeTask() {
            return mTopTask != null && mTopTask.configuration.windowConfiguration
                    .getActivityType() == ACTIVITY_TYPE_HOME;
        }

        public boolean isRecentsTask() {
            return mTopTask != null && mTopTask.configuration.windowConfiguration
                    .getActivityType() == ACTIVITY_TYPE_RECENTS;
        }

        /**
         * Returns {@code true} if this task windowing mode is set to {@link
         * android.app.WindowConfiguration#WINDOWING_MODE_FREEFORM}
         */
        public boolean isFreeformTask() {
            return mTopTask != null && mTopTask.configuration.windowConfiguration.getWindowingMode()
                    == WINDOWING_MODE_FREEFORM;
        }

        /**
         * Returns {@link Task} array which can be used as a placeholder until the true object
         * is loaded by the model
         */
        public Task[] getPlaceholderTasks() {
            return mTopTask == null ? new Task[0]
                    : new Task[]{Task.from(new TaskKey(mTopTask), mTopTask, false)};
        }

        /**
         * Returns {@link Task} array corresponding to the provided task ids which can be used as a
         * placeholder until the true object is loaded by the model
         */
        public Task[] getPlaceholderTasks(int[] taskIds) {
            if (mTopTask == null) {
                return new Task[0];
            }
            Task[] result = new Task[taskIds.length];
            for (int i = 0; i < taskIds.length; i++) {
                final int index = i;
                int taskId = taskIds[i];
                mAllCachedTasks.forEach(rti -> {
                    if (rti.taskId == taskId) {
                        result[index] = Task.from(new TaskKey(rti), rti, false);
                    }
                });
            }
            return result;
        }

        @UserIdInt
        @Nullable
        public Integer getUserId() {
            return mTopTask == null ? null : mTopTask.userId;
        }

        @Nullable
        public String getPackageName() {
            if (mTopTask == null || mTopTask.baseActivity == null) {
                return null;
            }
            return mTopTask.baseActivity.getPackageName();
        }
    }
}
