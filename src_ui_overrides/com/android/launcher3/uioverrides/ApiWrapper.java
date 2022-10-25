/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.uioverrides;

import android.app.Person;
import android.appwidget.AppWidgetHost;
import android.content.pm.ShortcutInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.Utilities;
import com.android.launcher3.widget.LauncherAppWidgetHost;

/**
 * A wrapper for the hidden API calls
 */
public class ApiWrapper {

    public static final boolean TASKBAR_DRAWN_IN_PROCESS = false;

    public static Person[] getPersons(ShortcutInfo si) {
        return Utilities.EMPTY_PERSON_ARRAY;
    }

    /**
     * Set the interaction handler for the host
     * @param host AppWidgetHost that needs the interaction handler
     * @param handler InteractionHandler for the views in the host
     */
    public static void setHostInteractionHandler(@NonNull AppWidgetHost host,
            @Nullable LauncherAppWidgetHost.LauncherWidgetInteractionHandler handler) {
        // No-op
    }
}
