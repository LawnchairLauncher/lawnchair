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

import static android.view.InsetsSource.FLAG_FORCE_CONSUMING;
import static android.view.InsetsSource.FLAG_FORCE_CONSUMING_OPAQUE_CAPTION_BAR;

import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_BUBBLES_NOISY;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Insets;
import android.graphics.Rect;
import android.gui.TrustedOverlay;
import android.os.Binder;
import android.util.CloseGuard;
import android.view.SurfaceControl;
import android.view.WindowInsets;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper;

import java.io.PrintWriter;
import java.util.concurrent.Executor;

/**
 * This class represents the visible aspect of a task in a {@link TaskView}. All the {@link
 * TaskView} to {@link TaskViewTaskController} interactions are done via direct method calls.
 *
 * The reverse communication is done via the {@link TaskViewBase} interface.
 */
public class TaskViewTaskController implements ShellTaskOrganizer.TaskListener {

    private static final String TAG = TaskViewTaskController.class.getSimpleName();

    private final CloseGuard mGuard = new CloseGuard();
    private final SurfaceControl.Transaction mTransaction = new SurfaceControl.Transaction();
    /** Used to inset the activity content to allow space for a caption bar. */
    private final Binder mCaptionInsetsOwner = new Binder();
    @NonNull
    private final ShellTaskOrganizer mTaskOrganizer;
    private final Executor mShellExecutor;
    private final SyncTransactionQueue mSyncQueue;
    private final TaskViewController mTaskViewController;
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

    public TaskViewTaskController(Context context, @NonNull ShellTaskOrganizer organizer,
            TaskViewController taskViewController, SyncTransactionQueue syncQueue) {
        mContext = context;
        mTaskOrganizer = organizer;
        mShellExecutor = organizer.getExecutor();
        mSyncQueue = syncQueue;
        mTaskViewController = taskViewController;
        mShellExecutor.execute(() -> {
            if (mTaskViewController != null) {
                mTaskViewController.registerTaskView(this);
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
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "TaskController.setHideTaskWithSurface(): taskView=%d "
                + "hideTask=%b", hashCode(), hideTaskWithSurface);
        // TODO(b/299535374): Remove mHideTaskWithSurface once the taskviews with launch root tasks
        // are moved to a window in SystemUI in auto.
        mHideTaskWithSurface = hideTaskWithSurface;
    }

    @VisibleForTesting
    SurfaceControl getTaskLeash() {
        return mTaskLeash;
    }

    SurfaceControl getSurfaceControl() {
        return mSurfaceControl;
    }

    Context getContext() {
        return mContext;
    }

    /**
     * Sets the provided {@link TaskViewBase}, which is used to notify the client part about the
     * task related changes and getting the current bounds.
     */
    public void setTaskViewBase(TaskViewBase taskViewBase) {
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "TaskController.setTaskViewBase(): taskView=%d base=%s",
                hashCode(), taskViewBase.hashCode());
        mTaskViewBase = taskViewBase;
    }

    /**
     * @return {@code True} when the TaskView's surface has been created, {@code False} otherwise.
     */
    public boolean isInitialized() {
        return mIsInitialized;
    }

    /** Returns the task token for the task in the TaskView. */
    public WindowContainerToken getTaskToken() {
        return mTaskToken;
    }

    void setResizeBgColor(SurfaceControl.Transaction t, int bgColor) {
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "TaskController.setResizeBgColor(): taskView=%d "
                + "bgColor=%s", hashCode(), Integer.toHexString(bgColor));
        mTaskViewBase.setResizeBgColor(t, bgColor);
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
     * Release this container if it is initialized.
     */
    public void release() {
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "TaskController.release(): taskView=%d", hashCode());
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
            if (mTaskViewController != null) {
                mTaskViewController.unregisterTaskView(this);
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
        if (mTaskLeash != null) {
            mTaskLeash.release();
            mTaskLeash = null;
        }
        mPendingInfo = null;
        mTaskNotFound = false;
    }

    /** This method shouldn't be called when shell transitions are enabled. */
    private void updateTaskVisibility() {
        boolean visible = mSurfaceCreated;
        if (!visible && !mHideTaskWithSurface) {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "TaskController.updateTaskVisibility(): taskView=%d "
                    + "Not visible, skip hide", hashCode());
            return;
        }
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "TaskController.updateTaskVisibility(): taskView=%d "
                + "visible=%b", hashCode(), visible);
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
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "TaskController.onTaskAppeared(): taskView=%d task=%s",
                hashCode(), taskInfo);
        mPendingInfo = taskInfo;
        if (mTaskNotFound) {
            // If we were already notified by shell transit that we don't have the
            // the task, clean it up now.
            cleanUpPendingTask();
        }
        // Everything else handled by enter transition.
    }

    @Override
    public void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "TaskController.onTaskVanished(): taskView=%d task=%s",
                hashCode(), taskInfo);
        // Unlike Appeared, we can't yet guarantee that vanish will happen within a transition that
        // we know about -- so leave clean-up here even if shell transitions are enabled.
        if (mTaskToken == null || !mTaskToken.equals(taskInfo.token)) return;

        if (BubbleAnythingFlagHelper.enableCreateAnyBubble()) {
            handleAndNotifyTaskRemoval(taskInfo);
        } else {
            handleAndNotifyTaskRemoval(mTaskInfo);
        }

        resetTaskInfo();
    }

    @Override
    public void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
        mTaskViewBase.onTaskInfoChanged(taskInfo);
        if (BubbleAnythingFlagHelper.enableCreateAnyBubble()) {
            if (mListener != null) {
                mListenerExecutor.execute(() -> {
                    mListener.onTaskInfoChanged(taskInfo);
                });
            }
        }
    }

    @Override
    public void onBackPressedOnTaskRoot(ActivityManager.RunningTaskInfo taskInfo) {
        if (mTaskToken == null || !mTaskToken.equals(taskInfo.token)) {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "TaskController.onBackPressedOnTaskRoot(): "
                    + "taskView=%d Ignored", hashCode());
            return;
        }
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "TaskController.onBackPressedOnTaskRoot(): taskView=%d "
                + "task=%s", hashCode(), taskInfo);
        if (mListener != null) {
            final int taskId = taskInfo.taskId;
            mListenerExecutor.execute(() -> {
                mListener.onBackPressedOnTaskRoot(taskId);
            });
        }
    }

    @Override
    public void attachChildSurfaceToTask(int taskId, SurfaceControl.Builder b) {
        if (BubbleAnythingFlagHelper.enableCreateAnyBubble()) {
            // TODO(b/419342398): Add a notifier when the surface is ready for this to be called.
            if (!mIsInitialized) return;
        }
        b.setParent(findTaskSurface(taskId));
    }

    @Override
    public void reparentChildSurfaceToTask(int taskId, SurfaceControl sc,
            SurfaceControl.Transaction t) {
        if (BubbleAnythingFlagHelper.enableCreateAnyBubble()) {
            // TODO(b/419342398): Add a notifier when the surface is ready for this to be called.
            if (!mIsInitialized) return;
        }
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
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "TaskController.surfaceCreated(): taskView=%d",
                hashCode());
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
        if (!mNotifiedForInitialized) {
            notifyInitialized();
        } else {
            notifySurfaceAlreadyCreated();
        }
        mShellExecutor.execute(() -> {
            if (mTaskToken == null) {
                // Nothing to update, task is not yet available
                return;
            }
            mTaskViewController.setTaskViewVisible(this, true /* visible */);
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
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "TaskController.setCaptionInsets(): taskView=%d "
                        + "captionInsets=%s", hashCode(), captionInsets);
        mCaptionInsets = captionInsets;
        applyCaptionInsetsIfNeeded();
    }

    @Nullable Binder getCaptionInsetsOwner() {
        return mCaptionInsetsOwner;
    }

    void applyCaptionInsetsIfNeeded() {
        if (mTaskToken == null) return;
        WindowContainerTransaction wct = new WindowContainerTransaction();
        if (mCaptionInsets != null) {
            int flags = 0;
            if (BubbleAnythingFlagHelper.enableCreateAnyBubbleWithAppCompatFixes()) {
                // When the bubble bar app handle is visible, the caption insets will be set and
                // should always be consumed, otherwise the handle may block app content.
                flags = FLAG_FORCE_CONSUMING | FLAG_FORCE_CONSUMING_OPAQUE_CAPTION_BAR;
            }
            if (com.android.window.flags2.Flags.relativeInsets()) {
                wct.addInsetsSource(mTaskToken, mCaptionInsetsOwner, 0,
                        WindowInsets.Type.captionBar(), Insets.of(0, mCaptionInsets.height(), 0, 0),
                        null /* boundingRects */, flags);
            } else {
                wct.addInsetsSource(mTaskToken, mCaptionInsetsOwner, 0,
                        WindowInsets.Type.captionBar(), mCaptionInsets, null /* boundingRects */,
                        flags);
            }
        } else {
            wct.removeInsetsSource(mTaskToken, mCaptionInsetsOwner, 0,
                    WindowInsets.Type.captionBar());
        }
        mTaskOrganizer.applyTransaction(wct);
    }

    /** Should be called when the client surface is destroyed. */
    public void surfaceDestroyed() {
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "TaskController.surfaceDestroyed(): taskView=%d",
                hashCode());
        mSurfaceCreated = false;
        mSurfaceControl = null;
        mShellExecutor.execute(() -> {
            if (mTaskToken == null) {
                // Nothing to update, task is not yet available
                return;
            }

            mTaskViewController.setTaskViewVisible(this, false /* visible */);
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

    /** Called when the surface is created, only when the task view is alreayd initialized. */
    protected void notifySurfaceAlreadyCreated() {
        if (mListener != null) {
            mListenerExecutor.execute(() -> {
                mListener.onSurfaceAlreadyCreated();
            });
        }
    }

    /** Notifies listeners of a task being removed. */
    public void notifyTaskRemovalStarted(@NonNull ActivityManager.RunningTaskInfo taskInfo) {
        if (mListener == null) return;
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "TaskController.notifyTaskRemovalStarted(): taskView=%d "
                + "task=%s", hashCode(), taskInfo);

        if (BubbleAnythingFlagHelper.enableCreateAnyBubble()) {
            // Update mTaskInfo to reflect the latest task state before notifying the listener, as
            // it may have been changed by ShellTaskOrganizer#onTaskInfoChanged(), which triggers
            // task listener updates via ShellTaskOrganizer#updateTaskListenerIfNeeded() when a
            // task's info changes, resulting in onTaskVanished() being called on the old listener;
            // without updating mTaskInfo here would leave it with outdated information (e.g.,
            // windowing mode), potentially causing incorrect state checks and unintended cleanup
            // actions in consumers of TaskViewTaskController, such as task removal in
            // BubbleTaskView#cleanup.
            mTaskInfo = taskInfo;
            mTaskToken = mTaskInfo.token;
        }

        final int taskId = taskInfo.taskId;
        mListenerExecutor.execute(() -> mListener.onTaskRemovalStarted(taskId));
    }

    /** Notifies listeners of a task being removed and stops intercepting back presses on it. */
    private void handleAndNotifyTaskRemoval(ActivityManager.RunningTaskInfo taskInfo) {
        if (taskInfo != null) {
            notifyTaskRemovalStarted(taskInfo);
            mTaskViewBase.onTaskVanished(taskInfo);
        }
    }

    /** Returns the task info for the task in the TaskView. */
    @Nullable
    public ActivityManager.RunningTaskInfo getTaskInfo() {
        return mTaskInfo;
    }

    /** Returns the task organizer for the task in the TaskView. */
    @NonNull
    public ShellTaskOrganizer getTaskOrganizer() {
        return mTaskOrganizer;
    }

    @VisibleForTesting
    ActivityManager.RunningTaskInfo getPendingInfo() {
        return mPendingInfo;
    }

    /**
     * Indicates that the task was not found in the start animation for the transition.
     * In this case we should clean up the task if we have the pending info. If we don't
     * have the pending info, we'll do it when we receive it in
     * {@link #onTaskAppeared(ActivityManager.RunningTaskInfo, SurfaceControl)}.
     */
    public void setTaskNotFound() {
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "TaskController.setTaskNotFound(): taskView=%d",
                hashCode());
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
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "TaskController.cleanUpPendingTask(): taskView=%d "
                + "pending=%s", hashCode(), mPendingInfo);
        if (mPendingInfo != null) {
            final ActivityManager.RunningTaskInfo pendingInfo = mPendingInfo;
            handleAndNotifyTaskRemoval(pendingInfo);

            // Make sure the task is removed
            mTaskViewController.removeTaskView(this, pendingInfo.token);
        }
        resetTaskInfo();
    }

    void prepareHideAnimation(@NonNull SurfaceControl.Transaction finishTransaction) {
        if (mTaskToken == null) {
            // Nothing to update, task is not yet available
            return;
        }

        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "TaskController.prepareHideAnimation(): taskView=%d",
                hashCode());
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
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "TaskController.prepareCloseAnimation(): taskView=%d",
                hashCode());
        handleAndNotifyTaskRemoval(mTaskInfo);
        resetTaskInfo();
    }

    /**
     * Prepare this taskview to open {@param taskInfo}.
     * @return The bounds of the task or {@code null} on failure (surface is destroyed)
     */
    Rect prepareOpen(ActivityManager.RunningTaskInfo taskInfo, SurfaceControl leash) {
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "TaskController.prepareOpen(): taskView=%d", hashCode());
        mPendingInfo = null;
        mTaskInfo = taskInfo;
        mTaskToken = mTaskInfo.token;
        mTaskLeash = new SurfaceControl(leash, "TaskController.prepareOpen");
        if (!mSurfaceCreated) {
            return null;
        }
        return mTaskViewBase.getCurrentBoundsOnScreen();
    }

    /** Notify that the associated task has appeared. This will call appropriate listeners. */
    void notifyAppeared(final boolean newTask) {
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
