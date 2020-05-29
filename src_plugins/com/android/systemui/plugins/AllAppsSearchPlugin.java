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

package com.android.systemui.plugins;

import android.app.Activity;
import android.view.ViewGroup;
import android.widget.EditText;

import com.android.systemui.plugins.annotations.ProvidesInterface;

/**
 * Implement this plugin interface to replace the all apps recycler view of the all apps drawer.
 */
@ProvidesInterface(action = AllAppsSearchPlugin.ACTION, version = AllAppsSearchPlugin.VERSION)
public interface AllAppsSearchPlugin extends Plugin {
    String ACTION = "com.android.systemui.action.PLUGIN_ALL_APPS_SEARCH_ACTIONS";
    int VERSION = 3;

    /** Following are the order that these methods should be called. */
    void setup(ViewGroup parent, Activity activity, float allAppsContainerHeight);

    /**
     * When drag starts, pass window inset related fields and the progress to indicate
     * whether user is swiping down or swiping up
     */
    void onDragStart(float progress);

    /** progress is between [0, 1] 1: down, 0: up */
    void setProgress(float progress);

    /** Called when container animation stops, so that plugin can perform cleanups */
    void onAnimationEnd(float progress);

    /** pass over the search box object */
    void setEditText(EditText editText);
}
