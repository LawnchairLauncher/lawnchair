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
import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.wm.shell.compatui.impl.CompatUIEventsKt.SIZE_COMPAT_RESTART_BUTTON_APPEARED;
import static com.android.wm.shell.compatui.impl.CompatUIEventsKt.SIZE_COMPAT_RESTART_BUTTON_CLICKED;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_TASK_ORG;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.TaskInfo;
import android.app.WindowConfiguration;
import android.content.LocusId;
import android.content.pm.ActivityInfo;
import android.graphics.Rect;
import android.os.Binder;
import android.os.Debug;
import android.os.IBinder;
import android.util.ArrayMap;
import android.util.Log;
import android.util.SparseArray;
import android.view.SurfaceControl;
import android.window.ITaskOrganizerController;
import android.window.StartingWindowInfo;
import android.window.StartingWindowRemovalInfo;
import android.window.TaskAppearedInfo;
import android.window.TaskOrganizer;
import android.window.WindowContainerTransaction;
import android.window.WindowContainerTransactionCallback;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.ProtoLog;
import com.android.internal.util.FrameworkStatsLog;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.compatui.CompatUIController;
import com.android.wm.shell.compatui.api.CompatUIHandler;
import com.android.wm.shell.compatui.api.CompatUIInfo;
import com.android.wm.shell.compatui.impl.CompatUIEvents.SizeCompatRestartButtonAppeared;
import com.android.wm.shell.compatui.impl.CompatUIEvents.SizeCompatRestartButtonClicked;
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
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Unified task organizer for all components in the shell.
 * TODO(b/167582004): may consider consolidating this class and TaskOrganizer
 */
public class ShellTaskOrganizer extends TaskOrganizer {
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
    public interface TaskListener extends TaskVanishedListener, TaskAppearedListener,
            TaskInfoChangedListener {

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
     * Limited scope callback to notify when a task is removed from the system.  This signal is
     * not synchronized with anything (or any transition), and should not be used in cases where
     * that is necessary.
     */
    public interface TaskVanishedListener {
        /**
         * Invoked when a Task is removed from Shell.
         *
         * @param taskInfo The RunningTaskInfo for the Task.
         */
        default void onTaskVanished(RunningTaskInfo taskInfo) {}
    }

    /**
     * Limited scope callback to notify when a task is added from the system. This signal is
     * not synchronized with anything (or any transition), and should not be used in cases where
     * that is necessary.
     */
    public interface TaskAppearedListener {
        /**
         * Invoked when a Task appears on Shell. Because the leash can be shared between different
         * implementations, it's important to not apply changes in the related callback.
         *
         * @param taskInfo The RunningTaskInfo for the Task.
         * @param leash    The leash for the Task which should not be changed through this callback.
         */
        default void onTaskAppeared(RunningTaskInfo taskInfo, SurfaceControl leash) {}
    }

    /**
     * Limited scope callback to notify when a task has updated. This signal is
     * not synchronized with anything (or any transition), and should not be used in cases where
     * that is necessary.
     */
    public interface TaskInfoChangedListener {
        /**
         * Invoked when a Task is updated on Shell.
         *
         * @param taskInfo The RunningTaskInfo for the Task.
         */
        default void onTaskInfoChanged(RunningTaskInfo taskInfo) {}
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

    /** @see #setPendingTaskListener(int, TaskListener)  */
    private final ArrayMap<Integer, TaskListener> mPendingTaskToListener = new ArrayMap<>();

    // Keeps track of taskId's with visible locusIds. Used to notify any {@link LocusIdListener}s
    // that might be set.
    private final SparseArray<LocusId> mVisibleTasksWithLocusId = new SparseArray<>();

    /** @see #addLocusIdListener */
    private final CopyOnWriteArrayList<LocusIdListener> mLocusIdListeners =
            new CopyOnWriteArrayList<>();

    private final CopyOnWriteArrayList<FocusListener> mFocusListeners =
            new CopyOnWriteArrayList<>();

    // Listeners that should be notified when a task is vanished.
    private final CopyOnWriteArrayList<TaskVanishedListener> mTaskVanishedListeners =
            new CopyOnWriteArrayList<>();

    // Listeners that should be notified when a task has appeared.
    private final CopyOnWriteArrayList<TaskAppearedListener> mTaskAppearedListeners =
            new CopyOnWriteArrayList<>();

    // Listeners that should be notified when a task is updated
    private final CopyOnWriteArrayList<TaskInfoChangedListener> mTaskInfoChangedListeners =
            new CopyOnWriteArrayList<>();

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
     * <p>NOTE: only the main {@link ShellTaskOrganizer} should have a {@link CompatUIHandler},
     * Subclasses should be initialized with a {@code null} {@link CompatUIHandler}.
     */
    @Nullable
    private final CompatUIHandler mCompatUI;

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
            @Nullable CompatUIHandler compatUI,
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
            @Nullable CompatUIHandler compatUI,
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
            mCompatUI.setCallback(compatUIEvent -> {
                switch(compatUIEvent.getEventId()) {
                    case SIZE_COMPAT_RESTART_BUTTON_APPEARED:
                        onSizeCompatRestartButtonAppeared(compatUIEvent.asType());
                        break;
                    case SIZE_COMPAT_RESTART_BUTTON_CLICKED:
                        onSizeCompatRestartButtonClicked(compatUIEvent.asType());
                        break;
                    default:

                }
            });
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

    @Override
    public void applyTransaction(@NonNull WindowContainerTransaction t) {
        if (!t.isEmpty()) {
            ProtoLog.v(WM_SHELL_TASK_ORG, "applyTransaction(): wct=%s caller=%s",
                    t, Debug.getCallers(4));
        }
        super.applyTransaction(t);
    }

    @Override
    public int applySyncTransaction(@NonNull WindowContainerTransaction t,
            @NonNull WindowContainerTransactionCallback callback) {
        if (!t.isEmpty()) {
            ProtoLog.v(WM_SHELL_TASK_ORG, "applySyncTransaction(): wct=%s caller=%s",
                    t, Debug.getCallers(4));
        }
        return super.applySyncTransaction(t, callback);
    }

    /**
     * Creates a persistent root task in WM for a particular windowing-mode.
     * @param displayId The display to create the root task on.
     * @param windowingMode Windowing mode to put the root task in.
     * @param listener The listener to get the created task callback.
     *
     * @deprecated Use {@link #createRootTask(CreateRootTaskRequest, TaskListener)}
     */
    public void createRootTask(int displayId, int windowingMode, TaskListener listener) {
        createRootTask(new CreateRootTaskRequest()
                        .setDisplayId(displayId)
                        .setWindowingMode(windowingMode),
                listener);
    }

    /**
     * Creates a persistent root task in WM for a particular windowing-mode.
     * @param displayId The display to create the root task on.
     * @param windowingMode Windowing mode to put the root task in.
     * @param listener The listener to get the created task callback.
     * @param removeWithTaskOrganizer True if this task should be removed when organizer destroyed.
     *
     * @deprecated Use {@link #createRootTask(CreateRootTaskRequest, TaskListener)}
     */
    public void createRootTask(int displayId, int windowingMode, TaskListener listener,
            boolean removeWithTaskOrganizer) {
        createRootTask(new CreateRootTaskRequest()
                        .setDisplayId(displayId)
                        .setWindowingMode(windowingMode)
                        .setRemoveWithTaskOrganizer(removeWithTaskOrganizer),
                listener);
    }

    /**
     * Creates a persistent root task in WM for a particular windowing-mode.
     * @param displayId The display to create the root task on.
     * @param windowingMode Windowing mode to put the root task in.
     * @param listener The listener to get the created task callback.
     * @param removeWithTaskOrganizer True if this task should be removed when organizer destroyed.
     * @param reparentOnDisplayRemoval True if this task should be reparented on display removal.
     *
     * @deprecated Use {@link #createRootTask(CreateRootTaskRequest, TaskListener)}
     */
    public void createRootTask(int displayId, int windowingMode, TaskListener listener,
            boolean removeWithTaskOrganizer, boolean reparentOnDisplayRemoval) {
        createRootTask(new CreateRootTaskRequest()
                        .setDisplayId(displayId)
                        .setWindowingMode(windowingMode)
                        .setRemoveWithTaskOrganizer(removeWithTaskOrganizer)
                        .setReparentOnDisplayRemoval(reparentOnDisplayRemoval),
                listener);
    }

    /**
     * Creates a persistent root task in WM for a particular windowing-mode.
     * @param request The data for this request
     * @param listener The listener to get the created task callback.
     *
     * @hide
     */
    public void createRootTask(@NonNull CreateRootTaskRequest request, TaskListener listener) {
        ProtoLog.v(WM_SHELL_TASK_ORG, "createRootTask() displayId=%d winMode=%d listener=%s" ,
                request.displayId, request.windowingMode, listener.toString());
        final IBinder cookie = new Binder();
        request.setLaunchCookie(cookie);
        setPendingLaunchCookieListener(cookie, listener);
        super.createRootTask(request);
    }

    /**
     * @hide
     */
    public void initStartingWindow(StartingWindowController startingWindow) {
        mStartingWindow = startingWindow;
    }

    /**
     * Adds a listener for a specific task id.  This only applies if
     */
    public void addListenerForTaskId(TaskListener listener, int taskId) {
        synchronized (mLock) {
            ProtoLog.v(WM_SHELL_TASK_ORG, "addListenerForTaskId taskId=%s", taskId);
            final TaskListener existingListener = mTaskListeners.get(taskId);
            if (existingListener != null) {
                if (existingListener == listener) {
                    // Same listener already registered
                    return;
                } else {
                    throw new IllegalArgumentException(
                            "Listener for taskId=" + taskId + " already exists");
                }
            }

            final TaskAppearedInfo info = mTasks.get(taskId);
            if (info == null) {
                ProtoLog.v(WM_SHELL_TASK_ORG, "Queueing pending listener");
                // The caller may have received a transition with the task before the organizer
                // was notified of the task appearing, so set a pending task listener for the
                // task to be retrieved when the task actually appears
                mPendingTaskToListener.put(taskId, listener);
                return;
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

            // Remove all occurrences of the pending listener
            for (int i = mPendingTaskToListener.size() - 1; i >= 0; --i) {
                if (mPendingTaskToListener.valueAt(i) == listener) {
                    mPendingTaskToListener.removeAt(i);
                }
            }

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

            // Remove occurrences of the listener
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
     * appears.  If both this and a pending task-id listener is set, then this will take priority.
     */
    public void setPendingLaunchCookieListener(IBinder cookie, TaskListener listener) {
        ProtoLog.v(WM_SHELL_TASK_ORG, "setPendingLaunchCookieListener(): cookie=%s listener=%s",
                cookie, listener);
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
     * Removes a locus id listener.
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
     * Removes a focus listener.
     */
    public void removeFocusListener(FocusListener listener) {
        synchronized (mLock) {
            mFocusListeners.remove(listener);
        }
    }

    /**
     * Adds a listener to be notified when a task vanishes.
     */
    public void addTaskVanishedListener(TaskVanishedListener listener) {
        synchronized (mLock) {
            mTaskVanishedListeners.add(listener);
        }
    }

    /**
     * Removes a task-vanished listener.
     */
    public void removeTaskVanishedListener(TaskVanishedListener listener) {
        synchronized (mLock) {
            mTaskVanishedListeners.remove(listener);
        }
    }

    /**
     * Adds a listener to be notified when a task is appears.
     */
    public void addTaskAppearedListener(TaskAppearedListener listener) {
        synchronized (mLock) {
            mTaskAppearedListeners.add(listener);
        }
    }

    /**
     * Removes a task-appeared listener.
     */
    public void removeTaskAppearedListener(TaskAppearedListener listener) {
        synchronized (mLock) {
            mTaskAppearedListeners.remove(listener);
        }
    }

    /**
     * Adds a listener to be notified when a task is updated.
     */
    public void addTaskInfoChangedListener(TaskInfoChangedListener listener) {
        synchronized (mLock) {
            mTaskInfoChangedListeners.add(listener);
        }
    }

    /**
     * Removes a taskInfo-update listener.
     */
    public void removeTaskInfoChangedListener(TaskInfoChangedListener listener) {
        synchronized (mLock) {
            mTaskInfoChangedListeners.remove(listener);
        }
    }

    /**
     * Returns a surface which can be used to attach overlays to the home root task
     */
    @NonNull
    public SurfaceControl getHomeTaskOverlayContainer() {
        return mHomeTaskOverlayContainer;
    }

    /**
     * Returns the home task surface, not for wide use.
     */
    @Nullable
    public SurfaceControl getHomeTaskSurface(int displayId) {
        for (int i = 0; i < mTasks.size(); i++) {
            final TaskAppearedInfo info = mTasks.valueAt(i);
            if (info.getTaskInfo().getActivityType() == ACTIVITY_TYPE_HOME
                    && info.getTaskInfo().displayId == displayId) {
                return info.getLeash();
            }
        }
        return null;
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

        if (isHomeTaskOnDefaultDisplay(info.getTaskInfo())) {
            ProtoLog.v(WM_SHELL_TASK_ORG, "Adding overlay to home task");
            final SurfaceControl.Transaction t = new SurfaceControl.Transaction();
            t.setLayer(mHomeTaskOverlayContainer, Integer.MAX_VALUE);
            t.reparent(mHomeTaskOverlayContainer, info.getLeash());
            t.apply();
        }

        notifyLocusVisibilityIfNeeded(info.getTaskInfo());
        notifyCompatUI(info.getTaskInfo(), listener);
        mRecentTasks.ifPresent(recentTasks -> recentTasks.onTaskAdded(info.getTaskInfo()));
        for (TaskAppearedListener l : mTaskAppearedListeners) {
            l.onTaskAppeared(info.getTaskInfo(), info.getLeash());
        }
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
            final TaskListener newListener = getTaskListener(taskInfo,
                    true /* removeLaunchCookieIfNeeded */);
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
            if (windowModeChanged
                    || hasFreeformConfigurationChanged(data.getTaskInfo(), taskInfo)) {
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
                for (FocusListener focusListener : mFocusListeners) {
                    focusListener.onFocusTaskChanged(taskInfo);
                }
                mLastFocusedTaskInfo = taskInfo;
            }
            for (TaskInfoChangedListener l : mTaskInfoChangedListeners) {
                l.onTaskInfoChanged(taskInfo);
            }
        }
    }

    private boolean hasFreeformConfigurationChanged(RunningTaskInfo oldTaskInfo,
            RunningTaskInfo newTaskInfo) {
        if (newTaskInfo.getWindowingMode() != WINDOWING_MODE_FREEFORM) {
            return false;
        }
        return oldTaskInfo.isVisible != newTaskInfo.isVisible
                || !oldTaskInfo.positionInParent.equals(newTaskInfo.positionInParent)
                || !Objects.equals(oldTaskInfo.configuration.windowConfiguration.getAppBounds(),
                newTaskInfo.configuration.windowConfiguration.getAppBounds());
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
            if (isHomeTaskOnDefaultDisplay(taskInfo)) {
                SurfaceControl.Transaction t = new SurfaceControl.Transaction();
                t.reparent(mHomeTaskOverlayContainer, null);
                t.apply();
                ProtoLog.v(WM_SHELL_TASK_ORG, "Removing overlay surface");
            }
            for (TaskVanishedListener l : mTaskVanishedListeners) {
                l.onTaskVanished(taskInfo);
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

    /** Return list of {@link RunningTaskInfo}s on all the displays. */
    public ArrayList<RunningTaskInfo> getRunningTasks() {
        ArrayList<RunningTaskInfo> result = new ArrayList<>();
        for (int i = 0; i < mTasks.size(); i++) {
            result.add(mTasks.valueAt(i).getTaskInfo());
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

    /**
     * Shows/hides the given task surface.  Not for general use as changing the task visibility may
     * conflict with other Transitions.  This is currently ONLY used to temporarily hide a task
     * while a drag is in session.
     */
    public void setTaskSurfaceVisibility(int taskId, boolean visible) {
        synchronized (mLock) {
            final TaskAppearedInfo info = mTasks.get(taskId);
            if (info != null) {
                SurfaceControl.Transaction t = new SurfaceControl.Transaction();
                t.setVisibility(info.getLeash(), visible);
                t.apply();
            }
        }
    }

    private boolean updateTaskListenerIfNeeded(RunningTaskInfo taskInfo, SurfaceControl leash,
            TaskListener oldListener, TaskListener newListener) {
        if (oldListener == newListener) return false;
        ProtoLog.v(WM_SHELL_TASK_ORG, "  Migrating from listener %s to %s",
                oldListener, newListener);
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
        for (LocusIdListener l : mLocusIdListeners) {
            l.onVisibilityChanged(taskId, locus, visible);
        }
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

    @VisibleForTesting
    void onSizeCompatRestartButtonAppeared(@NonNull SizeCompatRestartButtonAppeared compatUIEvent) {
        final int taskId = compatUIEvent.getTaskId();
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

    @VisibleForTesting
    void onSizeCompatRestartButtonClicked(@NonNull SizeCompatRestartButtonClicked compatUIEvent) {
        final int taskId = compatUIEvent.getTaskId();
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
            mCompatUI.onCompatInfoChanged(new CompatUIInfo(taskInfo, null /* taskListener */));
            return;
        }
        mCompatUI.onCompatInfoChanged(new CompatUIInfo(taskInfo, taskListener));
    }

    private TaskListener getTaskListener(RunningTaskInfo runningTaskInfo) {
        return getTaskListener(runningTaskInfo, false /*removeLaunchCookieIfNeeded*/);
    }

    private TaskListener getTaskListener(RunningTaskInfo runningTaskInfo,
            boolean removePendingIfNeeded) {

        final int taskId = runningTaskInfo.taskId;
        TaskListener listener;

        // First priority goes to listener that might be pending for this task.
        final ArrayList<IBinder> launchCookies = runningTaskInfo.launchCookies;
        for (int i = launchCookies.size() - 1; i >= 0; --i) {
            final IBinder cookie = launchCookies.get(i);
            listener = mLaunchCookieToListener.get(cookie);
            if (listener == null) continue;

            if (removePendingIfNeeded) {
                ProtoLog.v(WM_SHELL_TASK_ORG, "Migrating cookie listener to task: taskId=%d",
                        taskId);
                // Remove the cookie and add the listener.
                mLaunchCookieToListener.remove(cookie);
                if (mPendingTaskToListener.containsKey(taskId)
                        && mPendingTaskToListener.get(taskId) != listener) {
                    Log.w(TAG, "Conflicting pending task listeners reported for taskId=" + taskId);
                }
                mPendingTaskToListener.remove(taskId);
                mTaskListeners.put(taskId, listener);
            }
            return listener;
        }

        // Next priority goes to the pending task id listener
        if (mPendingTaskToListener.containsKey(taskId)) {
            listener = mPendingTaskToListener.get(taskId);
            if (listener != null) {
                if (removePendingIfNeeded) {
                    ProtoLog.v(WM_SHELL_TASK_ORG, "Migrating pending listener to task: taskId=%d",
                            taskId);
                    mPendingTaskToListener.remove(taskId);
                    mTaskListeners.put(taskId, listener);
                }
                return listener;
            }
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
    boolean hasTaskListener(int taskId) {
        return mTaskListeners.contains(taskId);
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

    /**
     * Return true if {@link RunningTaskInfo} is Home/Launcher activity type, plus it's the one on
     * default display (rather than on external display). This is used to check if we need to
     * reparent mHomeTaskOverlayContainer that is used for -1 screen on default display.
     */
    @VisibleForTesting
    static boolean isHomeTaskOnDefaultDisplay(RunningTaskInfo taskInfo) {
        return taskInfo.getActivityType() == ACTIVITY_TYPE_HOME
                && taskInfo.displayId == DEFAULT_DISPLAY;
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
            pw.println(innerPrefix + mLaunchCookieToListener.size()
                    + " Pending launch cookies listeners");
            for (int i = mLaunchCookieToListener.size() - 1; i >= 0; --i) {
                final IBinder key = mLaunchCookieToListener.keyAt(i);
                final TaskListener listener = mLaunchCookieToListener.valueAt(i);
                pw.println(innerPrefix + "#" + i + " cookie=" + key + " listener=" + listener);
            }

            pw.println();
            pw.println(innerPrefix + mPendingTaskToListener.size() + " Pending task listeners");
            for (int i = mPendingTaskToListener.size() - 1; i >= 0; --i) {
                final int taskId = mPendingTaskToListener.keyAt(i);
                final TaskListener listener = mPendingTaskToListener.valueAt(i);
                pw.println(innerPrefix + "#" + i + " taskId=" + taskId + " listener=" + listener);
            }
        }
    }
}
