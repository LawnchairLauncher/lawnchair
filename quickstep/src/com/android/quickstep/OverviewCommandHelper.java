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

import static com.android.launcher3.PagedView.INVALID_PAGE;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_OVERVIEW_SHOW_OVERVIEW_FROM_3_BUTTON;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_OVERVIEW_SHOW_OVERVIEW_FROM_KEYBOARD_QUICK_SWITCH;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_OVERVIEW_SHOW_OVERVIEW_FROM_KEYBOARD_SHORTCUT;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.quickstep.util.ActiveGestureLog.INTENT_EXTRA_LOG_TRACE_ID;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.graphics.PointF;
import android.os.SystemClock;
import android.os.Trace;
import android.util.Log;
import android.view.View;

import androidx.annotation.BinderThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.internal.jank.Cuj;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.taskbar.TaskbarUIController;
import com.android.launcher3.util.RunnableList;
import com.android.quickstep.RecentsAnimationCallbacks.RecentsAnimationListener;
import com.android.quickstep.util.ActiveGestureLog;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.RecentsViewContainer;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.InteractionJankMonitorWrapper;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Helper class to handle various atomic commands for switching between Overview.
 */
public class OverviewCommandHelper {
    private static final String TAG = "OverviewCommandHelper";

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
    private int mKeyboardTaskFocusIndex = -1;

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
        if (mPendingCommands.isEmpty()) {
            Log.d(TAG, "no pending commands to schedule");
            return;
        }
        if (mPendingCommands.get(0) != command) {
            Log.d(TAG, "next task not scheduled."
                    + " mPendingCommands[0] type is " + mPendingCommands.get(0)
                    + " - command type is: " + command);
            return;
        }
        Log.d(TAG, "scheduleNextTask called: " + command);
        mPendingCommands.remove(0);
        executeNext();
    }

    /**
     * Executes the next command from the queue. If the command finishes immediately (returns true),
     * it continues to execute the next command, until the queue is empty of a command defer's its
     * completion (returns false).
     */
    @UiThread
    private void executeNext() {
        if (mPendingCommands.isEmpty()) {
            Log.d(TAG, "executeNext - mPendingCommands is empty");
            return;
        }
        CommandInfo cmd = mPendingCommands.get(0);

        boolean result = executeCommand(cmd);
        Log.d(TAG, "executeNext cmd type: " + cmd + ", result: " + result);
        if (result) {
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
            Log.d(TAG, "the pending command queue is full (" + mPendingCommands.size() + "). "
                    + "command not added: " + type);
            return;
        }
        Log.d(TAG, "adding command type: " + type);
        CommandInfo cmd = new CommandInfo(type);
        MAIN_EXECUTOR.execute(() -> addCommand(cmd));
    }

    @UiThread
    public void clearPendingCommands() {
        Log.d(TAG, "clearing pending commands - size: " + mPendingCommands.size());
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
            taskView.setEndQuickSwitchCuj(true);
            callbackList = taskView.launchTasks();
        }

        if (callbackList != null) {
            callbackList.add(() -> {
                Log.d(TAG, "launching task callback: " + cmd);
                scheduleNextTask(cmd);
                mWaitForToggleCommandComplete = false;
            });
            Log.d(TAG, "launching task - waiting for callback: " + cmd);
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
    private <T extends StatefulActivity<?> & RecentsViewContainer> boolean executeCommand(
            CommandInfo cmd) {
        if (mWaitForToggleCommandComplete && cmd.type == TYPE_TOGGLE) {
            Log.d(TAG, "executeCommand: " + cmd
                    + " - waiting for toggle command complete");
            return true;
        }
        BaseActivityInterface<?, T> activityInterface =
                mOverviewComponentObserver.getActivityInterface();
        RecentsView visibleRecentsView = activityInterface.getVisibleRecentsView();
        RecentsView createdRecentsView;

        Log.d(TAG, "executeCommand: " + cmd
                + " - visibleRecentsView: " + visibleRecentsView);
        if (visibleRecentsView == null) {
            T activity = activityInterface.getCreatedContainer();
            createdRecentsView = activity == null ? null : activity.getOverviewPanel();
            DeviceProfile dp = activity == null ? null : activity.getDeviceProfile();
            TaskbarUIController uiController = activityInterface.getTaskbarController();
            boolean allowQuickSwitch = FeatureFlags.ENABLE_KEYBOARD_QUICK_SWITCH.get()
                    && uiController != null
                    && dp != null
                    && (dp.isTablet || dp.isTwoPanels);

            switch (cmd.type) {
                case TYPE_HIDE:
                    if (!allowQuickSwitch) {
                        return true;
                    }
                    mKeyboardTaskFocusIndex = uiController.launchFocusedTask();
                    if (mKeyboardTaskFocusIndex == -1) {
                        return true;
                    }
                    break;
                case TYPE_KEYBOARD_INPUT:
                    if (allowQuickSwitch) {
                        uiController.openQuickSwitchView();
                        return true;
                    } else {
                        mKeyboardTaskFocusIndex = 0;
                        break;
                    }
                case TYPE_HOME:
                    ActiveGestureLog.INSTANCE.addLog(
                            "OverviewCommandHelper.executeCommand(TYPE_HOME)");
                    mService.startActivity(mOverviewComponentObserver.getHomeIntent());
                    return true;
                case TYPE_SHOW:
                    // When Recents is not currently visible, the command's type is TYPE_SHOW
                    // when overview is triggered via the keyboard overview button or Action+Tab
                    // keys (Not Alt+Tab which is KQS). The overview button on-screen in 3-button
                    // nav is TYPE_TOGGLE.
                    mKeyboardTaskFocusIndex = 0;
                    break;
                default:
                    // continue below to handle displaying Recents.
            }
        } else {
            createdRecentsView = visibleRecentsView;
            switch (cmd.type) {
                case TYPE_SHOW:
                    // already visible
                    return true;
                case TYPE_KEYBOARD_INPUT: {
                    if (visibleRecentsView.isHandlingTouch()) {
                        return true;
                    }
                }
                case TYPE_HIDE: {
                    if (visibleRecentsView.isHandlingTouch()) {
                        return true;
                    }
                    mKeyboardTaskFocusIndex = INVALID_PAGE;
                    int currentPage = visibleRecentsView.getNextPage();
                    TaskView tv = (currentPage >= 0
                            && currentPage < visibleRecentsView.getTaskViewCount())
                            ? (TaskView) visibleRecentsView.getPageAt(currentPage)
                            : null;
                    return launchTask(visibleRecentsView, tv, cmd);
                }
                case TYPE_TOGGLE:
                    return launchTask(visibleRecentsView, getNextTask(visibleRecentsView), cmd);
                case TYPE_HOME:
                    visibleRecentsView.startHome();
                    return true;
            }
        }

        if (createdRecentsView != null) {
            createdRecentsView.setKeyboardTaskFocusIndex(mKeyboardTaskFocusIndex);
        }
        // Handle recents view focus when launching from home
        Animator.AnimatorListener animatorListener = new AnimatorListenerAdapter() {

            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                updateRecentsViewFocus(cmd);
                logShowOverviewFrom(cmd.type);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                Log.d(TAG, "switching to Overview state - onAnimationEnd: " + cmd);
                super.onAnimationEnd(animation);
                onRecentsViewFocusUpdated(cmd);
                scheduleNextTask(cmd);
            }
        };
        if (activityInterface.switchToRecentsIfVisible(animatorListener)) {
            Log.d(TAG, "switching to Overview state - waiting: " + cmd);
            // If successfully switched, wait until animation finishes
            return false;
        }

        final T activity = activityInterface.getCreatedContainer();
        if (activity != null) {
            InteractionJankMonitorWrapper.begin(
                    activity.getRootView(),
                    Cuj.CUJ_LAUNCHER_QUICK_SWITCH);
        }

        GestureState gestureState = mService.createGestureState(GestureState.DEFAULT_STATE,
                GestureState.TrackpadGestureType.NONE);
        gestureState.setHandlingAtomicEvent(true);
        AbsSwipeUpHandler interactionHandler = mService.getSwipeUpHandlerFactory()
                .newHandler(gestureState, cmd.createTime);
        interactionHandler.setGestureEndCallback(
                () -> onTransitionComplete(cmd, interactionHandler));
        interactionHandler.initWhenReady("OverviewCommandHelper: cmd.type=" + cmd.type);

        RecentsAnimationListener recentAnimListener = new RecentsAnimationListener() {
            @Override
            public void onRecentsAnimationStart(RecentsAnimationController controller,
                    RecentsAnimationTargets targets) {
                updateRecentsViewFocus(cmd);
                logShowOverviewFrom(cmd.type);
                activityInterface.runOnInitBackgroundStateUI(() ->
                        interactionHandler.onGestureEnded(0, new PointF()));
                cmd.removeListener(this);
            }

            @Override
            public void onRecentsAnimationCanceled(HashMap<Integer, ThumbnailData> thumbnailDatas) {
                interactionHandler.onGestureCancelled();
                cmd.removeListener(this);

                T createdActivity = activityInterface.getCreatedContainer();
                if (createdActivity == null) {
                    return;
                }
                if (createdRecentsView != null) {
                    createdRecentsView.onRecentsAnimationComplete();
                }
            }
        };

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
        Log.d(TAG, "switching via recents animation - onGestureStarted: " + cmd);
        return false;
    }

    private void onTransitionComplete(CommandInfo cmd, AbsSwipeUpHandler handler) {
        Log.d(TAG, "switching via recents animation - onTransitionComplete: " + cmd);
        cmd.removeListener(handler);
        Trace.endAsyncSection(TRANSITION_NAME, 0);
        onRecentsViewFocusUpdated(cmd);
        scheduleNextTask(cmd);
    }

    private void updateRecentsViewFocus(CommandInfo cmd) {
        RecentsView recentsView =
                mOverviewComponentObserver.getActivityInterface().getVisibleRecentsView();
        if (recentsView == null || (cmd.type != TYPE_KEYBOARD_INPUT && cmd.type != TYPE_HIDE
                && cmd.type != TYPE_SHOW)) {
            return;
        }
        // When the overview is launched via alt tab (cmd type is TYPE_KEYBOARD_INPUT),
        // the touch mode somehow is not change to false by the Android framework.
        // The subsequent tab to go through tasks in overview can only be dispatched to
        // focuses views, while focus can only be requested in
        // {@link View#requestFocusNoSearch(int, Rect)} when touch mode is false. To note,
        // here we launch overview with live tile.
        recentsView.getViewRootImpl().touchModeChanged(false);
        // Ensure that recents view has focus so that it receives the followup key inputs
        if (requestFocus(recentsView.getTaskViewAt(mKeyboardTaskFocusIndex))) {
            return;
        }
        if (requestFocus(recentsView.getNextTaskView())) {
            return;
        }
        if (requestFocus(recentsView.getTaskViewAt(0))) {
            return;
        }
        requestFocus(recentsView);
    }

    private void onRecentsViewFocusUpdated(CommandInfo cmd) {
        RecentsView recentsView =
                mOverviewComponentObserver.getActivityInterface().getVisibleRecentsView();
        if (recentsView == null
                || cmd.type != TYPE_HIDE
                || mKeyboardTaskFocusIndex == INVALID_PAGE) {
            return;
        }
        recentsView.setKeyboardTaskFocusIndex(INVALID_PAGE);
        recentsView.setCurrentPage(mKeyboardTaskFocusIndex);
        mKeyboardTaskFocusIndex = INVALID_PAGE;
    }

    private boolean requestFocus(@Nullable View taskView) {
        if (taskView == null) {
            return false;
        }
        taskView.post(() -> {
            taskView.requestFocus();
            taskView.requestAccessibilityFocus();
        });
        return true;
    }

    private <T extends StatefulActivity<?> & RecentsViewContainer>
            void logShowOverviewFrom(int cmdType) {
        BaseActivityInterface<?, T> activityInterface =
                mOverviewComponentObserver.getActivityInterface();
        var container = activityInterface.getCreatedContainer();
        if (container != null) {
            StatsLogManager.LauncherEvent event;
            switch (cmdType) {
                case TYPE_SHOW -> event = LAUNCHER_OVERVIEW_SHOW_OVERVIEW_FROM_KEYBOARD_SHORTCUT;
                case TYPE_HIDE ->
                        event = LAUNCHER_OVERVIEW_SHOW_OVERVIEW_FROM_KEYBOARD_QUICK_SWITCH;
                case TYPE_TOGGLE -> event = LAUNCHER_OVERVIEW_SHOW_OVERVIEW_FROM_3_BUTTON;
                default -> {
                    return;
                }
            }

            StatsLogManager.newInstance(container.asContext())
                    .logger()
                    .withContainerInfo(LauncherAtom.ContainerInfo.newBuilder()
                            .setTaskSwitcherContainer(
                                    LauncherAtom.TaskSwitcherContainer.getDefaultInstance())
                            .build())
                    .log(event);
        }
    }

    public void dump(PrintWriter pw) {
        pw.println("OverviewCommandHelper:");
        pw.println("  mPendingCommands=" + mPendingCommands.size());
        if (!mPendingCommands.isEmpty()) {
            pw.println("    pendingCommandType=" + mPendingCommands.get(0).type);
        }
        pw.println("  mKeyboardTaskFocusIndex=" + mKeyboardTaskFocusIndex);
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

        @NonNull
        @Override
        public String toString() {
            return "CommandInfo("
                    + "type=" + type + ", "
                    + "createTime=" + createTime + ", "
                    + "mActiveCallbacks=" + mActiveCallbacks
                    + ")";
        }
    }
}
