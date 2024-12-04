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

package com.android.wm.shell;


import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;

import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_TASK_ORG;
import static com.android.wm.shell.transition.Transitions.ENABLE_SHELL_TRANSITIONS;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.CameraCompatTaskInfo.CameraCompatControlState;
import android.app.TaskInfo;
import android.app.WindowConfiguration;
import android.content.LocusId;
import android.content.pm.ActivityInfo;
import android.graphics.Rect;
import android.os.Binder;
import android.os.IBinder;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;
import android.view.SurfaceControl;
import android.window.ITaskOrganizerController;
import android.window.ScreenCapture;
import android.window.StartingWindowInfo;
import android.window.StartingWindowRemovalInfo;
import android.window.TaskAppearedInfo;
import android.window.TaskOrganizer;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.common.ProtoLog;
import com.android.internal.util.FrameworkStatsLog;
import com.android.wm.shell.common.ScreenshotUtils;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.compatui.CompatUIController;
import com.android.wm.shell.recents.RecentTasksController;
import com.android.wm.shell.startingsurface.StartingWindowController;
import com.android.wm.shell.sysui.ShellCommandHandler;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.unfold.UnfoldAnimationController;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Unified task organizer for all components in the shell.
 * TODO(b/167582004): may consider consolidating this class and TaskOrganizer
 */
public class ShellTaskOrganizer extends TaskOrganizer implements
        CompatUIController.CompatUICallback {
    private static final String TAG = "ShellTaskOrganizer";

    // Intentionally using negative numbers here so the positive numbers can be used
    // for task id specific listeners that will be added later.
    public static final int TASK_LISTENER_TYPE_UNDEFINED = -1;
    public static final int TASK_LISTENER_TYPE_FULLSCREEN = -2;
    public static final int TASK_LISTENER_TYPE_MULTI_WINDOW = -3;
    public static final int TASK_LISTENER_TYPE_PIP = -4;
    public static final int TASK_LISTENER_TYPE_FREEFORM = -5;

    @IntDef(prefix = {"TASK_LISTENER_TYPE_"}, value = {
            TASK_LISTENER_TYPE_UNDEFINED,
            TASK_LISTENER_TYPE_FULLSCREEN,
            TASK_LISTENER_TYPE_MULTI_WINDOW,
            TASK_LISTENER_TYPE_PIP,
            TASK_LISTENER_TYPE_FREEFORM,
    })
    public @interface TaskListenerType {}

    /**
     * Callbacks for when the tasks change in the system.
     */
    public interface TaskListener {
        default void onTaskAppeared(RunningTaskInfo taskInfo, SurfaceControl leash) {}
        default void onTaskInfoChanged(RunningTaskInfo taskInfo) {}
        default void onTaskVanished(RunningTaskInfo taskInfo) {}
        default void onBackPressedOnTaskRoot(RunningTaskInfo taskInfo) {}
        /** Whether this task listener supports compat UI. */
        default boolean supportCompatUI() {
            // All TaskListeners should support compat UI except PIP and StageCoordinator.
            return true;
        }
        /** Attaches a child window surface to the task surface. */
        default void attachChildSurfaceToTask(int taskId, SurfaceControl.Builder b) {
            throw new IllegalStateException(
                    "This task listener doesn't support child surface attachment.");
        }
        /** Reparents a child window surface to the task surface. */
        default void reparentChildSurfaceToTask(int taskId, SurfaceControl sc,
                SurfaceControl.Transaction t) {
            throw new IllegalStateException(
                    "This task listener doesn't support child surface reparent.");
        }
        default void dump(@NonNull PrintWriter pw, String prefix) {};
    }

    /**
     * Callbacks for events on a task with a locus id.
     */
    public interface LocusIdListener {
        /**
         * Notifies when a task with a locusId becomes visible, when a visible task's locusId
         * changes, or if a previously visible task with a locusId becomes invisible.
         */
        void onVisibilityChanged(int taskId, LocusId locus, boolean visible);
    }

    /**
     * Callbacks for events in which the focus has changed.
     */
    public interface FocusListener {
        /**
         * Notifies when the task which is focused has changed.
         */
        void onFocusTaskChanged(RunningTaskInfo taskInfo);
    }

    /**
     * Keys map from either a task id or {@link TaskListenerType}.
     * @see #addListenerForTaskId
     * @see #addListenerForType
     */
    private final SparseArray<TaskListener> mTaskListeners = new SparseArray<>();

    // Keeps track of all the tasks reported to this organizer (changes in windowing mode will
    // require us to report to both old and new listeners)
    private final SparseArray<TaskAppearedInfo> mTasks = new SparseArray<>();

    /** @see #setPendingLaunchCookieListener */
    private final ArrayMap<IBinder, TaskListener> mLaunchCookieToListener = new ArrayMap<>();

    // Keeps track of taskId's with visible locusIds. Used to notify any {@link LocusIdListener}s
    // that might be set.
    private final SparseArray<LocusId> mVisibleTasksWithLocusId = new SparseArray<>();

    /** @see #addLocusIdListener */
    private final ArraySet<LocusIdListener> mLocusIdListeners = new ArraySet<>();

    private final ArraySet<FocusListener> mFocusListeners = new ArraySet<>();

    private final Object mLock = new Object();
    private StartingWindowController mStartingWindow;

    /** Overlay surface for home root task */
    private final SurfaceControl mHomeTaskOverlayContainer = new SurfaceControl.Builder()
            .setName("home_task_overlay_container")
            .setContainerLayer()
            .setHidden(false)
            .setCallsite("ShellTaskOrganizer.mHomeTaskOverlayContainer")
            .build();

    /**
     * In charge of showing compat UI. Can be {@code null} if the device doesn't support size
     * compat or if this isn't the main {@link ShellTaskOrganizer}.
     *
     * <p>NOTE: only the main {@link ShellTaskOrganizer} should have a {@link CompatUIController},
     * and register itself as a {@link CompatUIController.CompatUICallback}. Subclasses should be
     * initialized with a {@code null} {@link CompatUIController}.
     */
    @Nullable
    private final CompatUIController mCompatUI;

    @NonNull
    private final ShellCommandHandler mShellCommandHandler;

    @Nullable
    private final Optional<RecentTasksController> mRecentTasks;

    @Nullable
    private final UnfoldAnimationController mUnfoldAnimationController;

    @Nullable
    private RunningTaskInfo mLastFocusedTaskInfo;

    public ShellTaskOrganizer(ShellExecutor mainExecutor) {
        this(null /* shellInit */, null /* shellCommandHandler */,
                null /* taskOrganizerController */, null /* compatUI */,
                Optional.empty() /* unfoldAnimationController */,
                Optional.empty() /* recentTasksController */,
                mainExecutor);
    }

    public ShellTaskOrganizer(ShellInit shellInit,
            ShellCommandHandler shellCommandHandler,
            @Nullable CompatUIController compatUI,
            Optional<UnfoldAnimationController> unfoldAnimationController,
            Optional<RecentTasksController> recentTasks,
            ShellExecutor mainExecutor) {
        this(shellInit, shellCommandHandler, null /* taskOrganizerController */, compatUI,
                unfoldAnimationController, recentTasks, mainExecutor);
    }

    @VisibleForTesting
    protected ShellTaskOrganizer(ShellInit shellInit,
            ShellCommandHandler shellCommandHandler,
            ITaskOrganizerController taskOrganizerController,
            @Nullable CompatUIController compatUI,
            Optional<UnfoldAnimationController> unfoldAnimationController,
            Optional<RecentTasksController> recentTasks,
            ShellExecutor mainExecutor) {
        super(taskOrganizerController, mainExecutor);
        mShellCommandHandler = shellCommandHandler;
        mCompatUI = compatUI;
        mRecentTasks = recentTasks;
        mUnfoldAnimationController = unfoldAnimationController.orElse(null);
        if (shellInit != null) {
            shellInit.addInitCallback(this::onInit, this);
        }
    }

    private void onInit() {
        mShellCommandHandler.addDumpCallback(this::dump, this);
        if (mCompatUI != null) {
            mCompatUI.setCompatUICallback(this);
        }
        registerOrganizer();
    }

    @Override
    public List<TaskAppearedInfo> registerOrganizer() {
        synchronized (mLock) {
            ProtoLog.v(WM_SHELL_TASK_ORG, "Registering organizer");
            final List<TaskAppearedInfo> taskInfos = super.registerOrganizer();
            for (int i = 0; i < taskInfos.size(); i++) {
                final TaskAppearedInfo info = taskInfos.get(i);
                ProtoLog.v(WM_SHELL_TASK_ORG, "Existing task: id=%d component=%s",
                        info.getTaskInfo().taskId, info.getTaskInfo().baseIntent);
                onTaskAppeared(info);
            }
            return taskInfos;
        }
    }

    @Override
    public void unregisterOrganizer() {
        super.unregisterOrganizer();
        if (mStartingWindow != null) {
            mStartingWindow.clearAllWindows();
        }
    }

    /**
     * Creates a persistent root task in WM for a particular windowing-mode.
     * @param displayId The display to create the root task on.
     * @param windowingMode Windowing mode to put the root task in.
     * @param listener The listener to get the created task callback.
     */
    public void createRootTask(int displayId, int windowingMode, TaskListener listener) {
        createRootTask(displayId, windowingMode, listener, false /* removeWithTaskOrganizer */);
    }

    /**
     * Creates a persistent root task in WM for a particular windowing-mode.
     * @param displayId The display to create the root task on.
     * @param windowingMode Windowing mode to put the root task in.
     * @param listener The listener to get the created task callback.
     * @param removeWithTaskOrganizer True if this task should be removed when organizer destroyed.
     */
    public void createRootTask(int displayId, int windowingMode, TaskListener listener,
            boolean removeWithTaskOrganizer) {
        ProtoLog.v(WM_SHELL_TASK_ORG, "createRootTask() displayId=%d winMode=%d listener=%s" ,
                displayId, windowingMode, listener.toString());
        final IBinder cookie = new Binder();
        setPendingLaunchCookieListener(cookie, listener);
        super.createRootTask(displayId, windowingMode, cookie, removeWithTaskOrganizer);
    }

    /**
     * @hide
     */
    public void initStartingWindow(StartingWindowController startingWindow) {
        mStartingWindow = startingWindow;
    }

    /**
     * Adds a listener for a specific task id.
     */
    public void addListenerForTaskId(TaskListener listener, int taskId) {
        synchronized (mLock) {
            ProtoLog.v(WM_SHELL_TASK_ORG, "addListenerForTaskId taskId=%s", taskId);
            if (mTaskListeners.get(taskId) != null) {
                throw new IllegalArgumentException(
                        "Listener for taskId=" + taskId + " already exists");
            }

            final TaskAppearedInfo info = mTasks.get(taskId);
            if (info == null) {
                throw new IllegalArgumentException("addListenerForTaskId unknown taskId=" + taskId);
            }

            final TaskListener oldListener = getTaskListener(info.getTaskInfo());
            mTaskListeners.put(taskId, listener);
            updateTaskListenerIfNeeded(info.getTaskInfo(), info.getLeash(), oldListener, listener);
        }
    }

    /**
     * Adds a listener for tasks with given types.
     */
    public void addListenerForType(TaskListener listener, @TaskListenerType int... listenerTypes) {
        synchronized (mLock) {
            ProtoLog.v(WM_SHELL_TASK_ORG, "addListenerForType types=%s listener=%s",
                    Arrays.toString(listenerTypes), listener);
            for (int listenerType : listenerTypes) {
                if (mTaskListeners.get(listenerType) != null) {
                    throw new IllegalArgumentException("Listener for listenerType=" + listenerType
                            + " already exists");
                }
                mTaskListeners.put(listenerType, listener);
            }

            // Notify the listener of all existing tasks with the given type.
            for (int i = mTasks.size() - 1; i >= 0; --i) {
                final TaskAppearedInfo data = mTasks.valueAt(i);
                final TaskListener taskListener = getTaskListener(data.getTaskInfo());
                if (taskListener != listener) continue;
                listener.onTaskAppeared(data.getTaskInfo(), data.getLeash());
            }
        }
    }

    /**
     * Removes a registered listener.
     */
    public void removeListener(TaskListener listener) {
        synchronized (mLock) {
            ProtoLog.v(WM_SHELL_TASK_ORG, "Remove listener=%s", listener);
            final int index = mTaskListeners.indexOfValue(listener);
            if (index == -1) {
                Log.w(TAG, "No registered listener found");
                return;
            }

            // Collect tasks associated with the listener we are about to remove.
            final ArrayList<TaskAppearedInfo> tasks = new ArrayList<>();
            for (int i = mTasks.size() - 1; i >= 0; --i) {
                final TaskAppearedInfo data = mTasks.valueAt(i);
                final TaskListener taskListener = getTaskListener(data.getTaskInfo());
                if (taskListener != listener) continue;
                tasks.add(data);
            }

            // Remove listener, there can be the multiple occurrences, so search the whole list.
            for (int i = mTaskListeners.size() - 1; i >= 0; --i) {
                if (mTaskListeners.valueAt(i) == listener) {
                    mTaskListeners.removeAt(i);
                }
            }

            // Associate tasks with new listeners if needed.
            for (int i = tasks.size() - 1; i >= 0; --i) {
                final TaskAppearedInfo data = tasks.get(i);
                updateTaskListenerIfNeeded(data.getTaskInfo(), data.getLeash(),
                        null /* oldListener already removed*/, getTaskListener(data.getTaskInfo()));
            }
        }
    }

    /**
     * Associated a listener to a pending launch cookie so we can route the task later once it
     * appears.
     */
    public void setPendingLaunchCookieListener(IBinder cookie, TaskListener listener) {
        synchronized (mLock) {
            mLaunchCookieToListener.put(cookie, listener);
        }
    }

    /**
     * Adds a listener to be notified for {@link LocusId} visibility changes.
     */
    public void addLocusIdListener(LocusIdListener listener) {
        synchronized (mLock) {
            mLocusIdListeners.add(listener);
            for (int i = 0; i < mVisibleTasksWithLocusId.size(); i++) {
                listener.onVisibilityChanged(mVisibleTasksWithLocusId.keyAt(i),
                        mVisibleTasksWithLocusId.valueAt(i), true /* visible */);
            }
        }
    }

    /**
     * Removes listener.
     */
    public void removeLocusIdListener(LocusIdListener listener) {
        synchronized (mLock) {
            mLocusIdListeners.remove(listener);
        }
    }

    /**
     * Adds a listener to be notified for task focus changes.
     */
    public void addFocusListener(FocusListener listener) {
        synchronized (mLock) {
            mFocusListeners.add(listener);
            if (mLastFocusedTaskInfo != null) {
                listener.onFocusTaskChanged(mLastFocusedTaskInfo);
            }
        }
    }

    /**
     * Removes listener.
     */
    public void removeFocusListener(FocusListener listener) {
        synchronized (mLock) {
            mFocusListeners.remove(listener);
        }
    }

    /**
     * Returns a surface which can be used to attach overlays to the home root task
     */
    @NonNull
    public SurfaceControl getHomeTaskOverlayContainer() {
        return mHomeTaskOverlayContainer;
    }

    @Override
    public void addStartingWindow(StartingWindowInfo info) {
        if (mStartingWindow != null) {
            mStartingWindow.addStartingWindow(info);
        }
    }

    @Override
    public void removeStartingWindow(StartingWindowRemovalInfo removalInfo) {
        if (mStartingWindow != null) {
            mStartingWindow.removeStartingWindow(removalInfo);
        }
    }

    @Override
    public void copySplashScreenView(int taskId) {
        if (mStartingWindow != null) {
            mStartingWindow.copySplashScreenView(taskId);
        }
    }

    @Override
    public void onAppSplashScreenViewRemoved(int taskId) {
        if (mStartingWindow != null) {
            mStartingWindow.onAppSplashScreenViewRemoved(taskId);
        }
    }

    @Override
    public void onImeDrawnOnTask(int taskId) {
        if (mStartingWindow != null) {
            mStartingWindow.onImeDrawnOnTask(taskId);
        }
    }

    @Override
    public void onTaskAppeared(RunningTaskInfo taskInfo, SurfaceControl leash) {
        if (leash != null) {
            leash.setUnreleasedWarningCallSite("ShellTaskOrganizer.onTaskAppeared");
        }
        synchronized (mLock) {
            onTaskAppeared(new TaskAppearedInfo(taskInfo, leash));
        }
    }

    private void onTaskAppeared(TaskAppearedInfo info) {
        final int taskId = info.getTaskInfo().taskId;
        mTasks.put(taskId, info);
        final TaskListener listener =
                getTaskListener(info.getTaskInfo(), true /*removeLaunchCookieIfNeeded*/);
        ProtoLog.v(WM_SHELL_TASK_ORG, "Task appeared taskId=%d listener=%s", taskId, listener);
        if (listener != null) {
            listener.onTaskAppeared(info.getTaskInfo(), info.getLeash());
        }
        if (mUnfoldAnimationController != null) {
            mUnfoldAnimationController.onTaskAppeared(info.getTaskInfo(), info.getLeash());
        }

        if (info.getTaskInfo().getActivityType() == ACTIVITY_TYPE_HOME) {
            ProtoLog.v(WM_SHELL_TASK_ORG, "Adding overlay to home task");
            final SurfaceControl.Transaction t = new SurfaceControl.Transaction();
            t.setLayer(mHomeTaskOverlayContainer, Integer.MAX_VALUE);
            t.reparent(mHomeTaskOverlayContainer, info.getLeash());
            t.apply();
        }

        notifyLocusVisibilityIfNeeded(info.getTaskInfo());
        notifyCompatUI(info.getTaskInfo(), listener);
        mRecentTasks.ifPresent(recentTasks -> recentTasks.onTaskAdded(info.getTaskInfo()));
    }

    /**
     * Take a screenshot of a task.
     */
    public void screenshotTask(RunningTaskInfo taskInfo, Rect crop,
            Consumer<ScreenCapture.ScreenshotHardwareBuffer> consumer) {
        final TaskAppearedInfo info = mTasks.get(taskInfo.taskId);
        if (info == null) {
            return;
        }
        ScreenshotUtils.captureLayer(info.getLeash(), crop, consumer);
    }


    @Override
    public void onTaskInfoChanged(RunningTaskInfo taskInfo) {
        synchronized (mLock) {
            ProtoLog.v(WM_SHELL_TASK_ORG, "Task info changed taskId=%d", taskInfo.taskId);

            if (mUnfoldAnimationController != null) {
                mUnfoldAnimationController.onTaskInfoChanged(taskInfo);
            }

            final TaskAppearedInfo data = mTasks.get(taskInfo.taskId);
            final TaskListener oldListener = getTaskListener(data.getTaskInfo());
            final TaskListener newListener = getTaskListener(taskInfo);
            mTasks.put(taskInfo.taskId, new TaskAppearedInfo(taskInfo, data.getLeash()));
            final boolean updated = updateTaskListenerIfNeeded(
                    taskInfo, data.getLeash(), oldListener, newListener);
            if (!updated && newListener != null) {
                newListener.onTaskInfoChanged(taskInfo);
            }
            notifyLocusVisibilityIfNeeded(taskInfo);
            if (updated || !taskInfo.equalsForCompatUi(data.getTaskInfo())) {
                // Notify the compat UI if the listener or task info changed.
                notifyCompatUI(taskInfo, newListener);
            }
            final boolean windowModeChanged =
                    data.getTaskInfo().getWindowingMode() != taskInfo.getWindowingMode();
            final boolean visibilityChanged = data.getTaskInfo().isVisible != taskInfo.isVisible;
            if (windowModeChanged || visibilityChanged) {
                mRecentTasks.ifPresent(recentTasks ->
                        recentTasks.onTaskRunningInfoChanged(taskInfo));
            }
            // TODO (b/207687679): Remove check for HOME once bug is fixed
            final boolean isFocusedOrHome = taskInfo.isFocused
                    || (taskInfo.topActivityType == WindowConfiguration.ACTIVITY_TYPE_HOME
                    && taskInfo.isVisible);
            final boolean focusTaskChanged = (mLastFocusedTaskInfo == null
                    || mLastFocusedTaskInfo.taskId != taskInfo.taskId
                    || mLastFocusedTaskInfo.getWindowingMode() != taskInfo.getWindowingMode())
                    && isFocusedOrHome;
            if (focusTaskChanged) {
                for (int i = 0; i < mFocusListeners.size(); i++) {
                    mFocusListeners.valueAt(i).onFocusTaskChanged(taskInfo);
                }
                mLastFocusedTaskInfo = taskInfo;
            }
        }
    }

    @Override
    public void onBackPressedOnTaskRoot(RunningTaskInfo taskInfo) {
        synchronized (mLock) {
            ProtoLog.v(WM_SHELL_TASK_ORG, "Task root back pressed taskId=%d", taskInfo.taskId);
            final TaskListener listener = getTaskListener(taskInfo);
            if (listener != null) {
                listener.onBackPressedOnTaskRoot(taskInfo);
            }
        }
    }

    @Override
    public void onTaskVanished(RunningTaskInfo taskInfo) {
        synchronized (mLock) {
            ProtoLog.v(WM_SHELL_TASK_ORG, "Task vanished taskId=%d", taskInfo.taskId);
            if (mUnfoldAnimationController != null) {
                mUnfoldAnimationController.onTaskVanished(taskInfo);
            }

            final int taskId = taskInfo.taskId;
            final TaskAppearedInfo appearedInfo = mTasks.get(taskId);
            final TaskListener listener = getTaskListener(appearedInfo.getTaskInfo());
            mTasks.remove(taskId);
            if (listener != null) {
                listener.onTaskVanished(taskInfo);
            }
            notifyLocusVisibilityIfNeeded(taskInfo);
            // Pass null for listener to remove the compat UI on this task if there is any.
            notifyCompatUI(taskInfo, null /* taskListener */);
            // Notify the recent tasks that a task has been removed
            mRecentTasks.ifPresent(recentTasks -> recentTasks.onTaskRemoved(taskInfo));
            if (taskInfo.getActivityType() == ACTIVITY_TYPE_HOME) {
                SurfaceControl.Transaction t = new SurfaceControl.Transaction();
                t.reparent(mHomeTaskOverlayContainer, null);
                t.apply();
                ProtoLog.v(WM_SHELL_TASK_ORG, "Removing overlay surface");
            }

            if (!ENABLE_SHELL_TRANSITIONS && (appearedInfo.getLeash() != null)) {
                // Preemptively clean up the leash only if shell transitions are not enabled
                appearedInfo.getLeash().release();
            }
        }
    }

    /**
     * Return list of {@link RunningTaskInfo}s for the given display.
     *
     * @return filtered list of tasks or empty list
     */
    public ArrayList<RunningTaskInfo> getRunningTasks(int displayId) {
        ArrayList<RunningTaskInfo> result = new ArrayList<>();
        for (int i = 0; i < mTasks.size(); i++) {
            RunningTaskInfo taskInfo = mTasks.valueAt(i).getTaskInfo();
            if (taskInfo.displayId == displayId) {
                result.add(taskInfo);
            }
        }
        return result;
    }

    /** Gets running task by taskId. Returns {@code null} if no such task observed. */
    @Nullable
    public RunningTaskInfo getRunningTaskInfo(int taskId) {
        synchronized (mLock) {
            final TaskAppearedInfo info = mTasks.get(taskId);
            return info != null ? info.getTaskInfo() : null;
        }
    }

    private boolean updateTaskListenerIfNeeded(RunningTaskInfo taskInfo, SurfaceControl leash,
            TaskListener oldListener, TaskListener newListener) {
        if (oldListener == newListener) return false;
        // TODO: We currently send vanished/appeared as the task moves between types, but
        //       we should consider adding a different mode-changed callback
        if (oldListener != null) {
            oldListener.onTaskVanished(taskInfo);
        }
        if (newListener != null) {
            newListener.onTaskAppeared(taskInfo, leash);
        }
        return true;
    }

    private void notifyLocusVisibilityIfNeeded(TaskInfo taskInfo) {
        final int taskId = taskInfo.taskId;
        final LocusId prevLocus = mVisibleTasksWithLocusId.get(taskId);
        final boolean sameLocus = Objects.equals(prevLocus, taskInfo.mTopActivityLocusId);
        if (prevLocus == null) {
            // New visible locus
            if (taskInfo.mTopActivityLocusId != null && taskInfo.isVisible) {
                mVisibleTasksWithLocusId.put(taskId, taskInfo.mTopActivityLocusId);
                notifyLocusIdChange(taskId, taskInfo.mTopActivityLocusId, true /* visible */);
            }
        } else if (sameLocus && !taskInfo.isVisible) {
            // Hidden locus
            mVisibleTasksWithLocusId.remove(taskId);
            notifyLocusIdChange(taskId, taskInfo.mTopActivityLocusId, false /* visible */);
        } else if (!sameLocus) {
            // Changed locus
            if (taskInfo.isVisible) {
                mVisibleTasksWithLocusId.put(taskId, taskInfo.mTopActivityLocusId);
                notifyLocusIdChange(taskId, prevLocus, false /* visible */);
                notifyLocusIdChange(taskId, taskInfo.mTopActivityLocusId, true /* visible */);
            } else {
                mVisibleTasksWithLocusId.remove(taskInfo.taskId);
                notifyLocusIdChange(taskId, prevLocus, false /* visible */);
            }
        }
    }

    private void notifyLocusIdChange(int taskId, LocusId locus, boolean visible) {
        for (int i = 0; i < mLocusIdListeners.size(); i++) {
            mLocusIdListeners.valueAt(i).onVisibilityChanged(taskId, locus, visible);
        }
    }

    @Override
    public void onSizeCompatRestartButtonAppeared(int taskId) {
        final TaskAppearedInfo info;
        synchronized (mLock) {
            info = mTasks.get(taskId);
        }
        if (info == null) {
            return;
        }
        logSizeCompatRestartButtonEventReported(info,
                FrameworkStatsLog.SIZE_COMPAT_RESTART_BUTTON_EVENT_REPORTED__EVENT__APPEARED);
    }

    @Override
    public void onSizeCompatRestartButtonClicked(int taskId) {
        final TaskAppearedInfo info;
        synchronized (mLock) {
            info = mTasks.get(taskId);
        }
        if (info == null) {
            return;
        }
        logSizeCompatRestartButtonEventReported(info,
                FrameworkStatsLog.SIZE_COMPAT_RESTART_BUTTON_EVENT_REPORTED__EVENT__CLICKED);
        restartTaskTopActivityProcessIfVisible(info.getTaskInfo().token);
    }

    @Override
    public void onCameraControlStateUpdated(int taskId, @CameraCompatControlState int state) {
        final TaskAppearedInfo info;
        synchronized (mLock) {
            info = mTasks.get(taskId);
        }
        if (info == null) {
            return;
        }
        updateCameraCompatControlState(info.getTaskInfo().token, state);
    }

    /** Reparents a child window surface to the task surface. */
    public void reparentChildSurfaceToTask(int taskId, SurfaceControl sc,
            SurfaceControl.Transaction t) {
        final TaskListener taskListener;
        synchronized (mLock) {
            taskListener = mTasks.contains(taskId)
                    ? getTaskListener(mTasks.get(taskId).getTaskInfo())
                    : null;
        }
        if (taskListener == null) {
            ProtoLog.w(WM_SHELL_TASK_ORG, "Failed to find Task to reparent surface taskId=%d",
                    taskId);
            return;
        }
        taskListener.reparentChildSurfaceToTask(taskId, sc, t);
    }

    private void logSizeCompatRestartButtonEventReported(@NonNull TaskAppearedInfo info,
            int event) {
        ActivityInfo topActivityInfo = info.getTaskInfo().topActivityInfo;
        if (topActivityInfo == null) {
            return;
        }
        FrameworkStatsLog.write(FrameworkStatsLog.SIZE_COMPAT_RESTART_BUTTON_EVENT_REPORTED,
                topActivityInfo.applicationInfo.uid, event);
    }

    /**
     * Notifies {@link CompatUIController} about the compat info changed on the give Task
     * to update the UI accordingly.
     *
     * @param taskInfo the new Task info
     * @param taskListener listener to handle the Task Surface placement. {@code null} if task is
     *                     vanished.
     */
    private void notifyCompatUI(RunningTaskInfo taskInfo, @Nullable TaskListener taskListener) {
        if (mCompatUI == null) {
            return;
        }

        // The task is vanished or doesn't support compat UI, notify to remove compat UI
        // on this Task if there is any.
        if (taskListener == null || !taskListener.supportCompatUI()
                || !taskInfo.appCompatTaskInfo.hasCompatUI() || !taskInfo.isVisible) {
            mCompatUI.onCompatInfoChanged(taskInfo, null /* taskListener */);
            return;
        }
        mCompatUI.onCompatInfoChanged(taskInfo, taskListener);
    }

    private TaskListener getTaskListener(RunningTaskInfo runningTaskInfo) {
        return getTaskListener(runningTaskInfo, false /*removeLaunchCookieIfNeeded*/);
    }

    private TaskListener getTaskListener(RunningTaskInfo runningTaskInfo,
            boolean removeLaunchCookieIfNeeded) {

        final int taskId = runningTaskInfo.taskId;
        TaskListener listener;

        // First priority goes to listener that might be pending for this task.
        final ArrayList<IBinder> launchCookies = runningTaskInfo.launchCookies;
        for (int i = launchCookies.size() - 1; i >= 0; --i) {
            final IBinder cookie = launchCookies.get(i);
            listener = mLaunchCookieToListener.get(cookie);
            if (listener == null) continue;

            if (removeLaunchCookieIfNeeded) {
                // Remove the cookie and add the listener.
                mLaunchCookieToListener.remove(cookie);
                mTaskListeners.put(taskId, listener);
            }
            return listener;
        }

        // Next priority goes to taskId specific listeners.
        listener = mTaskListeners.get(taskId);
        if (listener != null) return listener;

        // Next priority goes to the listener listening to its parent.
        if (runningTaskInfo.hasParentTask()) {
            listener = mTaskListeners.get(runningTaskInfo.parentTaskId);
            if (listener != null) return listener;
        }

        // Next we try type specific listeners.
        final int taskListenerType = taskInfoToTaskListenerType(runningTaskInfo);
        return mTaskListeners.get(taskListenerType);
    }

    @VisibleForTesting
    static @TaskListenerType int taskInfoToTaskListenerType(RunningTaskInfo runningTaskInfo) {
        switch (runningTaskInfo.getWindowingMode()) {
            case WINDOWING_MODE_FULLSCREEN:
                return TASK_LISTENER_TYPE_FULLSCREEN;
            case WINDOWING_MODE_MULTI_WINDOW:
                return TASK_LISTENER_TYPE_MULTI_WINDOW;
            case WINDOWING_MODE_PINNED:
                return TASK_LISTENER_TYPE_PIP;
            case WINDOWING_MODE_FREEFORM:
                return TASK_LISTENER_TYPE_FREEFORM;
            case WINDOWING_MODE_UNDEFINED:
            default:
                return TASK_LISTENER_TYPE_UNDEFINED;
        }
    }

    public static String taskListenerTypeToString(@TaskListenerType int type) {
        switch (type) {
            case TASK_LISTENER_TYPE_FULLSCREEN:
                return "TASK_LISTENER_TYPE_FULLSCREEN";
            case TASK_LISTENER_TYPE_MULTI_WINDOW:
                return "TASK_LISTENER_TYPE_MULTI_WINDOW";
            case TASK_LISTENER_TYPE_PIP:
                return "TASK_LISTENER_TYPE_PIP";
            case TASK_LISTENER_TYPE_FREEFORM:
                return "TASK_LISTENER_TYPE_FREEFORM";
            case TASK_LISTENER_TYPE_UNDEFINED:
                return "TASK_LISTENER_TYPE_UNDEFINED";
            default:
                return "taskId#" + type;
        }
    }

    public void dump(@NonNull PrintWriter pw, String prefix) {
        synchronized (mLock) {
            final String innerPrefix = prefix + "  ";
            final String childPrefix = innerPrefix + "  ";
            pw.println(prefix + TAG);
            pw.println(innerPrefix + mTaskListeners.size() + " Listeners");
            for (int i = mTaskListeners.size() - 1; i >= 0; --i) {
                final int key = mTaskListeners.keyAt(i);
                final TaskListener listener = mTaskListeners.valueAt(i);
                pw.println(innerPrefix + "#" + i + " " + taskListenerTypeToString(key));
                listener.dump(pw, childPrefix);
            }

            pw.println();
            pw.println(innerPrefix + mTasks.size() + " Tasks");
            for (int i = mTasks.size() - 1; i >= 0; --i) {
                final int key = mTasks.keyAt(i);
                final TaskAppearedInfo info = mTasks.valueAt(i);
                final TaskListener listener = getTaskListener(info.getTaskInfo());
                final int windowingMode = info.getTaskInfo().getWindowingMode();
                String pkg = "";
                if (info.getTaskInfo().baseActivity != null) {
                    pkg = info.getTaskInfo().baseActivity.getPackageName();
                }
                Rect bounds = info.getTaskInfo().getConfiguration().windowConfiguration.getBounds();
                boolean running = info.getTaskInfo().isRunning;
                boolean visible = info.getTaskInfo().isVisible;
                boolean focused = info.getTaskInfo().isFocused;
                pw.println(innerPrefix + "#" + i + " task=" + key + " listener=" + listener
                        + " wmMode=" + windowingMode + " pkg=" + pkg + " bounds=" + bounds
                        + " running=" + running + " visible=" + visible + " focused=" + focused);
            }

            pw.println();
            pw.println(innerPrefix + mLaunchCookieToListener.size() + " Launch Cookies");
            for (int i = mLaunchCookieToListener.size() - 1; i >= 0; --i) {
                final IBinder key = mLaunchCookieToListener.keyAt(i);
                final TaskListener listener = mLaunchCookieToListener.valueAt(i);
                pw.println(innerPrefix + "#" + i + " cookie=" + key + " listener=" + listener);
            }

        }
    }
}
