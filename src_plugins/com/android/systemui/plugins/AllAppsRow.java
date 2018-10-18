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

package com.android.systemui.plugins;

import android.view.View;
import android.view.ViewGroup;

import com.android.systemui.plugins.annotations.ProvidesInterface;

/**
 * Implement this plugin interface to add a row of views to the top of the all apps drawer.
 */
@ProvidesInterface(action = AllAppsRow.ACTION, version = AllAppsRow.VERSION)
public interface AllAppsRow extends Plugin {
    String ACTION = "com.android.systemui.action.PLUGIN_ALL_APPS_ACTIONS";
    int VERSION = 1;

    /**
     * Setup the row and return the parent view.
     * @param parent The ViewGroup to which launcher will add this row.
     */
    View setup(ViewGroup parent);

    /**
     * @return The height to reserve in all apps for your views.
     */
    int getExpectedHeight();

    /**
     * Update launcher whenever {@link #getExpectedHeight()} changes.
     */
    void setOnHeightUpdatedListener(OnHeightUpdatedListener onHeightUpdatedListener);

    interface OnHeightUpdatedListener {
        void onHeightUpdated();
    }
}
