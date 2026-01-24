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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.protolog.ProtoLog;

/**
 * Proxy class used for OverviewCommandHelper ProtoLog support. (e.g. for 3 button nav)
 */
public class OverviewCommandHelperProtoLogProxy {

    public static void logCommandQueueFull(@NonNull Object type, @NonNull Object commandQueue) {
        if (!isProtoLogInitialized()) return;
        ProtoLog.d(OVERVIEW_COMMAND_HELPER,
                "command not added: %s - queue is full (%s).",
                type,
                commandQueue);
    }

    public static void logCommandAdded(@NonNull Object command) {
        if (!isProtoLogInitialized()) return;
        ProtoLog.d(OVERVIEW_COMMAND_HELPER, "command added: %s", command);
    }

    public static void logCommandExecuted(@NonNull Object command, int queueSize) {
        if (!isProtoLogInitialized()) return;
        ProtoLog.d(OVERVIEW_COMMAND_HELPER,
                "execute: %s - queue size: %d",
                command,
                queueSize);
    }

    public static void logCommandNotExecuted(@NonNull Object command, int queueSize) {
        if (!isProtoLogInitialized()) return;
        ProtoLog.d(OVERVIEW_COMMAND_HELPER,
                "command not executed: %s - queue size: %d",
                command,
                queueSize);
    }

    public static void logClearPendingCommands(@NonNull Object commandQueue) {
        if (!isProtoLogInitialized()) return;
        ProtoLog.d(OVERVIEW_COMMAND_HELPER,
                "clearing pending commands: %s", commandQueue);
    }

    public static void logNoPendingCommands() {
        if (!isProtoLogInitialized()) return;
        ProtoLog.d(OVERVIEW_COMMAND_HELPER, "no pending commands to be executed.");
    }

    public static void logExecutingCommand(@NonNull Object command) {
        if (!isProtoLogInitialized()) return;
        ProtoLog.d(OVERVIEW_COMMAND_HELPER, "executing command: %s", command);
    }

    public static void logExecutingCommand(@NonNull Object command, @Nullable Object recentsView) {
        if (!isProtoLogInitialized()) return;
        ProtoLog.d(OVERVIEW_COMMAND_HELPER,
                "executing command: %s - visibleRecentsView: %s",
                command,
                recentsView);
    }

    public static void logExecutedCommandWithResult(@NonNull Object command, boolean isCompleted) {
        if (!isProtoLogInitialized()) return;
        ProtoLog.d(OVERVIEW_COMMAND_HELPER, "command executed: %s with result: %b",
                command,
                isCompleted);
    }

    public static void logWaitingForCommandCallback(@NonNull Object command) {
        if (!isProtoLogInitialized()) return;
        ProtoLog.d(OVERVIEW_COMMAND_HELPER, "waiting for command callback: %s", command);
    }

    public static void logLaunchingTaskCallback(@NonNull Object command) {
        if (!isProtoLogInitialized()) return;
        ProtoLog.d(OVERVIEW_COMMAND_HELPER, "launching task callback: %s", command);
    }

    public static void logLaunchingTaskWaitingForCallback(@NonNull Object command) {
        if (!isProtoLogInitialized()) return;
        ProtoLog.d(OVERVIEW_COMMAND_HELPER, "launching task - waiting for callback: %s", command);
    }

    public static void logSwitchingToOverviewStateStart(@NonNull Object command) {
        if (!isProtoLogInitialized()) return;
        ProtoLog.d(OVERVIEW_COMMAND_HELPER, "switching to Overview state - onAnimationStart: %s",
                command);
    }

    public static void logSwitchingToOverviewStateEnd(@NonNull Object command) {
        if (!isProtoLogInitialized()) return;
        ProtoLog.d(OVERVIEW_COMMAND_HELPER, "switching to Overview state - onAnimationEnd: %s",
                command);
    }

    public static void logSwitchingToOverviewStateWaiting(@NonNull Object command) {
        if (!isProtoLogInitialized()) return;
        ProtoLog.d(OVERVIEW_COMMAND_HELPER, "switching to Overview state - waiting: %s", command);
    }

    public static void logRecentsAnimStarted(@NonNull Object command) {
        if (!isProtoLogInitialized()) return;
        ProtoLog.d(OVERVIEW_COMMAND_HELPER, "recents animation started: %s", command);
    }

    public static void logOnInitBackgroundStateUI(@NonNull Object command) {
        if (!isProtoLogInitialized()) return;
        ProtoLog.d(OVERVIEW_COMMAND_HELPER,
                "recents animation started - onInitBackgroundStateUI: %s", command);
    }

    public static void logRecentsAnimCanceled(@NonNull Object command) {
        if (!isProtoLogInitialized()) return;
        ProtoLog.d(OVERVIEW_COMMAND_HELPER, "recents animation canceled: %s", command);
    }

    public static void logSwitchingViaRecentsAnim(@NonNull Object command) {
        if (!isProtoLogInitialized()) return;
        ProtoLog.d(OVERVIEW_COMMAND_HELPER,
                "switching via recents animation - onGestureStarted: %s", command);
    }

    public static void logSwitchingViaRecentsAnimComplete(@NonNull Object command) {
        if (!isProtoLogInitialized()) return;
        ProtoLog.d(OVERVIEW_COMMAND_HELPER,
                "switching via recents animation - onTransitionComplete: %s", command);
    }

    public static void logCommandFinishedButNotScheduled(@Nullable Object nextCommandInQueue,
            @NonNull Object command) {
        if (!isProtoLogInitialized()) return;
        ProtoLog.d(OVERVIEW_COMMAND_HELPER,
                "next task not scheduled. First pending command type is %s - command type is: %s",
                nextCommandInQueue,
                command);
    }

    public static void logCommandFinishedSuccessfully(@NonNull Object command) {
        if (!isProtoLogInitialized()) return;
        ProtoLog.d(OVERVIEW_COMMAND_HELPER, "command executed successfully: %s", command);
    }

    public static void logCommandCanceled(@NonNull Object command, @Nullable Throwable throwable) {
        if (!isProtoLogInitialized()) return;
        ProtoLog.e(OVERVIEW_COMMAND_HELPER, "command canceled: %s - %s", command, throwable);
    }

    public static void logOnNewIntent(boolean alreadyOnHome, boolean shouldMoveToDefaultScreen,
            String intentAction, boolean internalStateHandled) {
        if (!isProtoLogInitialized()) return;
        ProtoLog.d(OVERVIEW_COMMAND_HELPER,
                "Launcher.onNewIntent: alreadyOnHome: %b, shouldMoveToDefaultScreen: %b, "
                        + "intentAction: %s, internalStateHandled: %b",
                alreadyOnHome,
                shouldMoveToDefaultScreen,
                intentAction,
                internalStateHandled);
    }
}
