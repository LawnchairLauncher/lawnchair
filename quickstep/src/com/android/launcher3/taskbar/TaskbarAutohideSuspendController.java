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
package com.android.launcher3.taskbar;

import static com.android.launcher3.util.FlagDebugUtils.appendFlag;

import androidx.annotation.IntDef;

import com.android.quickstep.SystemUiProxy;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.StringJoiner;

/**
 * Normally Taskbar will auto-hide when entering immersive (fullscreen) apps. This controller allows
 * us to suspend that behavior in certain cases (e.g. opening a Folder or dragging an icon).
 */
public class TaskbarAutohideSuspendController implements
        TaskbarControllers.LoggableTaskbarController {

    // Taskbar window is fullscreen.
    public static final int FLAG_AUTOHIDE_SUSPEND_FULLSCREEN = 1 << 0;
    // User is dragging item.
    public static final int FLAG_AUTOHIDE_SUSPEND_DRAGGING = 1 << 1;
    // User has touched down but has not lifted finger.
    public static final int FLAG_AUTOHIDE_SUSPEND_TOUCHING = 1 << 2;
    // Taskbar EDU overlay is open above the Taskbar. */
    public static final int FLAG_AUTOHIDE_SUSPEND_EDU_OPEN = 1 << 3;
    // Taskbar is in immersive mode in overview
    public static final int FLAG_AUTOHIDE_SUSPEND_IN_LAUNCHER = 1 << 4;
    // Transient Taskbar is temporarily unstashed (pending a timeout).
    public static final int FLAG_AUTOHIDE_SUSPEND_TRANSIENT_TASKBAR = 1 << 5;
    // User has hovered the taskbar.
    public static final int FLAG_AUTOHIDE_SUSPEND_HOVERING_ICONS = 1 << 6;

    @IntDef(flag = true, value = {
            FLAG_AUTOHIDE_SUSPEND_FULLSCREEN,
            FLAG_AUTOHIDE_SUSPEND_DRAGGING,
            FLAG_AUTOHIDE_SUSPEND_TOUCHING,
            FLAG_AUTOHIDE_SUSPEND_EDU_OPEN,
            FLAG_AUTOHIDE_SUSPEND_IN_LAUNCHER,
            FLAG_AUTOHIDE_SUSPEND_TRANSIENT_TASKBAR,
            FLAG_AUTOHIDE_SUSPEND_HOVERING_ICONS,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface AutohideSuspendFlag {}

    private final TaskbarActivityContext mActivity;
    private final SystemUiProxy mSystemUiProxy;

    private @AutohideSuspendFlag int mAutohideSuspendFlags = 0;

    public TaskbarAutohideSuspendController(TaskbarActivityContext activity) {
        mActivity = activity;
        mSystemUiProxy = SystemUiProxy.INSTANCE.get(activity);
    }

    public void onDestroy() {
        mSystemUiProxy.notifyTaskbarAutohideSuspend(false);
    }

    /**
     * Adds or removes the given flag, then notifies system UI proxy whether to suspend auto-hide.
     */
    public void updateFlag(@AutohideSuspendFlag int flag, boolean enabled) {
        int flagsBefore = mAutohideSuspendFlags;
        if (enabled) {
            mAutohideSuspendFlags |= flag;
        } else {
            mAutohideSuspendFlags &= ~flag;
        }
        if (flagsBefore == mAutohideSuspendFlags) {
            // Nothing has changed, no need to notify.
            return;
        }

        boolean isSuspended = isSuspended();
        mSystemUiProxy.notifyTaskbarAutohideSuspend(isSuspended);
        mActivity.onTransientAutohideSuspendFlagChanged(isTransientTaskbarStashingSuspended());
    }

    /**
     * Returns true iff taskbar autohide is currently suspended.
     */
    private boolean isSuspended() {
        return mAutohideSuspendFlags != 0;
    }

    /**
     * Returns whether Transient Taskbar should avoid auto-stashing in Launcher(Overview).
     */
    public boolean isSuspendedForTransientTaskbarInLauncher() {
        return (mAutohideSuspendFlags & FLAG_AUTOHIDE_SUSPEND_IN_LAUNCHER) != 0;
    }

    /**
     * Returns whether Transient Taskbar should avoid auto-stashing.
     */
    public boolean isTransientTaskbarStashingSuspended() {
        return (mAutohideSuspendFlags & ~FLAG_AUTOHIDE_SUSPEND_TRANSIENT_TASKBAR) != 0;
    }

    @Override
    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "TaskbarAutohideSuspendController:");

        pw.println(prefix + "\tmAutohideSuspendFlags=" + getStateString(mAutohideSuspendFlags));
    }

    private static String getStateString(int flags) {
        StringJoiner str = new StringJoiner("|");
        appendFlag(str, flags, FLAG_AUTOHIDE_SUSPEND_FULLSCREEN,
                "FLAG_AUTOHIDE_SUSPEND_FULLSCREEN");
        appendFlag(str, flags, FLAG_AUTOHIDE_SUSPEND_DRAGGING, "FLAG_AUTOHIDE_SUSPEND_DRAGGING");
        appendFlag(str, flags, FLAG_AUTOHIDE_SUSPEND_TOUCHING, "FLAG_AUTOHIDE_SUSPEND_TOUCHING");
        appendFlag(str, flags, FLAG_AUTOHIDE_SUSPEND_EDU_OPEN, "FLAG_AUTOHIDE_SUSPEND_EDU_OPEN");
        appendFlag(str, flags, FLAG_AUTOHIDE_SUSPEND_IN_LAUNCHER,
                "FLAG_AUTOHIDE_SUSPEND_IN_LAUNCHER");
        appendFlag(str, flags, FLAG_AUTOHIDE_SUSPEND_TRANSIENT_TASKBAR,
                "FLAG_AUTOHIDE_SUSPEND_TRANSIENT_TASKBAR");
        return str.toString();
    }
}
