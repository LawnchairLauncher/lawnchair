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

    public enum GestureEvent {
        MOTION_DOWN, MOTION_UP, SET_END_TARGET, ON_SETTLED_ON_END_TARGET, START_RECENTS_ANIMATION,
        FINISH_RECENTS_ANIMATION, CANCEL_RECENTS_ANIMATION, STATE_GESTURE_STARTED,
        STATE_GESTURE_COMPLETED, STATE_GESTURE_CANCELLED, STATE_END_TARGET_ANIMATION_FINISHED,
        STATE_RECENTS_SCROLLING_FINISHED
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
                    case MOTION_DOWN:
                    case SET_END_TARGET:
                    case START_RECENTS_ANIMATION:
                    case STATE_GESTURE_STARTED:
                    case STATE_END_TARGET_ANIMATION_FINISHED:
                    case STATE_RECENTS_SCROLLING_FINISHED:
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
