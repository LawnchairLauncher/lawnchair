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

import android.util.Log;

import androidx.annotation.NonNull;

import com.android.internal.protolog.ProtoLog;
import com.android.internal.protolog.common.IProtoLogGroup;

import java.util.UUID;

/** Enums used to interface with the ProtoLog API. */
public enum QuickstepProtoLogGroup implements IProtoLogGroup {

    ACTIVE_GESTURE_LOG(true, true, Constants.DEBUG_ACTIVE_GESTURE, "ActiveGestureLog"),
    RECENTS_WINDOW(true, true, Constants.DEBUG_RECENTS_WINDOW, "RecentsWindow"),
    LAUNCHER_STATE_MANAGER(true, true, Constants.DEBUG_STATE_MANAGER, "LauncherStateManager");

    private final boolean mEnabled;
    private volatile boolean mLogToProto;
    private volatile boolean mLogToLogcat;
    private final @NonNull String mTag;

    public static boolean isProtoLogInitialized() {
        if (!Variables.sIsInitialized) {
            Log.w(Constants.TAG,
                    "Attempting to log to ProtoLog before initializing it.",
                    new IllegalStateException());
        }
        return Variables.sIsInitialized;
    }

    public static void initProtoLog() {
        if (Variables.sIsInitialized) {
            Log.e(Constants.TAG,
                    "Attempting to re-initialize ProtoLog.", new IllegalStateException());
            return;
        }
        Log.i(Constants.TAG, "Initializing ProtoLog.");
        Variables.sIsInitialized = true;
        ProtoLog.init(QuickstepProtoLogGroup.values());
    }

    /**
     * @param enabled     set to false to exclude all log statements for this group from
     *                    compilation,
     *                    they will not be available in runtime.
     * @param logToProto  enable binary logging for the group
     * @param logToLogcat enable text logging for the group
     * @param tag         name of the source of the logged message
     */
    QuickstepProtoLogGroup(
            boolean enabled, boolean logToProto, boolean logToLogcat, @NonNull String tag) {
        this.mEnabled = enabled;
        this.mLogToProto = logToProto;
        this.mLogToLogcat = logToLogcat;
        this.mTag = tag;
    }

    @Override
    public boolean isEnabled() {
        return mEnabled;
    }

    @Override
    public boolean isLogToProto() {
        return mLogToProto;
    }

    @Override
    public boolean isLogToLogcat() {
        return mLogToLogcat;
    }

    @Override
    public boolean isLogToAny() {
        return mLogToLogcat || mLogToProto;
    }

    @Override
    public int getId() {
        return Constants.LOG_START_ID + this.ordinal();
    }

    @Override
    public @NonNull String getTag() {
        return mTag;
    }

    @Override
    public void setLogToProto(boolean logToProto) {
        this.mLogToProto = logToProto;
    }

    @Override
    public void setLogToLogcat(boolean logToLogcat) {
        this.mLogToLogcat = logToLogcat;
    }

    private static final class Variables {

        private static boolean sIsInitialized = false;
    }

    private static final class Constants {

        private static final String TAG = "QuickstepProtoLogGroup";

        private static final boolean DEBUG_ACTIVE_GESTURE = false;
        private static final boolean DEBUG_RECENTS_WINDOW = false;
        private static final boolean DEBUG_STATE_MANAGER = true; // b/279059025, b/325463989

        private static final int LOG_START_ID =
                (int) (UUID.nameUUIDFromBytes(QuickstepProtoLogGroup.class.getName().getBytes())
                        .getMostSignificantBits() % Integer.MAX_VALUE);
    }
}
