/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.quickstep.util.ActiveGestureLog.INTENT_EXTRA_LOG_TRACE_ID;

import android.annotation.TargetApi;
import android.content.Intent;
import android.graphics.PointF;
import android.os.Build;
import android.os.SystemClock;
import android.os.Trace;
import android.view.View;

import androidx.annotation.BinderThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.taskbar.TaskbarUIController;
import com.android.launcher3.util.RunnableList;
import com.android.quickstep.RecentsAnimationCallbacks.RecentsAnimationListener;
import com.android.quickstep.util.ActiveGestureLog;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.InteractionJankMonitorWrapper;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Helper class to handle various atomic commands for switching between Overview.
 */
@TargetApi(Build.VERSION_CODES.P)
public class OverviewCommandHelper {

    public static final int TYPE_SHOW = 1;
    public static final int TYPE_KEYBOARD_INPUT = 2;
    public static final int TYPE_HIDE = 3;
    public static final int TYPE_TOGGLE = 4;
    public static final int TYPE_HOME = 5;

    /**
     * Use case for needing a queue is double tapping recents button in 3 button nav.
     * Size of 2 should be enough. We'll toss in one more because we're kind hearted.
     */
    private final static int MAX_QUEUE_SIZE = 3;

    private static final String TRANSITION_NAME = "Transition:toOverview";

    private final TouchInteractionService mService;
    private final OverviewComponentObserver mOverviewComponentObserver;
    private final TaskAnimationManager mTaskAnimationManager;
    private final ArrayList<CommandInfo> mPendingCommands = new ArrayList<>();

    /**
     * Index of the TaskView that should be focused when launching Overview. Persisted so that we
     * do not lose the focus across multiple calls of
     * {@link OverviewCommandHelper#executeCommand(CommandInfo)} for the same command
     */
    private int mTaskFocusIndexOverride = -1;

    /**
     * Whether we should incoming toggle commands while a previous toggle command is still ongoing.
     * This serves as a rate-limiter to prevent overlapping animations that can clobber each other
     * and prevent clean-up callbacks from running. This thus prevents a recurring set of bugs with
     * janky recents animations and unresponsive home and overview buttons.
     */
    private boolean mWaitForToggleCommandComplete = false;

    public OverviewCommandHelper(TouchInteractionService service,
            OverviewComponentObserver observer,
            TaskAnimationManager taskAnimationManager) {
        mService = service;
        mOverviewComponentObserver = observer;
        mTaskAnimationManager = taskAnimationManager;
    }

    /**
     * Called when the command finishes execution.
     */
    private void scheduleNextTask(CommandInfo command) {
        if (!mPendingCommands.isEmpty() && mPendingCommands.get(0) == command) {
            mPendingCommands.remove(0);
            executeNext();
        }
    }

    /**
     * Executes the next command from the queue. If the command finishes immediately (returns true),
     * it continues to execute the next command, until the queue is empty of a command defer's its
     * completion (returns false).
     */
    @UiThread
    private void executeNext() {
        if (mPendingCommands.isEmpty()) {
            return;
        }
        CommandInfo cmd = mPendingCommands.get(0);
        if (executeCommand(cmd)) {
            scheduleNextTask(cmd);
        }
    }

    @UiThread
    private void addCommand(CommandInfo cmd) {
        boolean wasEmpty = mPendingCommands.isEmpty();
        mPendingCommands.add(cmd);
        if (wasEmpty) {
            executeNext();
        }
    }

    /**
     * Adds a command to be executed next, after all pending tasks are completed.
     * Max commands that can be queued is {@link #MAX_QUEUE_SIZE}.
     * Requests after reaching that limit will be silently dropped.
     */
    @BinderThread
    public void addCommand(int type) {
        if (mPendingCommands.size() >= MAX_QUEUE_SIZE) {
            return;
        }
        CommandInfo cmd = new CommandInfo(type);
        MAIN_EXECUTOR.execute(() -> addCommand(cmd));
    }

    @UiThread
    public void clearPendingCommands() {
        mPendingCommands.clear();
    }

    @UiThread
    public boolean canStartHomeSafely() {
        return mPendingCommands.isEmpty() || mPendingCommands.get(0).type == TYPE_HOME;
    }

    @Nullable
    private TaskView getNextTask(RecentsView view) {
        final TaskView runningTaskView = view.getRunningTaskView();

        if (runningTaskView == null) {
            return view.getTaskViewAt(0);
        } else {
            final TaskView nextTask = view.getNextTaskView();
            return nextTask != null ? nextTask : runningTaskView;
        }
    }

    private boolean launchTask(RecentsView recents, @Nullable TaskView taskView, CommandInfo cmd) {
        RunnableList callbackList = null;
        if (taskView != null) {
            mWaitForToggleCommandComplete = true;
            taskView.setEndQuickswitchCuj(true);
            callbackList = taskView.launchTasks();
        }

        if (callbackList != null) {
            callbackList.add(() -> {
                scheduleNextTask(cmd);
                mWaitForToggleCommandComplete = false;
            });
            return false;
        } else {
            recents.startHome();
            mWaitForToggleCommandComplete = false;
            return true;
        }
    }

    /**
     * Executes the task and returns true if next task can be executed. If false, then the next
     * task is deferred until {@link #scheduleNextTask} is called
     */
    private <T extends StatefulActivity<?>> boolean executeCommand(CommandInfo cmd) {
        if (mWaitForToggleCommandComplete && cmd.type == TYPE_TOGGLE) {
            return true;
        }
        BaseActivityInterface<?, T> activityInterface =
                mOverviewComponentObserver.getActivityInterface();
        RecentsView recents = activityInterface.getVisibleRecentsView();
        if (recents == null) {
            T activity = activityInterface.getCreatedActivity();
            DeviceProfile dp = activity == null ? null : activity.getDeviceProfile();
            TaskbarUIController uiController = activityInterface.getTaskbarController();
            boolean allowQuickSwitch = FeatureFlags.ENABLE_KEYBOARD_QUICK_SWITCH.get()
                    && uiController != null
                    && dp != null
                    && (dp.isTablet || dp.isTwoPanels);

            if (cmd.type == TYPE_HIDE) {
                if (!allowQuickSwitch) {
                    return true;
                }
                mTaskFocusIndexOverride = uiController.launchFocusedTask();
                if (mTaskFocusIndexOverride == -1) {
                    return true;
                }
            }
            if (cmd.type == TYPE_KEYBOARD_INPUT && allowQuickSwitch) {
                uiController.openQuickSwitchView();
                return true;
            }
            if (cmd.type == TYPE_HOME) {
                ActiveGestureLog.INSTANCE.addLog("OverviewCommandHelper.executeCommand(TYPE_HOME)");
                mService.startActivity(mOverviewComponentObserver.getHomeIntent());
                return true;
            }
        } else {
            switch (cmd.type) {
                case TYPE_SHOW:
                    // already visible
                    return true;
                case TYPE_HIDE: {
                    mTaskFocusIndexOverride = -1;
                    int currentPage = recents.getNextPage();
                    TaskView tv = (currentPage >= 0 && currentPage < recents.getTaskViewCount())
                            ? (TaskView) recents.getPageAt(currentPage)
                            : null;
                    return launchTask(recents, tv, cmd);
                }
                case TYPE_TOGGLE:
                    return launchTask(recents, getNextTask(recents), cmd);
                case TYPE_HOME:
                    recents.startHome();
                    return true;
            }
        }

        final Runnable completeCallback = () -> {
            RecentsView rv = activityInterface.getVisibleRecentsView();
            if (rv != null && (cmd.type == TYPE_KEYBOARD_INPUT || cmd.type == TYPE_HIDE)) {
                updateRecentsViewFocus(rv);
            }
            scheduleNextTask(cmd);
        };
        if (activityInterface.switchToRecentsIfVisible(completeCallback)) {
            // If successfully switched, wait until animation finishes
            return false;
        }

        final T activity = activityInterface.getCreatedActivity();
        if (activity != null) {
            InteractionJankMonitorWrapper.begin(
                    activity.getRootView(),
                    InteractionJankMonitorWrapper.CUJ_QUICK_SWITCH);
        }

        GestureState gestureState = mService.createGestureState(GestureState.DEFAULT_STATE,
                GestureState.TrackpadGestureType.NONE);
        gestureState.setHandlingAtomicEvent(true);
        AbsSwipeUpHandler interactionHandler = mService.getSwipeUpHandlerFactory()
                .newHandler(gestureState, cmd.createTime);
        interactionHandler.setGestureEndCallback(
                () -> onTransitionComplete(cmd, interactionHandler));
        interactionHandler.initWhenReady();

        RecentsAnimationListener recentAnimListener = new RecentsAnimationListener() {
            @Override
            public void onRecentsAnimationStart(RecentsAnimationController controller,
                    RecentsAnimationTargets targets) {
                activityInterface.runOnInitBackgroundStateUI(() ->
                        interactionHandler.onGestureEnded(0, new PointF()));
                cmd.removeListener(this);
            }

            @Override
            public void onRecentsAnimationCanceled(HashMap<Integer, ThumbnailData> thumbnailDatas) {
                interactionHandler.onGestureCancelled();
                cmd.removeListener(this);

                T createdActivity = activityInterface.getCreatedActivity();
                if (createdActivity == null) {
                    return;
                }
                RecentsView createdRecents = createdActivity.getOverviewPanel();
                if (createdRecents != null) {
                    createdRecents.onRecentsAnimationComplete();
                }
            }
        };

        RecentsView<?, ?> visibleRecentsView = activityInterface.getVisibleRecentsView();
        if (visibleRecentsView != null) {
            visibleRecentsView.moveRunningTaskToFront();
        }
        if (mTaskAnimationManager.isRecentsAnimationRunning()) {
            cmd.mActiveCallbacks = mTaskAnimationManager.continueRecentsAnimation(gestureState);
            cmd.mActiveCallbacks.addListener(interactionHandler);
            mTaskAnimationManager.notifyRecentsAnimationState(interactionHandler);
            interactionHandler.onGestureStarted(true /*isLikelyToStartNewTask*/);

            cmd.mActiveCallbacks.addListener(recentAnimListener);
            mTaskAnimationManager.notifyRecentsAnimationState(recentAnimListener);
        } else {
            Intent intent = new Intent(interactionHandler.getLaunchIntent());
            intent.putExtra(INTENT_EXTRA_LOG_TRACE_ID, gestureState.getGestureId());
            cmd.mActiveCallbacks = mTaskAnimationManager.startRecentsAnimation(
                    gestureState, intent, interactionHandler);
            interactionHandler.onGestureStarted(false /*isLikelyToStartNewTask*/);
            cmd.mActiveCallbacks.addListener(recentAnimListener);
        }

        Trace.beginAsyncSection(TRANSITION_NAME, 0);
        return false;
    }

    private void onTransitionComplete(CommandInfo cmd, AbsSwipeUpHandler handler) {
        cmd.removeListener(handler);
        Trace.endAsyncSection(TRANSITION_NAME, 0);

        RecentsView rv =
                mOverviewComponentObserver.getActivityInterface().getVisibleRecentsView();
        if (rv != null && (cmd.type == TYPE_KEYBOARD_INPUT || cmd.type == TYPE_HIDE)) {
            updateRecentsViewFocus(rv);
        }
        scheduleNextTask(cmd);
    }

    private void updateRecentsViewFocus(@NonNull RecentsView rv) {
        // When the overview is launched via alt tab (cmd type is TYPE_KEYBOARD_INPUT),
        // the touch mode somehow is not change to false by the Android framework.
        // The subsequent tab to go through tasks in overview can only be dispatched to
        // focuses views, while focus can only be requested in
        // {@link View#requestFocusNoSearch(int, Rect)} when touch mode is false. To note,
        // here we launch overview with live tile.
        rv.getViewRootImpl().touchModeChanged(false);
        // Ensure that recents view has focus so that it receives the followup key inputs
        TaskView taskView = rv.getTaskViewAt(mTaskFocusIndexOverride);
        if (taskView != null) {
            requestFocus(taskView);
            return;
        }
        taskView = rv.getNextTaskView();
        if (taskView != null) {
            requestFocus(taskView);
            return;
        }
        taskView = rv.getTaskViewAt(0);
        if (taskView != null) {
            requestFocus(taskView);
            return;
        }
        requestFocus(rv);
    }

    private void requestFocus(@NonNull View view) {
        view.post(() -> {
            view.requestFocus();
            view.requestAccessibilityFocus();
        });
    }

    public void dump(PrintWriter pw) {
        pw.println("OverviewCommandHelper:");
        pw.println("  mPendingCommands=" + mPendingCommands.size());
        if (!mPendingCommands.isEmpty()) {
            pw.println("    pendingCommandType=" + mPendingCommands.get(0).type);
        }
        pw.println("  mTaskFocusIndexOverride=" + mTaskFocusIndexOverride);
        pw.println("  mWaitForToggleCommandComplete=" + mWaitForToggleCommandComplete);
    }

    private static class CommandInfo {
        public final long createTime = SystemClock.elapsedRealtime();
        public final int type;
        RecentsAnimationCallbacks mActiveCallbacks;

        CommandInfo(int type) {
            this.type = type;
        }

        void removeListener(RecentsAnimationListener listener) {
            if (mActiveCallbacks != null) {
                mActiveCallbacks.removeListener(listener);
            }
        }
    }
}
