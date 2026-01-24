/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.wm.shell.splitscreen;

import static com.android.internal.util.FrameworkStatsLog.SPLITSCREEN_UICHANGED__ENTER_REASON__LAUNCHER;
import static com.android.internal.util.FrameworkStatsLog.SPLITSCREEN_UICHANGED__ENTER_REASON__MULTI_INSTANCE;
import static com.android.internal.util.FrameworkStatsLog.SPLITSCREEN_UICHANGED__ENTER_REASON__UNKNOWN_ENTER;
import static com.android.internal.util.FrameworkStatsLog.SPLITSCREEN_UICHANGED__EXIT_REASON__APP_DOES_NOT_SUPPORT_MULTIWINDOW;
import static com.android.internal.util.FrameworkStatsLog.SPLITSCREEN_UICHANGED__EXIT_REASON__APP_FINISHED;
import static com.android.internal.util.FrameworkStatsLog.SPLITSCREEN_UICHANGED__EXIT_REASON__CHILD_TASK_ENTER_PIP;
import static com.android.internal.util.FrameworkStatsLog.SPLITSCREEN_UICHANGED__EXIT_REASON__DEVICE_FOLDED;
import static com.android.internal.util.FrameworkStatsLog.SPLITSCREEN_UICHANGED__EXIT_REASON__DRAG_DIVIDER;
import static com.android.internal.util.FrameworkStatsLog.SPLITSCREEN_UICHANGED__EXIT_REASON__FULLSCREEN_REQUEST;
import static com.android.internal.util.FrameworkStatsLog.SPLITSCREEN_UICHANGED__EXIT_REASON__FULLSCREEN_SHORTCUT;
import static com.android.internal.util.FrameworkStatsLog.SPLITSCREEN_UICHANGED__EXIT_REASON__DESKTOP_MODE;
import static com.android.internal.util.FrameworkStatsLog.SPLITSCREEN_UICHANGED__EXIT_REASON__RECREATE_SPLIT;
import static com.android.internal.util.FrameworkStatsLog.SPLITSCREEN_UICHANGED__EXIT_REASON__RETURN_HOME;
import static com.android.internal.util.FrameworkStatsLog.SPLITSCREEN_UICHANGED__EXIT_REASON__ROOT_TASK_VANISHED;
import static com.android.internal.util.FrameworkStatsLog.SPLITSCREEN_UICHANGED__EXIT_REASON__SCREEN_LOCKED;
import static com.android.internal.util.FrameworkStatsLog.SPLITSCREEN_UICHANGED__EXIT_REASON__SCREEN_LOCKED_SHOW_ON_TOP;
import static com.android.internal.util.FrameworkStatsLog.SPLITSCREEN_UICHANGED__EXIT_REASON__UNKNOWN_EXIT;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_UNDEFINED;
import static com.android.wm.shell.splitscreen.SplitScreenController.ENTER_REASON_DRAG;
import static com.android.wm.shell.splitscreen.SplitScreenController.ENTER_REASON_LAUNCHER;
import static com.android.wm.shell.splitscreen.SplitScreenController.ENTER_REASON_MULTI_INSTANCE;
import static com.android.wm.shell.splitscreen.SplitScreenController.ENTER_REASON_UNKNOWN;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_APP_DOES_NOT_SUPPORT_MULTIWINDOW;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_APP_FINISHED;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_CHILD_TASK_ENTER_PIP;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_DEVICE_FOLDED;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_DRAG_DIVIDER;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_DESKTOP_MODE;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_FULLSCREEN_REQUEST;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_FULLSCREEN_SHORTCUT;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_RECREATE_SPLIT;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_RETURN_HOME;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_ROOT_TASK_VANISHED;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_SCREEN_LOCKED;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_SCREEN_LOCKED_SHOW_ON_TOP;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_UNKNOWN;

import android.annotation.Nullable;
import android.util.Slog;

import com.android.internal.logging.InstanceId;
import com.android.internal.logging.InstanceIdSequence;
import com.android.internal.protolog.ProtoLog;
import com.android.internal.util.FrameworkStatsLog;
import com.android.wm.shell.shared.split.SplitScreenConstants.SplitPosition;
import com.android.wm.shell.splitscreen.SplitScreenController.ExitReason;

/**
 * Helper class that to log Drag & Drop UIEvents for a single session, see also go/uievent
 */
public class SplitscreenEventLogger {

    // Used to generate instance ids for this drag if one is not provided
    private final InstanceIdSequence mIdSequence;

    // The instance id for the current splitscreen session (from start to end)
    private InstanceId mLoggerSessionId;

    // Drag info
    private @SplitPosition int mDragEnterPosition;
    private @Nullable InstanceId mEnterSessionId;

    // For deduping async events
    private int mLastMainStagePosition = -1;
    private int mLastMainStageUid = -1;
    private int mLastSideStagePosition = -1;
    private int mLastSideStageUid = -1;
    private float mLastSplitRatio = -1f;
    private @SplitScreenController.SplitEnterReason int mEnterReason = ENTER_REASON_UNKNOWN;

    public SplitscreenEventLogger() {
        mIdSequence = new InstanceIdSequence(Integer.MAX_VALUE);
    }

    /**
     * Return whether a splitscreen session has started.
     */
    public boolean hasStartedSession() {
        return mLoggerSessionId != null;
    }

    public boolean isEnterRequestedByDrag() {
        return mEnterReason == ENTER_REASON_DRAG;
    }

    /**
     * May be called before logEnter() to indicate that the session was started from a drag.
     */
    public void enterRequestedByDrag(@SplitPosition int position, InstanceId enterSessionId) {
        mDragEnterPosition = position;
        enterRequested(enterSessionId, ENTER_REASON_DRAG);
    }

    /**
     * May be called before logEnter() to indicate that the session was started from launcher.
     * This specifically is for all the scenarios where split started without a drag interaction
     */
    public void enterRequested(@Nullable InstanceId enterSessionId,
            @SplitScreenController.SplitEnterReason int enterReason) {
        mEnterSessionId = enterSessionId;
        mEnterReason = enterReason;
    }

    /**
     * @return if an enterSessionId has been set via either
     *         {@link #enterRequested(InstanceId, int)} or
     *         {@link #enterRequestedByDrag(int, InstanceId)}
     */
    public boolean hasValidEnterSessionId() {
        return mEnterSessionId != null;
    }

    /**
     * Logs when the user enters splitscreen.
     */
    public void logEnter(float splitRatio,
            @SplitPosition int mainStagePosition, int mainStageUid,
            @SplitPosition int sideStagePosition, int sideStageUid,
            boolean isLandscape) {
        if (hasStartedSession()) {
            ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "logEnter: no-op, previous session has not ended");
            return;
        }

        mLoggerSessionId = mIdSequence.newInstanceId();
        int enterReason = getLoggerEnterReason(isLandscape);
        updateMainStageState(getMainStagePositionFromSplitPosition(mainStagePosition, isLandscape),
                mainStageUid);
        updateSideStageState(getSideStagePositionFromSplitPosition(sideStagePosition, isLandscape),
                sideStageUid);
        updateSplitRatioState(splitRatio);

        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "logEnter: enterReason=%d splitRatio=%f "
                        + "mainStagePosition=%d mainStageUid=%d sideStagePosition=%d "
                        + "sideStageUid=%d isLandscape=%b mEnterSessionId=%d mLoggerSessionId=%d",
                enterReason, splitRatio, mLastMainStagePosition, mLastMainStageUid,
                mLastSideStagePosition, mLastSideStageUid, isLandscape,
                mEnterSessionId != null ? mEnterSessionId.getId() : 0, mLoggerSessionId.getId());

        FrameworkStatsLog.write(FrameworkStatsLog.SPLITSCREEN_UI_CHANGED,
                FrameworkStatsLog.SPLITSCREEN_UICHANGED__ACTION__ENTER,
                enterReason,
                0 /* exitReason */,
                splitRatio,
                mLastMainStagePosition,
                mLastMainStageUid,
                mLastSideStagePosition,
                mLastSideStageUid,
                mEnterSessionId != null ? mEnterSessionId.getId() : 0,
                mLoggerSessionId.getId());
    }

    private int getLoggerEnterReason(boolean isLandscape) {
        switch (mEnterReason) {
            case ENTER_REASON_MULTI_INSTANCE:
                return SPLITSCREEN_UICHANGED__ENTER_REASON__MULTI_INSTANCE;
            case ENTER_REASON_LAUNCHER:
                return SPLITSCREEN_UICHANGED__ENTER_REASON__LAUNCHER;
            case ENTER_REASON_DRAG:
                return getDragEnterReasonFromSplitPosition(mDragEnterPosition, isLandscape);
            case ENTER_REASON_UNKNOWN:
            default:
                return SPLITSCREEN_UICHANGED__ENTER_REASON__UNKNOWN_ENTER;
        }
    }

    /**
     * Returns the framework logging constant given a splitscreen exit reason.
     */
    private int getLoggerExitReason(@ExitReason int exitReason) {
        switch (exitReason) {
            case EXIT_REASON_APP_DOES_NOT_SUPPORT_MULTIWINDOW:
                return SPLITSCREEN_UICHANGED__EXIT_REASON__APP_DOES_NOT_SUPPORT_MULTIWINDOW;
            case EXIT_REASON_APP_FINISHED:
                return SPLITSCREEN_UICHANGED__EXIT_REASON__APP_FINISHED;
            case EXIT_REASON_DEVICE_FOLDED:
                return SPLITSCREEN_UICHANGED__EXIT_REASON__DEVICE_FOLDED;
            case EXIT_REASON_DRAG_DIVIDER:
                return SPLITSCREEN_UICHANGED__EXIT_REASON__DRAG_DIVIDER;
            case EXIT_REASON_RETURN_HOME:
                return SPLITSCREEN_UICHANGED__EXIT_REASON__RETURN_HOME;
            case EXIT_REASON_ROOT_TASK_VANISHED:
                return SPLITSCREEN_UICHANGED__EXIT_REASON__ROOT_TASK_VANISHED;
            case EXIT_REASON_SCREEN_LOCKED:
                return SPLITSCREEN_UICHANGED__EXIT_REASON__SCREEN_LOCKED;
            case EXIT_REASON_SCREEN_LOCKED_SHOW_ON_TOP:
                return SPLITSCREEN_UICHANGED__EXIT_REASON__SCREEN_LOCKED_SHOW_ON_TOP;
            case EXIT_REASON_CHILD_TASK_ENTER_PIP:
                return SPLITSCREEN_UICHANGED__EXIT_REASON__CHILD_TASK_ENTER_PIP;
            case EXIT_REASON_RECREATE_SPLIT:
                return SPLITSCREEN_UICHANGED__EXIT_REASON__RECREATE_SPLIT;
            case EXIT_REASON_FULLSCREEN_SHORTCUT:
                return SPLITSCREEN_UICHANGED__EXIT_REASON__FULLSCREEN_SHORTCUT;
            case EXIT_REASON_DESKTOP_MODE:
                return SPLITSCREEN_UICHANGED__EXIT_REASON__DESKTOP_MODE;
            case EXIT_REASON_FULLSCREEN_REQUEST:
                return SPLITSCREEN_UICHANGED__EXIT_REASON__FULLSCREEN_REQUEST;
            case EXIT_REASON_UNKNOWN:
                // Fall through
            default:
                Slog.e("SplitscreenEventLogger", "Unknown exit reason: " + exitReason);
                return SPLITSCREEN_UICHANGED__EXIT_REASON__UNKNOWN_EXIT;
        }
    }

    /**
     * Logs when the user exits splitscreen.  Only one of the main or side stages should be
     * specified to indicate which position was focused as a part of exiting (both can be unset).
     */
    public void logExit(@ExitReason int exitReason,
            @SplitPosition int mainStagePosition, int mainStageUid,
            @SplitPosition int sideStagePosition, int sideStageUid, boolean isLandscape) {
        if (mLoggerSessionId == null) {
            ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "logExit: no-op, mLoggerSessionId is null");
            // Ignore changes until we've started logging the session
            return;
        }
        if ((mainStagePosition != SPLIT_POSITION_UNDEFINED
                && sideStagePosition != SPLIT_POSITION_UNDEFINED)
                        || (mainStageUid != 0 && sideStageUid != 0)) {
            ProtoLog.d(WM_SHELL_SPLIT_SCREEN,
                    "logExit: no-op, only main or side stage should be set, not both/none");
            throw new IllegalArgumentException("Only main or side stage should be set");
        }

        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "logExit: exitReason=%d mainStagePosition=%d"
                        + " mainStageUid=%d sideStagePosition=%d sideStageUid=%d isLandscape=%b"
                        + " mLoggerSessionId=%d", getLoggerExitReason(exitReason),
                getMainStagePositionFromSplitPosition(mainStagePosition, isLandscape), mainStageUid,
                getSideStagePositionFromSplitPosition(sideStagePosition, isLandscape), sideStageUid,
                isLandscape, mLoggerSessionId.getId());

        FrameworkStatsLog.write(FrameworkStatsLog.SPLITSCREEN_UI_CHANGED,
                FrameworkStatsLog.SPLITSCREEN_UICHANGED__ACTION__EXIT,
                0 /* enterReason */,
                getLoggerExitReason(exitReason),
                0f /* splitRatio */,
                getMainStagePositionFromSplitPosition(mainStagePosition, isLandscape),
                mainStageUid,
                getSideStagePositionFromSplitPosition(sideStagePosition, isLandscape),
                sideStageUid,
                0 /* dragInstanceId */,
                mLoggerSessionId.getId());

        // Reset states
        mLoggerSessionId = null;
        mDragEnterPosition = SPLIT_POSITION_UNDEFINED;
        mEnterSessionId = null;
        mLastMainStagePosition = -1;
        mLastMainStageUid = -1;
        mLastSideStagePosition = -1;
        mLastSideStageUid = -1;
        mEnterReason = ENTER_REASON_UNKNOWN;
    }

    /**
     * Logs when an app in the main stage changes.
     */
    public void logMainStageAppChange(@SplitPosition int mainStagePosition, int mainStageUid,
            boolean isLandscape) {
        if (mLoggerSessionId == null) {
            // Ignore changes until we've started logging the session
            return;
        }
        if (!updateMainStageState(getMainStagePositionFromSplitPosition(mainStagePosition,
                isLandscape), mainStageUid)) {
            // Ignore if there are no user perceived changes
            return;
        }

        FrameworkStatsLog.write(FrameworkStatsLog.SPLITSCREEN_UI_CHANGED,
                FrameworkStatsLog.SPLITSCREEN_UICHANGED__ACTION__APP_CHANGE,
                0 /* enterReason */,
                0 /* exitReason */,
                0f /* splitRatio */,
                mLastMainStagePosition,
                mLastMainStageUid,
                0 /* sideStagePosition */,
                0 /* sideStageUid */,
                0 /* dragInstanceId */,
                mLoggerSessionId.getId());
    }

    /**
     * Logs when an app in the side stage changes.
     */
    public void logSideStageAppChange(@SplitPosition int sideStagePosition, int sideStageUid,
            boolean isLandscape) {
        if (mLoggerSessionId == null) {
            // Ignore changes until we've started logging the session
            return;
        }
        if (!updateSideStageState(getSideStagePositionFromSplitPosition(sideStagePosition,
                isLandscape), sideStageUid)) {
            // Ignore if there are no user perceived changes
            return;
        }

        FrameworkStatsLog.write(FrameworkStatsLog.SPLITSCREEN_UI_CHANGED,
                FrameworkStatsLog.SPLITSCREEN_UICHANGED__ACTION__APP_CHANGE,
                0 /* enterReason */,
                0 /* exitReason */,
                0f /* splitRatio */,
                0 /* mainStagePosition */,
                0 /* mainStageUid */,
                mLastSideStagePosition,
                mLastSideStageUid,
                0 /* dragInstanceId */,
                mLoggerSessionId.getId());
    }

    /**
     * Logs when the splitscreen ratio changes.
     */
    public void logResize(float splitRatio) {
        if (mLoggerSessionId == null) {
            ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "logResize: no-op, mLoggerSessionId is null");
            // Ignore changes until we've started logging the session
            return;
        }
        if (splitRatio <= 0f || splitRatio >= 1f) {
            ProtoLog.d(WM_SHELL_SPLIT_SCREEN,
                    "logResize: no-op, splitRatio indicates that user is dismissing, not resizing");
            // Don't bother reporting resizes that end up dismissing the split, that will be logged
            // via the exit event
            return;
        }
        if (!updateSplitRatioState(splitRatio)) {
            ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "logResize: no-op, split ratio was not changed");
            // Ignore if there are no user perceived changes
            return;
        }

        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "logResize: splitRatio=%f mLoggerSessionId=%d",
                mLastSplitRatio, mLoggerSessionId.getId());
        FrameworkStatsLog.write(FrameworkStatsLog.SPLITSCREEN_UI_CHANGED,
                FrameworkStatsLog.SPLITSCREEN_UICHANGED__ACTION__RESIZE,
                0 /* enterReason */,
                0 /* exitReason */,
                mLastSplitRatio,
                mLastMainStagePosition,
                mLastMainStageUid,
                mLastSideStagePosition,
                mLastSideStageUid,
                0 /* dragInstanceId */,
                mLoggerSessionId.getId());
    }

    /**
     * Logs when the apps in splitscreen are swapped.
     */
    public void logSwap(@SplitPosition int mainStagePosition, int mainStageUid,
            @SplitPosition int sideStagePosition, int sideStageUid, boolean isLandscape) {
        if (mLoggerSessionId == null) {
            ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "logSwap: no-op, mLoggerSessionId is null");
            // Ignore changes until we've started logging the session
            return;
        }

        updateMainStageState(getMainStagePositionFromSplitPosition(mainStagePosition, isLandscape),
                mainStageUid);
        updateSideStageState(getSideStagePositionFromSplitPosition(sideStagePosition, isLandscape),
                sideStageUid);

        ProtoLog.d(WM_SHELL_SPLIT_SCREEN, "logSwap: mainStagePosition=%d mainStageUid=%d "
                + "sideStagePosition=%d sideStageUid=%d mLoggerSessionId=%d",
                mLastMainStagePosition, mLastMainStageUid, mLastSideStagePosition,
                mLastSideStageUid, mLoggerSessionId.getId());
        FrameworkStatsLog.write(FrameworkStatsLog.SPLITSCREEN_UI_CHANGED,
                FrameworkStatsLog.SPLITSCREEN_UICHANGED__ACTION__SWAP,
                0 /* enterReason */,
                0 /* exitReason */,
                0f /* splitRatio */,
                mLastMainStagePosition,
                mLastMainStageUid,
                mLastSideStagePosition,
                mLastSideStageUid,
                0 /* dragInstanceId */,
                mLoggerSessionId.getId());
    }

    private boolean updateMainStageState(int mainStagePosition, int mainStageUid) {
        boolean changed = (mLastMainStagePosition != mainStagePosition)
                || (mLastMainStageUid != mainStageUid);
        if (!changed) {
            return false;
        }

        mLastMainStagePosition = mainStagePosition;
        mLastMainStageUid = mainStageUid;
        return true;
    }

    private boolean updateSideStageState(int sideStagePosition, int sideStageUid) {
        boolean changed = (mLastSideStagePosition != sideStagePosition)
                || (mLastSideStageUid != sideStageUid);
        if (!changed) {
            return false;
        }

        mLastSideStagePosition = sideStagePosition;
        mLastSideStageUid = sideStageUid;
        return true;
    }

    private boolean updateSplitRatioState(float splitRatio) {
        boolean changed = Float.compare(mLastSplitRatio, splitRatio) != 0;
        if (!changed) {
            return false;
        }

        mLastSplitRatio = splitRatio;
        return true;
    }

    public int getDragEnterReasonFromSplitPosition(@SplitPosition int position,
            boolean isLandscape) {
        if (isLandscape) {
            return position == SPLIT_POSITION_TOP_OR_LEFT
                    ? FrameworkStatsLog.SPLITSCREEN_UICHANGED__ENTER_REASON__DRAG_LEFT
                    : FrameworkStatsLog.SPLITSCREEN_UICHANGED__ENTER_REASON__DRAG_RIGHT;
        } else {
            return position == SPLIT_POSITION_TOP_OR_LEFT
                    ? FrameworkStatsLog.SPLITSCREEN_UICHANGED__ENTER_REASON__DRAG_TOP
                    : FrameworkStatsLog.SPLITSCREEN_UICHANGED__ENTER_REASON__DRAG_BOTTOM;
        }
    }

    private int getMainStagePositionFromSplitPosition(@SplitPosition int position,
            boolean isLandscape) {
        if (position == SPLIT_POSITION_UNDEFINED) {
            return 0;
        }
        if (isLandscape) {
            return position == SPLIT_POSITION_TOP_OR_LEFT
                    ? FrameworkStatsLog.SPLITSCREEN_UICHANGED__MAIN_STAGE_POSITION__LEFT
                    : FrameworkStatsLog.SPLITSCREEN_UICHANGED__MAIN_STAGE_POSITION__RIGHT;
        } else {
            return position == SPLIT_POSITION_TOP_OR_LEFT
                    ? FrameworkStatsLog.SPLITSCREEN_UICHANGED__MAIN_STAGE_POSITION__TOP
                    : FrameworkStatsLog.SPLITSCREEN_UICHANGED__MAIN_STAGE_POSITION__BOTTOM;
        }
    }

    private int getSideStagePositionFromSplitPosition(@SplitPosition int position,
            boolean isLandscape) {
        if (position == SPLIT_POSITION_UNDEFINED) {
            return 0;
        }
        if (isLandscape) {
            return position == SPLIT_POSITION_TOP_OR_LEFT
                    ? FrameworkStatsLog.SPLITSCREEN_UICHANGED__SIDE_STAGE_POSITION__LEFT
                    : FrameworkStatsLog.SPLITSCREEN_UICHANGED__SIDE_STAGE_POSITION__RIGHT;
        } else {
            return position == SPLIT_POSITION_TOP_OR_LEFT
                    ? FrameworkStatsLog.SPLITSCREEN_UICHANGED__SIDE_STAGE_POSITION__TOP
                    : FrameworkStatsLog.SPLITSCREEN_UICHANGED__SIDE_STAGE_POSITION__BOTTOM;
        }
    }
}
