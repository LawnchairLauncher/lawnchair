/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.systemui.plugins;

import android.content.ComponentName;
import android.os.UserHandle;

import com.android.systemui.plugins.annotations.ProvidesInterface;

/**
 * Plugin interface which sends app launch events.
 */
@ProvidesInterface(action = AppLaunchEventsPlugin.ACTION, version = AppLaunchEventsPlugin.VERSION)
public interface AppLaunchEventsPlugin extends Plugin {
    String ACTION = "com.android.systemui.action.PLUGIN_APP_EVENTS";
    int VERSION = 1;

    /**
     * Receives onStartShortcut event from
     * {@link com.android.launcher3.appprediction.PredictionAppTracker}.
     */
    void onStartShortcut(String packageName, String shortcutId, UserHandle user, String container);

    /**
     * Receives onStartApp event from
     * {@link com.android.launcher3.appprediction.PredictionAppTracker}.
     */
    void onStartApp(ComponentName componentName, UserHandle user, String container);

    /**
     * Receives onDismissApp event from
     * {@link com.android.launcher3.appprediction.PredictionAppTracker}.
     */
    void onDismissApp(ComponentName componentName, UserHandle user, String container);

    /**
     * Receives onReturnedToHome event from
     * {@link com.android.launcher3.appprediction.PredictionAppTracker}.
     */
    void onReturnedToHome();
}
