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
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.content.Intent.ACTION_CHOOSER;
import static android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Display.INVALID_DISPLAY;

import static com.android.launcher3.BuildConfig.DEBUG;
import static com.android.quickstep.fallback.window.RecentsWindowFlags.enableOverviewOnConnectedDisplays;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_TYPE_A;
import static com.android.wm.shell.Flags.enableShellTopTaskTracking;
import static com.android.wm.shell.Flags.enableFlexibleSplit;
import static com.android.wm.shell.shared.GroupedTaskInfo.TYPE_DESK;
import static com.android.wm.shell.shared.GroupedTaskInfo.TYPE_SPLIT;
import static com.android.launcher3.statehandlers.DesktopVisibilityController.INACTIVE_DESK_ID;
import static com.android.wm.shell.shared.desktopmode.DesktopModeStatus.canEnterDesktopMode;
import static com.android.wm.shell.shared.desktopmode.DesktopModeStatus.enableMultipleDesktops;

import android.app.ActivityManager.RunningTaskInfo;
import android.app.TaskInfo;
import android.app.WindowConfiguration;
import android.content.Context;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.statehandlers.DesktopVisibilityController;
import com.android.launcher3.util.DaggerSingletonObject;
import com.android.launcher3.util.DaggerSingletonTracker;
import com.android.launcher3.util.SplitConfigurationOptions;
import com.android.launcher3.util.SplitConfigurationOptions.SplitStageInfo;
import com.android.launcher3.util.SplitConfigurationOptions.StagePosition;
import com.android.launcher3.util.SplitConfigurationOptions.StageType;
import com.android.launcher3.util.TraceHelper;
import com.android.quickstep.dagger.QuickstepBaseAppComponent;
import com.android.quickstep.util.DesksUtils;
import com.android.quickstep.util.ExternalDisplaysKt;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;
import com.android.wm.shell.shared.GroupedTaskInfo;
import com.android.wm.shell.splitscreen.ISplitScreenListener;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.Collectors;

import javax.inject.Inject;

import app.lawnchair.compat.LawnchairQuickstepCompat;

/**
 * This class tracked the top-most task and  some 'approximate' task history to allow faster
 * system state estimation during touch interaction
 */
@LauncherAppSingleton
public class TopTaskTracker extends ISplitScreenListener.Stub implements TaskStackChangeListener {
    private static final String TAG = "TopTaskTracker";
    public static DaggerSingletonObject<TopTaskTracker> INSTANCE =
            new DaggerSingletonObject<>(QuickstepBaseAppComponent::getTopTaskTracker);

    private static final int HISTORY_SIZE = 5;

    // Only used when Flags.enableShellTopTaskTracking() is disabled
    // Ordered list with first item being the most recent task.
    private final LinkedList<TaskInfo> mOrderedTaskList = new LinkedList<>();
    private final SplitStageInfo mMainStagePosition = new SplitStageInfo();
    private final SplitStageInfo mSideStagePosition = new SplitStageInfo();
    private int mPinnedTaskId = INVALID_TASK_ID;

    // Only used when Flags.enableShellTopTaskTracking() is enabled
    // Mapping of display id to visible tasks.  Visible tasks are ordered from top most to bottom
    // most.
    private ArrayMap<Integer, GroupedTaskInfo> mVisibleTasks = new ArrayMap<>();

    private final Context mContext;
    private final DesktopVisibilityController mDesktopVisibilityController;

    @Inject
    public TopTaskTracker(@ApplicationContext Context context, DaggerSingletonTracker tracker,
            SystemUiProxy systemUiProxy, DesktopVisibilityController desktopVisibilityController) {
        if (!enableShellTopTaskTracking()) {
            mMainStagePosition.stageType = SplitConfigurationOptions.STAGE_TYPE_MAIN;
            mSideStagePosition.stageType = SplitConfigurationOptions.STAGE_TYPE_SIDE;

            TaskStackChangeListeners.getInstance().registerTaskStackListener(this);
            systemUiProxy.registerSplitScreenListener(this);
        }

        tracker.addCloseable(() -> {
            if (enableShellTopTaskTracking()) {
                return;
            }

            TaskStackChangeListeners.getInstance().unregisterTaskStackListener(this);
            systemUiProxy.unregisterSplitScreenListener(this);
        });

        mContext = context;
        mDesktopVisibilityController = desktopVisibilityController;
    }

    @Override
    public void onTaskRemoved(int taskId) {
        if (enableShellTopTaskTracking()) {
            return;
        }

        mOrderedTaskList.removeIf(rto -> rto.taskId == taskId);
    }

    @Override
    public void onTaskMovedToFront(RunningTaskInfo taskInfo) {
        handleTaskMovedToFront(taskInfo);
    }

    void handleTaskMovedToFront(TaskInfo taskInfo) {
        if (enableShellTopTaskTracking()) {
            return;
        }

        mOrderedTaskList.removeIf(rto -> rto.taskId == taskInfo.taskId);
        mOrderedTaskList.addFirst(taskInfo);

        // Workaround for b/372067617, b/390564114, if the home task or any fullscreen occluding
        // task is brought to front, then mark other tasks behind as not-visible
        if (taskInfo.getActivityType() == ACTIVITY_TYPE_HOME
                || (taskInfo.getWindowingMode() == WINDOWING_MODE_FULLSCREEN
                        && !taskInfo.isActivityStackTransparent)) {
            // We've moved the task to the front of the list above, so only iterate the tasks after
            for (int i = 1; i < mOrderedTaskList.size(); i++) {
                final TaskInfo info = mOrderedTaskList.get(i);
                if (info.displayId != taskInfo.displayId) {
                    // Only fall through to reset visibility for tasks on the same display as the
                    // home task being brought forward
                    continue;
                }
                info.isVisible = false;
                info.isVisibleRequested = false;
            }
        }

        // Keep the home display's top running task in the first while adding a non-home
        // display's task to the list, to avoid showing non-home display's task upon going to
        // Recents animation.
        if (taskInfo.displayId != DEFAULT_DISPLAY) {
            final TaskInfo topTaskOnHomeDisplay = mOrderedTaskList.stream()
                    .filter(rto -> rto.displayId == DEFAULT_DISPLAY).findFirst().orElse(null);
            if (topTaskOnHomeDisplay != null) {
                mOrderedTaskList.removeIf(rto -> rto.taskId == topTaskOnHomeDisplay.taskId);
                mOrderedTaskList.addFirst(topTaskOnHomeDisplay);
            }
        }

        if (mOrderedTaskList.size() >= HISTORY_SIZE) {
            // If we grow in size, remove the last taskInfo which is not part of the split task.
            Iterator<TaskInfo> itr = mOrderedTaskList.descendingIterator();
            while (itr.hasNext()) {
                TaskInfo info = itr.next();
                if (info.taskId != taskInfo.taskId
                        && info.taskId != mMainStagePosition.taskId
                        && info.taskId != mSideStagePosition.taskId) {
                    itr.remove();
                    return;
                }
            }
        }
    }

    /**
     * Called when the set of visible tasks have changed.
     */
    public void onVisibleTasksChanged(GroupedTaskInfo[] visibleTasks) {
        if (!enableShellTopTaskTracking()) {
            return;
        }

        // Clear existing tasks for each display
        mVisibleTasks.clear();

        // Update the visible tasks on each display
        Log.d(TAG, "onVisibleTasksChanged:");
        for (GroupedTaskInfo groupedTask : visibleTasks) {
            Log.d(TAG, "\t" + groupedTask);
            GroupedTaskInfo baseGroupedTask = groupedTask.getBaseGroupedTask();
            int displayId;
            if (enableMultipleDesktops(mContext) && baseGroupedTask.isBaseType(TYPE_DESK)) {
                displayId = baseGroupedTask.getDeskDisplayId();
            } else {
                displayId = baseGroupedTask.getTaskInfo1().getDisplayId();
            }
            mVisibleTasks.put(displayId, groupedTask);
        }
    }

    @Override
    public void onStagePositionChanged(@StageType int stage, @StagePosition int position) {
        if (enableShellTopTaskTracking()) {
            return;
        }

        if (stage == SplitConfigurationOptions.STAGE_TYPE_MAIN) {
            mMainStagePosition.stagePosition = position;
        } else {
            mSideStagePosition.stagePosition = position;
        }
    }

    public void onTaskChanged(RunningTaskInfo taskInfo) {
        if (enableShellTopTaskTracking()) {
            return;
        }

        for (int i = 0; i < mOrderedTaskList.size(); i++) {
            if (mOrderedTaskList.get(i).taskId == taskInfo.taskId) {
                mOrderedTaskList.set(i, taskInfo);
                break;
            }
        }
    }

    @Override
    public void onTaskStageChanged(int taskId, @StageType int stage, boolean visible) {
        if (enableShellTopTaskTracking()) {
            return;
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

        if (stage == SplitConfigurationOptions.STAGE_TYPE_MAIN
                || (enableFlexibleSplit() && stage == STAGE_TYPE_A)) {
            mMainStagePosition.taskId = taskId;
        } else {
            mSideStagePosition.taskId = taskId;
        }
    }

    @Override
    public void onActivityPinned(String packageName, int userId, int taskId, int stackId) {
        if (enableShellTopTaskTracking()) {
            return;
        }

        mPinnedTaskId = taskId;
    }

    @Override
    public void onActivityUnpinned() {
        if (enableShellTopTaskTracking()) {
            return;
        }

        mPinnedTaskId = INVALID_TASK_ID;
    }

    /**
     * Return the running split task ids.  Index 0 will be task in left/top position, index 1 in
     * right/bottom position, or and empty array if device is not in splitscreen.
     */
    public int[] getRunningSplitTaskIds() {
        if (enableShellTopTaskTracking()) {
            // TODO(346588978): This assumes default display as splitscreen is only currently there
            final GroupedTaskInfo visibleTasks = mVisibleTasks.get(DEFAULT_DISPLAY);
            final GroupedTaskInfo splitTaskInfo =
                    visibleTasks != null && visibleTasks.isBaseType(TYPE_SPLIT)
                            ? visibleTasks.getBaseGroupedTask()
                            : null;
            if (splitTaskInfo != null && splitTaskInfo.getSplitBounds() != null) {
                return new int[] {
                        splitTaskInfo.getSplitBounds().leftTopTaskId,
                        splitTaskInfo.getSplitBounds().rightBottomTaskId
                };
            }
            return new int[0];
        } else {
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
    }

    /**
     * Dumps the list of tasks in top task tracker.
     */
    public void dump(PrintWriter pw) {
        if (!enableShellTopTaskTracking()) {
            return;
        }

        pw.println("TopTaskTracker:");
        mVisibleTasks.forEach((displayId, tasks) ->
                pw.println("  visibleTasks(" + displayId + "): " + tasks));
    }

    /**
     * Returns the CachedTaskInfo for the top most task
     */
    @NonNull
    @UiThread
    public CachedTaskInfo getCachedTopTask(boolean filterOnlyVisibleRecents, int displayId) {
        if (enableShellTopTaskTracking()) {
            // TODO(346588978): Currently ignore filterOnlyVisibleRecents, but perhaps make this an
            //  explicit filter For things to ignore (ie. PIP/Bubbles/Assistant/etc/so that this is
            //  explicit)
            return new CachedTaskInfo(mVisibleTasks.get(displayId));
        } else {
            int activeDeskId = mDesktopVisibilityController.getActiveDeskId(displayId);
            if (filterOnlyVisibleRecents) {
                // Since we only know about the top most task, any filtering may not be applied on
                // the cache. The second to top task may change while the top task is still the
                // same.
                TaskInfo[] tasks = TraceHelper.allowIpcs("getCachedTopTask.true", () ->
                        ActivityManagerWrapper.getInstance().getRunningTasks(true));
                if (enableOverviewOnConnectedDisplays()) {
                    return new CachedTaskInfo(Arrays.stream(tasks).filter(
                            info -> ExternalDisplaysKt.getSafeDisplayId(info)
                                    == displayId).toList(), mContext, displayId, activeDeskId);
                } else {
                    return new CachedTaskInfo(Arrays.asList(tasks), mContext,
                            displayId, activeDeskId);
                }
            }

            if (mOrderedTaskList.isEmpty()) {
                RunningTaskInfo[] tasks = TraceHelper.allowIpcs("getCachedTopTask.false", () ->
                        ActivityManagerWrapper.getInstance().getRunningTasks(
                                false /* filterOnlyVisibleRecents */));
                Collections.addAll(mOrderedTaskList, tasks);
            }

            Stream<TaskInfo> taskStream = mOrderedTaskList.stream()
                    // Strip the pinned task and recents task.
                    .filter(t -> t.taskId != mPinnedTaskId && !isRecentsTask(t));
            if (enableOverviewOnConnectedDisplays()) {
                taskStream = taskStream.filter(
                        info -> ExternalDisplaysKt.getSafeDisplayId(info) == displayId);
            }
            if (enableMultipleDesktops(mContext)) {
                taskStream = taskStream.takeWhile(
                        taskInfo -> !DesksUtils.isDesktopWallpaperTask(taskInfo));
            } else {
                taskStream = taskStream.filter(
                        taskInfo -> !DesksUtils.isDesktopWallpaperTask(taskInfo));
            }

            return new CachedTaskInfo(taskStream.toList(), mContext, displayId, activeDeskId);
        }
    }

    private static boolean isHomeTask(TaskInfo task) {
        return task != null && task.configuration.windowConfiguration
                .getActivityType() == ACTIVITY_TYPE_HOME;
    }

    private static boolean isRecentsTask(TaskInfo task) {
        return task != null && task.configuration.windowConfiguration
                .getActivityType() == ACTIVITY_TYPE_RECENTS;
    }

    /**
     * Class to provide information about a task which can be safely cached and do not change
     * during the lifecycle of the task.
     */
    public static class CachedTaskInfo {
        // Only used when enableShellTopTaskTracking() is disabled.
        private int mDisplayId = INVALID_DISPLAY;
        // Only used when enableShellTopTaskTracking() is disabled
        @Nullable
        private final TaskInfo mTopTask;
        @Nullable
        public final List<TaskInfo> mAllCachedTasks;

        // Only used when enableShellTopTaskTracking() is enabled
        @Nullable
        private final GroupedTaskInfo mVisibleTasks;

        private Context mContext;
        private final int mActiveDeskId;

        // Only used when enableShellTopTaskTracking() is enabled
        CachedTaskInfo(@Nullable GroupedTaskInfo visibleTasks) {
            mAllCachedTasks = null;
            mTopTask = null;
            mVisibleTasks = visibleTasks;
            mActiveDeskId = INACTIVE_DESK_ID;
        }

        // Only used when enableShellTopTaskTracking() is disabled
        CachedTaskInfo(@NonNull List<TaskInfo> allCachedTasks, Context context,
                int displayId, int activeDeskId) {
            mDisplayId = displayId;
            mVisibleTasks = null;
            mAllCachedTasks = allCachedTasks;
            mTopTask = allCachedTasks.isEmpty() ? null : allCachedTasks.get(0);
            mContext = context;
            mActiveDeskId = activeDeskId;
        }

        /**
         * Returns the "base" task that is used the as the representative running task of the set
         * of tasks initially provided.
         *
         * Not for general use, as in other windowing modes (ie. split/desktop) the caller should
         * not make assumptions about there being a single base task.
         * TODO(346588978): Try to remove all usage of this if possible
         */
        @Nullable
        private TaskInfo getLegacyBaseTask() {
            if (enableShellTopTaskTracking()) {
                return mVisibleTasks != null
                        ? mVisibleTasks.getBaseGroupedTask().getTaskInfo1()
                        : null;
            } else {
                return mTopTask;
            }
        }

        /**
         * Returns the top task id.
         */
        public int getTaskId() {
            if (enableShellTopTaskTracking()) {
                // Callers should use topGroupedTaskContainsTask() instead
                return INVALID_TASK_ID;
            } else {
                return mTopTask != null ? mTopTask.taskId : INVALID_TASK_ID;
            }
        }

        /**
         * Returns the top grouped task ids if Flags.enableShellTopTaskTracking() is true, otherwise
         * an empty array.
         */
        public int[] topGroupedTaskIds() {
            if (enableShellTopTaskTracking()) {
                if (mVisibleTasks == null) {
                    return new int[0];
                }
                List<TaskInfo> groupedTasks = mVisibleTasks.getTaskInfoList();
                return groupedTasks.stream().mapToInt(
                        groupedTask -> groupedTask.taskId).toArray();
            } else {
                // Not used
                return new int[0];
            }
        }

        /**
         * Returns whether the top grouped task contains the given {@param taskId} if
         * Flags.enableShellTopTaskTracking() is true, otherwise it checks the top task as reported
         * from TaskStackListener.
         */
        public boolean topGroupedTaskContainsTask(int taskId) {
            if (enableShellTopTaskTracking()) {
                return mVisibleTasks != null && mVisibleTasks.containsTask(taskId);
            } else {
                return mTopTask != null && mTopTask.taskId == taskId;
            }
        }

        /**
         * Returns true if this represents the task chooser activity
         */
        public boolean isRootChooseActivity() {
            final TaskInfo baseTask = getLegacyBaseTask();
            return baseTask != null && ACTION_CHOOSER.equals(baseTask.baseIntent.getAction());
        }

        /**
         * Returns true if this represents the HOME activity type task
         */
        public boolean isHomeTask() {
            final TaskInfo baseTask = getLegacyBaseTask();
            return baseTask != null && TopTaskTracker.isHomeTask(baseTask);
        }

        /**
         * Returns true if this represents the RECENTS activity type task
         */
        public boolean isRecentsTask() {
            final TaskInfo baseTask = getLegacyBaseTask();
            return baseTask != null && TopTaskTracker.isRecentsTask(baseTask);
        }

        /**
         * If the given task holds an activity that is excluded from recents, and there
         * is another running task that is not excluded from recents, returns that underlying task.
         */
        public @Nullable CachedTaskInfo getVisibleNonExcludedTask() {
            if (enableShellTopTaskTracking()) {
                // Callers should not need this when the full set of visible tasks are provided
                return null;
            }
            if (mTopTask == null
                    || (mTopTask.baseIntent.getFlags() & FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) == 0) {
                // Not an excluded task.
                return null;
            }
            List<TaskInfo> visibleNonExcludedTasks = mAllCachedTasks.stream()
                    .filter(t -> LawnchairQuickstepCompat.ATLEAST_S && t.isVisible
                            && (t.baseIntent.getFlags() & FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) == 0
                            && t.getActivityType() != ACTIVITY_TYPE_HOME
                            && t.getActivityType() != ACTIVITY_TYPE_RECENTS)
                    .toList();
            return visibleNonExcludedTasks.isEmpty() ? null
                    : new CachedTaskInfo(visibleNonExcludedTasks, mContext, mDisplayId,
                            mActiveDeskId);
        }

        /**
         * Returns {@link TaskInfo} array corresponding to the provided task ids which can be
         * used as a placeholder until the true object is loaded by the model. Only used when
         * enableShellTopTaskTracking() is disabled.
         */
        private TaskInfo[] getSplitPlaceholderTasksInfo(int[] splitTaskIds) {
            if (mTopTask == null) {
                return new TaskInfo[0];
            }
            TaskInfo[] result = new TaskInfo[splitTaskIds.length];
            for (int i = 0; i < splitTaskIds.length; i++) {
                final int index = i;
                int taskId = splitTaskIds[i];
                mAllCachedTasks.forEach(rti -> {
                    if (rti.taskId == taskId) {
                        result[index] = rti;
                    }
                });
            }
            return result;
        }

        private boolean isDesktopTask(@Nullable TaskInfo taskInfo) {
            return taskInfo != null && canEnterDesktopMode(mContext)
                    && taskInfo.configuration.windowConfiguration.getWindowingMode()
                    == WindowConfiguration.WINDOWING_MODE_FREEFORM;
        }

        // TODO(346588978): Update this to return more than a single task once the callers
        //  are refactored.
        /**
         * Returns a {@link GroupedTaskInfo} which can be used as a placeholder until the true
         * object is loaded by the model.
         *
         * @param splitTaskIds provide if it is for split, which represents the task ids of the
         *                     paired tasks. Otherwise, provide null.
         */
        public @Nullable GroupedTaskInfo getPlaceholderGroupedTaskInfo(
                @Nullable int[] splitTaskIds) {
            if (enableShellTopTaskTracking()) {
                if (mVisibleTasks == null) {
                    return null;
                }
                return mVisibleTasks.getBaseGroupedTask();
            } else {
                if (splitTaskIds != null && splitTaskIds.length >= 2) {
                    TaskInfo[] splitTasksInfo = getSplitPlaceholderTasksInfo(splitTaskIds);
                    if (splitTasksInfo[0] == null || splitTasksInfo[1] == null) {
                        return null;
                    }
                    return GroupedTaskInfo.forSplitTasks(splitTasksInfo[0],
                            splitTasksInfo[1], /* splitBounds = */ null);
                } else {
                    final TaskInfo baseTaskInfo = getLegacyBaseTask();
                    if (enableMultipleDesktops(mContext)) {
                        if (mActiveDeskId != INACTIVE_DESK_ID) {
                            return GroupedTaskInfo.forDeskTasks(
                                    mActiveDeskId, mDisplayId, mAllCachedTasks,
                                    /* minimizedFreeformTaskIds = */ Collections.emptySet());
                        }
                    } else if (isDesktopTask(baseTaskInfo)) {
                        return GroupedTaskInfo.forDeskTasks(INACTIVE_DESK_ID, mDisplayId,
                                Collections.singletonList(
                                        baseTaskInfo), /* minimizedFreeformTaskIds = */
                                Collections.emptySet());
                    }
                    return baseTaskInfo == null
                            ? null
                            : GroupedTaskInfo.forFullscreenTasks(baseTaskInfo);
                }
            }
        }

        @Nullable
        public String getPackageName() {
            final TaskInfo baseTask = getLegacyBaseTask();
            if (baseTask == null || baseTask.baseActivity == null) {
                return null;
            }
            return baseTask.baseActivity.getPackageName();
        }
    }
}
