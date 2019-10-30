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
import android.content.Intent;
import android.view.View;

import com.android.launcher3.R;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.ResourceBasedOverride;
import com.android.launcher3.logging.StatsLogUtils.LogStateProvider;

/**
 * Handles the user event logging in Q.
 */
public class StatsLogManager implements ResourceBasedOverride {

    protected LogStateProvider mStateProvider;
    public static StatsLogManager newInstance(Context context, LogStateProvider stateProvider) {
        StatsLogManager mgr = Overrides.getObject(StatsLogManager.class,
                context.getApplicationContext(), R.string.stats_log_manager_class);
        mgr.mStateProvider = stateProvider;
        mgr.verify();
        return mgr;
    }

    public void logAppLaunch(View v, Intent intent) { }
    public void logTaskLaunch(View v, ComponentKey key) { }
    public void logTaskDismiss(View v, ComponentKey key) { }
    public void logSwipeOnContainer(boolean isSwipingToLeft, int pageId) { }
    public void verify() {}     // TODO: should move into robo tests
}
