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
import android.content.Context;
import android.widget.FrameLayout;

import com.android.systemui.plugins.annotations.ProvidesInterface;

/**
 * Implement this interface to allow extra card on recents overview.
 */
@ProvidesInterface(action = RecentsExtraCard.ACTION, version = RecentsExtraCard.VERSION)
public interface RecentsExtraCard extends Plugin {

    String ACTION = "com.android.systemui.action.PLUGIN_RECENTS_EXTRA_CARD";
    int VERSION = 1;

    /**
     * Sets up the recents overview extra card and fills in data.
     *
     * @param context Plugin context
     * @param frameLayout PlaceholderView
     * @param activity Recents activity to hold extra view
     */
    void setupView(Context context, FrameLayout frameLayout, Activity activity);
}
