/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.wm.shell.protolog;

import com.android.internal.protolog.common.IProtoLogGroup;

import java.util.UUID;

/**
 * Defines logging groups for ProtoLog.
 *
 * This file is used by the ProtoLogTool to generate optimized logging code.
 */
public enum ShellProtoLogGroup implements IProtoLogGroup {
    // NOTE: Since we enable these from the same WM ShellCommand, these names should not conflict
    // with those in the framework ProtoLogGroup
    WM_SHELL(Consts.ENABLE_DEBUG, Consts.ENABLE_LOG_TO_PROTO_DEBUG, true,
            Consts.TAG_WM_SHELL),
    WM_SHELL_INIT(Consts.ENABLE_DEBUG, Consts.ENABLE_LOG_TO_PROTO_DEBUG, true,
            Consts.TAG_WM_SHELL),
    WM_SHELL_TASK_ORG(Consts.ENABLE_DEBUG, Consts.ENABLE_LOG_TO_PROTO_DEBUG, false,
            Consts.TAG_WM_SHELL),
    WM_SHELL_TRANSITIONS(Consts.ENABLE_DEBUG, Consts.ENABLE_LOG_TO_PROTO_DEBUG, true,
            Consts.TAG_WM_SHELL),
    WM_SHELL_RECENTS_TRANSITION(Consts.ENABLE_DEBUG, Consts.ENABLE_LOG_TO_PROTO_DEBUG, true,
            "ShellRecents"),
    WM_SHELL_DRAG_AND_DROP(Consts.ENABLE_DEBUG, Consts.ENABLE_LOG_TO_PROTO_DEBUG, true,
            "ShellDragAndDrop"),
    WM_SHELL_STARTING_WINDOW(Consts.ENABLE_DEBUG, Consts.ENABLE_LOG_TO_PROTO_DEBUG, false,
            Consts.TAG_WM_STARTING_WINDOW),
    WM_SHELL_BACK_PREVIEW(Consts.ENABLE_DEBUG, Consts.ENABLE_LOG_TO_PROTO_DEBUG, true,
            "ShellBackPreview"),
    WM_SHELL_RECENT_TASKS(Consts.ENABLE_DEBUG, Consts.ENABLE_LOG_TO_PROTO_DEBUG, false,
            Consts.TAG_WM_SHELL),
    // TODO(b/282232877): turn logToLogcat to false.
    WM_SHELL_PICTURE_IN_PICTURE(Consts.ENABLE_DEBUG, Consts.ENABLE_LOG_TO_PROTO_DEBUG, true,
            Consts.TAG_WM_SHELL),
    WM_SHELL_SPLIT_SCREEN(Consts.ENABLE_DEBUG, Consts.ENABLE_LOG_TO_PROTO_DEBUG, true,
            Consts.TAG_WM_SPLIT_SCREEN),
    WM_SHELL_SYSUI_EVENTS(Consts.ENABLE_DEBUG, Consts.ENABLE_LOG_TO_PROTO_DEBUG, false,
            Consts.TAG_WM_SHELL),
    WM_SHELL_DESKTOP_MODE(Consts.ENABLE_DEBUG, Consts.ENABLE_LOG_TO_PROTO_DEBUG, true,
            Consts.TAG_WM_DESKTOP_MODE),
    WM_SHELL_FLOATING_APPS(Consts.ENABLE_DEBUG, Consts.ENABLE_LOG_TO_PROTO_DEBUG, false,
            Consts.TAG_WM_SHELL),
    WM_SHELL_FOLDABLE(Consts.ENABLE_DEBUG, Consts.ENABLE_LOG_TO_PROTO_DEBUG, false,
            Consts.TAG_WM_SHELL),
    WM_SHELL_BUBBLES(Consts.ENABLE_DEBUG, Consts.ENABLE_LOG_TO_PROTO_DEBUG, false,
            "Bubbles"),
    TEST_GROUP(true, true, false, "WindowManagerShellProtoLogTest");

    private final boolean mEnabled;
    private volatile boolean mLogToProto;
    private volatile boolean mLogToLogcat;
    private final String mTag;

    /**
     * @param enabled     set to false to exclude all log statements for this group from
     *                    compilation,
     *                    they will not be available in runtime.
     * @param logToProto  enable binary logging for the group
     * @param logToLogcat enable text logging for the group
     * @param tag         name of the source of the logged message
     */
    ShellProtoLogGroup(boolean enabled, boolean logToProto, boolean logToLogcat, String tag) {
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
    public String getTag() {
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

    @Override
    public int getId() {
        return Consts.START_ID + this.ordinal();
    }

    private static class Consts {
        private static final String TAG_WM_SHELL = "WindowManagerShell";
        private static final String TAG_WM_STARTING_WINDOW = "ShellStartingWindow";
        private static final String TAG_WM_SPLIT_SCREEN = "ShellSplitScreen";
        private static final String TAG_WM_DESKTOP_MODE = "ShellDesktopMode";

        private static final boolean ENABLE_DEBUG = true;
        private static final boolean ENABLE_LOG_TO_PROTO_DEBUG = true;

        private static final int START_ID = (int) (
                UUID.nameUUIDFromBytes(ShellProtoLogGroup.class.getName().getBytes())
                        .getMostSignificantBits() % Integer.MAX_VALUE);
    }
}
