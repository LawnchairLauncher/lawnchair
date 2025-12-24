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

package com.android.launcher3.util;

import static com.android.quickstep.util.QuickstepProtoLogGroup.LAUNCHER_STATE_MANAGER;
import static com.android.quickstep.util.QuickstepProtoLogGroup.isProtoLogInitialized;

import android.util.Log;
import android.window.DesktopModeFlags.DesktopModeFlag;

import androidx.annotation.NonNull;

import com.android.internal.protolog.ProtoLog;
import com.android.launcher3.Flags;
import com.android.quickstep.util.QuickstepProtoLogGroup;

/**
 * Proxy class used for StateManager ProtoLog support.
 */
public class StateManagerProtoLogProxy {
    private static final boolean IS_STATE_MANAGER_PROTOLOG_ENABLED = new DesktopModeFlag(
            Flags::enableStateManagerProtoLog, true).isTrue();

    private static final QuickstepProtoLogGroup PROTO_LOG_GROUP = LAUNCHER_STATE_MANAGER;

    private static boolean willProtoLog() {
        return IS_STATE_MANAGER_PROTOLOG_ENABLED && isProtoLogInitialized();
    }

    private static void logToLogcatIfNeeded(String message, Object... args) {
        if (!willProtoLog() || !PROTO_LOG_GROUP.isLogToLogcat()) {
            Log.d(PROTO_LOG_GROUP.getTag(), String.format(message, args));
        }
    }

    public static void logGoToState(@NonNull Object fromState, @NonNull Object toState,
            @NonNull String trace) {
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP,
                    "StateManager.goToState: fromState: %s, toState: %s, partial trace:\n%s",
                    fromState, toState, trace);
        }
        logToLogcatIfNeeded(
                "StateManager.goToState: fromState: %s, toState: %s, partial trace:\n%s",
                fromState, toState, trace);
    }

    public static void logCreateAtomicAnimation(@NonNull Object fromState, @NonNull Object toState,
            @NonNull String trace) {
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "StateManager.createAtomicAnimation: "
                    + "fromState: %s, toState: %s, partial trace:\n%s", fromState, toState, trace);
        }
        logToLogcatIfNeeded("StateManager.createAtomicAnimation: "
                + "fromState: %s, toState: %s, partial trace:\n%s", fromState, toState, trace);
    }

    public static void logOnStateTransitionStart(@NonNull Object state) {
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "StateManager.onStateTransitionStart: state: %s", state);
        }
        logToLogcatIfNeeded("StateManager.onStateTransitionStart: state: %s", state);
    }

    public static void logOnStateTransitionEnd(@NonNull Object state) {
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "StateManager.onStateTransitionEnd: state: %s", state);
        }
        logToLogcatIfNeeded("StateManager.onStateTransitionEnd: state: %s", state);
    }

    public static void logOnRepeatStateSetAborted(@NonNull Object state) {
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "StateManager.onRepeatStateSetAborted: state: %s", state);
        }
        logToLogcatIfNeeded("StateManager.onRepeatStateSetAborted: state: %s", state);
    }

    public static void logCancelAnimation(boolean animationOngoing, @NonNull String trace) {
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP,
                    "StateManager.cancelAnimation: animation ongoing: %b, partial trace:\n%s",
                    animationOngoing, trace);
        }
        logToLogcatIfNeeded(
                "StateManager.cancelAnimation: animation ongoing: %b, partial trace:\n%s",
                animationOngoing, trace);
    }
}
