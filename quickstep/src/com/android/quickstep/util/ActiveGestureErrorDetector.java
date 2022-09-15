/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.util.ArraySet;

import androidx.annotation.NonNull;

import java.io.PrintWriter;
import java.util.List;
import java.util.Set;

/**
 * Utility class for tracking gesture navigation events as they happen, then detecting and reporting
 * known issues at log dump time.
 */
public class ActiveGestureErrorDetector {

    /**
     * Enums associated to gesture navigation events.
     */
    public enum GestureEvent {
        MOTION_DOWN, MOTION_UP, SET_END_TARGET, SET_END_TARGET_HOME, SET_END_TARGET_LAST_TASK,
        SET_END_TARGET_NEW_TASK, ON_SETTLED_ON_END_TARGET, START_RECENTS_ANIMATION,
        FINISH_RECENTS_ANIMATION, CANCEL_RECENTS_ANIMATION, SET_ON_PAGE_TRANSITION_END_CALLBACK,
        CANCEL_CURRENT_ANIMATION, CLEANUP_SCREENSHOT, SCROLLER_ANIMATION_ABORTED, TASK_APPEARED,

        /**
         * These GestureEvents are specifically associated to state flags that get set in
         * {@link com.android.quickstep.MultiStateCallback}. If a state flag needs to be tracked
         * for error detection, an enum should be added here and that state flag-enum pair should
         * be added to the state flag's container class' {@code getTrackedEventForState} method.
         */
        STATE_GESTURE_STARTED, STATE_GESTURE_COMPLETED, STATE_GESTURE_CANCELLED,
        STATE_END_TARGET_ANIMATION_FINISHED, STATE_RECENTS_SCROLLING_FINISHED,
        STATE_CAPTURE_SCREENSHOT, STATE_SCREENSHOT_CAPTURED, STATE_HANDLER_INVALIDATED,
        STATE_RECENTS_ANIMATION_CANCELED, STATE_LAUNCHER_DRAWN(true, false);

        public final boolean mLogEvent;
        public final boolean mTrackEvent;

        GestureEvent() {
            this(false, true);
        }

        GestureEvent(boolean logEvent, boolean trackEvent) {
            mLogEvent = logEvent;
            mTrackEvent = trackEvent;
        }
    }

    private ActiveGestureErrorDetector() {}

    protected static void analyseAndDump(
            @NonNull String prefix,
            @NonNull PrintWriter writer,
            List<ActiveGestureLog.EventLog> eventLogs) {
        writer.println(prefix + "ActiveGestureErrorDetector:");
        for (int i = 0; i < eventLogs.size(); i++) {
            ActiveGestureLog.EventLog eventLog = eventLogs.get(i);
            if (eventLog == null) {
                continue;
            }
            int gestureId = eventLog.logId;
            writer.println(prefix + "\tError messages for gesture ID: " + gestureId);

            boolean errorDetected = false;
            // Use a Set since the order is inherently checked in the loop.
            final Set<GestureEvent> encounteredEvents = new ArraySet<>();
            // Set flags and check order of operations.
            for (ActiveGestureLog.EventEntry eventEntry : eventLog.eventEntries) {
                GestureEvent gestureEvent = eventEntry.getGestureEvent();
                if (gestureEvent == null) {
                    continue;
                }
                encounteredEvents.add(gestureEvent);
                switch (gestureEvent) {
                    case MOTION_UP:
                        errorDetected |= printErrorIfTrue(
                                !encounteredEvents.contains(GestureEvent.MOTION_DOWN),
                                /* errorMessage= */ prefix + "\t\tMotion up detected before/without"
                                        + " motion down.",
                                writer);
                        break;
                    case ON_SETTLED_ON_END_TARGET:
                        errorDetected |= printErrorIfTrue(
                                !encounteredEvents.contains(GestureEvent.SET_END_TARGET),
                                /* errorMessage= */ prefix + "\t\tonSettledOnEndTarget called "
                                        + "before/without setEndTarget.",
                                writer);
                        break;
                    case FINISH_RECENTS_ANIMATION:
                        errorDetected |= printErrorIfTrue(
                                !encounteredEvents.contains(GestureEvent.START_RECENTS_ANIMATION),
                                /* errorMessage= */ prefix + "\t\tfinishRecentsAnimation called "
                                        + "before/without startRecentsAnimation.",
                                writer);
                        break;
                    case CANCEL_RECENTS_ANIMATION:
                        errorDetected |= printErrorIfTrue(
                                !encounteredEvents.contains(GestureEvent.START_RECENTS_ANIMATION),
                                /* errorMessage= */ prefix + "\t\tcancelRecentsAnimation called "
                                        + "before/without startRecentsAnimation.",
                                writer);
                        break;
                    case CLEANUP_SCREENSHOT:
                        errorDetected |= printErrorIfTrue(
                                !encounteredEvents.contains(GestureEvent.STATE_SCREENSHOT_CAPTURED),
                                /* errorMessage= */ prefix + "\t\trecents activity screenshot was "
                                        + "cleaned up before/without STATE_SCREENSHOT_CAPTURED "
                                        + "being set.",
                                writer);
                        break;
                    case SCROLLER_ANIMATION_ABORTED:
                        errorDetected |= printErrorIfTrue(
                                encounteredEvents.contains(GestureEvent.SET_END_TARGET_HOME)
                                        && !encounteredEvents.contains(
                                                GestureEvent.ON_SETTLED_ON_END_TARGET),
                                /* errorMessage= */ prefix + "\t\trecents view scroller animation "
                                        + "aborted after setting end target HOME, but before"
                                        + " settling on end target.",
                                writer);
                        break;
                    case TASK_APPEARED:
                        errorDetected |= printErrorIfTrue(
                                !encounteredEvents.contains(GestureEvent.SET_END_TARGET_LAST_TASK)
                                        && !encounteredEvents.contains(
                                        GestureEvent.SET_END_TARGET_NEW_TASK),
                                /* errorMessage= */ prefix + "\t\tonTasksAppeared called "
                                        + "before/without setting end target to last or new task",
                                writer);
                        break;
                    case STATE_GESTURE_COMPLETED:
                        errorDetected |= printErrorIfTrue(
                                !encounteredEvents.contains(GestureEvent.MOTION_UP),
                                /* errorMessage= */ prefix + "\t\tSTATE_GESTURE_COMPLETED set "
                                        + "before/without motion up.",
                                writer);
                        errorDetected |= printErrorIfTrue(
                                !encounteredEvents.contains(GestureEvent.STATE_GESTURE_STARTED),
                                /* errorMessage= */ prefix + "\t\tSTATE_GESTURE_COMPLETED set "
                                        + "before/without STATE_GESTURE_STARTED.",
                                writer);
                        break;
                    case STATE_GESTURE_CANCELLED:
                        errorDetected |= printErrorIfTrue(
                                !encounteredEvents.contains(GestureEvent.MOTION_UP),
                                /* errorMessage= */ prefix + "\t\tSTATE_GESTURE_CANCELLED set "
                                        + "before/without motion up.",
                                writer);
                        errorDetected |= printErrorIfTrue(
                                !encounteredEvents.contains(GestureEvent.STATE_GESTURE_STARTED),
                                /* errorMessage= */ prefix + "\t\tSTATE_GESTURE_CANCELLED set "
                                        + "before/without STATE_GESTURE_STARTED.",
                                writer);
                        break;
                    case STATE_SCREENSHOT_CAPTURED:
                        errorDetected |= printErrorIfTrue(
                                !encounteredEvents.contains(GestureEvent.STATE_CAPTURE_SCREENSHOT),
                                /* errorMessage= */ prefix + "\t\tSTATE_SCREENSHOT_CAPTURED set "
                                        + "before/without STATE_CAPTURE_SCREENSHOT.",
                                writer);
                        break;
                    case STATE_RECENTS_SCROLLING_FINISHED:
                        errorDetected |= printErrorIfTrue(
                                !encounteredEvents.contains(
                                        GestureEvent.SET_ON_PAGE_TRANSITION_END_CALLBACK),
                                /* errorMessage= */ prefix + "\t\tSTATE_RECENTS_SCROLLING_FINISHED "
                                        + "set before/without calling "
                                        + "setOnPageTransitionEndCallback.",
                                writer);
                        break;
                    case STATE_RECENTS_ANIMATION_CANCELED:
                        errorDetected |= printErrorIfTrue(
                                !encounteredEvents.contains(
                                        GestureEvent.START_RECENTS_ANIMATION),
                                /* errorMessage= */ prefix + "\t\tSTATE_RECENTS_ANIMATION_CANCELED "
                                        + "set before/without startRecentsAnimation.",
                                writer);
                        break;
                    case MOTION_DOWN:
                    case SET_END_TARGET:
                    case SET_END_TARGET_HOME:
                    case START_RECENTS_ANIMATION:
                    case SET_ON_PAGE_TRANSITION_END_CALLBACK:
                    case CANCEL_CURRENT_ANIMATION:
                    case STATE_GESTURE_STARTED:
                    case STATE_END_TARGET_ANIMATION_FINISHED:
                    case STATE_CAPTURE_SCREENSHOT:
                    case STATE_HANDLER_INVALIDATED:
                    case STATE_LAUNCHER_DRAWN:
                    default:
                        // No-Op
                }
            }

            // Check that all required events were found.
            errorDetected |= printErrorIfTrue(
                    !encounteredEvents.contains(GestureEvent.MOTION_DOWN),
                    /* errorMessage= */ prefix + "\t\tMotion down never detected.",
                    writer);
            errorDetected |= printErrorIfTrue(
                    !encounteredEvents.contains(GestureEvent.MOTION_UP),
                    /* errorMessage= */ prefix + "\t\tMotion up never detected.",
                    writer);

            errorDetected |= printErrorIfTrue(
                    /* condition= */ encounteredEvents.contains(GestureEvent.SET_END_TARGET)
                            && !encounteredEvents.contains(GestureEvent.ON_SETTLED_ON_END_TARGET),
                    /* errorMessage= */ prefix + "\t\tsetEndTarget was called, but "
                            + "onSettledOnEndTarget wasn't.",
                    writer);
            errorDetected |= printErrorIfTrue(
                    /* condition= */ encounteredEvents.contains(GestureEvent.SET_END_TARGET)
                            && !encounteredEvents.contains(
                                    GestureEvent.STATE_END_TARGET_ANIMATION_FINISHED),
                    /* errorMessage= */ prefix + "\t\tsetEndTarget was called, but "
                            + "STATE_END_TARGET_ANIMATION_FINISHED was never set.",
                    writer);
            errorDetected |= printErrorIfTrue(
                    /* condition= */ encounteredEvents.contains(GestureEvent.SET_END_TARGET)
                            && !encounteredEvents.contains(
                                    GestureEvent.STATE_RECENTS_SCROLLING_FINISHED),
                    /* errorMessage= */ prefix + "\t\tsetEndTarget was called, but "
                            + "STATE_RECENTS_SCROLLING_FINISHED was never set.",
                    writer);
            errorDetected |= printErrorIfTrue(
                    /* condition= */ encounteredEvents.contains(
                            GestureEvent.STATE_END_TARGET_ANIMATION_FINISHED)
                            && encounteredEvents.contains(
                                    GestureEvent.STATE_RECENTS_SCROLLING_FINISHED)
                            && !encounteredEvents.contains(GestureEvent.ON_SETTLED_ON_END_TARGET),
                    /* errorMessage= */ prefix + "\t\tSTATE_END_TARGET_ANIMATION_FINISHED and "
                            + "STATE_RECENTS_SCROLLING_FINISHED were set, but onSettledOnEndTarget "
                            + "wasn't called.",
                    writer);

            errorDetected |= printErrorIfTrue(
                    /* condition= */ encounteredEvents.contains(
                            GestureEvent.START_RECENTS_ANIMATION)
                            && !encounteredEvents.contains(GestureEvent.FINISH_RECENTS_ANIMATION)
                            && !encounteredEvents.contains(GestureEvent.CANCEL_RECENTS_ANIMATION),
                    /* errorMessage= */ prefix + "\t\tstartRecentsAnimation was called, but "
                            + "finishRecentsAnimation and cancelRecentsAnimation weren't.",
                    writer);

            errorDetected |= printErrorIfTrue(
                    /* condition= */ encounteredEvents.contains(GestureEvent.STATE_GESTURE_STARTED)
                            && !encounteredEvents.contains(GestureEvent.STATE_GESTURE_COMPLETED)
                            && !encounteredEvents.contains(GestureEvent.STATE_GESTURE_CANCELLED),
                    /* errorMessage= */ prefix + "\t\tSTATE_GESTURE_STARTED was set, but "
                            + "STATE_GESTURE_COMPLETED and STATE_GESTURE_CANCELLED weren't.",
                    writer);

            errorDetected |= printErrorIfTrue(
                    /* condition= */ encounteredEvents.contains(
                            GestureEvent.STATE_CAPTURE_SCREENSHOT)
                            && !encounteredEvents.contains(GestureEvent.STATE_SCREENSHOT_CAPTURED),
                    /* errorMessage= */ prefix + "\t\tSTATE_CAPTURE_SCREENSHOT was set, but "
                            + "STATE_SCREENSHOT_CAPTURED wasn't.",
                    writer);

            errorDetected |= printErrorIfTrue(
                    /* condition= */ encounteredEvents.contains(
                            GestureEvent.SET_ON_PAGE_TRANSITION_END_CALLBACK)
                            && !encounteredEvents.contains(
                                    GestureEvent.STATE_RECENTS_SCROLLING_FINISHED),
                    /* errorMessage= */ prefix + "\t\tsetOnPageTransitionEndCallback called, but "
                            + "STATE_RECENTS_SCROLLING_FINISHED wasn't set.",
                    writer);

            errorDetected |= printErrorIfTrue(
                    /* condition= */ !encounteredEvents.contains(
                            GestureEvent.CANCEL_CURRENT_ANIMATION)
                            && !encounteredEvents.contains(GestureEvent.STATE_HANDLER_INVALIDATED),
                    /* errorMessage= */ prefix + "\t\tAbsSwipeUpHandler.cancelCurrentAnimation "
                            + "wasn't called and STATE_HANDLER_INVALIDATED wasn't set.",
                    writer);

            errorDetected |= printErrorIfTrue(
                    /* condition= */ encounteredEvents.contains(
                            GestureEvent.STATE_RECENTS_ANIMATION_CANCELED)
                            && !encounteredEvents.contains(GestureEvent.CLEANUP_SCREENSHOT),
                    /* errorMessage= */ prefix + "\t\tSTATE_RECENTS_ANIMATION_CANCELED was set but "
                            + "the task screenshot wasn't cleaned up.",
                    writer);

            errorDetected |= printErrorIfTrue(
                    /* condition= */ encounteredEvents.contains(
                            GestureEvent.SET_END_TARGET_LAST_TASK)
                            && !encounteredEvents.contains(GestureEvent.TASK_APPEARED),
                    /* errorMessage= */ prefix + "\t\tend target set to last task, but "
                            + "onTaskAppeared wasn't called.",
                    writer);
            errorDetected |= printErrorIfTrue(
                    /* condition= */ encounteredEvents.contains(
                            GestureEvent.SET_END_TARGET_NEW_TASK)
                            && !encounteredEvents.contains(GestureEvent.TASK_APPEARED),
                    /* errorMessage= */ prefix + "\t\tend target set to new task, but "
                            + "onTaskAppeared wasn't called.",
                    writer);

            if (!errorDetected) {
                writer.println(prefix + "\t\tNo errors detected.");
            }
        }
    }

    private static boolean printErrorIfTrue(
            boolean condition, String errorMessage, PrintWriter writer) {
        if (!condition) {
            return false;
        }
        writer.println(errorMessage);
        return true;
    }
}
