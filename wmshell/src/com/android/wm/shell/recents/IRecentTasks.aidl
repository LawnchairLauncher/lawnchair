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

package com.android.wm.shell.recents;

import android.app.ActivityManager.RunningTaskInfo;
import android.app.IApplicationThread;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.view.IRecentsAnimationRunner;

import com.android.wm.shell.recents.IRecentTasksListener;
import com.android.wm.shell.util.GroupedRecentTaskInfo;

/**
 * Interface that is exposed to remote callers to fetch recent tasks.
 */
interface IRecentTasks {

    /**
     * Registers a recent tasks listener.
     */
    oneway void registerRecentTasksListener(in IRecentTasksListener listener) = 1;

    /**
     * Unregisters a recent tasks listener.
     */
    oneway void unregisterRecentTasksListener(in IRecentTasksListener listener) = 2;

    /**
     * Gets the set of recent tasks.
     */
    GroupedRecentTaskInfo[] getRecentTasks(int maxNum, int flags, int userId) = 3;

    /**
     * Gets the set of running tasks.
     */
    RunningTaskInfo[] getRunningTasks(int maxNum) = 4;

    /**
     * Starts a recents transition.
     */
    oneway void startRecentsTransition(in PendingIntent intent, in Intent fillIn, in Bundle options,
                    IApplicationThread appThread, IRecentsAnimationRunner listener) = 5;
}
