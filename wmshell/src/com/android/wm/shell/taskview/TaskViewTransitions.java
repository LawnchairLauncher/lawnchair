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

import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.view.WindowManager.TRANSIT_TO_FRONT;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.graphics.Rect;
import android.os.IBinder;
import android.util.ArrayMap;
import android.util.Slog;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerTransaction;

import androidx.annotation.VisibleForTesting;

import com.android.wm.shell.shared.TransitionUtil;
import com.android.wm.shell.transition.Transitions;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Handles Shell Transitions that involve TaskView tasks.
 */
public class TaskViewTransitions implements Transitions.TransitionHandler {
    static final String TAG = "TaskViewTransitions";

    private final ArrayMap<TaskViewTaskController, TaskViewRequestedState> mTaskViews =
            new ArrayMap<>();
    private final ArrayList<PendingTransition> mPending = new ArrayList<>();
    private final Transitions mTransitions;
    private final boolean[] mRegistered = new boolean[]{false};

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
    }

    /**
     * Visibility and bounds state that has been requested for a {@link TaskViewTaskController}.
     */
    private static class TaskViewRequestedState {
        boolean mVisible;
        Rect mBounds = new Rect();
    }

    public TaskViewTransitions(Transitions transitions) {
        mTransitions = transitions;
        // Defer registration until the first TaskView because we want this to be the "first" in
        // priority when handling requests.
        // TODO(210041388): register here once we have an explicit ordering mechanism.
    }

    void addTaskView(TaskViewTaskController tv) {
        synchronized (mRegistered) {
            if (!mRegistered[0]) {
                mRegistered[0] = true;
                mTransitions.addHandler(this);
            }
        }
        mTaskViews.put(tv, new TaskViewRequestedState());
    }

    void removeTaskView(TaskViewTaskController tv) {
        mTaskViews.remove(tv);
        // Note: Don't unregister handler since this is a singleton with lifetime bound to Shell
    }

    boolean isEnabled() {
        return mTransitions.isRegistered();
    }

    /**
     * Looks through the pending transitions for a closing transaction that matches the provided
     * `taskView`.
     *
     * @param taskView the pending transition should be for this.
     */
    private PendingTransition findPendingCloseTransition(TaskViewTaskController taskView) {
        for (int i = mPending.size() - 1; i >= 0; --i) {
            if (mPending.get(i).mTaskView != taskView) continue;
            if (TransitionUtil.isClosingType(mPending.get(i).mType)) {
                return mPending.get(i);
            }
        }
        return null;
    }

    /**
     * Starts a transition outside of the handler associated with {@link TaskViewTransitions}.
     */
    public void startInstantTransition(@WindowManager.TransitionType int type,
            WindowContainerTransaction wct) {
        mTransitions.startTransition(type, wct, null);
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
            if (mPending.get(i).mType == type) {
                return mPending.get(i);
            }
        }
        return null;
    }

    private PendingTransition findPending(IBinder claimed) {
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
        if (!TransitionUtil.isClosingType(request.getType())) return null;
        PendingTransition pending = new PendingTransition(request.getType(), null,
                taskView, null /* cookie */);
        pending.mClaimed = transition;
        mPending.add(pending);
        return new WindowContainerTransaction();
    }

    private TaskViewTaskController findTaskView(ActivityManager.RunningTaskInfo taskInfo) {
        for (int i = 0; i < mTaskViews.size(); ++i) {
            if (mTaskViews.keyAt(i).getTaskInfo() == null) continue;
            if (taskInfo.token.equals(mTaskViews.keyAt(i).getTaskInfo().token)) {
                return mTaskViews.keyAt(i);
            }
        }
        return null;
    }

    void startTaskView(@NonNull WindowContainerTransaction wct,
            @NonNull TaskViewTaskController taskView, @NonNull IBinder launchCookie) {
        updateVisibilityState(taskView, true /* visible */);
        mPending.add(new PendingTransition(TRANSIT_OPEN, wct, taskView, launchCookie));
        startNextTransition();
    }

    void closeTaskView(@NonNull WindowContainerTransaction wct,
            @NonNull TaskViewTaskController taskView) {
        updateVisibilityState(taskView, false /* visible */);
        mPending.add(new PendingTransition(TRANSIT_CLOSE, wct, taskView, null /* cookie */));
        startNextTransition();
    }

    /** Starts a new transition to make the given {@code taskView} visible. */
    public void setTaskViewVisible(TaskViewTaskController taskView, boolean visible) {
        setTaskViewVisible(taskView, visible, false /* reorder */);
    }

    /**
     * Starts a new transition to make the given {@code taskView} visible and optionally change
     * the task order.
     *
     * @param taskView the task view which the visibility is being changed for
     * @param visible  the new visibility of the task view
     * @param reorder  whether to reorder the task or not. If this is {@code true}, the task will be
     *                 reordered as per the given {@code visible}. For {@code visible = true}, task
     *                 will be reordered to top. For {@code visible = false}, task will be reordered
     *                 to the bottom
     */
    public void setTaskViewVisible(TaskViewTaskController taskView, boolean visible,
            boolean reorder) {
        if (mTaskViews.get(taskView) == null) return;
        if (mTaskViews.get(taskView).mVisible == visible) return;
        if (taskView.getTaskInfo() == null) {
            // Nothing to update, task is not yet available
            return;
        }
        mTaskViews.get(taskView).mVisible = visible;
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.setHidden(taskView.getTaskInfo().token, !visible /* hidden */);
        wct.setBounds(taskView.getTaskInfo().token, mTaskViews.get(taskView).mBounds);
        if (reorder) {
            wct.reorder(taskView.getTaskInfo().token, visible /* onTop */);
        }
        PendingTransition pending = new PendingTransition(
                visible ? TRANSIT_TO_FRONT : TRANSIT_TO_BACK, wct, taskView, null /* cookie */);
        mPending.add(pending);
        startNextTransition();
        // visibility is reported in transition.
    }

    /** Starts a new transition to reorder the given {@code taskView}'s task. */
    public void reorderTaskViewTask(TaskViewTaskController taskView, boolean onTop) {
        if (mTaskViews.get(taskView) == null) return;
        if (taskView.getTaskInfo() == null) {
            // Nothing to update, task is not yet available
            return;
        }
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.reorder(taskView.getTaskInfo().token, onTop /* onTop */);
        PendingTransition pending = new PendingTransition(
                onTop ? TRANSIT_TO_FRONT : TRANSIT_TO_BACK, wct, taskView, null /* cookie */);
        mPending.add(pending);
        startNextTransition();
        // visibility is reported in transition.
    }

    void updateBoundsState(TaskViewTaskController taskView, Rect boundsOnScreen) {
        TaskViewRequestedState state = mTaskViews.get(taskView);
        if (state == null) return;
        state.mBounds.set(boundsOnScreen);
    }

    void updateVisibilityState(TaskViewTaskController taskView, boolean visible) {
        TaskViewRequestedState state = mTaskViews.get(taskView);
        if (state == null) return;
        state.mVisible = visible;
    }

    void setTaskBounds(TaskViewTaskController taskView, Rect boundsOnScreen) {
        TaskViewRequestedState state = mTaskViews.get(taskView);
        if (state == null || Objects.equals(boundsOnScreen, state.mBounds)) {
            return;
        }
        state.mBounds.set(boundsOnScreen);
        if (!state.mVisible) {
            // Task view isn't visible, the bounds will next visibility update.
            return;
        }
        if (hasPending()) {
            // There is already a transition in-flight, the window bounds will be set in
            // prepareOpenAnimation.
            return;
        }
        WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.setBounds(taskView.getTaskInfo().token, boundsOnScreen);
        mPending.add(new PendingTransition(TRANSIT_CHANGE, wct, taskView, null /* cookie */));
        startNextTransition();
    }

    private void startNextTransition() {
        if (mPending.isEmpty()) return;
        final PendingTransition pending = mPending.get(0);
        if (pending.mClaimed != null) {
            // Wait for this to start animating.
            return;
        }
        pending.mClaimed = mTransitions.startTransition(pending.mType, pending.mWct, this);
    }

    @Override
    public void onTransitionConsumed(@NonNull IBinder transition, boolean aborted,
            @NonNull SurfaceControl.Transaction finishTransaction) {
        if (!aborted) return;
        final PendingTransition pending = findPending(transition);
        if (pending == null) return;
        mPending.remove(pending);
        startNextTransition();
    }

    @Override
    public boolean startAnimation(@NonNull IBinder transition,
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        PendingTransition pending = findPending(transition);
        if (pending != null) {
            mPending.remove(pending);
        }
        if (mTaskViews.isEmpty()) {
            if (pending != null) {
                Slog.e(TAG, "Pending taskview transition but no task-views");
            }
            return false;
        }
        boolean stillNeedsMatchingLaunch = pending != null && pending.mLaunchCookie != null;
        int changesHandled = 0;
        WindowContainerTransaction wct = null;
        for (int i = 0; i < info.getChanges().size(); ++i) {
            final TransitionInfo.Change chg = info.getChanges().get(i);
            if (chg.getTaskInfo() == null) continue;
            if (TransitionUtil.isClosingType(chg.getMode())) {
                final boolean isHide = chg.getMode() == TRANSIT_TO_BACK;
                TaskViewTaskController tv = findTaskView(chg.getTaskInfo());
                if (tv == null && !isHide) {
                    // TaskView can be null when closing
                    changesHandled++;
                    continue;
                }
                if (tv == null) {
                    if (pending != null) {
                        Slog.w(TAG, "Found a non-TaskView task in a TaskView Transition. This "
                                + "shouldn't happen, so there may be a visual artifact: "
                                + chg.getTaskInfo().taskId);
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
                final boolean taskIsNew = chg.getMode() == TRANSIT_OPEN;
                final TaskViewTaskController tv;
                if (taskIsNew) {
                    if (pending == null
                            || !chg.getTaskInfo().containsLaunchCookie(pending.mLaunchCookie)) {
                        Slog.e(TAG, "Found a launching TaskView in the wrong transition. All "
                                + "TaskView launches should be initiated by shell and in their "
                                + "own transition: " + chg.getTaskInfo().taskId);
                        continue;
                    }
                    stillNeedsMatchingLaunch = false;
                    tv = pending.mTaskView;
                } else {
                    tv = findTaskView(chg.getTaskInfo());
                    if (tv == null) {
                        if (pending != null) {
                            Slog.w(TAG, "Found a non-TaskView task in a TaskView Transition. This "
                                    + "shouldn't happen, so there may be a visual artifact: "
                                    + chg.getTaskInfo().taskId);
                        }
                        continue;
                    }
                }
                if (wct == null) wct = new WindowContainerTransaction();
                tv.prepareOpenAnimation(taskIsNew, startTransaction, finishTransaction,
                        chg.getTaskInfo(), chg.getLeash(), wct);
                changesHandled++;
            } else if (chg.getMode() == TRANSIT_CHANGE) {
                TaskViewTaskController tv = findTaskView(chg.getTaskInfo());
                if (tv == null) {
                    if (pending != null) {
                        Slog.w(TAG, "Found a non-TaskView task in a TaskView Transition. This "
                                + "shouldn't happen, so there may be a visual artifact: "
                                + chg.getTaskInfo().taskId);
                    }
                    continue;
                }
                startTransaction.reparent(chg.getLeash(), tv.getSurfaceControl());
                finishTransaction.reparent(chg.getLeash(), tv.getSurfaceControl())
                        .setPosition(chg.getLeash(), 0, 0);
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
        // No animation, just show it immediately.
        startTransaction.apply();
        finishCallback.onTransitionFinished(wct);
        startNextTransition();
        return true;
    }
}
