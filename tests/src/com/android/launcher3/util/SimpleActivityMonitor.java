/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.launcher3.util;

import android.app.Activity;
import android.app.Application.*;
import android.os.Bundle;

import java.util.ArrayList;

/**
 * Simple monitor to keep a list of active activities.
 */
public class SimpleActivityMonitor implements ActivityLifecycleCallbacks {

    public final ArrayList<Activity> created = new ArrayList<>();
    public final ArrayList<Activity> started = new ArrayList<>();
    public final ArrayList<Activity> resumed = new ArrayList<>();

    @Override
    public void onActivityCreated(Activity activity, Bundle bundle) {
        created.add(activity);
    }

    @Override
    public void onActivityStarted(Activity activity) {
        started.add(activity);
    }

    @Override
    public void onActivityResumed(Activity activity) {
        resumed.add(activity);
    }

    @Override
    public void onActivityPaused(Activity activity) {
        resumed.remove(activity);
    }

    @Override
    public void onActivityStopped(Activity activity) {
        started.remove(activity);
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) { }

    @Override
    public void onActivityDestroyed(Activity activity) {
        created.remove(activity);
    }
}
