/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.sysui;

import android.content.res.Configuration;

/**
 * Callbacks for when the configuration changes.
 */
public interface ConfigurationChangeListener {

    /**
     * Called when a configuration changes. This precedes all the following callbacks.
     */
    default void onConfigurationChanged(Configuration newConfiguration) {}

    /**
     * Convenience method to the above, called when the density or font scale changes.
     */
    default void onDensityOrFontScaleChanged() {}

    /**
     * Convenience method to the above, called when the smallest screen width changes.
     */
    default void onSmallestScreenWidthChanged() {}

    /**
     * Convenience method to the above, called when the system theme changes, including dark/light
     * UI_MODE changes.
     */
    default void onThemeChanged() {}

    /**
     * Convenience method to the above, called when the local list or layout direction changes.
     */
    default void onLocaleOrLayoutDirectionChanged() {}
}
