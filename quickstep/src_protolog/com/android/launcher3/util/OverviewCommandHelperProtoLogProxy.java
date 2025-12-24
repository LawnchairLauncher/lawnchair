/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static com.android.quickstep.util.QuickstepProtoLogGroup.OVERVIEW_COMMAND_HELPER;
import static com.android.quickstep.util.QuickstepProtoLogGroup.isProtoLogInitialized;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.protolog.ProtoLog;
import com.android.quickstep.util.QuickstepProtoLogGroup;

/**
 * Proxy class used for OverviewCommandHelper ProtoLog support. (e.g. for 3 button nav)
 */
public class OverviewCommandHelperProtoLogProxy {
    private static final QuickstepProtoLogGroup PROTO_LOG_GROUP = OVERVIEW_COMMAND_HELPER;

    private static boolean willProtoLog() {
        return isProtoLogInitialized();
    }

    private static void logToLogcatIfNeeded(String message, Object... args) {
        if (!willProtoLog() || !PROTO_LOG_GROUP.isLogToLogcat()) {
            Log.d(PROTO_LOG_GROUP.getTag(), String.format(message, args));
        }
    }

    private static void logEToLogcatIfNeeded(String message, Object... args) {
        if (!willProtoLog() || !PROTO_LOG_GROUP.isLogToLogcat()) {
            Log.e(PROTO_LOG_GROUP.getTag(), String.format(message, args));
        }
    }

    public static void logCommandQueueFull(@NonNull Object type, @NonNull Object commandQueue) {
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "command not added: %s - queue is full (%s).", type,
                    commandQueue);
        }
        logToLogcatIfNeeded("command not added: %s - queue is full (%s).", type, commandQueue);
    }

    public static void logCommandAdded(@NonNull Object command) {
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "command added: %s", command);
        }
        logToLogcatIfNeeded("command added: %s", command);
    }

    public static void logCommandExecuted(@NonNull Object command, int queueSize) {
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "execute: %s - queue size: %d", command, queueSize);
        }
        logToLogcatIfNeeded("execute: %s - queue size: %d", command, queueSize);
    }

    public static void logCommandNotExecuted(@NonNull Object command, int queueSize) {
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "command not executed: %s - queue size: %d", command,
                    queueSize);
        }
        logToLogcatIfNeeded("command not executed: %s - queue size: %d", command, queueSize);
    }

    public static void logClearPendingCommands(@NonNull Object commandQueue) {
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "clearing pending commands: %s", commandQueue);
        }
        logToLogcatIfNeeded("clearing pending commands: %s", commandQueue);
    }

    public static void logNoPendingCommands() {
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "no pending commands to be executed.");
        }
        logToLogcatIfNeeded("no pending commands to be executed.");
    }

    public static void logExecutingCommand(@NonNull Object command) {
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "executing command: %s", command);
        }
        logToLogcatIfNeeded("executing command: %s", command);
    }

    public static void logExecutingCommand(@NonNull Object command, @Nullable Object recentsView) {
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "executing command: %s - visibleRecentsView: %s", command,
                    recentsView);
        }
        logToLogcatIfNeeded("executing command: %s - visibleRecentsView: %s", command, recentsView);
    }

    public static void logExecutedCommandWithResult(@NonNull Object command, boolean isCompleted) {
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "command executed: %s with result: %b", command,
                    isCompleted);
        }
        logToLogcatIfNeeded("command executed: %s with result: %b", command, isCompleted);
    }

    public static void logWaitingForCommandCallback(@NonNull Object command) {
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "waiting for command callback: %s", command);
        }
        logToLogcatIfNeeded("waiting for command callback: %s", command);
    }

    public static void logLaunchingTaskCallback(@NonNull Object command) {
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "launching task callback: %s", command);
        }
        logToLogcatIfNeeded("launching task callback: %s", command);
    }

    public static void logLaunchingTaskWaitingForCallback(@NonNull Object command) {
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "launching task - waiting for callback: %s", command);
        }
        logToLogcatIfNeeded("launching task - waiting for callback: %s", command);
    }

    public static void logSwitchingToOverviewStateStart(@NonNull Object command) {
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "switching to Overview state - onAnimationStart: %s",
                    command);
        }
        logToLogcatIfNeeded("switching to Overview state - onAnimationStart: %s", command);
    }

    public static void logSwitchingToOverviewStateEnd(@NonNull Object command) {
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "switching to Overview state - onAnimationEnd: %s",
                    command);
        }
        logToLogcatIfNeeded("switching to Overview state - onAnimationEnd: %s", command);
    }

    public static void logSwitchingToOverviewStateWaiting(@NonNull Object command) {
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "switching to Overview state - waiting: %s", command);
        }
        logToLogcatIfNeeded("switching to Overview state - waiting: %s", command);
    }

    public static void logRecentsAnimStarted(@NonNull Object command) {
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "recents animation started: %s", command);
        }
        logToLogcatIfNeeded("recents animation started: %s", command);
    }

    public static void logOnInitBackgroundStateUI(@NonNull Object command) {
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "recents animation started - onInitBackgroundStateUI: %s",
                    command);
        }
        logToLogcatIfNeeded("recents animation started - onInitBackgroundStateUI: %s", command);
    }

    public static void logRecentsAnimCanceled(@NonNull Object command) {
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "recents animation canceled: %s", command);
        }
        logToLogcatIfNeeded("recents animation canceled: %s", command);
    }

    public static void logSwitchingViaRecentsAnim(@NonNull Object command,
            @NonNull Object endTarget) {
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP,
                    "switching via recents animation - onGestureStarted: %s with end target: %s",
                    command, endTarget);
        }
        logToLogcatIfNeeded(
                "switching via recents animation - onGestureStarted: %s with end target: %s",
                command, endTarget);
    }

    public static void logSwitchingViaRecentsAnimComplete(@NonNull Object command) {
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP,
                    "switching via recents animation - onTransitionComplete: %s", command);
        }
        logToLogcatIfNeeded("switching via recents animation - onTransitionComplete: %s", command);
    }

    public static void logCommandFinishedButNotScheduled(@Nullable Object nextCommandInQueue,
            @NonNull Object command) {
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP,
                    "next task not scheduled. First pending command type is %s - command type is:"
                            + " %s", nextCommandInQueue, command);
        }
        logToLogcatIfNeeded(
                "next task not scheduled. First pending command type is %s - command type is:"
                        + " %s", nextCommandInQueue, command);
    }

    public static void logCommandFinishedSuccessfully(@NonNull Object command) {
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP, "command executed successfully: %s", command);
        }
        logToLogcatIfNeeded("command executed successfully: %s", command);
    }

    public static void logCommandCanceled(@NonNull Object command, @Nullable Throwable throwable) {
        if (willProtoLog()) {
            ProtoLog.e(PROTO_LOG_GROUP, "command canceled: %s - %s", command, throwable);
        }
        logEToLogcatIfNeeded("command canceled: %s - %s", command, throwable);
    }

    public static void logOnNewIntent(boolean alreadyOnHome, boolean shouldMoveToDefaultScreen,
            String intentAction, boolean internalStateHandled) {
        if (willProtoLog()) {
            ProtoLog.d(PROTO_LOG_GROUP,
                    "Launcher.onNewIntent: alreadyOnHome: %b, shouldMoveToDefaultScreen: %b, "
                            + "intentAction: %s, internalStateHandled: %b", alreadyOnHome,
                    shouldMoveToDefaultScreen, intentAction, internalStateHandled);
        }
        logToLogcatIfNeeded(
                "Launcher.onNewIntent: alreadyOnHome: %b, shouldMoveToDefaultScreen: %b, "
                        + "intentAction: %s, internalStateHandled: %b", alreadyOnHome,
                shouldMoveToDefaultScreen, intentAction, internalStateHandled);
    }
}
