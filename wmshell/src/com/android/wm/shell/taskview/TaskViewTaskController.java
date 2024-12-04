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

package com.android.wm.shell.taskview;

import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.view.WindowManager.TRANSIT_CHANGE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.graphics.Rect;
import android.gui.TrustedOverlay;
import android.os.Binder;
import android.util.CloseGuard;
import android.util.Slog;
import android.view.SurfaceControl;
import android.view.WindowInsets;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.SyncTransactionQueue;

import java.io.PrintWriter;
import java.util.concurrent.Executor;

/**
 * This class implements the core logic to show a task on the {@link TaskView}. All the {@link
 * TaskView} to {@link TaskViewTaskController} interactions are done via direct method calls.
 *
 * The reverse communication is done via the {@link TaskViewBase} interface.
 *
 * <ul>
 *     <li>The entry point for an activity based task view is {@link
 *     TaskViewTaskController#startActivity(PendingIntent, Intent, ActivityOptions, Rect)}</li>
 *
 *     <li>The entry point for an activity (represented by {@link ShortcutInfo}) based task view
 *     is {@link TaskViewTaskController#startShortcutActivity(ShortcutInfo, ActivityOptions, Rect)}
 *     </li>
 *
 *     <li>The entry point for a root-task based task view is {@link
 *     TaskViewTaskController#startRootTask(ActivityManager.RunningTaskInfo, SurfaceControl,
 *     WindowContainerTransaction)}.
 *     This method is special as it doesn't create a root task and instead expects that the
 *     launch root task is already created and started. This method just attaches the taskInfo to
 *     the TaskView.
 *     </li>
 * </ul>
 */
public class TaskViewTaskController implements ShellTaskOrganizer.TaskListener {

    private static final String TAG = TaskViewTaskController.class.getSimpleName();

    private final CloseGuard mGuard = new CloseGuard();
    private final SurfaceControl.Transaction mTransaction = new SurfaceControl.Transaction();
    /** Used to inset the activity content to allow space for a caption bar. */
    private final Binder mCaptionInsetsOwner = new Binder();
    private final ShellTaskOrganizer mTaskOrganizer;
    private final Executor mShellExecutor;
    private final SyncTransactionQueue mSyncQueue;
    private final TaskViewTransitions mTaskViewTransitions;
    private final Context mContext;

    /**
     * There could be a situation where we have task info and receive
     * {@link #onTaskAppeared(ActivityManager.RunningTaskInfo, SurfaceControl)}, however, the
     * activity might fail to open, and in this case we need to clean up the task view / notify
     * listeners of a task removal. This requires task info, so we save the info from onTaskAppeared
     * in this situation to allow us to notify listeners correctly if the task failed to open.
     */
    private ActivityManager.RunningTaskInfo mPendingInfo;
    private TaskViewBase mTaskViewBase;
    protected ActivityManager.RunningTaskInfo mTaskInfo;
    private WindowContainerToken mTaskToken;
    private SurfaceControl mTaskLeash;
    /* Indicates that the task we attempted to launch in the task view failed to launch. */
    private boolean mTaskNotFound;
    private boolean mSurfaceCreated;
    private SurfaceControl mSurfaceControl;
    private boolean mIsInitialized;
    private boolean mNotifiedForInitialized;
    private boolean mHideTaskWithSurface = true;
    private TaskView.Listener mListener;
    private Executor mListenerExecutor;
    private Rect mCaptionInsets;

    public TaskViewTaskController(Context context, ShellTaskOrganizer organizer,
            TaskViewTransitions taskViewTransitions, SyncTransactionQueue syncQueue) {
        mContext = context;
        mTaskOrganizer = organizer;
        mShellExecutor = organizer.getExecutor();
        mSyncQueue = syncQueue;
        mTaskViewTransitions = taskViewTransitions;
        mShellExecutor.execute(() -> {
            if (mTaskViewTransitions != null) {
                mTaskViewTransitions.addTaskView(this);
            }
        });
        mGuard.open("release");
    }

    /**
     * Specifies if the task should be hidden when the surface is destroyed.
     * <p>This is {@code true} by default.
     *
     * @param hideTaskWithSurface {@code false} if task needs to remain visible even when the
     *                            surface is destroyed, {@code true} otherwise.
     */
    public void setHideTaskWithSurface(boolean hideTaskWithSurface) {
        // TODO(b/299535374): Remove mHideTaskWithSurface once the taskviews with launch root tasks
        // are moved to a window in SystemUI in auto.
        mHideTaskWithSurface = hideTaskWithSurface;
    }

    SurfaceControl getSurfaceControl() {
        return mSurfaceControl;
    }

    /**
     * Sets the provided {@link TaskViewBase}, which is used to notify the client part about the
     * task related changes and getting the current bounds.
     */
    public void setTaskViewBase(TaskViewBase taskViewBase) {
        mTaskViewBase = taskViewBase;
    }

    /**
     * @return {@code True} when the TaskView's surface has been created, {@code False} otherwise.
     */
    public boolean isInitialized() {
        return mIsInitialized;
    }

    /** Until all users are converted, we may have mixed-use (eg. Car). */
    public boolean isUsingShellTransitions() {
        return mTaskViewTransitions != null && mTaskViewTransitions.isEnabled();
    }

    /**
     * Only one listener may be set on the view, throws an exception otherwise.
     */
    void setListener(@NonNull Executor executor, TaskView.Listener listener) {
        if (mListener != null) {
            throw new IllegalStateException(
                    "Trying to set a listener when one has already been set");
        }
        mListener = listener;
        mListenerExecutor = executor;
    }

    /**
     * Launch an activity represented by {@link ShortcutInfo}.
     * <p>The owner of this container must be allowed to access the shortcut information,
     * as defined in {@link LauncherApps#hasShortcutHostPermission()} to use this method.
     *
     * @param shortcut     the shortcut used to launch the activity.
     * @param options      options for the activity.
     * @param launchBounds the bounds (window size and position) that the activity should be
     *                     launched in, in pixels and in screen coordinates.
     */
    public void startShortcutActivity(@NonNull ShortcutInfo shortcut,
            @NonNull ActivityOptions options, @Nullable Rect launchBounds) {
        prepareActivityOptions(options, launchBounds);
        LauncherApps service = mContext.getSystemService(LauncherApps.class);
        if (isUsingShellTransitions()) {
            mShellExecutor.execute(() -> {
                final WindowContainerTransaction wct = new WindowContainerTransaction();
                wct.startShortcut(mContext.getPackageName(), shortcut, options.toBundle());
                mTaskViewTransitions.startTaskView(wct, this, options.getLaunchCookie());
            });
            return;
        }
        try {
            service.startShortcut(shortcut, null /* sourceBounds */, options.toBundle());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Launch a new activity.
     *
     * @param pendingIntent Intent used to launch an activity.
     * @param fillInIntent  Additional Intent data, see {@link Intent#fillIn Intent.fillIn()}
     * @param options       options for the activity.
     * @param launchBounds  the bounds (window size and position) that the activity should be
     *                      launched in, in pixels and in screen coordinates.
     */
    public void startActivity(@NonNull PendingIntent pendingIntent, @Nullable Intent fillInIntent,
            @NonNull ActivityOptions options, @Nullable Rect launchBounds) {
        prepareActivityOptions(options, launchBounds);
        if (isUsingShellTransitions()) {
            mShellExecutor.execute(() -> {
                WindowContainerTransaction wct = new WindowContainerTransaction();
                wct.sendPendingIntent(pendingIntent, fillInIntent, options.toBundle());
                mTaskViewTransitions.startTaskView(wct, this, options.getLaunchCookie());
            });
            return;
        }
        try {
            pendingIntent.send(mContext, 0 /* code */, fillInIntent,
                    null /* onFinished */, null /* handler */, null /* requiredPermission */,
                    options.toBundle());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Attaches the given root task {@code taskInfo} in the task view.
     *
     * <p> Since {@link ShellTaskOrganizer#createRootTask(int, int,
     * ShellTaskOrganizer.TaskListener)} does not use the shell transitions flow, this method is
     * used as an entry point for an already-created root-task in the task view.
     *
     * @param taskInfo the task info of the root task.
     * @param leash    the {@link android.content.pm.ShortcutInfo.Surface} of the root task
     * @param wct      The Window container work that should happen as part of this set up.
     */
    public void startRootTask(ActivityManager.RunningTaskInfo taskInfo, SurfaceControl leash,
            @Nullable WindowContainerTransaction wct) {
        if (wct == null) {
            wct = new WindowContainerTransaction();
        }
        // This method skips the regular flow where an activity task is launched as part of a new
        // transition in taskview and then transition is intercepted using the launchcookie.
        // The task here is already created and running, it just needs to be reparented, resized
        // and tracked correctly inside taskview. Which is done by calling
        // prepareOpenAnimationInternal() and then manually enqueuing the resulting window container
        // transaction.
        prepareOpenAnimationInternal(true /* newTask */, mTransaction /* startTransaction */,
                null /* finishTransaction */, taskInfo, leash, wct);
        mTransaction.apply();
        mTaskViewTransitions.startInstantTransition(TRANSIT_CHANGE, wct);
    }

    private void prepareActivityOptions(ActivityOptions options, Rect launchBounds) {
        final Binder launchCookie = new Binder();
        mShellExecutor.execute(() -> {
            mTaskOrganizer.setPendingLaunchCookieListener(launchCookie, this);
        });
        options.setLaunchBounds(launchBounds);
        options.setLaunchCookie(launchCookie);
        options.setLaunchWindowingMode(WINDOWING_MODE_MULTI_WINDOW);
        options.setRemoveWithTaskOrganizer(true);
    }

    /**
     * Release this container if it is initialized.
     */
    public void release() {
        performRelease();
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if (mGuard != null) {
                mGuard.warnIfOpen();
                performRelease();
            }
        } finally {
            super.finalize();
        }
    }

    private void performRelease() {
        mShellExecutor.execute(() -> {
            if (mTaskViewTransitions != null) {
                mTaskViewTransitions.removeTaskView(this);
            }
            mTaskOrganizer.removeListener(this);
            resetTaskInfo();
        });
        mGuard.close();
        mIsInitialized = false;
        notifyReleased();
    }

    /** Called when the {@link TaskViewTaskController} has been released. */
    protected void notifyReleased() {
        if (mListener != null && mNotifiedForInitialized) {
            mListenerExecutor.execute(() -> {
                mListener.onReleased();
            });
            mNotifiedForInitialized = false;
        }
    }

    private void resetTaskInfo() {
        mTaskInfo = null;
        mTaskToken = null;
        mTaskLeash = null;
        mPendingInfo = null;
        mTaskNotFound = false;
    }

    /** This method shouldn't be called when shell transitions are enabled. */
    private void updateTaskVisibility() {
        boolean visible = mSurfaceCreated;
        if (!visible && !mHideTaskWithSurface) {
            return;
        }
        WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.setHidden(mTaskToken, !visible /* hidden */);
        if (!visible) {
            wct.reorder(mTaskToken, false /* onTop */);
        }
        mSyncQueue.queue(wct);
        if (mListener == null) {
            return;
        }
        int taskId = mTaskInfo.taskId;
        mSyncQueue.runInSync((t) -> {
            mListenerExecutor.execute(() -> {
                mListener.onTaskVisibilityChanged(taskId, mSurfaceCreated);
            });
        });
    }

    @Override
    public void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo,
            SurfaceControl leash) {
        if (isUsingShellTransitions()) {
            mPendingInfo = taskInfo;
            if (mTaskNotFound) {
                // If we were already notified by shell transit that we don't have the
                // the task, clean it up now.
                cleanUpPendingTask();
            }
            // Everything else handled by enter transition.
            return;
        }
        mTaskInfo = taskInfo;
        mTaskToken = taskInfo.token;
        mTaskLeash = leash;

        if (mSurfaceCreated) {
            // Surface is ready, so just reparent the task to this surface control
            mTransaction.reparent(mTaskLeash, mSurfaceControl)
                    .show(mTaskLeash)
                    .apply();
        } else {
            // The surface has already been destroyed before the task has appeared,
            // so go ahead and hide the task entirely
            updateTaskVisibility();
        }
        mTaskOrganizer.setInterceptBackPressedOnTaskRoot(mTaskToken, true);
        mSyncQueue.runInSync((t) -> {
            mTaskViewBase.onTaskAppeared(taskInfo, leash);
        });

        if (mListener != null) {
            final int taskId = taskInfo.taskId;
            final ComponentName baseActivity = taskInfo.baseActivity;
            mListenerExecutor.execute(() -> {
                mListener.onTaskCreated(taskId, baseActivity);
            });
        }
    }

    @Override
    public void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
        // Unlike Appeared, we can't yet guarantee that vanish will happen within a transition that
        // we know about -- so leave clean-up here even if shell transitions are enabled.
        if (mTaskToken == null || !mTaskToken.equals(taskInfo.token)) return;

        final SurfaceControl taskLeash = mTaskLeash;
        handleAndNotifyTaskRemoval(mTaskInfo);

        mTransaction.reparent(taskLeash, null).apply();
        resetTaskInfo();
    }

    @Override
    public void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
        mTaskViewBase.onTaskInfoChanged(taskInfo);
    }

    @Override
    public void onBackPressedOnTaskRoot(ActivityManager.RunningTaskInfo taskInfo) {
        if (mTaskToken == null || !mTaskToken.equals(taskInfo.token)) return;
        if (mListener != null) {
            final int taskId = taskInfo.taskId;
            mListenerExecutor.execute(() -> {
                mListener.onBackPressedOnTaskRoot(taskId);
            });
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
        if (mTaskInfo == null || mTaskLeash == null || mTaskInfo.taskId != taskId) {
            throw new IllegalArgumentException("There is no surface for taskId=" + taskId);
        }
        return mTaskLeash;
    }

    @Override
    public void dump(@androidx.annotation.NonNull PrintWriter pw, String prefix) {
        final String innerPrefix = prefix + "  ";
        final String childPrefix = innerPrefix + "  ";
        pw.println(prefix + this);
    }

    @Override
    public String toString() {
        return "TaskViewTaskController" + ":" + (mTaskInfo != null ? mTaskInfo.taskId : "null");
    }

    /**
     * Should be called when the client surface is created.
     *
     * @param surfaceControl the {@link SurfaceControl} for the underlying surface.
     */
    public void surfaceCreated(SurfaceControl surfaceControl) {
        mSurfaceCreated = true;
        mIsInitialized = true;
        mSurfaceControl = surfaceControl;
        // SurfaceControl is expected to be null only in the case of unit tests. Guard against it
        // to avoid runtime exception in SurfaceControl.Transaction.
        if (surfaceControl != null) {
            // TaskView is meant to contain app activities which shouldn't have trusted overlays
            // flag set even when itself reparented in a window which is trusted.
            mTransaction.setTrustedOverlay(surfaceControl, TrustedOverlay.DISABLED)
                    .apply();
        }
        notifyInitialized();
        mShellExecutor.execute(() -> {
            if (mTaskToken == null) {
                // Nothing to update, task is not yet available
                return;
            }
            if (isUsingShellTransitions()) {
                mTaskViewTransitions.setTaskViewVisible(this, true /* visible */);
                return;
            }
            // Reparent the task when this surface is created
            mTransaction.reparent(mTaskLeash, mSurfaceControl)
                    .show(mTaskLeash)
                    .apply();
            updateTaskVisibility();
        });
    }

    /**
     * Sets the window bounds to {@code boundsOnScreen}.
     * Call when view position or size has changed. Can also be called before the animation when
     * the final bounds are known.
     * Do not call during the animation.
     *
     * @param boundsOnScreen the on screen bounds of the surface view.
     */
    public void setWindowBounds(Rect boundsOnScreen) {
        if (mTaskToken == null) {
            return;
        }

        if (isUsingShellTransitions()) {
            mShellExecutor.execute(() -> {
                // Sync Transactions can't operate simultaneously with shell transition collection.
                mTaskViewTransitions.setTaskBounds(this, boundsOnScreen);
            });
            return;
        }

        WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.setBounds(mTaskToken, boundsOnScreen);
        mSyncQueue.queue(wct);
    }

    /**
     * Call to remove the task from window manager. This task will not appear in recents.
     */
    void removeTask() {
        if (mTaskToken == null) {
            // Call to remove task before we have one, do nothing
            Slog.w(TAG, "Trying to remove a task that was never added? (no taskToken)");
            return;
        }
        mShellExecutor.execute(() -> {
            WindowContainerTransaction wct = new WindowContainerTransaction();
            wct.removeTask(mTaskToken);
            mTaskViewTransitions.closeTaskView(wct, this);
        });
    }

    /**
     * Sets a region of the task to inset to allow for a caption bar.
     *
     * @param captionInsets the rect for the insets in screen coordinates.
     */
    void setCaptionInsets(Rect captionInsets) {
        if (mCaptionInsets != null && mCaptionInsets.equals(captionInsets)) {
            return;
        }
        mCaptionInsets = captionInsets;
        applyCaptionInsetsIfNeeded();
    }

    void applyCaptionInsetsIfNeeded() {
        if (mTaskToken == null) return;
        WindowContainerTransaction wct = new WindowContainerTransaction();
        if (mCaptionInsets != null) {
            wct.addInsetsSource(mTaskToken, mCaptionInsetsOwner, 0,
                    WindowInsets.Type.captionBar(), mCaptionInsets, null /* boundingRects */);
        } else {
            wct.removeInsetsSource(mTaskToken, mCaptionInsetsOwner, 0,
                    WindowInsets.Type.captionBar());
        }
        mTaskOrganizer.applyTransaction(wct);
    }

    /** Should be called when the client surface is destroyed. */
    public void surfaceDestroyed() {
        mSurfaceCreated = false;
        mSurfaceControl = null;
        mShellExecutor.execute(() -> {
            if (mTaskToken == null) {
                // Nothing to update, task is not yet available
                return;
            }

            if (isUsingShellTransitions()) {
                mTaskViewTransitions.setTaskViewVisible(this, false /* visible */);
                return;
            }

            // Unparent the task when this surface is destroyed
            mTransaction.reparent(mTaskLeash, null).apply();
            updateTaskVisibility();
        });
    }

    /** Called when the {@link TaskViewTaskController} is initialized. */
    protected void notifyInitialized() {
        if (mListener != null && !mNotifiedForInitialized) {
            mNotifiedForInitialized = true;
            mListenerExecutor.execute(() -> {
                mListener.onInitialized();
            });
        }
    }

    /** Notifies listeners of a task being removed and stops intercepting back presses on it. */
    private void handleAndNotifyTaskRemoval(ActivityManager.RunningTaskInfo taskInfo) {
        if (taskInfo != null) {
            if (mListener != null) {
                final int taskId = taskInfo.taskId;
                mListenerExecutor.execute(() -> {
                    mListener.onTaskRemovalStarted(taskId);
                });
            }
            mTaskViewBase.onTaskVanished(taskInfo);
            mTaskOrganizer.setInterceptBackPressedOnTaskRoot(taskInfo.token, false);
        }
    }

    /** Returns the task info for the task in the TaskView. */
    @Nullable
    public ActivityManager.RunningTaskInfo getTaskInfo() {
        return mTaskInfo;
    }

    /**
     * Indicates that the task was not found in the start animation for the transition.
     * In this case we should clean up the task if we have the pending info. If we don't
     * have the pending info, we'll do it when we receive it in
     * {@link #onTaskAppeared(ActivityManager.RunningTaskInfo, SurfaceControl)}.
     */
    void setTaskNotFound() {
        mTaskNotFound = true;
        if (mPendingInfo != null) {
            cleanUpPendingTask();
        }
    }

    /**
     * Called when a task failed to open and we need to clean up task view /
     * notify users of task view.
     */
    void cleanUpPendingTask() {
        if (mPendingInfo != null) {
            final ActivityManager.RunningTaskInfo pendingInfo = mPendingInfo;
            handleAndNotifyTaskRemoval(pendingInfo);

            // Make sure the task is removed
            WindowContainerTransaction wct = new WindowContainerTransaction();
            wct.removeTask(pendingInfo.token);
            mTaskViewTransitions.closeTaskView(wct, this);
        }
        resetTaskInfo();
    }

    void prepareHideAnimation(@NonNull SurfaceControl.Transaction finishTransaction) {
        if (mTaskToken == null) {
            // Nothing to update, task is not yet available
            return;
        }

        finishTransaction.reparent(mTaskLeash, null);

        if (mListener != null) {
            final int taskId = mTaskInfo.taskId;
            mListener.onTaskVisibilityChanged(taskId, mSurfaceCreated /* visible */);
        }
    }

    /**
     * Called when the associated Task closes. If the TaskView is just being hidden, prepareHide
     * is used instead.
     */
    void prepareCloseAnimation() {
        handleAndNotifyTaskRemoval(mTaskInfo);
        resetTaskInfo();
    }

    void prepareOpenAnimation(final boolean newTask,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            ActivityManager.RunningTaskInfo taskInfo, SurfaceControl leash,
            WindowContainerTransaction wct) {
        prepareOpenAnimationInternal(newTask, startTransaction, finishTransaction, taskInfo, leash,
                wct);
    }

    private void prepareOpenAnimationInternal(final boolean newTask,
            SurfaceControl.Transaction startTransaction,
            SurfaceControl.Transaction finishTransaction,
            ActivityManager.RunningTaskInfo taskInfo, SurfaceControl leash,
            WindowContainerTransaction wct) {
        mPendingInfo = null;
        mTaskInfo = taskInfo;
        mTaskToken = mTaskInfo.token;
        mTaskLeash = leash;
        if (mSurfaceCreated) {
            // Surface is ready, so just reparent the task to this surface control
            startTransaction.reparent(mTaskLeash, mSurfaceControl)
                    .show(mTaskLeash);
            // Also reparent on finishTransaction since the finishTransaction will reparent back
            // to its "original" parent by default.
            Rect boundsOnScreen = mTaskViewBase.getCurrentBoundsOnScreen();
            if (finishTransaction != null) {
                finishTransaction.reparent(mTaskLeash, mSurfaceControl)
                        .setPosition(mTaskLeash, 0, 0)
                        // TODO: maybe once b/280900002 is fixed this will be unnecessary
                        .setWindowCrop(mTaskLeash, boundsOnScreen.width(), boundsOnScreen.height());
            }
            mTaskViewTransitions.updateBoundsState(this, boundsOnScreen);
            mTaskViewTransitions.updateVisibilityState(this, true /* visible */);
            wct.setBounds(mTaskToken, boundsOnScreen);
            applyCaptionInsetsIfNeeded();
        } else {
            // The surface has already been destroyed before the task has appeared,
            // so go ahead and hide the task entirely
            wct.setHidden(mTaskToken, true /* hidden */);
            mTaskViewTransitions.updateVisibilityState(this, false /* visible */);
            // listener callback is below
        }
        if (newTask) {
            mTaskOrganizer.setInterceptBackPressedOnTaskRoot(mTaskToken, true /* intercept */);
        }

        if (mTaskInfo.taskDescription != null) {
            int backgroundColor = mTaskInfo.taskDescription.getBackgroundColor();
            mTaskViewBase.setResizeBgColor(startTransaction, backgroundColor);
        }

        mTaskViewBase.onTaskAppeared(mTaskInfo, mTaskLeash);
        if (mListener != null) {
            final int taskId = mTaskInfo.taskId;
            final ComponentName baseActivity = mTaskInfo.baseActivity;

            mListenerExecutor.execute(() -> {
                if (newTask) {
                    mListener.onTaskCreated(taskId, baseActivity);
                }
                // Even if newTask, send a visibilityChange if the surface was destroyed.
                if (!newTask || !mSurfaceCreated) {
                    mListener.onTaskVisibilityChanged(taskId, mSurfaceCreated /* visible */);
                }
            });
        }
    }
}
