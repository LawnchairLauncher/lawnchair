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
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.RECENTS_ANIMATION_START_TIMEOUT;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.RECENT_TASKS_MISSING;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.SET_END_TARGET;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.START_RECENTS_ANIMATION;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.UNFINISHED_TASK_LAUNCH;
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
    private static final QuickstepProtoLogGroup PROTO_LOG_GROUP = ACTIVE_GESTURE_LOG;

    private static boolean willProtoLog() {
        return isProtoLogInitialized();
    }

    public static void logLauncherDestroyed() {
        ActiveGestureLog.INSTANCE.addLog("Launcher destroyed", LAUNCHER_DESTROYED);
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "Launcher destroyed");
        }
    }

    public static void logAbsSwipeUpHandlerOnRecentsAnimationCanceled() {
        ActiveGestureLog.INSTANCE.addLog(
                /* event= */ "AbsSwipeUpHandler.onRecentsAnimationCanceled",
                /* gestureEvent= */ CANCEL_RECENTS_ANIMATION);
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "AbsSwipeUpHandler.onRecentsAnimationCanceled");
        }
    }

    public static void logAbsSwipeUpHandlerOnRecentsAnimationFinished() {
        ActiveGestureLog.INSTANCE.addLog(
                /* event= */ "RecentsAnimationCallbacks.onAnimationFinished",
                ON_FINISH_RECENTS_ANIMATION);
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "AbsSwipeUpHandler.onAnimationFinished");
        }
    }

    public static void logAbsSwipeUpHandlerCancelCurrentAnimation() {
        ActiveGestureLog.INSTANCE.addLog("AbsSwipeUpHandler.cancelCurrentAnimation",
                ActiveGestureErrorDetector.GestureEvent.CANCEL_CURRENT_ANIMATION);
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "AbsSwipeUpHandler.cancelCurrentAnimation");
        }
    }

    public static void logAbsSwipeUpHandlerOnTasksAppeared() {
        ActiveGestureLog.INSTANCE.addLog("AbsSwipeUpHandler.onTasksAppeared: "
                + "force finish recents animation complete; clearing state callback.");
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "AbsSwipeUpHandler.onTasksAppeared: "
                    + "force finish recents animation complete; clearing state callback.");
        }
    }

    public static void logHandOffAnimation() {
        ActiveGestureLog.INSTANCE.addLog("AbsSwipeUpHandler.handOffAnimation");
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "AbsSwipeUpHandler.handOffAnimation");
        }
    }

    public static void logRecentsAnimationCallbacksOnAnimationCancelled() {
        ActiveGestureLog.INSTANCE.addLog(
                /* event= */ "RecentsAnimationCallbacks.onAnimationCanceled",
                /* gestureEvent= */ ON_CANCEL_RECENTS_ANIMATION);
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "RecentsAnimationCallbacks.onAnimationCanceled");
        }
    }

    public static void logRecentsAnimationCallbacksOnTasksAppeared() {
        ActiveGestureLog.INSTANCE.addLog("RecentsAnimationCallbacks.onTasksAppeared",
                ActiveGestureErrorDetector.GestureEvent.TASK_APPEARED);
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "RecentsAnimationCallbacks.onTasksAppeared");
        }
    }

    public static void logStartRecentsAnimation() {
        ActiveGestureLog.INSTANCE.addLog(
                /* event= */ "TaskAnimationManager.startRecentsAnimation",
                /* gestureEvent= */ START_RECENTS_ANIMATION);
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "TaskAnimationManager.startRecentsAnimation");
        }
    }

    public static void logLaunchingSideTaskFailed() {
        ActiveGestureLog.INSTANCE.addLog("Unable to launch side task (no recents)");
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "Unable to launch side task (no recents)");
        }
    }

    public static void logContinueRecentsAnimation() {
        ActiveGestureLog.INSTANCE.addLog(/* event= */ "continueRecentsAnimation");
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "continueRecentsAnimation");
        }
    }

    public static void logCleanUpRecentsAnimationSkipped() {
        ActiveGestureLog.INSTANCE.addLog(
                /* event= */ "cleanUpRecentsAnimation skipped due to wrong callbacks");
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "cleanUpRecentsAnimation skipped due to wrong callbacks");
        }
    }

    public static void logCleanUpRecentsAnimation() {
        ActiveGestureLog.INSTANCE.addLog(/* event= */ "cleanUpRecentsAnimation");
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "cleanUpRecentsAnimation");
        }
    }

    public static void logOnInputEventUserLocked(int displayId) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "TIS.onInputEvent(displayId=%d): Cannot process input event: user is locked",
                displayId));
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP,
                    "TIS.onInputEvent(displayId=%d): Cannot process input event: user is locked",
                    displayId);
        }
    }

    public static void logOnInputIgnoringFollowingEvents(int displayId) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                        "TIS.onMotionEvent(displayId=%d): A new gesture has been started, "
                                + "but a previously-requested recents animation hasn't started. "
                                + "Ignoring all following motion events.", displayId),
                RECENTS_ANIMATION_START_PENDING);
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP,
                    "TIS.onMotionEvent(displayId=%d): A new gesture has been started, "
                            + "but a previously-requested recents animation hasn't started. "
                            + "Ignoring all following motion events.", displayId);
        }
    }

    public static void logOnInputEventThreeButtonNav(int displayId) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "TIS.onInputEvent(displayId=%d): Cannot process input event: "
                        + "using 3-button nav and event is not a trackpad event", displayId));
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP,
                    "TIS.onInputEvent(displayId=%d): Cannot process input event: "
                            + "using 3-button nav and event is not a trackpad event", displayId);
        }
    }

    public static void logPreloadRecentsAnimation() {
        ActiveGestureLog.INSTANCE.addLog("preloadRecentsAnimation");
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "preloadRecentsAnimation");
        }
    }

    public static void logRecentTasksMissing() {
        ActiveGestureLog.INSTANCE.addLog("Null mRecentTasks", RECENT_TASKS_MISSING);
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "Null mRecentTasks");
        }
    }

    public static void logFinishRecentsAnimationCallback() {
        ActiveGestureLog.INSTANCE.addLog("finishRecentsAnimation-callback");
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "finishRecentsAnimation-callback");
        }
    }

    public static void logOnScrollerAnimationAborted() {
        ActiveGestureLog.INSTANCE.addLog("scroller animation aborted",
                ActiveGestureErrorDetector.GestureEvent.SCROLLER_ANIMATION_ABORTED);
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "scroller animation aborted");
        }
    }

    public static void logInputConsumerBecameActive(@NonNull String consumerName) {
        ActiveGestureLog.INSTANCE.addLog(
                new ActiveGestureLog.CompoundString("%s became active", consumerName));
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "%s became active", consumerName);
        }
    }

    public static void logTaskLaunchFailed(int launchedTaskId) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "Launch failed, task (id=%d) finished mid transition", launchedTaskId));
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "Launch failed, task (id=%d) finished mid transition",
                    launchedTaskId);
        }
    }

    public static void logOnPageEndTransition(int nextPageIndex) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "onPageEndTransition: current page index updated: %d", nextPageIndex));
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "onPageEndTransition: current page index updated: %d",
                    nextPageIndex);
        }
    }

    public static void logQuickSwitchFromHomeFallback(int taskIndex) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "Quick switch from home fallback case: The TaskView at index %d is missing.",
                taskIndex), QUICK_SWITCH_FROM_HOME_FALLBACK);
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP,
                    "Quick switch from home fallback case: The TaskView at index %d is missing.",
                    taskIndex);
        }
    }

    public static void logQuickSwitchFromHomeFailed(int taskIndex) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "Quick switch from home failed: TaskViews at indices %d and 0 are missing.",
                taskIndex), QUICK_SWITCH_FROM_HOME_FAILED);
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP,
                    "Quick switch from home failed: TaskViews at indices %d and 0 are missing.",
                    taskIndex);
        }
    }

    public static void logFinishRecentsAnimation(boolean toHome,
            @NonNull ActiveGestureLog.CompoundString reason) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                        "RecentsAnimationController.finishRecentsAnimation: toHome=%b, reason=",
                        toHome).append(reason),
                /* gestureEvent= */ FINISH_RECENTS_ANIMATION);
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP,
                    "RecentsAnimationController.finishRecentsAnimation: toHome=%b, reason=%s",
                    toHome, reason.toString());
        }
    }

    public static void logSetEndTarget(@NonNull String target) {
        ActiveGestureLog.INSTANCE.addLog(
                new ActiveGestureLog.CompoundString("setEndTarget %s", target), /* gestureEvent= */
                SET_END_TARGET);
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "setEndTarget %s", target);
        }
    }

    public static void logStartHomeIntent(@NonNull String reason) {
        ActiveGestureLog.INSTANCE.addLog(
                new ActiveGestureLog.CompoundString("OverviewComponentObserver.startHomeIntent: %s",
                        reason));
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "OverviewComponentObserver.startHomeIntent: %s", reason);
        }
    }

    public static void logRunningTaskPackage(@NonNull String packageName) {
        ActiveGestureLog.INSTANCE.addLog(
                new ActiveGestureLog.CompoundString("Current running task package name=%s",
                        packageName));
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "Current running task package name=%s", packageName);
        }
    }

    public static void logSysuiStateFlags(@NonNull String stateFlags) {
        ActiveGestureLog.INSTANCE.addLog(
                new ActiveGestureLog.CompoundString("Current SystemUi state flags=%s", stateFlags));
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "Current SystemUi state flags=%s", stateFlags);
        }
    }

    public static void logSetInputConsumer(@NonNull String consumerName, @NonNull String reason) {
        ActiveGestureLog.INSTANCE.addLog(
                new ActiveGestureLog.CompoundString("setInputConsumer: %s. reason(s):%s",
                        consumerName, reason));
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "setInputConsumer: %s. reason(s):%s", consumerName, reason);
        }
    }

    public static void logUpdateGestureStateRunningTask(@NonNull String otherTaskPackage,
            @NonNull String runningTaskPackage) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "Changing active task to %s because the previous task running on top of this "
                        + "one (%s) was excluded from recents", otherTaskPackage,
                runningTaskPackage));
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP,
                    "Changing active task to %s because the previous task running on top of this "
                            + "one (%s) was excluded from recents", otherTaskPackage,
                    runningTaskPackage);
        }
    }

    public static void logOnInputEventActionUp(int x, int y, int action,
            @NonNull String classification, int displayId) {
        String actionString = MotionEvent.actionToString(action);
        ActiveGestureLog.INSTANCE.addLog(
                new ActiveGestureLog.CompoundString("onMotionEvent(%d, %d): %s, %s, displayId=%d",
                        x, y, actionString, classification, displayId),
                /* gestureEvent= */ action == ACTION_DOWN ? MOTION_DOWN : MOTION_UP);
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "onMotionEvent(%d, %d): %s, %s, displayId=%d", x, y,
                    actionString, classification, displayId);
        }
    }

    public static void logOnInputEventActionMove(@NonNull String action,
            @NonNull String classification, int pointerCount, int displayId) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "onMotionEvent: %s, %s, pointerCount: %d, displayId=%d", action, classification,
                pointerCount, displayId), MOTION_MOVE);
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "onMotionEvent: %s, %s, pointerCount: %d, displayId=%d",
                    action, classification, pointerCount, displayId);
        }
    }

    public static void logOnInputEventGenericAction(@NonNull String action,
            @NonNull String classification, int displayId) {
        ActiveGestureLog.INSTANCE.addLog(
                new ActiveGestureLog.CompoundString("onMotionEvent: %s, %s, displayId=%d", action,
                        classification, displayId));
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "onMotionEvent: %s, %s, displayId=%d", action,
                    classification, displayId);
        }
    }

    public static void logOnInputEventNavModeSwitched(int displayId, @NonNull String startNavMode,
            @NonNull String currentNavMode) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "TIS.onInputEvent(displayId=%d): Navigation mode switched mid-gesture (%s"
                        + " -> %s); " + "cancelling gesture.", displayId, startNavMode,
                currentNavMode), NAVIGATION_MODE_SWITCHED);
        if (!isProtoLogInitialized()) return;
        ProtoLog.d(ACTIVE_GESTURE_LOG,
                "TIS.onInputEvent(displayId=%d): Navigation mode switched mid-gesture (%s -> %s); "
                        + "cancelling gesture.", displayId, startNavMode, currentNavMode);
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP,
                    "TIS.onInputEvent(displayId=%d): Navigation mode switched mid-gesture (%s -> "
                            + "%s); " + "cancelling gesture.", displayId, startNavMode,
                    currentNavMode);
        }
    }

    public static void logUnknownInputEvent(int displayId, @NonNull String event) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "TIS.onInputEvent(displayId=%d): Cannot process input event: "
                        + "received unknown event %s", displayId, event));
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP,
                    "TIS.onInputEvent(displayId=%d): Cannot process input event: "
                            + "received unknown event %s", displayId, event);
        }
    }

    public static void logFinishRunningRecentsAnimation(boolean toHome,
            @NonNull ActiveGestureLog.CompoundString reason) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "TaskAnimationManager.finishRunningRecentsAnimation: toHome=%b, reason=",
                toHome).append(reason));
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP,
                    "TaskAnimationManager.finishRunningRecentsAnimation: toHome=%b, reason=%s",
                    toHome, reason.toString());
        }
    }

    public static void logOnRecentsAnimationStartCancelled() {
        ActiveGestureLog.INSTANCE.addLog("RecentsAnimationCallbacks.onAnimationStart (canceled): 0",
                /* gestureEvent= */ ON_START_RECENTS_ANIMATION);
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "RecentsAnimationCallbacks.onAnimationStart (canceled): 0");
        }
    }

    public static void logOnRecentsAnimationStart(int appCount) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                        "RecentsAnimationCallbacks.onAnimationStart: %d", appCount),
                /* gestureEvent= */ ON_START_RECENTS_ANIMATION);
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "RecentsAnimationCallbacks.onAnimationStart: %d", appCount);
        }
    }

    public static void logStartRecentsAnimationCallback(@NonNull String callback) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "TaskAnimationManager.startRecentsAnimation(%s): "
                        + "Setting mRecentsAnimationStartPending = false", callback));
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "TaskAnimationManager.startRecentsAnimation(%s): "
                    + "Setting mRecentsAnimationStartPending = false", callback);
        }
    }

    public static void logSettingRecentsAnimationStartPending(boolean value) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "TaskAnimationManager.startRecentsAnimation: "
                        + "Setting mRecentsAnimationStartPending = %b", value));
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "TaskAnimationManager.startRecentsAnimation: "
                    + "Setting mRecentsAnimationStartPending = %b", value);
        }
    }

    public static void logLaunchingSideTask(int taskId) {
        ActiveGestureLog.INSTANCE.addLog(
                new ActiveGestureLog.CompoundString("Launching side task id=%d", taskId));
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "Launching side task id=%d", taskId);
        }
    }

    public static void logOnInputEventActionDown(int displayId,
            @NonNull ActiveGestureLog.CompoundString reason) {
        ActiveGestureLog.INSTANCE.addLog(
                new ActiveGestureLog.CompoundString("TIS.onMotionEvent(displayId=%d): ",
                        displayId).append(reason));
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "TIS.onMotionEvent(displayId=%d): %s", displayId,
                    reason.toString());
        }
    }

    public static void logStartNewTask(@NonNull ActiveGestureLog.CompoundString tasks) {
        ActiveGestureLog.INSTANCE.addLog(
                new ActiveGestureLog.CompoundString("Launching task: ").append(tasks));
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "TIS.onMotionEvent: %s", tasks.toString());
        }
    }

    public static void logMotionPauseDetectorEvent(@NonNull ActiveGestureLog.CompoundString event) {
        ActiveGestureLog.INSTANCE.addLog(
                new ActiveGestureLog.CompoundString("MotionPauseDetector: ").append(event));
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "MotionPauseDetector: %s", event.toString());
        }
    }

    public static void logOnInvalidTasksAppeared(@NonNull ActiveGestureLog.CompoundString reason) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "AbsSwipeHandler.onTasksAppeared check failed, "
                        + "attempting to forcefully finish recents animation. Reason: ").append(
                reason));
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "AbsSwipeHandler.onTasksAppeared check failed, "
                            + "attempting to forcefully finish recents animation. Reason: %s",
                    reason.toString());
        }
    }

    /**
     * This is for special cases where the string is purely dynamic and therefore has no format that
     * can be extracted. Do not use in any other case.
     */
    public static void logDynamicString(@NonNull String string,
            @Nullable ActiveGestureErrorDetector.GestureEvent gestureEvent) {
        ActiveGestureLog.INSTANCE.addLog(string, gestureEvent);
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "%s", string);
        }
    }

    public static void logOnSettledOnEndTarget(@NonNull String endTarget) {
        ActiveGestureLog.INSTANCE.addLog(
                new ActiveGestureLog.CompoundString("onSettledOnEndTarget %s", endTarget),
                /* gestureEvent= */ ON_SETTLED_ON_END_TARGET);
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "onSettledOnEndTarget %s", endTarget);
        }
    }

    public static void logOnCalculateEndTarget(float velocityX, float velocityY, double angle) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                        "calculateEndTarget: velocities=(x=%fdp/ms, y=%fdp/ms), angle=%f",
                        velocityX,
                        velocityY, angle),
                velocityX == 0 && velocityY == 0 ? INVALID_VELOCITY_ON_SWIPE_UP : null);
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP,
                    "calculateEndTarget: velocities=(x=%fdp/ms, y=%fdp/ms), angle=%f", velocityX,
                    velocityY, angle);
        }
    }

    public static void logCreateTouchRegionForDisplay(int displayRotation,
            @NonNull Point displaySize, @NonNull RectF swipeRegion, @NonNull RectF ohmRegion,
            int gesturalHeight, int largerGesturalHeight, @NonNull String reason) {
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "OrientationTouchTransformer.createRegionForDisplay: "
                            + "dispRot=%d, dispSize=%s, swipeRegion=%s, ohmRegion=%s, "
                            + "gesturalHeight=%d, largerGesturalHeight=%d, reason=%s",
                    displayRotation,
                    displaySize.flattenToString(), swipeRegion.toShortString(),
                    ohmRegion.toShortString(), gesturalHeight, largerGesturalHeight, reason);
        }
    }

    public static void logOnTaskAnimationManagerNotAvailable(int displayId) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "TaskAnimationManager not available for displayId=%d", displayId));
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "TaskAnimationManager not available for displayId=%d",
                    displayId);
        }
    }

    public static void logOnAbsSwipeUpHandlerNotAvailable(int displayId) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "AbsSwipeUpHandler not available for displayId=%d", displayId));
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "AbsSwipeUpHandler not available for displayId=%d",
                    displayId);
        }
    }

    public static void logGestureStartSwipeHandler(@NonNull String interactionHandler) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "OtherActivityInputConsumer.startTouchTrackingForWindowAnimation: "
                        + "interactionHandler=%s", interactionHandler));
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP,
                    "OtherActivityInputConsumer.startTouchTrackingForWindowAnimation: "
                            + "interactionHandler=%s", interactionHandler);
        }
    }

    public static void logQueuingForceFinishRecentsAnimation() {
        ActiveGestureLog.INSTANCE.addLog("Launcher destroyed while mRecentsAnimationStartPending =="
                        + " true, queuing a callback to clean the pending animation up on start",
                /* gestureEvent= */ ON_START_RECENTS_ANIMATION);
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "Launcher destroyed while mRecentsAnimationStartPending =="
                    + " true, queuing a callback to clean the pending animation up on start");
        }
    }

    public static void logRecentsAnimationStartTimedOut() {
        ActiveGestureLog.INSTANCE.addLog("Recents animation start has timed out; forcefully "
                        + "cleaning up the recents animation.",
                /* gestureEvent= */ RECENTS_ANIMATION_START_TIMEOUT);
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "Recents animation start has timed out; forcefully "
                    + "cleaning up the recents animation.");
        }
    }

    public static void logHandleUnfinishedTaskLaunch(@NonNull String endTarget,
            @NonNull ActiveGestureLog.CompoundString reason) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                        "Recents animation interrupted unexpectedly during gesture to %s, reason=",
                        endTarget).append(reason),
                /* gestureEvent= */ UNFINISHED_TASK_LAUNCH);
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP,
                    "Recents animation interrupted unexpectedly during gesture to %s, reason=%s",
                    endTarget, reason.toString());
        }
    }

    public static void logCalculateEndTargetResultAndReason(@NonNull String endTarget,
            @NonNull ActiveGestureLog.CompoundString reason) {
        ActiveGestureLog.INSTANCE.addLog(
                new ActiveGestureLog.CompoundString("calculateEndTarget: endTarget=%s, reason=",
                        endTarget).append(reason));
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "calculateEndTarget: endTarget=%s, reason=%s", endTarget,
                    reason.toString());
        }
    }
}
