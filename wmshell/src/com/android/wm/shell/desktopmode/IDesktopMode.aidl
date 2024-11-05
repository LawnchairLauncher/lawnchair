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

package com.android.wm.shell.desktopmode;

import android.app.ActivityManager.RunningTaskInfo;
import android.window.RemoteTransition;
import com.android.wm.shell.common.desktopmode.DesktopModeTransitionSource;
import com.android.wm.shell.desktopmode.IDesktopTaskListener;

/**
 * Interface that is exposed to remote callers to manipulate desktop mode features.
 */
interface IDesktopMode {

    /** Show apps on the desktop on the given display */
    void showDesktopApps(int displayId, in RemoteTransition remoteTransition);

    /** @deprecated use {@link #showDesktopApps} instead. */
    void stashDesktopApps(int displayId);

    /** @deprecated this is no longer supported. */
    void hideStashedDesktopApps(int displayId);

    /** Bring task with the given id to front */
    oneway void showDesktopApp(int taskId);

    /** Get count of visible desktop tasks on the given display */
    int getVisibleTaskCount(int displayId);

    /** Perform cleanup transactions after the animation to split select is complete */
    oneway void onDesktopSplitSelectAnimComplete(in RunningTaskInfo taskInfo);

    /** Set listener that will receive callbacks about updates to desktop tasks */
    oneway void setTaskListener(IDesktopTaskListener listener);

    /** Move a task with given `taskId` to desktop */
    void moveToDesktop(int taskId, in DesktopModeTransitionSource transitionSource);
}