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

import android.app.Activity;
import android.graphics.Bitmap;
import android.view.ViewGroup;

import com.android.systemui.plugins.annotations.ProvidesInterface;

/**
 * Implement this interface to add action buttons for overview screenshots, e.g. share, edit etc.
 */
@ProvidesInterface(
        action = OverviewScreenshotActions.ACTION, version = OverviewScreenshotActions.VERSION)
public interface OverviewScreenshotActions extends Plugin {
    String ACTION = "com.android.systemui.action.PLUGIN_OVERVIEW_SCREENSHOT_ACTIONS";
    int VERSION = 1;

    /**
     * Setup the actions for the screenshot, including edit, save, etc.
     * @param parent The parent view to add buttons on.
     * @param screenshot The screenshot we will do actions on.
     * @param activity THe host activity.
     */
    void setupActions(ViewGroup parent, Bitmap screenshot, Activity activity);
}
