/*
 * Copyright (C) 2021 The Android Open Source Project
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
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_NONE;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.view.WindowManager.TRANSIT_TO_FRONT;

import static com.android.window.flags2.Flags.enableHandlersDebuggingMode;
import static com.android.wm.shell.bubbles.util.BubbleUtils.getExitBubbleTransaction;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_BUBBLES;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_BUBBLES_NOISY;
import static com.android.wm.shell.transition.TransitionDispatchState.CAPTURED_CHANGE_IN_WRONG_TRANSITION;
import static com.android.wm.shell.transition.TransitionDispatchState.CAPTURED_UNRELATED_CHANGE;
import static com.android.wm.shell.transition.TransitionDispatchState.LOST_RELEVANT_CHANGE;
import static com.android.wm.shell.transition.Transitions.transitTypeToString;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.graphics.Rect;
import android.os.Binder;
import android.os.IBinder;
import android.util.Slog;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.annotation.VisibleForTesting;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.Flags;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.shared.TransitionUtil;
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper;
import com.android.wm.shell.transition.TransitionDispatchState;
import com.android.wm.shell.transition.Transitions;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Handles Shell Transitions that involve TaskView tasks.
 */
public class TaskViewTransitions implements Transitions.TransitionHandler, TaskViewController {
    static final String TAG = "TaskViewTransitions";

    private final TaskViewRepository mTaskViewRepo;
    private final ArrayList<PendingTransition> mPending = new ArrayList<>();
    private final Transitions mTransitions;
    private final boolean[] mRegistered = new boolean[]{false};
    private final ShellTaskOrganizer mTaskOrganizer;
    private final Executor mShellExecutor;
    private final SyncTransactionQueue mSyncQueue;

    /** A temp transaction used for quick things. */
    private final SurfaceControl.Transaction mTransaction = new SurfaceControl.Transaction();

    /**
     * A display change transition that also involves a TaskView. The display animation is deferred
     * until the TaskView's bounds are updated. This object holds the logic to dispatch the display
     * transition once the TaskView is ready.
     */
    private PendingRedirectTransition mPendingRedirectTransition;

    /**
     * TaskView makes heavy use of startTransition. Only one shell-initiated transition can be
     * in-flight (collecting) at a time (because otherwise, the operations could get merged into
     * a single transition). So, keep a queue here until we add a queue in server-side.
     */
    @VisibleForTesting
    static class PendingTransition {
        final @WindowManager.TransitionType int mType;
        final WindowContainerTransaction mWct;
        final @NonNull TaskViewTaskController mTaskView;
        ExternalTransition mExternalTransition;
        IBinder mClaimed;

        /**
         * This is needed because arbitrary activity launches can still "intrude" into any
         * transition since `startActivity` is a synchronous call. Once that is solved, we can
         * remove this.
         */
        final IBinder mLaunchCookie;

        PendingTransition(@WindowManager.TransitionType int type,
                @Nullable WindowContainerTransaction wct,
                @NonNull TaskViewTaskController taskView,
                @Nullable IBinder launchCookie) {
            mType = type;
            mWct = wct;
            mTaskView = taskView;
            mLaunchCookie = launchCookie;
        }
        /** Dumps PendingTransition state. */
        public void dump(PrintWriter pw, String prefix) {
            pw.print(prefix); pw.println("Pending transition:");
            pw.print(prefix); pw.println("  task view: " + mTaskView);
            pw.print(prefix); pw.println("  transition type: " + mType);
            pw.print(prefix); pw.println("  external transition: " + mExternalTransition);
            pw.print(prefix); pw.println("  claim token: " + mClaimed);
        }
    }

    public TaskViewTransitions(Transitions transitions, TaskViewRepository repository,
            ShellTaskOrganizer taskOrganizer, SyncTransactionQueue syncQueue) {
        mTransitions = transitions;
        mTaskOrganizer = taskOrganizer;
        mShellExecutor = taskOrganizer.getExecutor();
        mSyncQueue = syncQueue;
        mTaskViewRepo = repository;
        // Defer registration until the first TaskView because we want this to be the "first" in
        // priority when handling requests.
        // TODO(210041388): register here once we have an explicit ordering mechanism.
    }

    public TaskViewRepository getRepository() {
        return mTaskViewRepo;
    }

    @Override
    public void registerTaskView(TaskViewTaskController tv) {
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "Transitions.registerTaskView(): taskView=%d",
                tv.hashCode());
        synchronized (mRegistered) {
            if (!mRegistered[0]) {
                mRegistered[0] = true;
                mTransitions.addHandler(this);
            }
        }
        mTaskViewRepo.add(tv);
    }

    @Override
    public void unregisterTaskView(TaskViewTaskController tv) {
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "Transitions.unregisterTaskView: taskView=%d",
                tv.hashCode());
        mTaskViewRepo.remove(tv);
        // Note: Don't unregister handler since this is a singleton with lifetime bound to Shell
    }

    /**
     * Starts a transition outside of the handler associated with {@link TaskViewTransitions}.
     */
    public void startInstantTransition(@WindowManager.TransitionType int type,
            WindowContainerTransaction wct) {
        mTransitions.startTransition(type, wct, null);
    }

    /**
     * Starts or queues an "external" runnable into the pending queue. This means it will run
     * in order relative to the local transitions.
     *
     * The external operation *must* call {@link #onExternalDone} once it has finished.
     *
     * In practice, the external is usually another transition on a different handler.
     */
    public void enqueueExternal(@NonNull TaskViewTaskController taskView, ExternalTransition ext) {
        ProtoLog.d(WM_SHELL_BUBBLES,
                "TaskViewTransitions.enqueueExternal(): transition=%s taskView=%d pending=%d",
                ext, taskView.hashCode(), mPending.size());
        final PendingTransition pending = new PendingTransition(
                TRANSIT_NONE, null /* wct */, taskView, null /* cookie */);
        pending.mExternalTransition = ext;
        mPending.add(pending);
        startNextTransition();
    }

    /**
     * Add an already running external transition into the pending queue.
     * This transition has to be started externally. And it will block any new transitions from
     * starting in the pending queue.
     *
     * The external operation *must* call {@link #onExternalDone(IBinder)} once it has finished.
     */
    public void enqueueRunningExternal(@NonNull TaskViewTaskController taskView,
            IBinder transition) {
        ProtoLog.d(WM_SHELL_BUBBLES,
                "TaskViewTransitions.enqueueRunningExternal(): "
                    + "transition=%s taskView=%d pending=%d",
                transition, taskView.hashCode(), mPending.size());
        final PendingTransition pending = new PendingTransition(
                TRANSIT_NONE, null /* wct */, taskView, null /* cookie */);
        pending.mExternalTransition = () -> transition;
        pending.mClaimed = transition;
        mPending.add(pending);
    }

    /**
     * An external transition run in this "queue" is required to call this once it becomes ready.
     */
    public void onExternalDone(IBinder key) {
        final PendingTransition pending = findPending(key);
        if (pending == null) {
            ProtoLog.w(WM_SHELL_BUBBLES_NOISY,
                    "Transitions.onExternalDone(): unknown transition=%s", key);
            return;
        }
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "Transitions.onExternalDone(): taskView=%d "
                + "transition=%s", pending.mTaskView.hashCode(), key);
        mPending.remove(pending);
        startNextTransition();
    }

    /**
     * Looks through the pending transitions for a opening transaction that matches the provided
     * `taskView`.
     *
     * @param taskView the pending transition should be for this.
     */
    @VisibleForTesting
    PendingTransition findPendingOpeningTransition(TaskViewTaskController taskView) {
        for (int i = mPending.size() - 1; i >= 0; --i) {
            if (mPending.get(i).mTaskView != taskView) continue;
            if (mPending.get(i).mExternalTransition != null) continue;
            if (TransitionUtil.isOpeningType(mPending.get(i).mType)) {
                return mPending.get(i);
            }
        }
        return null;
    }

    /**
     * Looks through the pending transitions for one matching `taskView`.
     *
     * @param taskView the pending transition should be for this.
     * @param type     the type of transition it's looking for
     */
    PendingTransition findPending(TaskViewTaskController taskView, int type) {
        for (int i = mPending.size() - 1; i >= 0; --i) {
            if (mPending.get(i).mTaskView != taskView) continue;
            if (mPending.get(i).mExternalTransition != null) continue;
            if (mPending.get(i).mType == type) {
                return mPending.get(i);
            }
        }
        return null;
    }

    /** Looks through the pending transitions for one matching {@param claimed} */
    @VisibleForTesting
    public PendingTransition findPending(IBinder claimed) {
        for (int i = 0; i < mPending.size(); ++i) {
            if (mPending.get(i).mClaimed != claimed) continue;
            return mPending.get(i);
        }
        return null;
    }

    /** @return whether there are pending transitions on TaskViews. */
    public boolean hasPending() {
        return !mPending.isEmpty();
    }

    /** Removes all pending transitions for the given {@code taskView}. */
    public void removePendingTransitions(TaskViewTaskController taskView) {
        for (int i = mPending.size() - 1; i >= 0; --i) {
            if (mPending.get(i).mTaskView != taskView) continue;
            if (mPending.get(i).mExternalTransition != null) continue;
            mPending.remove(i);
        }
    }

    @Override
    public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
            @Nullable TransitionRequestInfo request) {
        final ActivityManager.RunningTaskInfo triggerTask = request.getTriggerTask();
        if (triggerTask == null) {
            return null;
        }
        final TaskViewTaskController taskView = findTaskView(triggerTask);
        if (taskView == null) return null;

        // Opening types should all be initiated by shell
        if (!TransitionUtil.isClosingType(request.getType())) {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "Transitions.handleRequest(): taskView=%d "
                    + "skipping transition=%d", taskView.hashCode(), transition.hashCode());
            return null;
        }
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "Transitions.handleRequest(): taskView=%d "
                        + "handling transition=%d", taskView.hashCode(), transition.hashCode());
        PendingTransition pending = new PendingTransition(request.getType(), null,
                taskView, null /* cookie */);
        pending.mClaimed = transition;
        mPending.add(pending);
        return new WindowContainerTransaction();
    }

    private TaskViewTaskController findTaskView(ActivityManager.RunningTaskInfo taskInfo) {
        final TaskViewRepository.TaskViewState state = mTaskViewRepo.byToken(taskInfo.token);
        return state != null ? state.getTaskView() : null;
    }

    /** Returns true if the given {@code taskInfo} belongs to a task view. */
    public boolean isTaskViewTask(ActivityManager.RunningTaskInfo taskInfo) {
        return findTaskView(taskInfo) != null;
    }

    private void prepareActivityOptions(ActivityOptions options, Rect launchBounds,
            @NonNull TaskViewTaskController destination) {
        final Binder launchCookie = new Binder();
        mShellExecutor.execute(() -> {
            mTaskOrganizer.setPendingLaunchCookieListener(launchCookie, destination);
        });
        options.setLaunchBounds(launchBounds);
        options.setLaunchCookie(launchCookie);
        options.setLaunchWindowingMode(WINDOWING_MODE_MULTI_WINDOW);
        options.setRemoveWithTaskOrganizer(true);
    }

    @Override
    public void startShortcutActivity(@NonNull TaskViewTaskController destination,
            @NonNull ShortcutInfo shortcut, @NonNull ActivityOptions options,
            @Nullable Rect launchBounds) {
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "Transitions.startShortcutActivity(): taskView=%d "
                        + "shortcut=%s bounds=%s", destination.hashCode(), shortcut, launchBounds);
        prepareActivityOptions(options, launchBounds, destination);
        final Context context = destination.getContext();
        mShellExecutor.execute(() -> {
            final WindowContainerTransaction wct = new WindowContainerTransaction();
            wct.startShortcut(context.getPackageName(), shortcut, options.toBundle());
            startTaskView(wct, destination, options.getLaunchCookie());
        });
    }

    @Override
    public void startActivity(@NonNull TaskViewTaskController destination,
            @NonNull PendingIntent pendingIntent, @Nullable Intent fillInIntent,
            @NonNull ActivityOptions options, @Nullable Rect launchBounds) {
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "Transitions.startActivity(): taskView=%d intent=%s",
                destination.hashCode(), pendingIntent.getIntent());
        prepareActivityOptions(options, launchBounds, destination);
        mShellExecutor.execute(() -> {
            WindowContainerTransaction wct = new WindowContainerTransaction();
            wct.sendPendingIntent(pendingIntent, fillInIntent, options.toBundle());
            startTaskView(wct, destination, options.getLaunchCookie());
        });
    }

    @Override
    public void startRootTask(@NonNull TaskViewTaskController destination,
            ActivityManager.RunningTaskInfo taskInfo, SurfaceControl leash,
            @Nullable WindowContainerTransaction wct) {
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "Transitions.startRootTask(): taskView=%d task=%s",
                destination.hashCode(), taskInfo);
        if (wct == null) {
            wct = new WindowContainerTransaction();
        }
        // This method skips the regular flow where an activity task is launched as part of a new
        // transition in taskview and then transition is intercepted using the launchcookie.
        // The task here is already created and running, it just needs to be reparented, resized
        // and tracked correctly inside taskview. Which is done by calling
        // prepareOpenAnimation() and then manually enqueuing the resulting window container
        // transaction.
        prepareOpenAnimation(destination, true /* newTask */, mTransaction /* startTransaction */,
                null /* finishTransaction */, taskInfo, leash, wct);
        mTransaction.apply();
        mTransitions.startTransition(TRANSIT_CHANGE, wct, null);
    }

    @VisibleForTesting
    void startTaskView(@NonNull WindowContainerTransaction wct,
            @NonNull TaskViewTaskController taskView, @NonNull IBinder launchCookie) {
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "Transitions.startTaskView(): taskView=%d",
                taskView.hashCode());
        updateVisibilityState(taskView, true /* visible */);
        mPending.add(new PendingTransition(TRANSIT_OPEN, wct, taskView, launchCookie));
        startNextTransition();
    }

    @Override
    public void removeTaskView(@NonNull TaskViewTaskController taskView,
            @Nullable WindowContainerToken taskToken) {
        final WindowContainerToken token = taskToken != null ? taskToken : taskView.getTaskToken();
        if (token == null) {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "Transitions.removeTaskView(): taskView=%d no token",
                    taskView.hashCode());
            // We don't have a task yet, so just clean up records
            unregisterTaskView(taskView);
            return;
        }
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "Transitions.removeTaskView(): taskView=%d",
                taskView.hashCode());
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.removeTask(token);
        updateVisibilityState(taskView, false /* visible */);
        mShellExecutor.execute(() -> {
            mPending.add(new PendingTransition(TRANSIT_CLOSE, wct, taskView, null /* cookie */));
            startNextTransition();
        });
    }

    @Override
    public void moveTaskViewToFullscreen(@NonNull TaskViewTaskController taskView) {
        final WindowContainerToken taskToken = taskView.getTaskToken();
        if (taskToken == null) return;
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "Transitions.moveTaskViewToFullscreen(): taskView=%d",
                taskView.hashCode());
        final WindowContainerTransaction wct =
                getExitBubbleTransaction(taskToken, taskView.getCaptionInsetsOwner());
        mShellExecutor.execute(() -> {
            mPending.add(new PendingTransition(TRANSIT_CHANGE, wct, taskView, null /* cookie */));
            startNextTransition();
            taskView.notifyTaskRemovalStarted(taskView.getTaskInfo());
        });
    }

    @Override
    public void setTaskViewVisible(TaskViewTaskController taskView, boolean visible) {
        setTaskViewVisible(taskView, visible, false /* reorder */);
    }

    /** See {@link #setTaskViewVisible(TaskViewTaskController, boolean, boolean, boolean)}. */
    public void setTaskViewVisible(TaskViewTaskController taskView, boolean visible,
            boolean reorder) {
        setTaskViewVisible(taskView, visible, reorder,
                true /* syncHiddenWithVisibilityOnReorder */);
    }

    /**
     * See {@link #setTaskViewVisible(TaskViewTaskController, boolean, boolean, boolean, boolean,
     * WindowContainerTransaction)}.
     */
    public void setTaskViewVisible(TaskViewTaskController taskView, boolean visible,
            boolean reorder, boolean syncHiddenWithVisibilityOnReorder) {
        setTaskViewVisible(taskView, visible, reorder, syncHiddenWithVisibilityOnReorder,
                false /* nonBlockingIfPossible */, null /* overrideTransaction */);
    }

    /**
     * Starts a new transition to make the given {@code taskView} visible and optionally
     * reordering it.
     *
     * @param reorder  Whether to reorder the task or not. If this is {@code true}, the task will
     *                 be reordered as per the given {@code visible}. For {@code visible = true},
     *                 task will be reordered to top. For {@code visible = false}, task will be
     *                 reordered to the bottom
     * @param syncHiddenWithVisibilityOnReorder Whether to also synchronize the hidden state of
     *                                          the task with the target visibility when
     *                                          reordering. This only takes effect if {@code
     *                                          reorder} is {@code true}.
     * @param nonBlockingIfPossible If true, the wct will be executed in a non-blocking way when
     *                              possible. It is possible if {@link #mShellExecutor} is an
     *                              instance of {@link ShellExecutor} that supports posting a
     *                              Runnable after the current execution.
     * @param overrideTransaction The transaction that already contains a set of task hierarchy
     *                            operations. If this is non-null, this method won't apply any
     *                            hierarchy related operations to avoid conflicts.
     * @throws IllegalStateException If the flag {@link FLAG_ENABLE_CREATE_ANY_BUBBLE} is not
     *                               enabled.
     */
    public void setTaskViewVisible(TaskViewTaskController taskView, boolean visible,
            boolean reorder, boolean syncHiddenWithVisibilityOnReorder,
            boolean nonBlockingIfPossible, WindowContainerTransaction overrideTransaction) {
        final TaskViewRepository.TaskViewState state = mTaskViewRepo.byTaskView(taskView);
        if (state == null) return;
        if (state.mVisible == visible) return;
        if (taskView.getTaskInfo() == null) {
            // Nothing to update, task is not yet available
            return;
        }
        state.mVisible = visible;

        final WindowContainerTransaction wct;
        if (overrideTransaction != null) {
            wct = overrideTransaction;
        } else {
            wct = new WindowContainerTransaction();
            wct.setBounds(taskView.getTaskInfo().token, state.mBounds);
            if (reorder && !syncHiddenWithVisibilityOnReorder) {
                // Reset hidden state to fix corner case where surface was destroyed before task
                // appeared in #prepareOpenAnimation.
                wct.setHidden(taskView.getTaskInfo().token, false /* hidden */);
                // Order of #setAlwaysOnTop and #reorder matters; hierarchy ops apply sequentially.
                wct.setAlwaysOnTop(taskView.getTaskInfo().token, visible /* alwaysOnTop */);
            } else {
                wct.setHidden(taskView.getTaskInfo().token, !visible /* hidden */);
            }
            if (reorder) {
                wct.reorder(taskView.getTaskInfo().token, visible /* onTop */);
            }
        }

        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "Transitions.setTaskViewVisible(): taskView=%d "
                + "visible=%b", taskView.hashCode(), visible);
        final PendingTransition pending = new PendingTransition(
                visible ? TRANSIT_TO_FRONT : TRANSIT_TO_BACK, wct, taskView, null /* cookie */);
        mPending.add(pending);
        if (nonBlockingIfPossible && mShellExecutor instanceof ShellExecutor executor) {
            executor.executeDelayed(this::startNextTransition, 0);
        } else {
            startNextTransition();
        }
        // visibility is reported in transition.
    }

    /** Starts a new transition to reorder the given {@code taskView}'s task. */
    public void reorderTaskViewTask(TaskViewTaskController taskView, boolean onTop) {
        final TaskViewRepository.TaskViewState state = mTaskViewRepo.byTaskView(taskView);
        if (state == null) return;
        if (taskView.getTaskInfo() == null) {
            // Nothing to update, task is not yet available
            return;
        }
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "Transitions.reorderTaskViewTask(): taskView=%d "
                        + "onTop=%b", taskView.hashCode(), onTop);
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.reorder(taskView.getTaskInfo().token, onTop /* onTop */);
        PendingTransition pending = new PendingTransition(
                onTop ? TRANSIT_TO_FRONT : TRANSIT_TO_BACK, wct, taskView, null /* cookie */);
        mPending.add(pending);
        startNextTransition();
        // visibility is reported in transition.
    }

    /** Updates the bounds state for the given task view. */
    public void updateBoundsState(TaskViewTaskController taskView, Rect boundsOnScreen) {
        final TaskViewRepository.TaskViewState state = mTaskViewRepo.byTaskView(taskView);
        if (state == null) return;
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY,
                "Transitions.updateBoundsState(): taskView=%d bounds=%s",
                taskView.hashCode(), boundsOnScreen);
        state.mBounds.set(boundsOnScreen);
    }

    void updateVisibilityState(TaskViewTaskController taskView, boolean visible) {
        final TaskViewRepository.TaskViewState state = mTaskViewRepo.byTaskView(taskView);
        if (state == null) return;
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "Transitions.updateVisibilityState(): taskView=%d "
                        + "visible=%b", taskView.hashCode(), visible);
        state.mVisible = visible;
    }

    @Override
    public void setTaskBounds(TaskViewTaskController taskView, Rect boundsOnScreen) {
        if (taskView.getTaskToken() == null) {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "Transitions.setTaskBounds(): null token");
            return;
        }

        mShellExecutor.execute(() -> {
            // Sync Transactions can't operate simultaneously with shell transition collection.
            setTaskBoundsInTransition(taskView, boundsOnScreen);
        });
    }

    private void setTaskBoundsInTransition(TaskViewTaskController taskView, Rect boundsOnScreen) {
        final TaskViewRepository.TaskViewState state = mTaskViewRepo.byTaskView(taskView);
        if (state == null || Objects.equals(boundsOnScreen, state.mBounds)) {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "Transitions.setTaskBoundsInTransition(): "
                    + "Skipping, same bounds");
            return;
        }
        state.mBounds.set(boundsOnScreen);
        if (!state.mVisible) {
            // Task view isn't visible, the bounds will next visibility update.
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "Transitions.setTaskBoundsInTransition(): "
                    + "Skipping, not visible");
            return;
        }
        if (hasPending()) {
            // There is already a transition in-flight, the window bounds will be set in
            // prepareOpenAnimation.
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "Transitions.setTaskBoundsInTransition(): "
                    + "Skipping, pending transition");
            return;
        }
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "Transitions.setTaskBoundsInTransition(): taskView=%d "
                        + "bounds=%s", taskView.hashCode(), boundsOnScreen);
        // If there is a pending redirect transition, it may have a WCT with other operations.
        final WindowContainerTransaction wct = mPendingRedirectTransition != null
                ? mPendingRedirectTransition.takePendingWct() : new WindowContainerTransaction();
        wct.setBounds(taskView.getTaskInfo().token, boundsOnScreen);
        mPending.add(new PendingTransition(TRANSIT_CHANGE, wct, taskView, null /* cookie */));
        startNextTransition();
    }

    private void startNextTransition() {
        if (mPending.isEmpty()) {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "Transitions.startNextTransition(): None pending");
            return;
        }
        final PendingTransition pending = mPending.get(0);
        if (pending.mClaimed != null) {
            // Wait for this to start animating.
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "Transitions.startNextTransition(): "
                    + "taskView=%d pending type=%s transition=%s", pending.mTaskView.hashCode(),
                    transitTypeToString(pending.mType), pending.mClaimed);
            return;
        }
        if (pending.mExternalTransition != null) {
            pending.mClaimed = pending.mExternalTransition.start();
            if (pending.mClaimed == null) {
                ProtoLog.w(WM_SHELL_BUBBLES_NOISY, "TaskViewTransitions.startNextTransition(): "
                        + "taskView=%d starting the external transition returned a null claim "
                        + "token. it may have already finished. removing it so that it does not "
                        + "block other transitions.", pending.mTaskView.hashCode());
                mPending.remove(pending);
                startNextTransition();
                return;
            }
        } else {
            pending.mClaimed = mTransitions.startTransition(pending.mType, pending.mWct, this);
        }
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "Transitions.startNextTransition(): "
                + "taskView=%d starting type=%s transition=%s", pending.mTaskView.hashCode(),
                transitTypeToString(pending.mType), pending.mClaimed);
    }

    @Override
    public void onTransitionConsumed(@NonNull IBinder transition, boolean aborted,
            @NonNull SurfaceControl.Transaction finishTransaction) {
        if (!Flags.fixTaskViewRotationAnimation() && !aborted) return;
        final PendingTransition pending = findPending(transition);
        if (pending == null) return;
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "Transitions.onTransitionConsumed(): taskView=%d "
                + "consumed type=%s transition=%s aborted=%b", pending.mTaskView.hashCode(),
                transitTypeToString(pending.mType), transition, aborted);
        mPending.remove(pending);
        startNextTransition();
    }

    /**
     * @param change the change to examine
     * @param pending the pending tansition
     * @return whether this is a TaskView that this handler will be able to handle
     */
    private boolean isValidTaskView(TransitionInfo.Change change, PendingTransition pending) {
        final ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
        if (taskInfo == null) {
            // Not a task, so ignore
            return false;
        }

        if (change.getMode() == TRANSIT_OPEN) {
            // Ignore tasks that are launched in the wrong transition
            return pending != null && taskInfo.containsLaunchCookie(pending.mLaunchCookie);
        }
        if (isTaskViewTask(taskInfo)) {
            return true;
        }

        // In some cases, findTaskView returns null but the change is still a task view:
        if (change.getMode() == TRANSIT_CLOSE) {
            // TaskView can be null when closing
            return true;
        }
        if (change.getMode() == TRANSIT_TO_FRONT && pending != null) {
            // Accept if an existing task, not currently in TaskView, is
            // brought to the front to be moved into TaskView
            return isTaskToTaskView(change, pending);
        }
        return false;
    }

    /**
     * @return if an existing task, not currently in TaskView, is brought to the front to be moved
     * into TaskView (e.g task being moved into a bubble)
     */
    private boolean isTaskToTaskView(TransitionInfo.Change change, PendingTransition pending) {
        return BubbleAnythingFlagHelper.enableCreateAnyBubble()
                && change.getMode() == TRANSIT_TO_FRONT
                && pending.mTaskView.getPendingInfo() != null
                && pending.mTaskView.getPendingInfo().taskId == change.getTaskInfo().taskId;
    }

    @Override
    public boolean startAnimation(@NonNull IBinder transition,
                                  @NonNull TransitionInfo info,
                                  @NonNull SurfaceControl.Transaction startTransaction,
                                  @NonNull SurfaceControl.Transaction finishTransaction,
                                  @NonNull Transitions.TransitionFinishCallback finishCallback) {
        return startAnimation(transition, info, TransitionDispatchState.getDummyInstance(),
                startTransaction, finishTransaction, finishCallback);
    }

    @Override
    public boolean startAnimation(@NonNull IBinder transition,
                                  @Nullable TransitionInfo transitionInfo,
                                  @NonNull TransitionDispatchState dispatchState,
                                  @NonNull SurfaceControl.Transaction startTransaction,
                                  @NonNull SurfaceControl.Transaction finishTransaction,
                                  @NonNull Transitions.TransitionFinishCallback finishCallback) {
        if (transitionInfo == null || transitionInfo.getChanges().isEmpty()) {
            PendingTransition pending = findPending(transition);
            if (pending != null) {
                ProtoLog.e(WM_SHELL_BUBBLES, "Transitions.startAnimation(): found a transition with"
                                + "no changes that is managed by TaskViewTransitions. taskView=%d "
                                + "type=%s transition=%s", pending.mTaskView.hashCode(),
                        transitTypeToString(pending.mType), transition);
                mPending.remove(pending);
                startNextTransition();
            }
            return false;
        }

        if (!Flags.taskViewTransitionsRefactor() && !enableHandlersDebuggingMode()) {
            return startAnimationLegacy(transition, transitionInfo, startTransaction,
                    finishTransaction, finishCallback);
        }
        final boolean inDataCollectionModeOnly =
                enableHandlersDebuggingMode() && transitionInfo == null;
        final TransitionInfo info = inDataCollectionModeOnly ? dispatchState.mInfo : transitionInfo;

        final PendingTransition pending = findPending(transition);
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "Transitions.startAnimation(): taskView=%d "
                    + "type=%s transition=%s", pending != null ? pending.mTaskView.hashCode() : -1,
                    pending != null ? transitTypeToString(pending.mType) : "unknown", transition);
        if (pending != null) {
            mPending.remove(pending);
        }
        if (mTaskViewRepo.isEmpty()) {
            if (pending != null) {
                Slog.e(TAG, "Pending taskview transition but no task-views");
            }
            return false;
        }
        boolean stillNeedsMatchingLaunch = pending != null && pending.mLaunchCookie != null;
        int changingDisplayId = -1;
        WindowContainerTransaction wct = null;

        // Collect all the tasks views that this handler can handle
        ArrayList<TransitionInfo.Change> taskViews = new ArrayList<>();
        ArrayList<TransitionInfo.Change> alienChanges = new ArrayList<>();
        for (int i = 0; i < info.getChanges().size(); ++i) {
            final TransitionInfo.Change chg = info.getChanges().get(i);
            if (isValidTaskView(chg, pending)) {
                taskViews.add(chg);
                if (inDataCollectionModeOnly) {
                    dispatchState.addError(this, chg, LOST_RELEVANT_CHANGE);
                }
            } else {
                alienChanges.add(chg);
                if (Flags.fixTaskViewRotationAnimation()
                        && chg.hasFlags(TransitionInfo.FLAG_IS_DISPLAY)
                        && chg.getMode() == TRANSIT_CHANGE && mPendingRedirectTransition == null) {
                    changingDisplayId = chg.getEndDisplayId();
                }
            }
        }
        if (inDataCollectionModeOnly) {
            return false;
        }

        // Prepare taskViews for animation
        boolean isReadyForAnimation = true;
        for (int i = 0; i < taskViews.size(); ++i) {
            final TransitionInfo.Change task = taskViews.get(i);
            final ActivityManager.RunningTaskInfo taskInfo = task.getTaskInfo();
            final SurfaceControl leash = task.getLeash();
            final TaskViewTaskController infoTv = findTaskView(taskInfo);

            switch (task.getMode()) {
                case TRANSIT_TO_BACK:
                    if (pending != null && pending.mType == TRANSIT_TO_BACK) {
                        // TO_BACK is only used when setting the task view visibility immediately,
                        // so in that case we can also hide the surface immediately
                        startTransaction.hide(leash);
                    }
                    infoTv.prepareHideAnimation(finishTransaction);
                    break;
                case TRANSIT_CLOSE:
                    // TaskView can be null when closing
                    if (infoTv != null) {
                        infoTv.prepareCloseAnimation();
                    }
                    break;
                case TRANSIT_OPEN:
                    stillNeedsMatchingLaunch = false;
                    if (wct == null) wct = new WindowContainerTransaction();
                    prepareOpenAnimation(pending.mTaskView, true /* isNewInTaskView */,
                            startTransaction, finishTransaction, taskInfo, leash, wct);
                    break;
                case TRANSIT_TO_FRONT:
                    if (wct == null) wct = new WindowContainerTransaction();
                    if (infoTv == null && pending != null && isTaskToTaskView(task, pending)) {
                        // The task is being moved into taskView, so it is still "new" from
                        // TaskView's perspective (e.g. task being moved into a bubble)
                        stillNeedsMatchingLaunch = false;
                        isReadyForAnimation &= prepareOpenAnimation(pending.mTaskView,
                                true /* isNewInTaskView */, startTransaction, finishTransaction,
                                taskInfo, leash, wct);
                    } else {
                        isReadyForAnimation &= prepareOpenAnimation(infoTv,
                                false /* isNewInTaskView */, startTransaction, finishTransaction,
                                taskInfo, leash, wct);
                    }
                    break;
                case TRANSIT_CHANGE:
                    final Rect boundsOnScreen = infoTv.prepareOpen(task.getTaskInfo(), leash);
                    if (boundsOnScreen != null) {
                        if (wct == null) wct = new WindowContainerTransaction();
                        updateBounds(infoTv, boundsOnScreen, startTransaction, finishTransaction,
                                taskInfo, leash, wct);
                        if (changingDisplayId == task.getEndDisplayId()) {
                            ProtoLog.d(WM_SHELL_BUBBLES, "Transitions.startAnimation(): "
                                    + "display change, taskView=%d", infoTv.hashCode());
                            // Remove the change from TransitionInfo to avoid the transition from
                            // being handled by another TaskViewTransitions instance.
                            info.getChanges().remove(task);
                        }
                    } else {
                        startTransaction.reparent(leash, infoTv.getSurfaceControl());
                        finishTransaction.reparent(leash, infoTv.getSurfaceControl())
                                .setPosition(leash, 0, 0);
                    }
                    break;
                default:
                    break;
            }
        }

        // Check for unexpected changes in transition
        for (int i = 0; i < alienChanges.size(); ++i) {
            final TransitionInfo.Change change = alienChanges.get(i);
            final ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
            if (taskInfo == null) {
                // Silently ignore non-tasks
                continue;
            }
            if (change.getMode() == TRANSIT_OPEN
                    && (pending == null || !taskInfo.containsLaunchCookie(pending.mLaunchCookie))) {
                Slog.e(TAG, "Found a launching TaskView in the wrong transition. All "
                        + "TaskView launches should be initiated by shell and in their "
                        + "own transition: " + taskInfo.taskId);
                dispatchState.addError(this, change, CAPTURED_CHANGE_IN_WRONG_TRANSITION);
            } else {
                Slog.w(TAG, "Found a non-TaskView task in a TaskView Transition. This "
                        + "shouldn't happen, so there may be a visual artifact: "
                        + taskInfo.taskId);
                dispatchState.addError(this, change, CAPTURED_UNRELATED_CHANGE);
            }
        }

        if (stillNeedsMatchingLaunch) {
            Slog.w(TAG, "Expected a TaskView launch in this transition but didn't get one, "
                    + "cleaning up the task view");
            // Didn't find a task so the task must have never launched
            pending.mTaskView.setTaskNotFound();
        } else if (wct == null && pending == null && taskViews.size() != info.getChanges().size()) {
            // Just some house-keeping, let another handler animate.
            return false;
        } else if (!isReadyForAnimation) {
            // Animation could not be fully prepared. The surface for one or more TaskViews was
            // destroyed before the animation could start, let another handler animate.
            Slog.w(TAG, "Animation not ready for all TaskViews, deferring to another handler.");
            return false;
        }
        if (changingDisplayId > -1) {
            // Wait for setTaskBoundsInTransition -> mergeAnimation to let DefaultTransitionHandler
            // run display level animation after the new bounds of TaskView is set.
            mPendingRedirectTransition = new PendingRedirectTransition(() ->
                    mTransitions.dispatchTransition(transition, info, startTransaction,
                            finishTransaction, finishCallback, this), wct);
            mTransitions.getMainExecutor().executeDelayed(() -> {
                if (mPendingRedirectTransition != null) {
                    Slog.w(TAG, "Timed out to wait for transition of setTaskBounds");
                    executePendingRedirectTransition();
                }
            }, PendingRedirectTransition.TIMEOUT_MS);
            return true;
        }
        // No animation, just show it immediately.
        startTransaction.apply();
        finishCallback.onTransitionFinished(wct);
        startNextTransition();
        return true;
    }

    private boolean startAnimationLegacy(@NonNull IBinder transition,
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        final PendingTransition pending = findPending(transition);
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "Transitions.startAnimation(): taskView=%d "
                + "type=%s transition=%s", pending != null ? pending.mTaskView.hashCode() : -1,
                pending != null ? transitTypeToString(pending.mType) : "unknown", transition);
        if (pending != null) {
            mPending.remove(pending);
        }
        if (mTaskViewRepo.isEmpty()) {
            if (pending != null) {
                Slog.e(TAG, "Pending taskview transition but no task-views");
            }
            return false;
        }
        boolean stillNeedsMatchingLaunch = pending != null && pending.mLaunchCookie != null;
        ArrayList<TransitionInfo.Change> taskViewChanges = null;
        int changingDisplayId = -1;
        int changesHandled = 0;
        WindowContainerTransaction wct = null;
        for (int i = 0; i < info.getChanges().size(); ++i) {
            final TransitionInfo.Change chg = info.getChanges().get(i);
            if (Flags.fixTaskViewRotationAnimation() && chg.hasFlags(TransitionInfo.FLAG_IS_DISPLAY)
                    && chg.getMode() == TRANSIT_CHANGE && mPendingRedirectTransition == null) {
                changingDisplayId = chg.getEndDisplayId();
                continue;
            }
            final ActivityManager.RunningTaskInfo taskInfo = chg.getTaskInfo();
            if (taskInfo == null) continue;
            if (TransitionUtil.isClosingType(chg.getMode())) {
                final boolean isHide = chg.getMode() == TRANSIT_TO_BACK;
                TaskViewTaskController tv = findTaskView(taskInfo);
                if (tv == null && !isHide) {
                    // TaskView can be null when closing
                    changesHandled++;
                    continue;
                }
                if (tv == null) {
                    if (pending != null) {
                        Slog.w(TAG, "Found a non-TaskView task in a TaskView Transition. This "
                                + "shouldn't happen, so there may be a visual artifact: "
                                + taskInfo.taskId);
                    }
                    continue;
                }
                if (isHide) {
                    if (pending != null && pending.mType == TRANSIT_TO_BACK) {
                        // TO_BACK is only used when setting the task view visibility immediately,
                        // so in that case we can also hide the surface immediately
                        startTransaction.hide(chg.getLeash());
                    }
                    tv.prepareHideAnimation(finishTransaction);
                } else {
                    tv.prepareCloseAnimation();
                }
                changesHandled++;
            } else if (TransitionUtil.isOpeningType(chg.getMode())) {
                boolean isNewInTaskView = false;
                TaskViewTaskController tv;
                if (chg.getMode() == TRANSIT_OPEN) {
                    isNewInTaskView = true;
                    if (pending == null || !taskInfo.containsLaunchCookie(pending.mLaunchCookie)) {
                        Slog.e(TAG, "Found a launching TaskView in the wrong transition. All "
                                + "TaskView launches should be initiated by shell and in their "
                                + "own transition: " + taskInfo.taskId);
                        continue;
                    }
                    stillNeedsMatchingLaunch = false;
                    tv = pending.mTaskView;
                } else {
                    tv = findTaskView(taskInfo);
                    if (tv == null && pending != null) {
                        if (BubbleAnythingFlagHelper.enableCreateAnyBubble()
                                && chg.getMode() == TRANSIT_TO_FRONT
                                && pending.mTaskView.getPendingInfo() != null
                                && pending.mTaskView.getPendingInfo().taskId == taskInfo.taskId) {
                            // In this case an existing task, not currently in TaskView, is
                            // brought to the front to be moved into TaskView. This is still
                            // "new" from TaskView's perspective. (e.g. task being moved into a
                            // bubble)
                            isNewInTaskView = true;
                            stillNeedsMatchingLaunch = false;
                            tv = pending.mTaskView;
                        } else {
                            Slog.w(TAG, "Found a non-TaskView task in a TaskView Transition. "
                                    + "This shouldn't happen, so there may be a visual "
                                    + "artifact: " + taskInfo.taskId);
                        }
                    }
                    if (tv == null) continue;
                }
                if (wct == null) wct = new WindowContainerTransaction();
                prepareOpenAnimation(tv, isNewInTaskView, startTransaction, finishTransaction,
                        taskInfo, chg.getLeash(), wct);
                changesHandled++;
            } else if (chg.getMode() == TRANSIT_CHANGE) {
                TaskViewTaskController tv = findTaskView(taskInfo);
                if (tv == null) {
                    if (pending != null) {
                        Slog.w(TAG, "Found a non-TaskView task in a TaskView Transition. This "
                                + "shouldn't happen, so there may be a visual artifact: "
                                + taskInfo.taskId);
                    }
                    continue;
                }
                if (taskViewChanges == null) {
                    taskViewChanges = new ArrayList<>();
                }
                taskViewChanges.add(chg);
                final Rect boundsOnScreen = tv.prepareOpen(chg.getTaskInfo(), chg.getLeash());
                if (boundsOnScreen != null) {
                    if (wct == null) wct = new WindowContainerTransaction();
                    updateBounds(tv, boundsOnScreen, startTransaction, finishTransaction,
                            chg.getTaskInfo(), chg.getLeash(), wct);
                } else {
                    startTransaction.reparent(chg.getLeash(), tv.getSurfaceControl());
                    finishTransaction.reparent(chg.getLeash(), tv.getSurfaceControl())
                            .setPosition(chg.getLeash(), 0, 0);
                }
                changesHandled++;
            }
        }
        if (stillNeedsMatchingLaunch) {
            Slog.w(TAG, "Expected a TaskView launch in this transition but didn't get one, "
                    + "cleaning up the task view");
            // Didn't find a task so the task must have never launched
            pending.mTaskView.setTaskNotFound();
        } else if (wct == null && pending == null && changesHandled != info.getChanges().size()) {
            // Just some house-keeping, let another handler animate.
            return false;
        }
        if (changingDisplayId > -1 && taskViewChanges != null) {
            ProtoLog.d(WM_SHELL_BUBBLES, "Transitions.startAnimationLegacy(): "
                    + "handle display change");
            // Remove the change from TransitionInfo to avoid being handled by
            // another TaskViewTransitions instance.
            info.getChanges().removeAll(taskViewChanges);
            // Wait for setTaskBoundsInTransition -> mergeAnimation to let DefaultTransitionHandler
            // run display level animation after the new bounds of TaskView is set.
            mPendingRedirectTransition = new PendingRedirectTransition(() ->
                    mTransitions.dispatchTransition(transition, info, startTransaction,
                            finishTransaction, finishCallback, this), wct);
            mTransitions.getMainExecutor().executeDelayed(() -> {
                if (mPendingRedirectTransition != null) {
                    Slog.w(TAG, "Timed out to wait for transition of setTaskBounds");
                    executePendingRedirectTransition();
                }
            }, PendingRedirectTransition.TIMEOUT_MS);
            return true;
        }
        // No animation, just show it immediately.
        startTransaction.apply();
        finishCallback.onTransitionFinished(wct);
        startNextTransition();
        return true;
    }

    /**
     * Prepares the TaskView for an open animation.
     *
     * @param taskView the {@link TaskViewTaskController} for the TaskView being opened.
     * @param newTask whether the task is considered new within this {@link TaskView}.
     * @param startTransaction the transaction to apply before the animation starts.
     * @param finishTransaction the transaction to apply after the animation finishes.
     * @param taskInfo information about the running task to animate.
     * @param leash the surface leash representing the task's surface.
     * @param wct a {@link WindowContainerTransaction} to apply changes.
     * @return {@code true} if the TaskView's surface is created and ready for animation,
     * {@code false} if the surface was destroyed and the animation should be deferred.
     */
    @VisibleForTesting
    public boolean prepareOpenAnimation(TaskViewTaskController taskView,
            final boolean newTask,
            SurfaceControl.Transaction startTransaction,
            SurfaceControl.Transaction finishTransaction,
            ActivityManager.RunningTaskInfo taskInfo, SurfaceControl leash,
            WindowContainerTransaction wct) {
        final Rect boundsOnScreen = taskView.prepareOpen(taskInfo, leash);
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "Transitions.prepareOpenAnimation(): taskView=%d "
                        + "newTask=%b bounds=%s", taskView.hashCode(), newTask, boundsOnScreen);
        final boolean isSurfaceCreated = boundsOnScreen != null;
        if (isSurfaceCreated) {
            updateBounds(taskView, boundsOnScreen, startTransaction, finishTransaction, taskInfo,
                    leash, wct);
        } else {
            // The surface has already been destroyed before the task has appeared,
            // so go ahead and hide the task entirely
            wct.setHidden(taskInfo.token, true /* hidden */);
            updateVisibilityState(taskView, false /* visible */);
            // listener callback is below
        }
        if (newTask) {
            wct.setInterceptBackPressedOnTaskRoot(taskInfo.token, true /* intercept */);
        }

        if (taskInfo.taskDescription != null) {
            int backgroundColor = taskInfo.taskDescription.getBackgroundColor();
            taskView.setResizeBgColor(startTransaction, backgroundColor);
        }

        // After the embedded task has appeared, set it to non-trimmable. This is important
        // to prevent recents from trimming and removing the embedded task.
        wct.setTaskTrimmableFromRecents(taskInfo.token, false /* isTrimmableFromRecents */);

        taskView.notifyAppeared(newTask);
        return isSurfaceCreated;
    }

    /**
     * Updates bounds for the task view during an unfold transition.
     *
     * @return true if the task was found and a transition for this task is pending. false
     * otherwise.
     */
    public boolean updateBoundsForUnfold(Rect bounds, SurfaceControl.Transaction startTransaction,
            SurfaceControl.Transaction finishTransaction,
            ActivityManager.RunningTaskInfo taskInfo, SurfaceControl leash) {
        final TaskViewTaskController taskView = findTaskView(taskInfo);
        if (taskView == null) {
            return false;
        }

        final PendingTransition pendingTransition = findPending(taskView, TRANSIT_CHANGE);
        if (pendingTransition == null) {
            return false;
        }

        mPending.remove(pendingTransition);

        updateSurface(leash, startTransaction, finishTransaction, taskView,
                bounds.width(), bounds.height());
        updateBoundsState(taskView, bounds);
        return true;
    }

    /** Updates the surface properties for a TaskView's task leash. */
    private void updateSurface(@NonNull SurfaceControl leash,
            @NonNull SurfaceControl.Transaction startT, @NonNull SurfaceControl.Transaction finishT,
            @NonNull TaskViewTaskController taskView, int width, int height) {
        // Reparent the task under the task view surface and set the bounds on it.
        startT.reparent(leash, taskView.getSurfaceControl())
                .setPosition(leash, 0, 0)
                .setWindowCrop(leash, width, height)
                .show(leash);
        // The finish transaction would reparent the task back to the window hierarchy parent, so
        // reparent it to the task view surface.
        finishT.reparent(leash, taskView.getSurfaceControl())
                .setPosition(leash, 0, 0)
                .setWindowCrop(leash, width, height);
    }

    private void updateBounds(TaskViewTaskController taskView, Rect boundsOnScreen,
            SurfaceControl.Transaction startTransaction,
            SurfaceControl.Transaction finishTransaction,
            ActivityManager.RunningTaskInfo taskInfo, SurfaceControl leash,
            WindowContainerTransaction wct) {
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "Transitions.updateBounds(): taskView=%d bounds=%s",
                taskView.hashCode(), boundsOnScreen);
        final SurfaceControl tvSurface = taskView.getSurfaceControl();
        // Surface is ready, so just reparent the task to this surface control
        startTransaction.reparent(leash, tvSurface)
                .show(leash);
        // Also reparent on finishTransaction since the finishTransaction will reparent back
        // to its "original" parent by default.
        if (finishTransaction != null) {
            finishTransaction.reparent(leash, tvSurface)
                    .setPosition(leash, 0, 0)
                    .setWindowCrop(leash, boundsOnScreen.width(), boundsOnScreen.height());
        }
        updateBoundsState(taskView, boundsOnScreen);
        updateVisibilityState(taskView, true /* visible */);
        wct.setBounds(taskInfo.token, boundsOnScreen);
        taskView.applyCaptionInsetsIfNeeded();
    }

    private void executePendingRedirectTransition() {
        if (mPendingRedirectTransition != null) {
            mPendingRedirectTransition.dispatchTransition();
            mPendingRedirectTransition = null;
        }
    }

    @Override
    public void mergeAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startT, @NonNull SurfaceControl.Transaction finishT,
            @NonNull IBinder mergeTarget,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        if (!Flags.fixTaskViewRotationAnimation()) return;
        final PendingTransition pending = findPending(transition);
        if (pending != null) {
            mPending.remove(pending);
        }
        executePendingRedirectTransition();
        boolean hasHandledTaskView = false;
        for (int i = 0; i < info.getChanges().size(); ++i) {
            final TransitionInfo.Change change = info.getChanges().get(i);
            final ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
            if (taskInfo == null) continue;
            final TaskViewTaskController taskView = findTaskView(taskInfo);
            if (taskView == null) continue;
            final SurfaceControl leash = change.getLeash();
            final Rect endBounds = change.getEndAbsBounds();
            updateSurface(leash, startT, finishT, taskView, endBounds.width(), endBounds.height());
            hasHandledTaskView = true;
        }
        ProtoLog.d(WM_SHELL_BUBBLES, "mergeAnimation(): matchedPending=%b hasHandledTaskView=%b",
                pending != null, hasHandledTaskView);
        if (hasHandledTaskView) {
            startT.apply();
            finishCallback.onTransitionFinished(null /* wct */);
        }
    }

    /** Dumps TaskViewTransitions state. */
    public void dump(PrintWriter pw) {
        pw.println("TaskViewTransitions state:");
        pw.println("  Pending transitions count: " + mPending.size());
        for (PendingTransition pendingTransition : mPending) {
            pendingTransition.dump(pw, "    ");
        }
        mTaskViewRepo.dump(pw, "  ");
    }

    /**
     * This holds a transition that is deferred, for example a display-change that also affects a
     * TaskView. The transition is dispatched once the TaskView reports its new bounds (i.e.
     * {@link #setTaskBounds}), which happens via a {@link #mergeAnimation} call, or on timeout.
     */
    private static class PendingRedirectTransition {
        static final long TIMEOUT_MS = 500;
        private final Runnable mDispatchTransition;
        private WindowContainerTransaction mWct;

        PendingRedirectTransition(@NonNull Runnable dispatch,
                @Nullable WindowContainerTransaction wct) {
            mDispatchTransition = dispatch;
            mWct = wct;
        }

        @NonNull
        WindowContainerTransaction takePendingWct() {
            final WindowContainerTransaction wct = mWct;
            mWct = null;
            return wct != null ? wct : new WindowContainerTransaction();
        }

        void dispatchTransition() {
            mDispatchTransition.run();
        }
    }

    /** Interface for running an external transition in this object's pending queue. */
    public interface ExternalTransition {
        /** Starts a transition and returns an identifying key for lookup. */
        IBinder start();
    }
}
