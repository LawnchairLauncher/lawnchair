/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.wm.shell.splitscreen;

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.content.res.Configuration.SCREEN_HEIGHT_DP_UNDEFINED;
import static android.content.res.Configuration.SCREEN_WIDTH_DP_UNDEFINED;
import static android.content.res.Configuration.SMALLEST_SCREEN_WIDTH_DP_UNDEFINED;
import static android.view.RemoteAnimationTarget.MODE_OPENING;

import static com.android.wm.shell.Flags.enableFlexibleSplit;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN;
import static com.android.wm.shell.shared.split.SplitScreenConstants.CONTROLLED_ACTIVITY_TYPES;
import static com.android.wm.shell.shared.split.SplitScreenConstants.CONTROLLED_WINDOWING_MODES;
import static com.android.wm.shell.shared.split.SplitScreenConstants.CONTROLLED_WINDOWING_MODES_WHEN_ACTIVE;
import static com.android.wm.shell.splitscreen.SplitScreen.stageTypeToString;

import android.annotation.CallSuper;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.TaskInfo;
import android.content.Context;
import android.graphics.Rect;
import android.os.IBinder;
import android.util.SparseArray;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.window.TaskOrganizer;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;

import com.android.internal.protolog.ProtoLog;
import com.android.internal.util.ArrayUtils;
import com.android.launcher3.icons.IconProvider;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.SurfaceUtils;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.common.split.SplitDecorManager;
import com.android.wm.shell.splitscreen.SplitScreen.StageType;
import com.android.wm.shell.windowdecor.WindowDecorViewModel;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Base class that handle common task org. related for split-screen stages.
 * Note that this class and its sub-class do not directly perform hierarchy operations.
 * They only serve to hold a collection of tasks and provide APIs like
 * {@link #addTask(ActivityManager.RunningTaskInfo, WindowContainerTransaction)} for the centralized
 * {@link StageCoordinator} to perform hierarchy operations in-sync with other containers.
 *
 * @see StageCoordinator
 */
public class StageTaskListener implements ShellTaskOrganizer.TaskListener {
    private static final String TAG = StageTaskListener.class.getSimpleName();

    // No current way to enforce this but if enableFlexibleSplit() is enabled, then only 1 of the
    // stages should have this be set/being used
    private boolean mIsActive;
    /** Unique identifier for this state, > 0 */
    @StageType private final int mId;
    /** Callback interface for listening to changes in a split-screen stage. */
    public interface StageListenerCallbacks {
        /** Called when the root task on current display appears. */
        void onRootTaskAppeared(ActivityManager.RunningTaskInfo taskInfo);

        void onStageVisibilityChanged(StageTaskListener stageTaskListener);

        void onChildTaskStatusChanged(StageTaskListener stage, int taskId, boolean present,
                boolean visible);


        /** Called when the root task on current display vanishes. */
        void onRootTaskVanished(ActivityManager.RunningTaskInfo taskInfo);

        void onNoLongerSupportMultiWindow(StageTaskListener stageTaskListener,
                ActivityManager.RunningTaskInfo taskInfo);
    }

    private final Context mContext;
    private final StageListenerCallbacks mCallbacks;
    private final SyncTransactionQueue mSyncQueue;
    private final IconProvider mIconProvider;
    private final Optional<WindowDecorViewModel> mWindowDecorViewModel;

    /** Whether or not the root task has been created. */
    boolean mHasRootTask = false;
    /** Whether or not the root task is visible. */
    boolean mVisible = false;
    /** Whether or not the root task has any children or not. */
    boolean mHasChildren = false;
    protected ActivityManager.RunningTaskInfo mRootTaskInfo;
    protected SurfaceControl mRootLeash;
    protected SurfaceControl mDimLayer;
    protected SparseArray<ActivityManager.RunningTaskInfo> mChildrenTaskInfo = new SparseArray<>();
    private final SparseArray<SurfaceControl> mChildrenLeashes = new SparseArray<>();
    // TODO(b/204308910): Extracts SplitDecorManager related code to common package.
    private SplitDecorManager mSplitDecorManager;

    StageTaskListener(Context context, ShellTaskOrganizer taskOrganizer, int displayId,
            StageListenerCallbacks callbacks, SyncTransactionQueue syncQueue,
            IconProvider iconProvider,
            Optional<WindowDecorViewModel> windowDecorViewModel, int id) {
        mContext = context;
        mCallbacks = callbacks;
        mSyncQueue = syncQueue;
        mIconProvider = iconProvider;
        mWindowDecorViewModel = windowDecorViewModel;
        taskOrganizer.createRootTask(
                new TaskOrganizer.CreateRootTaskRequest()
                        .setName(stageTypeToString(id).toLowerCase())
                        .setDisplayId(displayId)
                        .setWindowingMode(WINDOWING_MODE_MULTI_WINDOW),
                this);
        mId = id;
    }

    int getChildCount() {
        return mChildrenTaskInfo.size();
    }

    boolean containsTask(int taskId) {
        return mChildrenTaskInfo.contains(taskId);
    }

    boolean containsToken(WindowContainerToken token) {
        return contains(t -> t.token.equals(token));
    }

    boolean containsContainer(IBinder binder) {
        return contains(t -> t.token.asBinder() == binder);
    }

    /**
     * Returns the top visible child task's id.
     */
    int getTopVisibleChildTaskId() {
        // TODO(b/378601156): This doesn't get the top task (translucent tasks are also
        //  visible-requested)
        final ActivityManager.RunningTaskInfo taskInfo = getChildTaskInfo(t -> t.isVisible
                && t.isVisibleRequested);
        return taskInfo != null ? taskInfo.taskId : INVALID_TASK_ID;
    }

    /**
     * Returns all visible child task's ids.
     */
    List<Integer> getAllVisibleChildTaskIds() {
        return getAllChildTaskInfos(t -> t.isVisible
                && t.isVisibleRequested && t.taskId != INVALID_TASK_ID).stream()
                .map(runningTaskInfo -> runningTaskInfo.taskId)
                .collect(Collectors.toList());
    }

    /**
     * Returns the top activity uid for the top child task.
     */
    int getTopChildTaskUid() {
        // TODO(b/378601156): This doesn't get the top task
        final ActivityManager.RunningTaskInfo taskInfo =
                getChildTaskInfo(t -> t.topActivityInfo != null);
        return taskInfo != null ? taskInfo.topActivityInfo.applicationInfo.uid : 0;
    }

    /** @return {@code true} if this listener contains the currently focused task. */
    boolean isFocused() {
        return contains(t -> t.isFocused);
    }

    @StageType
    int getId() {
        return mId;
    }

    private boolean contains(Predicate<ActivityManager.RunningTaskInfo> predicate) {
        if (mRootTaskInfo != null && predicate.test(mRootTaskInfo)) {
            return true;
        }

        return getChildTaskInfo(predicate) != null;
    }

    public SurfaceControl getRootLeash() {
        return mRootLeash;
    }

    public ActivityManager.RunningTaskInfo getRunningTaskInfo() {
        return mRootTaskInfo;
    }

    public SplitDecorManager getDecorManager() {
        return mSplitDecorManager;
    }

    /**
     * Gets the leash for this task's dim layer. We manipulate this surface's alpha to dim the app
     * when it's moving offscreen or toward dismissal.
     */
    public SurfaceControl getDimLayer() {
        return mDimLayer;
    }

    @Nullable
    private ActivityManager.RunningTaskInfo getChildTaskInfo(
            Predicate<ActivityManager.RunningTaskInfo> predicate) {
        for (int i = mChildrenTaskInfo.size() - 1; i >= 0; --i) {
            final ActivityManager.RunningTaskInfo taskInfo = mChildrenTaskInfo.valueAt(i);
            if (predicate.test(taskInfo)) {
                return taskInfo;
            }
        }
        return null;
    }

    private List<ActivityManager.RunningTaskInfo> getAllChildTaskInfos(
            Predicate<ActivityManager.RunningTaskInfo> predicate) {
        List<ActivityManager.RunningTaskInfo> matchingTasks = new ArrayList<>();
        for (int i = mChildrenTaskInfo.size() - 1; i >= 0; --i) {
            final ActivityManager.RunningTaskInfo taskInfo = mChildrenTaskInfo.valueAt(i);
            if (predicate.test(taskInfo)) {
                matchingTasks.add(taskInfo);
            }
        }
        return matchingTasks;
    }

    @Override
    @CallSuper
    public void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo, SurfaceControl leash) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "onTaskAppeared: taskId=%d taskParent=%d rootTask=%d "
                        + "stageId=%s taskActivity=%s",
                taskInfo.taskId, taskInfo.parentTaskId,
                mRootTaskInfo != null ? mRootTaskInfo.taskId : -1,
                stageTypeToString(mId), taskInfo.baseActivity);
        if (mRootTaskInfo == null) {
            mRootLeash = leash;
            mRootTaskInfo = taskInfo;
            mSplitDecorManager = new SplitDecorManager(
                    mRootTaskInfo.configuration,
                    mIconProvider);
            mHasRootTask = true;
            mCallbacks.onRootTaskAppeared(taskInfo);
            if (mVisible != mRootTaskInfo.isVisible) {
                mVisible = mRootTaskInfo.isVisible;
                mCallbacks.onStageVisibilityChanged(this);
            }
            mSyncQueue.runInSync(t -> mDimLayer =
                    SurfaceUtils.makeDimLayer(t, mRootLeash, "Dim layer"));
        } else if (taskInfo.parentTaskId == mRootTaskInfo.taskId) {
            final int taskId = taskInfo.taskId;
            mChildrenLeashes.put(taskId, leash);
            mChildrenTaskInfo.put(taskId, taskInfo);
            mCallbacks.onChildTaskStatusChanged(this, taskId, true /* present */,
                    taskInfo.isVisible && taskInfo.isVisibleRequested);
        } else {
            throw new IllegalArgumentException(this + "\n Unknown task: " + taskInfo
                    + "\n mRootTaskInfo: " + mRootTaskInfo);
        }
    }

    @Override
    @CallSuper
    public void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "onTaskInfoChanged: taskId=%d taskAct=%s "
                        + "stageId=%s",
                taskInfo.taskId, taskInfo.baseActivity, stageTypeToString(mId));
        mWindowDecorViewModel.ifPresent(viewModel -> viewModel.onTaskInfoChanged(taskInfo));
        if (mRootTaskInfo.taskId == taskInfo.taskId) {
            mRootTaskInfo = taskInfo;
        } else if (taskInfo.parentTaskId == mRootTaskInfo.taskId) {
            if (!taskInfo.supportsMultiWindow
                    || !ArrayUtils.contains(CONTROLLED_ACTIVITY_TYPES, taskInfo.getActivityType())
                    || !ArrayUtils.contains(CONTROLLED_WINDOWING_MODES_WHEN_ACTIVE,
                    taskInfo.getWindowingMode())) {
                ProtoLog.d(WM_SHELL_SPLIT_SCREEN,
                        "onTaskInfoChanged: task=%d no longer supports multiwindow",
                        taskInfo.taskId);
                // Leave split screen if the task no longer supports multi window or have
                // uncontrolled task.
                mCallbacks.onNoLongerSupportMultiWindow(this, taskInfo);
                return;
            }
            mChildrenTaskInfo.put(taskInfo.taskId, taskInfo);
            mCallbacks.onChildTaskStatusChanged(this, taskInfo.taskId, true /* present */,
                    taskInfo.isVisible && taskInfo.isVisibleRequested);
        } else {
            throw new IllegalArgumentException(this + "\n Unknown task: " + taskInfo
                    + "\n mRootTaskInfo: " + mRootTaskInfo);
        }
    }

    @Override
    @CallSuper
    public void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "onTaskVanished: task=%d stageId=%s",
                taskInfo.taskId, stageTypeToString(mId));
        final int taskId = taskInfo.taskId;
        mWindowDecorViewModel.ifPresent(vm -> vm.onTaskVanished(taskInfo));
        if (mRootTaskInfo.taskId == taskId) {
            mHasRootTask = false;
            mVisible = false;
            mHasChildren = false;
            mCallbacks.onRootTaskVanished(taskInfo);
            mRootTaskInfo = null;
            mRootLeash = null;
            mSyncQueue.runInSync(t -> {
                t.remove(mDimLayer);
                mSplitDecorManager.release(t);
            });
        } else if (mChildrenTaskInfo.contains(taskId)) {
            mChildrenTaskInfo.remove(taskId);
            mChildrenLeashes.remove(taskId);
            mCallbacks.onChildTaskStatusChanged(this, taskId, false /* present */,
                    taskInfo.isVisible);
        } else {
            throw new IllegalArgumentException(this + "\n Unknown task: " + taskInfo
                    + "\n mRootTaskInfo: " + mRootTaskInfo);
        }
    }

    @Override
    public void attachChildSurfaceToTask(int taskId, SurfaceControl.Builder b) {
        b.setParent(findTaskSurface(taskId));
    }

    @Override
    public void reparentChildSurfaceToTask(int taskId, SurfaceControl sc,
            SurfaceControl.Transaction t) {
        t.reparent(sc, findTaskSurface(taskId));
    }

    private SurfaceControl findTaskSurface(int taskId) {
        if (mRootTaskInfo.taskId == taskId) {
            return mRootLeash;
        } else if (mChildrenLeashes.contains(taskId)) {
            return mChildrenLeashes.get(taskId);
        } else {
            throw new IllegalArgumentException("There is no surface for taskId=" + taskId);
        }
    }

    boolean isRootTaskId(int taskId) {
        return mRootTaskInfo != null && mRootTaskInfo.taskId == taskId;
    }

    void onResizing(Rect newBounds, Rect sideBounds, Rect displayBounds,
            SurfaceControl.Transaction t, int offsetX, int offsetY, boolean immediately) {
        if (mSplitDecorManager != null && mRootTaskInfo != null) {
            mSplitDecorManager.onResizing(mRootTaskInfo, newBounds, sideBounds, displayBounds, t,
                    offsetX, offsetY, immediately);
        }
    }

    void onResized(SurfaceControl.Transaction t) {
        if (mSplitDecorManager != null) {
            mSplitDecorManager.onResized(t, null);
        }
    }

    void screenshotIfNeeded(SurfaceControl.Transaction t) {
        if (mSplitDecorManager != null) {
            mSplitDecorManager.screenshotIfNeeded(t);
        }
    }

    void fadeOutDecor(Runnable finishedCallback) {
        if (mSplitDecorManager != null) {
            mSplitDecorManager.fadeOutDecor(finishedCallback, false /* addDelay */);
        } else {
            finishedCallback.run();
        }
    }

    SplitDecorManager getSplitDecorManager() {
        return mSplitDecorManager;
    }

    void addTask(ActivityManager.RunningTaskInfo task, WindowContainerTransaction wct) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "addTask: task=%d", task.taskId);
        // Clear overridden bounds and windowing mode to make sure the child task can inherit
        // windowing mode and bounds from split root.
        wct.setWindowingMode(task.token, WINDOWING_MODE_UNDEFINED)
                .setBounds(task.token, null);

        wct.reparent(task.token, mRootTaskInfo.token, true /* onTop*/);
    }

    void reorderChild(int taskId, boolean onTop, WindowContainerTransaction wct) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "reorderChild: task=%d onTop=%b", taskId, onTop);
        if (!containsTask(taskId)) {
            return;
        }
        wct.reorder(mChildrenTaskInfo.get(taskId).token, onTop /* onTop */);
    }

    void doForAllChildTasks(Consumer<Integer> consumer) {
        for (int i = mChildrenTaskInfo.size() - 1; i >= 0; i--) {
            final ActivityManager.RunningTaskInfo taskInfo = mChildrenTaskInfo.valueAt(i);
            consumer.accept(taskInfo.taskId);
        }
    }

    void doForAllChildTaskInfos(Consumer<ActivityManager.RunningTaskInfo> consumer) {
        for (int i = mChildrenTaskInfo.size() - 1; i >= 0; i--) {
            final ActivityManager.RunningTaskInfo taskInfo = mChildrenTaskInfo.valueAt(i);
            consumer.accept(taskInfo);
        }
    }

    /** Collects all the current child tasks and prepares transaction to evict them to display. */
    void evictAllChildren(WindowContainerTransaction wct) {
        for (int i = mChildrenTaskInfo.size() - 1; i >= 0; i--) {
            final ActivityManager.RunningTaskInfo taskInfo = mChildrenTaskInfo.valueAt(i);
            evictChild(wct, taskInfo, "all");
        }
    }

    void evictOtherChildren(WindowContainerTransaction wct, Set<Integer> keepTaskIds) {
        for (int i = mChildrenTaskInfo.size() - 1; i >= 0; i--) {
            final ActivityManager.RunningTaskInfo taskInfo = mChildrenTaskInfo.valueAt(i);
            if (keepTaskIds.contains(taskInfo.taskId)) continue;
            evictChild(wct, taskInfo, "other_" + stageTypeToString(mId));
        }
    }

    void evictNonOpeningChildren(RemoteAnimationTarget[] apps, WindowContainerTransaction wct) {
        final SparseArray<ActivityManager.RunningTaskInfo> toBeEvict = mChildrenTaskInfo.clone();
        for (int i = 0; i < apps.length; i++) {
            if (apps[i].mode == MODE_OPENING) {
                toBeEvict.remove(apps[i].taskId);
            }
        }
        for (int i = toBeEvict.size() - 1; i >= 0; i--) {
            final ActivityManager.RunningTaskInfo taskInfo = toBeEvict.valueAt(i);
            evictChild(wct, taskInfo, "non-opening");
        }
    }

    void evictInvisibleChildren(WindowContainerTransaction wct) {
        for (int i = mChildrenTaskInfo.size() - 1; i >= 0; i--) {
            final ActivityManager.RunningTaskInfo taskInfo = mChildrenTaskInfo.valueAt(i);
            if (!taskInfo.isVisible) {
                evictChild(wct, taskInfo, "invisible");
            }
        }
    }

    void evictChild(WindowContainerTransaction wct, int taskId, String reason) {
        final ActivityManager.RunningTaskInfo taskInfo = mChildrenTaskInfo.get(taskId);
        if (taskInfo != null) {
            evictChild(wct, taskInfo, reason);
        }
    }

    private void evictChild(@NonNull WindowContainerTransaction wct, @NonNull TaskInfo taskInfo,
            @NonNull String reason) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "Evict child: task=%d reason=%s", taskInfo.taskId,
                reason);
        // We are reparenting the task, but not removing the task from mChildrenTaskInfo, so to
        // prevent this task from being considered as a top task for the roots, we need to override
        // the visibility of the soon-to-be-hidden task
        taskInfo.isVisible = false;
        taskInfo.isVisibleRequested = false;
        wct.reparent(taskInfo.token, null /* parent */, false /* onTop */);
    }

    void reparentTopTask(WindowContainerTransaction wct) {
        wct.reparentTasks(null /* currentParent */, mRootTaskInfo.token,
                CONTROLLED_WINDOWING_MODES, CONTROLLED_ACTIVITY_TYPES,
                true /* onTop */, true /* reparentTopOnly */);
    }

    void resetBounds(WindowContainerTransaction wct) {
        wct.setBounds(mRootTaskInfo.token, null);
        wct.setAppBounds(mRootTaskInfo.token, null);
        wct.setSmallestScreenWidthDp(mRootTaskInfo.token, SMALLEST_SCREEN_WIDTH_DP_UNDEFINED);
        wct.setScreenSizeDp(mRootTaskInfo.token, SCREEN_WIDTH_DP_UNDEFINED,
                SCREEN_HEIGHT_DP_UNDEFINED);
    }

    void onSplitScreenListenerRegistered(SplitScreen.SplitScreenListener listener,
            @StageType int stage) {
        for (int i = mChildrenTaskInfo.size() - 1; i >= 0; --i) {
            int taskId = mChildrenTaskInfo.keyAt(i);
            listener.onTaskStageChanged(taskId, stage,
                    mChildrenTaskInfo.get(taskId).isVisible);
        }
    }

    // ---------
    // Previously only used in MainStage
    boolean isActive() {
        return mIsActive;
    }

    void activate(WindowContainerTransaction wct, boolean includingTopTask) {
        if (mIsActive && !enableFlexibleSplit()) return;
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "activate: includingTopTask=%b stage=%s",
                includingTopTask, stageTypeToString(mId));

        if (includingTopTask) {
            reparentTopTask(wct);
        }

        if (enableFlexibleSplit()) {
            return;
        }
        mIsActive = true;
    }

    void deactivate(WindowContainerTransaction wct) {
        deactivate(wct, false /* toTop */);
    }

    void deactivate(WindowContainerTransaction wct, boolean reparentTasksToTop) {
        if (!mIsActive && !enableFlexibleSplit()) return;
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "deactivate: reparentTasksToTop=%b "
                        + "rootTaskInfo=%s stage=%s",
                reparentTasksToTop, mRootTaskInfo, stageTypeToString(mId));
        if (!enableFlexibleSplit()) {
            mIsActive = false;
        }

        if (mRootTaskInfo == null) return;
        final WindowContainerToken rootToken = mRootTaskInfo.token;
        wct.reparentTasks(
                rootToken,
                null /* newParent */,
                null /* windowingModes */,
                null /* activityTypes */,
                reparentTasksToTop);
    }

    // --------
    // Previously only used in SideStage. With flexible split this is called for all stages
    boolean removeAllTasks(WindowContainerTransaction wct, boolean toTop) {
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "remove all side stage tasks: childCount=%d toTop=%b "
                        + " stageI=%s",
                mChildrenTaskInfo.size(), toTop, stageTypeToString(mId));
        if (mChildrenTaskInfo.size() == 0) return false;
        wct.reparentTasks(
                mRootTaskInfo.token,
                null /* newParent */,
                null /* windowingModes */,
                null /* activityTypes */,
                toTop);
        return true;
    }

    boolean removeTask(int taskId, WindowContainerToken newParent, WindowContainerTransaction wct) {
        final ActivityManager.RunningTaskInfo task = mChildrenTaskInfo.get(taskId);
        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "remove side stage task: task=%d exists=%b", taskId,
                task != null);
        if (task == null) return false;
        wct.reparent(task.token, newParent, false /* onTop */);
        return true;
    }

    @Override
    public String toString() {
        return "mId: " + stageTypeToString(mId)
                + " mVisible: " + mVisible
                + " mActive: " + mIsActive
                + " mHasRootTask: " + mHasRootTask
                + " childSize: " + mChildrenTaskInfo.size();
    }

    @Override
    @CallSuper
    public void dump(@NonNull PrintWriter pw, String prefix) {
        final String innerPrefix = prefix + "  ";
        final String childPrefix = innerPrefix + "  ";
        if (mChildrenTaskInfo.size() > 0) {
            pw.println(prefix + "Children list:");
            for (int i = mChildrenTaskInfo.size() - 1; i >= 0; --i) {
                final ActivityManager.RunningTaskInfo taskInfo = mChildrenTaskInfo.valueAt(i);
                pw.println(childPrefix + "Task#" + i + " taskID=" + taskInfo.taskId
                        + " baseActivity=" + taskInfo.baseActivity);
            }
        }
        pw.println(prefix + "mHasRootTask=" + mHasRootTask);
        pw.println(prefix + "mVisible=" + mVisible);
        pw.println(prefix + "mHasChildren=" + mHasChildren);
    }
}
