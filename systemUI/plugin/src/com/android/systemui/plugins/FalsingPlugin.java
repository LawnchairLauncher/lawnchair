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

import android.content.Context;

import com.android.systemui.plugins.annotations.DependsOn;
import com.android.systemui.plugins.annotations.ProvidesInterface;

/**
 * Used to capture Falsing data (related to unlocking the screen).
 *
 * The intent is that the data can later be analyzed to validate the quality of the
 * {@link FalsingManager}.
 */
@ProvidesInterface(action = FalsingPlugin.ACTION, version = FalsingPlugin.VERSION)
@DependsOn(target = FalsingManager.class)
public interface FalsingPlugin extends Plugin {
    String ACTION = "com.android.systemui.action.FALSING_PLUGIN";
    int VERSION = 2;

    /**
     * Called when there is data to be recorded.
     *
     * @param success Indicates whether the action is considered a success.
     * @param data The raw data to be recorded for analysis.
     */
    default void dataCollected(boolean success, byte[] data) { }

    /**
     * Return a {@link FalsingManager} to be used in place of the system's default.
     *
     * @param context
     */
    default FalsingManager getFalsingManager(Context context) {
        return null;
    }
}
