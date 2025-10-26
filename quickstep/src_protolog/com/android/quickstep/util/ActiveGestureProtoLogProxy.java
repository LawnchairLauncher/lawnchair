/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.quickstep.util;

import static android.view.MotionEvent.ACTION_DOWN;

import static com.android.launcher3.Flags.enableActiveGestureProtoLog;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.CANCEL_RECENTS_ANIMATION;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.FINISH_RECENTS_ANIMATION;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.INVALID_VELOCITY_ON_SWIPE_UP;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.LAUNCHER_DESTROYED;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.MOTION_DOWN;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.MOTION_MOVE;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.MOTION_UP;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.NAVIGATION_MODE_SWITCHED;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.ON_CANCEL_RECENTS_ANIMATION;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.ON_FINISH_RECENTS_ANIMATION;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.ON_SETTLED_ON_END_TARGET;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.ON_START_RECENTS_ANIMATION;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.QUICK_SWITCH_FROM_HOME_FAILED;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.QUICK_SWITCH_FROM_HOME_FALLBACK;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.RECENTS_ANIMATION_START_PENDING;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.RECENT_TASKS_MISSING;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.SET_END_TARGET;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.START_RECENTS_ANIMATION;
import static com.android.quickstep.util.QuickstepProtoLogGroup.ACTIVE_GESTURE_LOG;
import static com.android.quickstep.util.QuickstepProtoLogGroup.isProtoLogInitialized;

import android.graphics.Point;
import android.graphics.RectF;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.protolog.ProtoLog;
import com.android.internal.protolog.common.IProtoLogGroup;

/**
 * Proxy class used for ActiveGestureLog ProtoLog support.
 * <p>
 * This file will have all of its static strings in the
 * {@link ProtoLog#d(IProtoLogGroup, String, Object...)} calls replaced by dynamic code/strings.
 * <p>
 * When a new ActiveGestureLog entry needs to be added to the codebase (or and existing entry needs
 * to be modified), add it here under a new unique method and make sure the ProtoLog entry matches
 * to avoid confusion.
 */
public class ActiveGestureProtoLogProxy {

    public static void logLauncherDestroyed() {
        ActiveGestureLog.INSTANCE.addLog("Launcher destroyed", LAUNCHER_DESTROYED);
        if (isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG, "Launcher destroyed");
    }

    public static void logAbsSwipeUpHandlerOnRecentsAnimationCanceled() {
        ActiveGestureLog.INSTANCE.addLog(
                /* event= */ "AbsSwipeUpHandler.onRecentsAnimationCanceled",
                /* gestureEvent= */ CANCEL_RECENTS_ANIMATION);
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG, "AbsSwipeUpHandler.onRecentsAnimationCanceled");
    }

    public static void logAbsSwipeUpHandlerOnRecentsAnimationFinished() {
        ActiveGestureLog.INSTANCE.addLog(
                /* event= */ "RecentsAnimationCallbacks.onAnimationFinished",
                ON_FINISH_RECENTS_ANIMATION);
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG, "AbsSwipeUpHandler.onAnimationFinished");
    }

    public static void logAbsSwipeUpHandlerCancelCurrentAnimation() {
        ActiveGestureLog.INSTANCE.addLog(
                "AbsSwipeUpHandler.cancelCurrentAnimation",
                ActiveGestureErrorDetector.GestureEvent.CANCEL_CURRENT_ANIMATION);
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG, "AbsSwipeUpHandler.cancelCurrentAnimation");
    }

    public static void logAbsSwipeUpHandlerOnTasksAppeared() {
        ActiveGestureLog.INSTANCE.addLog("AbsSwipeUpHandler.onTasksAppeared: "
                + "force finish recents animation complete; clearing state callback.");
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG, "AbsSwipeUpHandler.onTasksAppeared: "
                + "force finish recents animation complete; clearing state callback.");
    }

    public static void logHandOffAnimation() {
        ActiveGestureLog.INSTANCE.addLog("AbsSwipeUpHandler.handOffAnimation");
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG, "AbsSwipeUpHandler.handOffAnimation");
    }

    public static void logFinishRecentsAnimationOnTasksAppeared() {
        ActiveGestureLog.INSTANCE.addLog("finishRecentsAnimationOnTasksAppeared");
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG, "finishRecentsAnimationOnTasksAppeared");
    }

    public static void logRecentsAnimationCallbacksOnAnimationCancelled() {
        ActiveGestureLog.INSTANCE.addLog(
                /* event= */ "RecentsAnimationCallbacks.onAnimationCanceled",
                /* gestureEvent= */ ON_CANCEL_RECENTS_ANIMATION);
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG, "RecentsAnimationCallbacks.onAnimationCanceled");
    }

    public static void logRecentsAnimationCallbacksOnTasksAppeared() {
        ActiveGestureLog.INSTANCE.addLog("RecentsAnimationCallbacks.onTasksAppeared",
                ActiveGestureErrorDetector.GestureEvent.TASK_APPEARED);
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG, "RecentsAnimationCallbacks.onTasksAppeared");
    }

    public static void logStartRecentsAnimation() {
        ActiveGestureLog.INSTANCE.addLog(
                /* event= */ "TaskAnimationManager.startRecentsAnimation",
                /* gestureEvent= */ START_RECENTS_ANIMATION);
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG, "TaskAnimationManager.startRecentsAnimation");
    }

    public static void logLaunchingSideTaskFailed() {
        ActiveGestureLog.INSTANCE.addLog("Unable to launch side task (no recents)");
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG, "Unable to launch side task (no recents)");
    }

    public static void logContinueRecentsAnimation() {
        ActiveGestureLog.INSTANCE.addLog(/* event= */ "continueRecentsAnimation");
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG, "continueRecentsAnimation");
    }

    public static void logCleanUpRecentsAnimationSkipped() {
        ActiveGestureLog.INSTANCE.addLog(
                /* event= */ "cleanUpRecentsAnimation skipped due to wrong callbacks");
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG, "cleanUpRecentsAnimation skipped due to wrong callbacks");
    }

    public static void logCleanUpRecentsAnimation() {
        ActiveGestureLog.INSTANCE.addLog(/* event= */ "cleanUpRecentsAnimation");
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG, "cleanUpRecentsAnimation");
    }

    public static void logOnInputEventUserLocked(int displayId) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "TIS.onInputEvent(displayId=%d): Cannot process input event: user is locked",
                displayId));
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG,
                "TIS.onInputEvent(displayId=%d): Cannot process input event: user is locked",
                displayId);
    }

    public static void logOnInputIgnoringFollowingEvents(int displayId) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "TIS.onMotionEvent(displayId=%d): A new gesture has been started, "
                        + "but a previously-requested recents animation hasn't started. "
                        + "Ignoring all following motion events.", displayId),
                RECENTS_ANIMATION_START_PENDING);
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG,
                "TIS.onMotionEvent(displayId=%d): A new gesture has been started, "
                        + "but a previously-requested recents animation hasn't started. "
                        + "Ignoring all following motion events.", displayId);
    }

    public static void logOnInputEventThreeButtonNav(int displayId) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "TIS.onInputEvent(displayId=%d): Cannot process input event: "
                        + "using 3-button nav and event is not a trackpad event", displayId));
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG,
                "TIS.onInputEvent(displayId=%d): Cannot process input event: "
                        + "using 3-button nav and event is not a trackpad event", displayId);
    }

    public static void logPreloadRecentsAnimation() {
        ActiveGestureLog.INSTANCE.addLog("preloadRecentsAnimation");
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG, "preloadRecentsAnimation");
    }

    public static void logRecentTasksMissing() {
        ActiveGestureLog.INSTANCE.addLog("Null mRecentTasks", RECENT_TASKS_MISSING);
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG, "Null mRecentTasks");
    }

    public static void logExecuteHomeCommand() {
        ActiveGestureLog.INSTANCE.addLog("OverviewCommandHelper.executeCommand(HOME)");
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG, "OverviewCommandHelper.executeCommand(HOME)");
    }

    public static void logFinishRecentsAnimationCallback() {
        ActiveGestureLog.INSTANCE.addLog("finishRecentsAnimation-callback");
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG, "finishRecentsAnimation-callback");
    }

    public static void logOnScrollerAnimationAborted() {
        ActiveGestureLog.INSTANCE.addLog("scroller animation aborted",
                ActiveGestureErrorDetector.GestureEvent.SCROLLER_ANIMATION_ABORTED);
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG, "scroller animation aborted");
    }

    public static void logInputConsumerBecameActive(@NonNull String consumerName) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "%s became active", consumerName));
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG, "%s became active", consumerName);
    }

    public static void logTaskLaunchFailed(int launchedTaskId) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "Launch failed, task (id=%d) finished mid transition", launchedTaskId));
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG,
                "Launch failed, task (id=%d) finished mid transition", launchedTaskId);
    }

    public static void logOnPageEndTransition(int nextPageIndex) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "onPageEndTransition: current page index updated: %d", nextPageIndex));
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG,
                "onPageEndTransition: current page index updated: %d", nextPageIndex);
    }

    public static void logQuickSwitchFromHomeFallback(int taskIndex) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "Quick switch from home fallback case: The TaskView at index %d is missing.",
                        taskIndex),
                QUICK_SWITCH_FROM_HOME_FALLBACK);
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG,
                "Quick switch from home fallback case: The TaskView at index %d is missing.",
                taskIndex);
    }

    public static void logQuickSwitchFromHomeFailed(int taskIndex) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "Quick switch from home failed: TaskViews at indices %d and 0 are missing.",
                        taskIndex),
                QUICK_SWITCH_FROM_HOME_FAILED);
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG,
                "Quick switch from home failed: TaskViews at indices %d and 0 are missing.",
                taskIndex);
    }

    public static void logFinishRecentsAnimation(boolean toRecents) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "finishRecentsAnimation: %b", toRecents),
                /* gestureEvent= */ FINISH_RECENTS_ANIMATION);
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG, "finishRecentsAnimation: %b", toRecents);
    }

    public static void logSetEndTarget(@NonNull String target) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "setEndTarget %s", target), /* gestureEvent= */ SET_END_TARGET);
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG, "setEndTarget %s", target);
    }

    public static void logStartHomeIntent(@NonNull String reason) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "OverviewComponentObserver.startHomeIntent: %s", reason));
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG, "OverviewComponentObserver.startHomeIntent: %s", reason);
    }

    public static void logRunningTaskPackage(@NonNull String packageName) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "Current running task package name=%s", packageName));
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG, "Current running task package name=%s", packageName);
    }

    public static void logSysuiStateFlags(@NonNull String stateFlags) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "Current SystemUi state flags=%s", stateFlags));
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG, "Current SystemUi state flags=%s", stateFlags);
    }

    public static void logSetInputConsumer(@NonNull String consumerName, @NonNull String reason) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "setInputConsumer: %s. reason(s):%s", consumerName, reason));
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG,
                "setInputConsumer: %s. reason(s):%s", consumerName, reason);
    }

    public static void logUpdateGestureStateRunningTask(
            @NonNull String otherTaskPackage, @NonNull String runningTaskPackage) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "Changing active task to %s because the previous task running on top of this "
                        + "one (%s) was excluded from recents",
                otherTaskPackage,
                runningTaskPackage));
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG,
                "Changing active task to %s because the previous task running on top of this "
                        + "one (%s) was excluded from recents",
                otherTaskPackage,
                runningTaskPackage);
    }

    public static void logOnInputEventActionUp(
            int x, int y, int action, @NonNull String classification, int displayId) {
        String actionString = MotionEvent.actionToString(action);
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "onMotionEvent(%d, %d): %s, %s, displayId=%d",
                        x,
                        y,
                        actionString,
                        classification,
                        displayId),
                /* gestureEvent= */ action == ACTION_DOWN
                        ? MOTION_DOWN
                        : MOTION_UP);
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG,
                "onMotionEvent(%d, %d): %s, %s, displayId=%d",
                x,
                y,
                actionString,
                classification,
                displayId);
    }

    public static void logOnInputEventActionMove(
            @NonNull String action,
            @NonNull String classification,
            int pointerCount,
            int displayId) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                        "onMotionEvent: %s, %s, pointerCount: %d, displayId=%d",
                        action,
                        classification,
                        pointerCount,
                        displayId),
                MOTION_MOVE);
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG,
                "onMotionEvent: %s, %s, pointerCount: %d, displayId=%d",
                action,
                classification,
                pointerCount,
                displayId);
    }

    public static void logOnInputEventGenericAction(
            @NonNull String action, @NonNull String classification, int displayId) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "onMotionEvent: %s, %s, displayId=%d", action, classification, displayId));
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG,
                "onMotionEvent: %s, %s, displayId=%d", action, classification, displayId);
    }

    public static void logOnInputEventNavModeSwitched(
            int displayId, @NonNull String startNavMode, @NonNull String currentNavMode) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "TIS.onInputEvent(displayId=%d): Navigation mode switched mid-gesture (%s -> %s); "
                        + "cancelling gesture.",
                        displayId,
                        startNavMode,
                        currentNavMode),
                NAVIGATION_MODE_SWITCHED);
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG,
                "TIS.onInputEvent(displayId=%d): Navigation mode switched mid-gesture (%s -> %s); "
                        + "cancelling gesture.",
                displayId,
                startNavMode,
                currentNavMode);
    }

    public static void logUnknownInputEvent(int displayId, @NonNull String event) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "TIS.onInputEvent(displayId=%d): Cannot process input event: "
                        + "received unknown event %s", displayId, event));
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG,
                "TIS.onInputEvent(displayId=%d): Cannot process input event: "
                        + "received unknown event %s", displayId, event);
    }

    public static void logFinishRunningRecentsAnimation(boolean toHome) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "finishRunningRecentsAnimation: %b", toHome));
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG, "finishRunningRecentsAnimation: %b", toHome);
    }

    public static void logOnRecentsAnimationStartCancelled() {
        ActiveGestureLog.INSTANCE.addLog("RecentsAnimationCallbacks.onAnimationStart (canceled): 0",
                /* gestureEvent= */ ON_START_RECENTS_ANIMATION);
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG, "RecentsAnimationCallbacks.onAnimationStart (canceled): 0");
    }

    public static void logOnRecentsAnimationStart(int appCount) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "RecentsAnimationCallbacks.onAnimationStart: %d", appCount),
                /* gestureEvent= */ ON_START_RECENTS_ANIMATION);
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG,
                "RecentsAnimationCallbacks.onAnimationStart: %d", appCount);
    }

    public static void logStartRecentsAnimationCallback(@NonNull String callback) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "TaskAnimationManager.startRecentsAnimation(%s): "
                        + "Setting mRecentsAnimationStartPending = false",
                callback));
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG,
                "TaskAnimationManager.startRecentsAnimation(%s): "
                        + "Setting mRecentsAnimationStartPending = false",
                callback);
    }

    public static void logSettingRecentsAnimationStartPending(boolean value) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "TaskAnimationManager.startRecentsAnimation: "
                        + "Setting mRecentsAnimationStartPending = %b",
                value));
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG,
                "TaskAnimationManager.startRecentsAnimation: "
                        + "Setting mRecentsAnimationStartPending = %b",
                value);
    }

    public static void logLaunchingSideTask(int taskId) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "Launching side task id=%d", taskId));
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG, "Launching side task id=%d", taskId);
    }

    public static void logOnInputEventActionDown(
            int displayId, @NonNull ActiveGestureLog.CompoundString reason) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "TIS.onMotionEvent(displayId=%d): ", displayId).append(reason));
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG,
                "TIS.onMotionEvent(displayId=%d): %s", displayId, reason.toString());
    }

    public static void logStartNewTask(@NonNull ActiveGestureLog.CompoundString tasks) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "Launching task: ").append(tasks));
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG, "TIS.onMotionEvent: %s", tasks.toString());
    }

    public static void logMotionPauseDetectorEvent(@NonNull ActiveGestureLog.CompoundString event) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "MotionPauseDetector: ").append(event));
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG, "MotionPauseDetector: %s", event.toString());
    }

    public static void logHandleTaskAppearedFailed(
            @NonNull ActiveGestureLog.CompoundString reason) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "handleTaskAppeared check failed: ").append(reason));
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG, "handleTaskAppeared check failed: %s", reason.toString());
    }

    /**
     * This is for special cases where the string is purely dynamic and therefore has no format that
     * can be extracted. Do not use in any other case.
     */
    public static void logDynamicString(
            @NonNull String string,
            @Nullable ActiveGestureErrorDetector.GestureEvent gestureEvent) {
        ActiveGestureLog.INSTANCE.addLog(string, gestureEvent);
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG, "%s", string);
    }

    public static void logOnSettledOnEndTarget(@NonNull String endTarget) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "onSettledOnEndTarget %s", endTarget),
                /* gestureEvent= */ ON_SETTLED_ON_END_TARGET);
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG, "onSettledOnEndTarget %s", endTarget);
    }

    public static void logOnCalculateEndTarget(float velocityX, float velocityY, double angle) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "calculateEndTarget: velocities=(x=%fdp/ms, y=%fdp/ms), angle=%f",
                        velocityX,
                        velocityY,
                        angle),
                velocityX == 0 && velocityY == 0 ? INVALID_VELOCITY_ON_SWIPE_UP : null);
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG,
                "calculateEndTarget: velocities=(x=%fdp/ms, y=%fdp/ms), angle=%f",
                velocityX,
                velocityY,
                angle);
    }

    public static void logUnexpectedTaskAppeared(int taskId, @NonNull String packageName) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "Forcefully finishing recents animation: Unexpected task appeared id=%d, pkg=%s",
                taskId,
                packageName));
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG,
                "Forcefully finishing recents animation: Unexpected task appeared id=%d, pkg=%s",
                taskId,
                packageName);
    }

    public static void logCreateTouchRegionForDisplay(int displayRotation,
            @NonNull Point displaySize, @NonNull RectF swipeRegion, @NonNull RectF ohmRegion,
            int gesturalHeight, int largerGesturalHeight, @NonNull String reason) {
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG,
                "OrientationTouchTransformer.createRegionForDisplay: "
                        + "dispRot=%d, dispSize=%s, swipeRegion=%s, ohmRegion=%s, "
                        + "gesturalHeight=%d, largerGesturalHeight=%d, reason=%s",
                displayRotation, displaySize.flattenToString(), swipeRegion.toShortString(),
                ohmRegion.toShortString(), gesturalHeight, largerGesturalHeight, reason);
    }

    public static void logOnTaskAnimationManagerNotAvailable(int displayId) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "TaskAnimationManager not available for displayId=%d",
                displayId));
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG, "TaskAnimationManager not available for displayId=%d",
                displayId);
    }

    public static void logGestureStartSwipeHandler(@NonNull String interactionHandler) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "OtherActivityInputConsumer.startTouchTrackingForWindowAnimation: "
                        + "interactionHandler=%s", interactionHandler));
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG,
                "OtherActivityInputConsumer.startTouchTrackingForWindowAnimation: "
                        + "interactionHandler=%s", interactionHandler);
    }

    public static void logQueuingForceFinishRecentsAnimation() {
        ActiveGestureLog.INSTANCE.addLog("Launcher destroyed while mRecentsAnimationStartPending =="
                        + " true, queuing a callback to clean the pending animation up on start",
                /* gestureEvent= */ ON_START_RECENTS_ANIMATION);
        if (!enableActiveGestureProtoLog() || !isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG, "Launcher destroyed while mRecentsAnimationStartPending =="
                + " true, queuing a callback to clean the pending animation up on start");
    }
}
