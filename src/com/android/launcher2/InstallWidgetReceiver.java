/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.launcher2;


/**
 * We will likely flesh this out later, to handle allow external apps to place widgets, but for now,
 * we just want to expose the action around for checking elsewhere.
 */
public class InstallWidgetReceiver {
    public static final String ACTION_INSTALL_WIDGET =
            "com.android.launcher.action.INSTALL_WIDGET";

    // Currently not exposed.  Put into Intent when we want to make it public.
    // TEMP: Should we call this "EXTRA_APPWIDGET_PROVIDER"?
    public static final String EXTRA_APPWIDGET_COMPONENT =
        "com.android.launcher.extra.widget.COMPONENT";
    public static final String EXTRA_APPWIDGET_CONFIGURATION_DATA =
        "com.android.launcher.extra.widget.CONFIGURATION_DATA";
}
