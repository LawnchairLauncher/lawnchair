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

package com.android.launcher3;

public final class BuildConfig {
    public static final String APPLICATION_ID = "com.android.launcher3";

    public static final boolean IS_STUDIO_BUILD = false;
    /**
     * Flag to state if the QSB is on the first screen and placed on the top,
     * this can be overwritten in other launchers with a different value, if needed.
     */
    public static final boolean QSB_ON_FIRST_SCREEN = true;

    /**
     * Flag to state if the widget on the top of the first screen should be shown.
     */
    public static final boolean WIDGET_ON_FIRST_SCREEN = false;

    /**
     * Flag to control various developer centric features
     */
    public static final boolean IS_DEBUG_DEVICE = false;

    // Flag to control widgets support in Launcher
    public static final boolean WIDGETS_ENABLED = false;
    // Flag to control notification dots support in Launcher
    public static final boolean NOTIFICATION_DOTS_ENABLED = false;
}
