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

package com.android.launcher3.ui;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.test.InstrumentationRegistry;

import com.android.launcher3.tapl.TestHelpers;

import java.util.WeakHashMap;

public class ActivityLeakTracker implements Application.ActivityLifecycleCallbacks {
    private final WeakHashMap<Activity, Boolean> mActivities = new WeakHashMap<>();

    private int mActivitiesCreated;

    ActivityLeakTracker() {
        if (!TestHelpers.isInLauncherProcess()) return;
        final Application app =
                (Application) InstrumentationRegistry.getTargetContext().getApplicationContext();
        app.registerActivityLifecycleCallbacks(this);
    }

    public int getActivitiesCreated() {
        return mActivitiesCreated;
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle bundle) {
        mActivities.put(activity, true);
        ++mActivitiesCreated;
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }

    @Override
    public void onActivityResumed(Activity activity) {
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }

    public boolean noLeakedActivities() {
        int liveActivities = 0;
        int destroyedActivities = 0;

        for (Activity activity : mActivities.keySet()) {
            if (activity.isDestroyed()) {
                ++destroyedActivities;
            } else {
                ++liveActivities;
            }
        }

        if (liveActivities > 2) return false;

        // It's OK to have 1 leaked activity if no active activities exist.
        return liveActivities == 0 ? destroyedActivities <= 1 : destroyedActivities == 0;
    }
}
