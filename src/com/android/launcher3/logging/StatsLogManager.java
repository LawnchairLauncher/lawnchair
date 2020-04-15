/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.launcher3.logging;

import android.content.Context;

import com.android.internal.logging.UiEvent;
import com.android.internal.logging.UiEventLogger;
import com.android.launcher3.R;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.logger.LauncherAtom.ItemInfo;
import com.android.launcher3.logging.StatsLogUtils.LogStateProvider;
import com.android.launcher3.util.ResourceBasedOverride;

/**
 * Handles the user event logging in R+.
 */
public class StatsLogManager implements ResourceBasedOverride {

    public enum LauncherEvent implements UiEventLogger.UiEventEnum {
        @UiEvent(doc = "App launched from workspace, hotseat or folder in launcher")
        LAUNCHER_APP_LAUNCH_TAP(338),
        @UiEvent(doc = "Task launched from overview using TAP")
        LAUNCHER_TASK_LAUNCH_TAP(339),
        @UiEvent(doc = "Task launched from overview using SWIPE DOWN")
        LAUNCHER_TASK_LAUNCH_SWIPE_DOWN(340),
        @UiEvent(doc = "TASK dismissed from overview using SWIPE UP")
        LAUNCHER_TASK_DISMISS_SWIPE_UP(341);
        // ADD MORE

        private final int mId;
        LauncherEvent(int id) {
            mId = id;
        }
        public int getId() {
            return mId;
        }
    }

    protected LogStateProvider mStateProvider;

    public static StatsLogManager newInstance(Context context, LogStateProvider stateProvider) {
        StatsLogManager mgr = Overrides.getObject(StatsLogManager.class,
                context.getApplicationContext(), R.string.stats_log_manager_class);
        mgr.mStateProvider = stateProvider;
        mgr.verify();
        return mgr;
    }

    /**
     * Logs an event and accompanying {@link ItemInfo}
     */
    public void log(LauncherEvent eventId, LauncherAtom.ItemInfo itemInfo) { }

    /**
     * Logs snapshot, or impression of the current workspace.
     */
    public void logSnapshot() { }

    public void verify() {}     // TODO: should move into robo tests
}
